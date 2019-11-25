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

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.BtcdCli4jVersion;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.client.BtcdClient;
import com.neemre.btcdcli4j.core.client.BtcdClientImpl;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.neemre.btcdcli4j.core.domain.enums.ScriptTypes;
import com.neemre.btcdcli4j.daemon.BtcdDaemon;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;
import java.util.Properties;
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
    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcHost;
    private final String rpcPort;
    private final String rpcBlockPort;
    private final String rpcBlockHost;

    private BtcdClient client;
    private BtcdDaemon daemon;

    // We could use multiple threads but then we need to support ordering of results in a queue
    // Keep that for optimization after measuring performance differences
    private final ListeningExecutorService executor = Utilities.getSingleThreadExecutor("RpcService");


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public RpcService(Preferences preferences,
                      @Named(DaoOptionKeys.RPC_HOST) String rpcHost,
                      @Named(DaoOptionKeys.RPC_PORT) String rpcPort,
                      @Named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) String rpcBlockPort,
                      @Named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_HOST) String rpcBlockHost) {
        this.rpcUser = preferences.getRpcUser();
        this.rpcPassword = preferences.getRpcPw();

        // mainnet is 8332, testnet 18332, regtest 18443
        boolean isHostSet = rpcHost != null && !rpcHost.isEmpty();
        boolean isPortSet = rpcPort != null && !rpcPort.isEmpty();
        boolean isMainnet = BisqEnvironment.getBaseCurrencyNetwork().isMainnet();
        boolean isTestnet = BisqEnvironment.getBaseCurrencyNetwork().isTestnet();
        boolean isDaoBetaNet = BisqEnvironment.getBaseCurrencyNetwork().isDaoBetaNet();
        this.rpcHost = isHostSet ? rpcHost : "127.0.0.1";
        this.rpcPort = isPortSet ? rpcPort :
                isMainnet || isDaoBetaNet ? "8332" :
                        isTestnet ? "18332" :
                                "18443"; // regtest
        boolean isBlockPortSet = rpcBlockPort != null && !rpcBlockPort.isEmpty();
        boolean isBlockHostSet = rpcBlockHost != null && !rpcBlockHost.isEmpty();
        this.rpcBlockPort = isBlockPortSet ? rpcBlockPort : "5125";
        this.rpcBlockHost = isBlockHostSet ? rpcBlockHost : "127.0.0.1";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setup(ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = executor.submit(() -> {
            try {
                log.info("Starting RPCService with btcd-cli4j version {} on {}:{} with user {}, " +
                                "listening for blocknotify on port {} from {}",
                        BtcdCli4jVersion.VERSION, this.rpcHost, this.rpcPort, this.rpcUser, this.rpcBlockPort,
                        this.rpcBlockHost);

                long startTs = System.currentTimeMillis();
                PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
                CloseableHttpClient httpProvider = HttpClients.custom().setConnectionManager(cm).build();
                Properties nodeConfig = new Properties();
                nodeConfig.setProperty("node.bitcoind.rpc.protocol", "http");
                nodeConfig.setProperty("node.bitcoind.rpc.host", rpcHost);
                nodeConfig.setProperty("node.bitcoind.rpc.auth_scheme", "Basic");
                nodeConfig.setProperty("node.bitcoind.rpc.user", rpcUser);
                nodeConfig.setProperty("node.bitcoind.rpc.password", rpcPassword);
                nodeConfig.setProperty("node.bitcoind.rpc.port", rpcPort);
                nodeConfig.setProperty("node.bitcoind.notification.block.port", rpcBlockPort);
                nodeConfig.setProperty("node.bitcoind.notification.block.host", rpcBlockHost);
                nodeConfig.setProperty("node.bitcoind.notification.alert.port", String.valueOf(bisq.network.p2p.Utils.findFreeSystemPort()));
                nodeConfig.setProperty("node.bitcoind.notification.wallet.port", String.valueOf(bisq.network.p2p.Utils.findFreeSystemPort()));

                nodeConfig.setProperty("node.bitcoind.http.auth_scheme", "Basic");
                BtcdClientImpl client = new BtcdClientImpl(httpProvider, nodeConfig);
                daemon = new BtcdDaemonImpl(client, throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                    UserThread.execute(() -> errorHandler.accept(new RpcException(throwable)));
                });
                log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
                this.client = client;
            } catch (BitcoindException | CommunicationException e) {
                if (e instanceof CommunicationException)
                    log.error("Probably Bitcoin Core is not running or the rpc port is not set correctly. rpcPort=" + rpcPort);
                log.error(e.toString());
                e.printStackTrace();
                log.error(e.getCause() != null ? e.getCause().toString() : "e.getCause()=null");
                throw new RpcException(e.getMessage(), e);
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
        });
    }

    void addNewBtcBlockHandler(Consumer<RawBlock> btcBlockHandler,
                               Consumer<Throwable> errorHandler) {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(com.neemre.btcdcli4j.core.domain.RawBlock rawBtcBlock) {
                if (rawBtcBlock.getHeight() == null || rawBtcBlock.getHeight() == 0) {
                    log.warn("We received a RawBlock with no data. blockHash={}", rawBtcBlock.getHash());
                    return;
                }

                try {
                    log.info("New block received: height={}, id={}", rawBtcBlock.getHeight(), rawBtcBlock.getHash());
                    List<RawTx> txList = rawBtcBlock.getTx().stream()
                            .map(e -> getTxFromRawTransaction(e, rawBtcBlock))
                            .collect(Collectors.toList());
                    UserThread.execute(() -> {
                        btcBlockHandler.accept(new RawBlock(rawBtcBlock.getHeight(),
                                rawBtcBlock.getTime() * 1000, // rawBtcBlock.getTime() is in sec but we want ms
                                rawBtcBlock.getHash(),
                                rawBtcBlock.getPreviousBlockHash(),
                                ImmutableList.copyOf(txList)));
                    });
                } catch (Throwable t) {
                    errorHandler.accept(t);
                }
            }
        });
    }

    void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Integer> future = executor.submit(client::getBlockCount);
        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(Integer chainHeight) {
                UserThread.execute(() -> resultHandler.accept(chainHeight));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    void requestBtcBlock(int blockHeight,
                         Consumer<RawBlock> resultHandler,
                         Consumer<Throwable> errorHandler) {
        ListenableFuture<RawBlock> future = executor.submit(() -> {
            long startTs = System.currentTimeMillis();
            String blockHash = client.getBlockHash(blockHeight);
            com.neemre.btcdcli4j.core.domain.RawBlock rawBtcBlock = client.getBlock(blockHash, 2);
            List<RawTx> txList = rawBtcBlock.getTx().stream()
                    .map(e -> getTxFromRawTransaction(e, rawBtcBlock))
                    .collect(Collectors.toList());
            log.info("requestBtcBlock from bitcoind at blockHeight {} with {} txs took {} ms",
                    blockHeight, txList.size(), System.currentTimeMillis() - startTs);
            return new RawBlock(rawBtcBlock.getHeight(),
                    rawBtcBlock.getTime() * 1000, // rawBtcBlock.getTime() is in sec but we want ms
                    rawBtcBlock.getHash(),
                    rawBtcBlock.getPreviousBlockHash(),
                    ImmutableList.copyOf(txList));
        });

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(RawBlock block) {
                UserThread.execute(() -> resultHandler.accept(block));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.error("Error at requestBtcBlock: blockHeight={}", blockHeight);
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RawTx getTxFromRawTransaction(RawTransaction rawBtcTx,
                                          com.neemre.btcdcli4j.core.domain.RawBlock rawBtcBlock) {
        String txId = rawBtcTx.getTxId();
        long blockTime = rawBtcBlock.getTime() * 1000; // We convert block time from sec to ms
        int blockHeight = rawBtcBlock.getHeight();
        String blockHash = rawBtcBlock.getHash();
        final List<TxInput> txInputs = rawBtcTx.getVIn()
                .stream()
                .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                .map(rawInput -> {
                    // We don't support segWit inputs yet as well as no pay to pubkey txs...
                    String[] split = rawInput.getScriptSig().getAsm().split("\\[ALL] ");
                    String pubKeyAsHex;
                    if (split.length == 2) {
                        pubKeyAsHex = rawInput.getScriptSig().getAsm().split("\\[ALL] ")[1];
                    } else {
                        // If we receive a pay to pubkey tx the pubKey is not included as
                        // it is in the output already.
                        // Bitcoin Core creates payToPubKey tx when spending mined coins (regtest)...
                        pubKeyAsHex = null;
                        log.debug("pubKeyAsHex is not set as we received a not supported sigScript " +
                                        "(segWit or payToPubKey tx). txId={}, asm={}",
                                rawBtcTx.getTxId(), rawInput.getScriptSig().getAsm());
                    }
                    return new TxInput(rawInput.getTxId(), rawInput.getVOut(), pubKeyAsHex);
                })
                .collect(Collectors.toList());

        final List<RawTxOutput> txOutputs = rawBtcTx.getVOut()
                .stream()
                .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                .map(rawBtcTxOutput -> {
                            byte[] opReturnData = null;
                            com.neemre.btcdcli4j.core.domain.PubKeyScript scriptPubKey = rawBtcTxOutput.getScriptPubKey();
                            if (ScriptTypes.NULL_DATA.equals(scriptPubKey.getType()) && scriptPubKey.getAsm() != null) {
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
                            return new RawTxOutput(rawBtcTxOutput.getN(),
                                    rawBtcTxOutput.getValue().movePointRight(8).longValue(),
                                    rawBtcTx.getTxId(),
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
}
