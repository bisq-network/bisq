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

package bisq.burningmandump;

import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.burningman.model.LegacyBurningMan;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Headless CLI app: boots the same modules as the seed/desktop node, syncs the DAO state via P2P,
 * and once block-chain parsing is complete dumps the latest burningman receiver addresses to a
 * properties-style .conf file in the appDataDir, then shuts down.
 *
 * Reuse an existing desktop data dir by passing e.g. --appName=Bisq --appDataDir=/path/to/Bisq.
 */
@Slf4j
public class BurningmanDumpMain extends ExecutableForAppWithP2p {

    private static final String INCLUDE_LEGACY_FLAG = "--include-legacy-burningmen";
    private static final String FULL_DAO_NODE_FLAG = "--fullDaoNode";

    public static void main(String[] args) {
        BurningmanDumpMain app = new BurningmanDumpMain();
        // Strip our own custom flag before passing args to BisqExecutable's joptsimple parser, which
        // would otherwise reject unknown options.
        List<String> remaining = new ArrayList<>(args.length + 1);
        boolean userSetFullDaoNode = false;
        boolean fullDaoNodeValue = false;
        for (String arg : args) {
            if (arg.equals(INCLUDE_LEGACY_FLAG)) {
                app.includeLegacyBurningmen = true;
            } else if (arg.startsWith(INCLUDE_LEGACY_FLAG + "=")) {
                app.includeLegacyBurningmen = Boolean.parseBoolean(arg.substring(INCLUDE_LEGACY_FLAG.length() + 1));
            } else {
                if (arg.equals(FULL_DAO_NODE_FLAG)) {
                    userSetFullDaoNode = true;
                    fullDaoNodeValue = true;
                } else if (arg.startsWith(FULL_DAO_NODE_FLAG + "=")) {
                    userSetFullDaoNode = true;
                    fullDaoNodeValue = Boolean.parseBoolean(arg.substring(FULL_DAO_NODE_FLAG.length() + 1));
                }
                remaining.add(arg);
            }
        }
        // Always pin fullDaoNode explicitly so BsqNodeProvider can't silently flip a reused desktop
        // dir's persisted pref (preferences.setDaoFullNode only persists when the CLI flag is NOT set).
        // Default lite when caller doesn't pass it.
        if (!userSetFullDaoNode) {
            remaining.add(FULL_DAO_NODE_FLAG + "=false");
        }
        // Lite node needs the BTC SPV wallet to know the chain tip and drive block requests
        // (LiteNode gates on walletsSetup.isDownloadComplete()). Full DAO node fetches via bitcoind
        // RPC and does not depend on the wallet, so we skip wallet startup there.
        app.walletEnabled = !fullDaoNodeValue;
        app.execute(remaining.toArray(new String[0]));
    }

    private DaoStateService daoStateService;
    private BurningManPresentationService burningManPresentationService;
    private WalletsSetup walletsSetup;
    private WalletsManager walletsManager;
    private final AtomicBoolean dumped = new AtomicBoolean(false);
    private boolean includeLegacyBurningmen = false;
    private boolean walletEnabled = true;

    public BurningmanDumpMain() {
        super("Bisq Burningman Dump", "bisq-burningman-dump", "bisq_burningman_dump", Version.VERSION);
    }

    @Override
    protected void doExecute() {
        super.doExecute();
        keepRunning();
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();
        daoStateService = injector.getInstance(DaoStateService.class);
        burningManPresentationService = injector.getInstance(BurningManPresentationService.class);
        if (walletEnabled) {
            walletsSetup = injector.getInstance(WalletsSetup.class);
            walletsManager = injector.getInstance(WalletsManager.class);
        }
    }

    @Override
    protected void startApplication() {
        super.startApplication();

        // ExecutableForAppWithP2p does not initialize WalletsSetup. The lite DAO node depends on the
        // BTC SPV wallet (chain tip + download-complete signal) to request BSQ blocks from seed nodes.
        // Without this call lite nodes never call requestBlocks(...) and only P2P data is fetched.
        // Full DAO node fetches via bitcoind RPC and skips this.
        if (walletEnabled) {
            walletsSetup.initialize(null,
                    () -> {
                        if (walletsManager.areWalletsEncrypted()) {
                            log.error("Wallet is encrypted. Headless dump cannot prompt for password — aborting.");
                            shutDown(this);
                            return;
                        }
                        log.info("WalletsSetup initialized; SPV download started.");
                    },
                    exception -> log.error("WalletsSetup initialization failed", exception));
        } else {
            log.info("Wallet disabled (--fullDaoNode=true). Using bitcoind RPC for DAO sync.");
        }

        daoStateService.addDaoStateListener(new DaoStateListener() {
            @Override
            public void onParseBlockChainComplete() {
                tryDump();
            }

            @Override
            public void onParseBlockCompleteAfterBatchProcessing(Block block) {
                if (daoStateService.isParseBlockChainComplete()) {
                    tryDump();
                }
            }
        });

        if (daoStateService.isParseBlockChainComplete()) {
            tryDump();
        }
    }

    private void tryDump() {
        if (!dumped.compareAndSet(false, true)) {
            return;
        }
        // Run on UserThread so BurningManPresentationService caches are stable.
        UserThread.execute(() -> {
            try {
                writeDump();
            } catch (Throwable t) {
                log.error("Dump failed", t);
            } finally {
                shutDown(this);
            }
        });
    }

    private void writeDump() throws Exception {
        Config config = injector.getInstance(Config.class);
        int chainHeight = daoStateService.getChainHeight();

        Map<String, BurningManCandidate> byName = burningManPresentationService.getBurningManCandidatesByName();

        List<Map.Entry<String, BurningManCandidate>> sorted = new ArrayList<>(byName.entrySet());
        sorted.sort(Comparator.comparingDouble(
                (Map.Entry<String, BurningManCandidate> e) -> e.getValue().getCappedBurnAmountShare()).reversed());

        StringBuilder out = new StringBuilder();
        out.append("# Bisq burningman receiver addresses\n");
        out.append("# Network: ").append(Config.baseCurrencyNetwork().name()).append('\n');
        out.append("# Chain height: ").append(chainHeight).append('\n');
        out.append("# Generated at: ").append(Instant.now()).append('\n');
        out.append("# Include legacy: ").append(includeLegacyBurningmen).append('\n');
        out.append("# Format: one bitcoin address per line. Lines starting with '#' or blank lines\n");
        out.append("# are comments / separators and describe the entry on the following non-comment line.\n");
        out.append("# Generated by bisq-burningman-dump.\n\n");

        int written = 0;
        for (Map.Entry<String, BurningManCandidate> e : sorted) {
            if (appendEntry(out, e.getKey(), e.getValue(), false)) {
                written++;
            }
        }

        int legacyWritten = 0;
        if (includeLegacyBurningmen) {
            // Legacy burningmen — receivers of the redistributed share that no other candidate could claim.
            if (appendEntry(out,
                    BurningManPresentationService.LEGACY_BURNING_MAN_DPT_NAME,
                    burningManPresentationService.getLegacyBurningManForDPT(),
                    true)) {
                legacyWritten++;
            }
            if (appendEntry(out,
                    BurningManPresentationService.LEGACY_BURNING_MAN_BTC_FEES_NAME,
                    burningManPresentationService.getLegacyBurningManForBtcFees(),
                    true)) {
                legacyWritten++;
            }
        }

        Path path = config.appDataDir.toPath().resolve("burningman_addresses.conf");
        Files.writeString(path, out.toString());
        log.info("Wrote {} addresses ({} regular + {} legacy) to {}",
                written + legacyWritten, written, legacyWritten, path);
    }

    private static boolean appendEntry(StringBuilder out, String name, BurningManCandidate c, boolean legacy) {
        String address = c.getReceiverAddress().orElse("");
        if (address.isEmpty()) {
            // Skip entries with no receiver address — output is meant to be an iterable address list.
            return false;
        }
        boolean valid = c.isReceiverAddressValid();

        out.append("# owner: ").append(name);
        if (legacy) {
            out.append(" (legacy)");
        }
        out.append('\n');
        out.append(String.format(
                "# burnShareCapped=%.4f%% burnShare=%.4f%% compShare=%.4f%% burnedSat=%d compSat=%d addrValid=%s%n",
                c.getCappedBurnAmountShare() * 100,
                c.getBurnAmountShare() * 100,
                c.getCompensationShare() * 100,
                c.getAccumulatedBurnAmount(),
                c.getAccumulatedCompensationAmount(),
                valid));
        if (!legacy && c.getAllAddresses().size() > 1) {
            out.append("# alt compensation addresses: ").append(c.getAllAddresses()).append('\n');
        }
        out.append(address).append("\n\n");
        return true;
    }
}
