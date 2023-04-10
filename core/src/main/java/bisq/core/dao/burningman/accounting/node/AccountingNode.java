/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.burningman.accounting.node;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.node.full.AccountingBlockParser;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.user.Preferences;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static org.bitcoinj.core.Utils.HEX;

@Slf4j
public abstract class AccountingNode implements DaoSetupService, DaoStateListener {
    public static final Set<String> PERMITTED_PUB_KEYS = Set.of("034527b1c2b644283c19c180efbcc9ba51258fbe5c5ce0c95b522f1c9b07896e48",
            "02a06121632518eef4400419a3aaf944534e8cf357138dd82bba3ad78ce5902f27",
            "029ff0da89aa03507dfe0529eb74a53bc65fbee7663ceba04f014b0ee4520973b5",
            "0205c992604969bc70914fc89a6daa43cd5157ac886224a8c0901dd1dc6dd1df45",
            "023e699281b3ee41f35991f064a5c12cb1b61286dda22c8220b3f707aa21235efb");

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Oracle verification
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Sha256Hash getSha256Hash(AccountingBlock block) {
        return Sha256Hash.of(block.toProtoMessage().toByteArray());
    }

    @Nullable
    public static Sha256Hash getSha256Hash(Collection<AccountingBlock> blocks) {
        long ts = System.currentTimeMillis();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (AccountingBlock accountingBlock : blocks) {
                outputStream.write(accountingBlock.toProtoMessage().toByteArray());
            }
            Sha256Hash hash = Sha256Hash.of(outputStream.toByteArray());
            // 2833 blocks takes about 23 ms
            log.info("getSha256Hash for {} blocks took {} ms", blocks.size(), System.currentTimeMillis() - ts);
            return hash;
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] getSignature(Sha256Hash sha256Hash, ECKey privKey) {
        ECKey.ECDSASignature ecdsaSignature = privKey.sign(sha256Hash);
        return ecdsaSignature.encodeToDER();
    }

    public static boolean isValidPubKeyAndSignature(Sha256Hash sha256Hash,
                                                    String pubKey,
                                                    byte[] signature,
                                                    boolean useDevPrivilegeKeys) {
        if (!getPermittedPubKeys(useDevPrivilegeKeys).contains(pubKey)) {
            log.warn("PubKey is not in supported key set. pubKey={}", pubKey);
            return false;
        }

        try {
            ECKey.ECDSASignature ecdsaSignature = ECKey.ECDSASignature.decodeFromDER(signature);
            ECKey ecPubKey = ECKey.fromPublicOnly(HEX.decode(pubKey));
            return ecPubKey.verify(sha256Hash, ecdsaSignature);
        } catch (Throwable e) {
            log.warn("Signature verification failed.");
            return false;
        }
    }

    public static boolean isPermittedPubKey(boolean useDevPrivilegeKeys, String pubKey) {
        return getPermittedPubKeys(useDevPrivilegeKeys).contains(pubKey);
    }

    private static Set<String> getPermittedPubKeys(boolean useDevPrivilegeKeys) {
        return useDevPrivilegeKeys ? Set.of(DevEnv.DEV_PRIVILEGE_PUB_KEY) : PERMITTED_PUB_KEYS;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected final P2PService p2PService;
    private final DaoStateService daoStateService;
    protected final BurningManAccountingService burningManAccountingService;
    protected final AccountingBlockParser accountingBlockParser;
    private final Preferences preferences;

    @Nullable
    protected Consumer<String> errorMessageHandler;
    @Nullable
    protected Consumer<String> warnMessageHandler;
    protected BootstrapListener bootstrapListener;
    protected int tryReorgCounter;
    protected boolean p2pNetworkReady;
    protected boolean initialBlockRequestsComplete;

    public AccountingNode(P2PService p2PService,
                          DaoStateService daoStateService,
                          BurningManAccountingService burningManAccountingService,
                          AccountingBlockParser accountingBlockParser,
                          Preferences preferences) {
        this.p2PService = p2PService;
        this.daoStateService = daoStateService;
        this.burningManAccountingService = burningManAccountingService;
        this.accountingBlockParser = accountingBlockParser;
        this.preferences = preferences;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        onInitialDaoBlockParsingComplete();

        // We get called onParseBlockChainComplete at each new block arriving but we want to react only after initial
        // parsing is done, so we remove after getting called ourself as listener.
        daoStateService.removeDaoStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        if (!preferences.isProcessBurningManAccountingData()) {
            return;
        }

        if (daoStateService.isParseBlockChainComplete()) {
            log.info("daoStateService.isParseBlockChainComplete is already true, " +
                    "we call onInitialDaoBlockParsingComplete directly");
            onInitialDaoBlockParsingComplete();
        } else {
            daoStateService.addDaoStateListener(this);
        }

        bootstrapListener = new BootstrapListener() {
            @Override
            public void onNoSeedNodeAvailable() {
                onP2PNetworkReady();
            }

            @Override
            public void onUpdatedDataReceived() {
                onP2PNetworkReady();
            }
        };
    }

    @Override
    public void start() {
        // We do not start yet but wait until DAO block parsing is complete to not interfere with
        // that higher priority activity.
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setErrorMessageHandler(@SuppressWarnings("NullableProblems") Consumer<String> errorMessageHandler) {
        this.errorMessageHandler = errorMessageHandler;
    }

    public void setWarnMessageHandler(@SuppressWarnings("NullableProblems") Consumer<String> warnMessageHandler) {
        this.warnMessageHandler = warnMessageHandler;
    }

    public abstract void shutDown();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void onInitialDaoBlockParsingComplete();

    protected void onInitialized() {
        if (p2PService.isBootstrapped()) {
            log.info("p2PService.isBootstrapped is already true, we call onP2PNetworkReady directly.");
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(bootstrapListener);
        }
    }

    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
        p2PService.removeP2PServiceListener(bootstrapListener);
    }

    protected abstract void startRequestBlocks();

    protected void onInitialBlockRequestsComplete() {
        initialBlockRequestsComplete = true;
        burningManAccountingService.onInitialBlockRequestsComplete();
    }

    protected void applyReOrg() {
        log.warn("applyReOrg called");
        tryReorgCounter++;
        if (tryReorgCounter < 5) {
            burningManAccountingService.purgeLastTenBlocks();
            // Increase delay at each retry
            UserThread.runAfter(this::startRequestBlocks, (long) tryReorgCounter * tryReorgCounter);
        } else {
            log.warn("We tried {} times to request blocks again after a reorg signal but it is still failing.", tryReorgCounter);
        }
    }
}
