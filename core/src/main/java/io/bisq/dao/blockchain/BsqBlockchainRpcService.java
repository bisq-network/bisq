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

package io.bisq.dao.blockchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.client.BtcdClientImpl;
import com.neemre.btcdcli4j.core.domain.Block;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.network_messages.dao.blockchain.RpcOptionKeys;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.script.Script;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Utils.HEX;

public class BsqBlockchainRpcService extends BsqBlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainRpcService.class);

    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcPort;
    private final String rpcBlockPort;
    private final String rpcWalletPort;
    private final ListeningExecutorService setupExecutorService = Utilities.getListeningExecutorService("BlockchainRpcService.setup", 1, 1, 5);
    private final ListeningExecutorService rpcRequestsExecutor = Utilities.getListeningExecutorService("BlockchainRpcService.requests", 1, 1, 10);

    private BtcdClientImpl client;
    private BtcdDaemonImpl daemon;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainRpcService(@Named(RpcOptionKeys.RPC_USER) String rpcUser,
                                   @Named(RpcOptionKeys.RPC_PASSWORD) String rpcPassword,
                                   @Named(RpcOptionKeys.RPC_PORT) String rpcPort,
                                   @Named(RpcOptionKeys.RPC_BLOCK_PORT) String rpcBlockPort,
                                   @Named(RpcOptionKeys.RPC_WALLET_PORT) String rpcWalletPort) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
        this.rpcPort = rpcPort;
        this.rpcBlockPort = rpcBlockPort;
        this.rpcWalletPort = rpcWalletPort;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    void setup(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        ListenableFuture<BtcdClientImpl> future = setupExecutorService.submit(() -> {
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
                    nodeConfig.setProperty("node.bitcoind.notification.wallet.port", rpcWalletPort);
                    BtcdClientImpl client = new BtcdClientImpl(httpProvider, nodeConfig);
                    daemon = new BtcdDaemonImpl(client);
                    log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
                    return client;
                } catch (IOException | BitcoindException | CommunicationException e) {
                    throw new BsqBlockchainException(e.getMessage(), e);
                }
            } catch (URISyntaxException | IOException e) {
                throw new BsqBlockchainException(e.getMessage(), e);
            }
        });

        Futures.addCallback(future, new FutureCallback<BtcdClientImpl>() {
            public void onSuccess(BtcdClientImpl client) {
                BsqBlockchainRpcService.this.client = client;
                resultHandler.handleResult();
            }

            public void onFailure(@NotNull Throwable throwable) {
                errorMessageHandler.handleErrorMessage(throwable.getMessage());
            }
        });
    }

    @Override
    protected ListenableFuture<Tuple2<Map<String, Map<Integer, BsqUTXO>>, Integer>> syncFromGenesis(int genesisBlockHeight, String genesisTxId) {
        return rpcRequestsExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap = new HashMap<>();
            int chainHeadHeight = requestChainHeadHeight();
            parseBlockchain(utxoByTxIdMap, chainHeadHeight, genesisBlockHeight, genesisTxId);
            log.info("syncFromGenesis took {} ms", System.currentTimeMillis() - startTs);
            return new Tuple2<>(utxoByTxIdMap, chainHeadHeight);
        });
    }

    @Override
    protected void syncFromGenesisCompete(String genesisTxId, int genesisBlockHeight, Consumer<Block> onNewBlockHandler) {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(Block block) {
                log.info("blockDetected " + block.getHash());
                if (onNewBlockHandler != null)
                    onNewBlockHandler.accept(block);
            }
        });
       /* daemon.addWalletListener(new WalletListener() {
            @Override
            public void walletChanged(Transaction transaction) {
                log.info("walletChanged " + transaction.getTxId());
               *//* try {
                   // parseTransaction(transaction.getTxId(), GENESIS_TX_ID, client.getBlockCount(), tempUtxoByTxIdMap, utxoByTxIdMap);
                    printUtxoMap(utxoByTxIdMap);
                } catch (BitcoindException | CommunicationException | BsqBlockchainException e) {
                    //TODO
                    e.printStackTrace();
                }*//*
            }
        });*/
    }

    @Override
    int requestChainHeadHeight() throws BitcoindException, CommunicationException {
        return client.getBlockCount();
    }

    @Override
    Block requestBlock(int blockHeight) throws BitcoindException, CommunicationException {
        return client.getBlock(client.getBlockHash(blockHeight));
    }

    @Override
    BsqTransaction requestTransaction(String txId) throws BsqBlockchainException {
        try {
            RawTransaction rawTransaction = getRawTransaction(txId);
            return new BsqTransaction(txId,
                    rawTransaction.getVIn()
                            .stream()
                            .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                            .map(rawInput -> new BsqTxInput(rawInput.getVOut(), rawInput.getTxId(), rawTransaction.getHex()))
                            .collect(Collectors.toList()),
                    rawTransaction.getVOut()
                            .stream()
                            .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                            .map(e -> new BsqTxOutput(e.getN(),
                                    Coin.valueOf(e.getValue().movePointRight(8).longValue()),
                                    e.getScriptPubKey().getAddresses(),
                                    e.getScriptPubKey().getHex() != null ? new Script(HEX.decode(e.getScriptPubKey().getHex())) : null))
                            .collect(Collectors.toList()));
        } catch (BitcoindException | CommunicationException e) {
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    protected RawTransaction getRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return (RawTransaction) client.getRawTransaction(txId, 1);
    }
}
