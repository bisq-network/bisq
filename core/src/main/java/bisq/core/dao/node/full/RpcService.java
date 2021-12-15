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

package bisq.core.dao.node.full;

import bisq.core.dao.node.full.rpc.BitcoindClient;
import bisq.core.dao.node.full.rpc.BitcoindDaemon;
import bisq.core.dao.node.full.rpc.dto.DtoPubKeyScript;
import bisq.core.dao.node.full.rpc.dto.RawDtoBlock;
import bisq.core.dao.node.full.rpc.dto.RawDtoInput;
import bisq.core.dao.node.full.rpc.dto.RawDtoTransaction;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.ScriptType;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.primitives.Chars;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;

import java.math.BigDecimal;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Request blockchain data via RPC from Bitcoin Core for a FullNode.
 * Runs in a custom thread.
 * See the rpc.md file in the doc directory for more info about the setup.
 */
@Slf4j
public class RpcService {
    private static final int ACTIVATE_HARD_FORK_2_HEIGHT_MAINNET = 680300;
    private static final int ACTIVATE_HARD_FORK_2_HEIGHT_TESTNET = 1943000;
    private static final int ACTIVATE_HARD_FORK_2_HEIGHT_REGTEST = 1;
    private static final Range<Integer> SUPPORTED_NODE_VERSION_RANGE = Range.closedOpen(180000, 210100);

    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcHost;
    private final int rpcPort;
    private final int rpcBlockPort;
    private final String rpcBlockHost;

    private BitcoindClient client;
    private BitcoindDaemon daemon;

    // We could use multiple threads, but then we need to support ordering of results in a queue
    // Keep that for optimization after measuring performance differences
    private final ListeningExecutorService executor = Utilities.getSingleThreadListeningExecutor("RpcService");
    private volatile boolean isShutDown;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RpcService(Preferences preferences,
                       @Named(Config.RPC_HOST) String rpcHost,
                       @Named(Config.RPC_PORT) int rpcPort,
                       @Named(Config.RPC_BLOCK_NOTIFICATION_PORT) int rpcBlockPort,
                       @Named(Config.RPC_BLOCK_NOTIFICATION_HOST) String rpcBlockHost) {
        this.rpcUser = preferences.getRpcUser();
        this.rpcPassword = preferences.getRpcPw();

        // mainnet is 8332, testnet 18332, regtest 18443
        boolean isHostSet = !rpcHost.isEmpty();
        boolean isPortSet = rpcPort != Config.UNSPECIFIED_PORT;
        boolean isMainnet = Config.baseCurrencyNetwork().isMainnet();
        boolean isTestnet = Config.baseCurrencyNetwork().isTestnet();
        boolean isDaoBetaNet = Config.baseCurrencyNetwork().isDaoBetaNet();
        this.rpcHost = isHostSet ? rpcHost : "127.0.0.1";
        this.rpcPort = isPortSet ? rpcPort :
                isMainnet || isDaoBetaNet ? 8332 :
                        isTestnet ? 18332 :
                                18443; // regtest
        boolean isBlockPortSet = rpcBlockPort != Config.UNSPECIFIED_PORT;
        boolean isBlockHostSet = !rpcBlockHost.isEmpty();
        this.rpcBlockPort = isBlockPortSet ? rpcBlockPort : 5125;
        this.rpcBlockHost = isBlockHostSet ? rpcBlockHost : "127.0.0.1";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        isShutDown = true;
        if (daemon != null) {
            daemon.shutdown();
            log.info("daemon shut down");
        }

        // A hard shutdown is justified for the RPC service.
        executor.shutdown();
    }

    void setup(ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        try {
            ListenableFuture<Void> future = executor.submit(() -> {
                try {
                    log.info("Starting RpcService on {}:{} with user {}, listening for blocknotify on port {} from {}",
                            this.rpcHost, this.rpcPort, this.rpcUser, this.rpcBlockPort, this.rpcBlockHost);

                    long startTs = System.currentTimeMillis();

                    client = BitcoindClient.builder()
                            .rpcHost(rpcHost)
                            .rpcPort(rpcPort)
                            .rpcUser(rpcUser)
                            .rpcPassword(rpcPassword)
                            .build();
                    checkNodeVersionAndHealth();

                    daemon = new BitcoindDaemon(rpcBlockHost, rpcBlockPort, throwable -> {
                        log.error(throwable.toString());
                        throwable.printStackTrace();
                        UserThread.execute(() -> errorHandler.accept(new RpcException(throwable)));
                    });

                    log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
                } catch (Throwable e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    throw new RpcException(e.toString(), e);
                }
                return null;
            });

            Futures.addCallback(future, new FutureCallback<>() {
                public void onSuccess(Void ignore) {
                    UserThread.execute(resultHandler::handleResult);
                }

                public void onFailure(@NotNull Throwable throwable) {
                    UserThread.execute(() -> errorHandler.accept(throwable));
                }
            }, MoreExecutors.directExecutor());
        } catch (Exception e) {
            if (!isShutDown || !(e instanceof RejectedExecutionException)) {
                log.warn(e.toString(), e);
                throw e;
            }
        }
    }

    private String decodeNodeVersion(Integer encodedVersion) {
        var paddedEncodedVersion = Strings.padStart(encodedVersion.toString(), 8, '0');

        return Lists.partition(Chars.asList(paddedEncodedVersion.toCharArray()), 2).stream()
                .map(chars -> new String(Chars.toArray(chars)).replaceAll("^0", ""))
                .collect(Collectors.joining("."))
                .replaceAll("\\.0$", "");
    }

    private void checkNodeVersionAndHealth() throws IOException {
        var networkInfo = client.getNetworkInfo();
        var nodeVersion = decodeNodeVersion(networkInfo.getVersion());

        if (SUPPORTED_NODE_VERSION_RANGE.contains(networkInfo.getVersion())) {
            log.info("Got Bitcoin Core version: {}", nodeVersion);
        } else {
            log.warn("Server version mismatch - client optimized for '[{} .. {})', node responded with '{}'",
                    decodeNodeVersion(SUPPORTED_NODE_VERSION_RANGE.lowerEndpoint()),
                    decodeNodeVersion(SUPPORTED_NODE_VERSION_RANGE.upperEndpoint()), nodeVersion);
        }

        var bestRawBlock = client.getBlock(client.getBestBlockHash(), 1);
        long currentTime = System.currentTimeMillis() / 1000;
        if ((currentTime - bestRawBlock.getTime()) > TimeUnit.HOURS.toSeconds(6)) {
            log.warn("Last available block was mined >{} hours ago; please check your network connection",
                    ((currentTime - bestRawBlock.getTime()) / 3600));
        }
    }

    void addNewDtoBlockHandler(Consumer<RawBlock> dtoBlockHandler,
                               Consumer<Throwable> errorHandler) {
        daemon.setBlockListener(blockHash -> {
            try {
                var rawDtoBlock = client.getBlock(blockHash, 2);
                log.info("New block received: height={}, id={}", rawDtoBlock.getHeight(), rawDtoBlock.getHash());

                var block = getBlockFromRawDtoBlock(rawDtoBlock);
                UserThread.execute(() -> dtoBlockHandler.accept(block));
            } catch (Throwable t) {
                errorHandler.accept(t);
            }
        });
    }

    void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        try {
            ListenableFuture<Integer> future = executor.submit(client::getBlockCount);
            Futures.addCallback(future, new FutureCallback<>() {
                public void onSuccess(Integer chainHeight) {
                    UserThread.execute(() -> resultHandler.accept(chainHeight));
                }

                public void onFailure(@NotNull Throwable throwable) {
                    UserThread.execute(() -> errorHandler.accept(throwable));
                }
            }, MoreExecutors.directExecutor());
        } catch (Exception e) {
            if (!isShutDown || !(e instanceof RejectedExecutionException)) {
                log.warn(e.toString(), e);
                throw e;
            }
        }
    }

    void requestDtoBlock(int blockHeight,
                         Consumer<RawBlock> resultHandler,
                         Consumer<Throwable> errorHandler) {
        try {
            ListenableFuture<RawBlock> future = executor.submit(() -> {
                long startTs = System.currentTimeMillis();
                String blockHash = client.getBlockHash(blockHeight);
                var rawDtoBlock = client.getBlock(blockHash, 2);
                var block = getBlockFromRawDtoBlock(rawDtoBlock);
                log.info("requestDtoBlock from bitcoind at blockHeight {} with {} txs took {} ms",
                        blockHeight, block.getRawTxs().size(), System.currentTimeMillis() - startTs);
                return block;
            });

            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(RawBlock block) {
                    UserThread.execute(() -> resultHandler.accept(block));
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.error("Error at requestDtoBlock: blockHeight={}", blockHeight);
                    UserThread.execute(() -> errorHandler.accept(throwable));
                }
            }, MoreExecutors.directExecutor());
        } catch (Exception e) {
            if (!isShutDown || !(e instanceof RejectedExecutionException)) {
                log.warn(e.toString(), e);
                throw e;
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static RawBlock getBlockFromRawDtoBlock(RawDtoBlock rawDtoBlock) {
        List<RawTx> txList = rawDtoBlock.getTx().stream()
                .map(e -> getTxFromRawTransaction(e, rawDtoBlock))
                .collect(Collectors.toList());
        return new RawBlock(rawDtoBlock.getHeight(),
                rawDtoBlock.getTime() * 1000, // rawDtoBlock.getTime() is in sec but we want ms
                rawDtoBlock.getHash(),
                rawDtoBlock.getPreviousBlockHash(),
                ImmutableList.copyOf(txList));
    }

    private static RawTx getTxFromRawTransaction(RawDtoTransaction rawDtoTx,
                                                 RawDtoBlock rawDtoBlock) {
        String txId = rawDtoTx.getTxId();
        long blockTime = rawDtoBlock.getTime() * 1000; // We convert block time from sec to ms
        int blockHeight = rawDtoBlock.getHeight();
        String blockHash = rawDtoBlock.getHash();

        // Extracting pubKeys for segwit (P2WPKH) inputs, instead of just P2PKH inputs as
        // originally, changes the DAO state and thus represents a hard fork. We disallow
        // it until the fork activates, which is determined by block height.
        boolean allowSegwit = blockHeight >= getActivateHardFork2Height();

        final List<TxInput> txInputs = rawDtoTx.getVIn()
                .stream()
                .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                .map(rawInput -> {
                    String pubKeyAsHex = extractPubKeyAsHex(rawInput, allowSegwit);
                    if (pubKeyAsHex == null) {
                        log.debug("pubKeyAsHex is not set as we received a not supported sigScript. " +
                                        "txId={}, asm={}, txInWitness={}",
                                rawDtoTx.getTxId(), rawInput.getScriptSig().getAsm(), rawInput.getTxInWitness());
                    }
                    return new TxInput(rawInput.getTxId(), rawInput.getVOut(), pubKeyAsHex);
                })
                .collect(Collectors.toList());

        final List<RawTxOutput> txOutputs = rawDtoTx.getVOut()
                .stream()
                .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                .map(rawDtoTxOutput -> {
                            byte[] opReturnData = null;
                            DtoPubKeyScript scriptPubKey = rawDtoTxOutput.getScriptPubKey();
                            if (ScriptType.NULL_DATA.equals(scriptPubKey.getType()) && scriptPubKey.getAsm() != null) {
                                String[] chunks = scriptPubKey.getAsm().split(" ");
                                // We get on testnet a lot of "OP_RETURN 0" data, so we filter those away
                                if (chunks.length == 2 && "OP_RETURN".equals(chunks[0]) && !"0".equals(chunks[1])) {
                                    try {
                                        opReturnData = Utils.HEX.decode(chunks[1]);
                                    } catch (Throwable t) {
                                        log.debug("Error at Utils.HEX.decode(chunks[1]): " + t.toString() +
                                                " / chunks[1]=" + chunks[1] +
                                                "\nWe get sometimes exceptions with opReturn data, seems BitcoinJ " +
                                                "cannot handle all " +
                                                "existing OP_RETURN data, but we ignore them anyway as the OP_RETURN " +
                                                "data used for DAO transactions are all valid in BitcoinJ");
                                    }
                                }
                            }
                            // We don't support raw MS which are the only case where scriptPubKey.getAddresses()>1
                            String address = scriptPubKey.getAddresses() != null &&
                                    scriptPubKey.getAddresses().size() == 1 ? scriptPubKey.getAddresses().get(0) : null;
                            PubKeyScript pubKeyScript = new PubKeyScript(scriptPubKey);
                            return new RawTxOutput(rawDtoTxOutput.getN(),
                                    BigDecimal.valueOf(rawDtoTxOutput.getValue()).movePointRight(8).longValueExact(),
                                    rawDtoTx.getTxId(),
                                    pubKeyScript,
                                    address,
                                    opReturnData,
                                    blockHeight);
                        }
                )
                .collect(Collectors.toList());

        return new RawTx(txId,
                blockHeight,
                blockHash,
                blockTime,
                ImmutableList.copyOf(txInputs),
                ImmutableList.copyOf(txOutputs));
    }

    private static int getActivateHardFork2Height() {
        return Config.baseCurrencyNetwork().isMainnet() ? ACTIVATE_HARD_FORK_2_HEIGHT_MAINNET :
                Config.baseCurrencyNetwork().isTestnet() ? ACTIVATE_HARD_FORK_2_HEIGHT_TESTNET :
                        ACTIVATE_HARD_FORK_2_HEIGHT_REGTEST;
    }

    @VisibleForTesting
    static String extractPubKeyAsHex(RawDtoInput rawInput, boolean allowSegwit) {
        // We only allow inputs with a single SIGHASH_ALL signature. That is, multisig or
        // signing of only some of the tx inputs/outputs is intentionally disallowed...
        if (rawInput.getScriptSig() == null) {
            // coinbase input - no pubKey to extract
            return null;
        }
        String[] split = rawInput.getScriptSig().getAsm().split(" ");
        if (split.length == 2 && split[0].endsWith("[ALL]")) {
            // P2PKH input
            return split[1];
        }
        List<String> txInWitness = rawInput.getTxInWitness() != null ? rawInput.getTxInWitness() : List.of();
        if (allowSegwit && split.length < 2 && txInWitness.size() == 2 && txInWitness.get(0).endsWith("01")) {
            // P2WPKH or P2SH-P2WPKH input
            return txInWitness.get(1);
        }
        // If we receive a pay to pubkey tx, the pubKey is not included as it is in the
        // output already.
        return null;
    }
}
