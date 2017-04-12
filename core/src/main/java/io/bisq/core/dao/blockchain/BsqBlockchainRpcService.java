/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.client.BtcdClientImpl;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;
import io.bisq.common.UserThread;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

// We are in threaded context. We map our results to the client into the UserThread to not extend thread contexts.
public class BsqBlockchainRpcService extends BsqBlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainRpcService.class);

    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcPort;
    private final String rpcBlockPort;
    private final ListeningExecutorService setupExecutor = Utilities.getListeningExecutorService("RpcService.setup", 1, 1, 5);
    private final ListeningExecutorService parseBlockchainExecutor = Utilities.getListeningExecutorService("RpcService.requests", 1, 1, 10);
    private final ListeningExecutorService getChainHeightExecutor = Utilities.getListeningExecutorService("RpcService.requests", 1, 1, 10);
    private BtcdClientImpl client;
    private BtcdDaemonImpl daemon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainRpcService(KeyRing keyRing,
                                   @Named(RpcOptionKeys.RPC_USER) String rpcUser,
                                   @Named(RpcOptionKeys.RPC_PASSWORD) String rpcPassword,
                                   @Named(RpcOptionKeys.RPC_PORT) String rpcPort,
                                   @Named(RpcOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) String rpcBlockPort) {
        super(keyRing);
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
        this.rpcPort = rpcPort;
        this.rpcBlockPort = rpcBlockPort;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Non blocking methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    void setup(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        ListenableFuture<BtcdClientImpl> future = setupExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            CloseableHttpClient httpProvider = HttpClients.custom().setConnectionManager(cm).build();
            Properties nodeConfig = new Properties();
            URL resource = getClass().getClassLoader().getResource("btcRpcConfig.properties");
            checkNotNull(resource, "btcRpcConfig.properties not found");
            try (FileInputStream fileInputStream = new FileInputStream(new File(resource.toURI()))) {
                try (InputStream inputStream = new BufferedInputStream(fileInputStream)) {
                    nodeConfig.load(inputStream);
                    nodeConfig.setProperty("node.bitcoind.rpc.user", rpcUser);
                    nodeConfig.setProperty("node.bitcoind.rpc.password", rpcPassword);
                    nodeConfig.setProperty("node.bitcoind.rpc.port", rpcPort);
                    nodeConfig.setProperty("node.bitcoind.notification.block.port", rpcBlockPort);
                    BtcdClientImpl client = new BtcdClientImpl(httpProvider, nodeConfig);
                    daemon = new BtcdDaemonImpl(client);
                    log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
                    return client;
                } catch (IOException | BitcoindException | CommunicationException e) {
                    if (e instanceof CommunicationException)
                        log.error("Maybe the rpc port is not set correctly? rpcPort=" + rpcPort);
                    log.error(e.toString());
                    e.printStackTrace();
                    log.error(e.getCause() != null ? e.getCause().toString() : "e.getCause()=null");
                    throw new BsqBlockchainException(e.getMessage(), e);
                }
            } catch (Throwable e) {
                log.error(e.toString());
                e.printStackTrace();
                throw new BsqBlockchainException(e.toString(), e);
            }
        });

        Futures.addCallback(future, new FutureCallback<BtcdClientImpl>() {
            public void onSuccess(BtcdClientImpl client) {
                UserThread.execute(() -> {
                    BsqBlockchainRpcService.this.client = client;
                    resultHandler.handleResult();
                });
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> {
                    log.error(throwable.toString());
                    errorMessageHandler.handleErrorMessage(throwable.toString());
                });
            }
        });
    }

    @Override
    void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Integer> future = getChainHeightExecutor.submit(client::getBlockCount);

        Futures.addCallback(future, new FutureCallback<Integer>() {
            public void onSuccess(Integer chainHeadHeight) {
                UserThread.execute(() -> resultHandler.accept(chainHeadHeight));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    @Override
    void parseBlocks(int startBlockHeight,
                     int chainHeadHeight,
                     int genesisBlockHeight,
                     String genesisTxId,
                     TxOutputMap txOutputMap,
                     Consumer<TxOutputMap> snapShotHandler,
                     Consumer<TxOutputMap> resultHandler,
                     Consumer<Throwable> errorHandler) {
        ListenableFuture<TxOutputMap> future = parseBlockchainExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            BsqParser bsqParser = new BsqParser(this);
            // txOutputMap us used in UserThread context, so we clone
            final TxOutputMap clonedMap = TxOutputMap.getClonedMap(txOutputMap);
            bsqParser.parseBlocks(startBlockHeight,
                    chainHeadHeight,
                    genesisBlockHeight,
                    genesisTxId,
                    clonedMap,
                    clonedSnapShotMap -> {
                        // We map to UserThread. We don't need to clone as it was created already newly in the parser.
                        UserThread.execute(() -> snapShotHandler.accept(clonedSnapShotMap));
                    });
            log.info("parseBlockchain took {} ms", System.currentTimeMillis() - startTs);
            return clonedMap;
        });

        Futures.addCallback(future, new FutureCallback<TxOutputMap>() {
            @Override
            public void onSuccess(TxOutputMap clonedMap) {
                UserThread.execute(() -> {
                    // We map to UserThread. Map was already cloned
                    UserThread.execute(() -> resultHandler.accept(clonedMap));
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                // We map to UserThread.
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    @Override
    void parseBlock(BsqBlock block,
                    int genesisBlockHeight,
                    String genesisTxId,
                    TxOutputMap txOutputMap,
                    Consumer<TxOutputMap> resultHandler,
                    Consumer<Throwable> errorHandler) {
        ListenableFuture<TxOutputMap> future = parseBlockchainExecutor.submit(() -> {
            BsqParser bsqParser = new BsqParser(this);
            // txOutputMap us used in UserThread context, so we clone);
            final TxOutputMap clonedMap = TxOutputMap.getClonedMap(txOutputMap);
            bsqParser.parseBlock(block,
                    genesisBlockHeight,
                    genesisTxId,
                    clonedMap);
            return clonedMap;
        });

        Futures.addCallback(future, new FutureCallback<TxOutputMap>() {
            @Override
            public void onSuccess(TxOutputMap clonedMap) {
                UserThread.execute(() -> {
                    // We map to UserThread. Map was already cloned
                    resultHandler.accept(clonedMap);
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                // We map to UserThread.
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    @Override
    void addBlockHandler(Consumer<BsqBlock> blockHandler) {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(com.neemre.btcdcli4j.core.domain.Block btcdBlock) {
                if (btcdBlock != null) {
                    UserThread.execute(() -> {
                        log.info("New block received: height={}, id={}", btcdBlock.getHeight(), btcdBlock.getHash());
                        blockHandler.accept(new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight()));
                    });
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Blocking methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    @Override
    int requestChainHeadHeight() throws BitcoindException, CommunicationException {
        return client.getBlockCount();
    }

    @VisibleForTesting
    @Override
    com.neemre.btcdcli4j.core.domain.Block requestBlock(int blockHeight) throws BitcoindException, CommunicationException {
        return client.getBlock(client.getBlockHash(blockHeight));
    }

    @VisibleForTesting
    @Override
    Tx requestTransaction(String txId, int blockHeight) throws BsqBlockchainException {
        try {
            RawTransaction rawTransaction = requestRawTransaction(txId);
            // rawTransaction.getTime() is in seconds but we keep it in ms internally
            final long time = rawTransaction.getTime() * 1000;
            final List<TxInput> txInputs = rawTransaction.getVIn()
                    .stream()
                    .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                    .map(rawInput -> new TxInput(rawInput.getVOut(), rawInput.getTxId(), rawTransaction.getHex()))
                    .collect(Collectors.toList());

            final List<TxOutput> txOutputs = rawTransaction.getVOut()
                    .stream()
                    .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                    .map(rawOutput -> new TxOutput(rawOutput.getN(),
                            rawOutput.getValue().movePointRight(8).longValue(),
                            rawTransaction.getTxId(),
                            new PubKeyScript(rawOutput.getScriptPubKey()),
                            blockHeight,
                            time,
                            signaturePubKey))
                    .collect(Collectors.toList());
            return new Tx(txId,
                    txInputs,
                    txOutputs,
                    time);
        } catch (BitcoindException | CommunicationException e) {
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    @VisibleForTesting
    @Override
    RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return (RawTransaction) client.getRawTransaction(txId, 1);
    }

}
