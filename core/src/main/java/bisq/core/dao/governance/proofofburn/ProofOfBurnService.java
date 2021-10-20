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

package bisq.core.dao.governance.proofofburn;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.base.Charsets;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.security.SignatureException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Utils.HEX;

@Slf4j
public class ProofOfBurnService implements DaoSetupService, DaoStateListener {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final MyProofOfBurnListService myProofOfBurnListService;
    private final DaoStateService daoStateService;

    @Getter
    private IntegerProperty updateFlag = new SimpleIntegerProperty(0);
    @Getter
    private final List<Tx> proofOfBurnTxList = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProofOfBurnService(BsqWalletService bsqWalletService,
                              BtcWalletService btcWalletService,
                              WalletsManager walletsManager,
                              MyProofOfBurnListService myProofOfBurnListService,
                              DaoStateService daoStateService) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
        this.myProofOfBurnListService = myProofOfBurnListService;
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
    }

    private void updateList() {
        proofOfBurnTxList.clear();
        proofOfBurnTxList.addAll(getAllProofOfBurnTxs());

        updateFlag.set(updateFlag.get() + 1);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction burn(String preImageAsString, long amount) throws InsufficientMoneyException, TxException {
        try {
            // We create a prepared Bsq Tx for the burn amount
            Transaction preparedBurnFeeTx = bsqWalletService.getPreparedProofOfBurnTx(Coin.valueOf(amount));
            byte[] hash = getHashFromPreImage(preImageAsString);
            byte[] opReturnData = ProofOfBurnConsensus.getOpReturnData(hash);
            // We add the BTC inputs for the miner fee.
            Transaction txWithBtcFee = btcWalletService.completePreparedBurnBsqTx(preparedBurnFeeTx, opReturnData);
            // We sign the BSQ inputs of the final tx.
            Transaction transaction = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
            log.info("Proof of burn tx: " + transaction);
            return transaction;
        } catch (WalletException | TransactionVerificationException e) {
            throw new TxException(e);
        }
    }

    public void publishTransaction(Transaction transaction, String preImageAsString, ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, TxType.PROOF_OF_BURN, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("Proof of burn tx has been published. TxId={}", transaction.getTxId().toString());
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });

        MyProofOfBurn myProofOfBurn = new MyProofOfBurn(transaction.getTxId().toString(), preImageAsString);
        myProofOfBurnListService.addMyProofOfBurn(myProofOfBurn);
    }

    public byte[] getHashFromOpReturnData(Tx tx) {
        return ProofOfBurnConsensus.getHashFromOpReturnData(tx.getLastTxOutput().getOpReturnData());
    }

    public String getHashAsString(String preImageAsString) {
        return Utilities.bytesAsHexString(getHashFromPreImage(preImageAsString));
    }

    public Optional<Tx> getTx(String txId) {
        return daoStateService.getTx(txId);
    }

    // Of connected output of first input. Used for signing and verification.
    // Proofs ownership of the proof of burn tx.
    public byte[] getPubKey(String txId) {
        return daoStateService.getTx(txId)
                .map(tx -> tx.getTxInputs().get(0))
                .map(e -> Utilities.decodeFromHex(e.getPubKey()))
                .orElse(new byte[0]);
    }

    public String getPubKeyAsHex(String proofOfBurnTxId) {
        return Utilities.bytesAsHexString(getPubKey(proofOfBurnTxId));
    }

    public Optional<String> sign(String proofOfBurnTxId, String message) {
        byte[] pubKey = getPubKey(proofOfBurnTxId);
        ECKey key = bsqWalletService.findKeyFromPubKey(pubKey);
        if (key == null)
            return Optional.empty();

        try {
            String signatureBase64 = bsqWalletService.isEncrypted()
                    ? key.signMessage(message, bsqWalletService.getAesKey())
                    : key.signMessage(message);
            return Optional.of(signatureBase64);
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            return Optional.empty();
        }
    }

    public void verify(String message, String pubKey, String signatureBase64) throws SignatureException {
        ECKey key = ECKey.fromPublicOnly(HEX.decode(pubKey));
        checkNotNull(key, "ECKey must not be null");
        key.verifyMessage(message, signatureBase64);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<Tx> getAllProofOfBurnTxs() {
        return daoStateService.getProofOfBurnOpReturnTxOutputs().stream()
                .map(txOutput -> daoStateService.getTx(txOutput.getTxId()).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BaseTx::getTime).reversed())
                .collect(Collectors.toList());
    }

    private byte[] getHashFromPreImage(String preImageAsString) {
        byte[] preImage = preImageAsString.getBytes(Charsets.UTF_8);
        return ProofOfBurnConsensus.getHash(preImage);
    }

    public long getAmount(Tx tx) {
        return tx.getBurntFee();
    }
}
