/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.blockchain;

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
import com.neemre.btcdcli4j.core.domain.Transaction;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;
import com.neemre.btcdcli4j.daemon.event.WalletListener;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.Utilities;
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

public class BlockchainRpcService extends BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainRpcService.class);

    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcPort;
    private final ListeningExecutorService setupExecutorService = Utilities.getListeningExecutorService("BlockchainRpcService.setup", 1, 1, 5);
    private final ListeningExecutorService rpcRequestsExecutor = Utilities.getListeningExecutorService("BlockchainRpcService.requests", 1, 1, 10);

    private BtcdClientImpl client;
    private BtcdDaemonImpl daemon;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlockchainRpcService(@Named(RpcOptionKeys.RPC_USER) String rpcUser,
                                @Named(RpcOptionKeys.RPC_PASSWORD) String rpcPassword,
                                @Named(RpcOptionKeys.RPC_PORT) String rpcPort) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
        this.rpcPort = rpcPort;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setup(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
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
                    nodeConfig.setProperty("node.bitcoind.rpc.port", rpcPort);
                    nodeConfig.setProperty("node.bitcoind.rpc.user", rpcUser);
                    nodeConfig.setProperty("node.bitcoind.rpc.password", rpcPassword);
                    BtcdClientImpl client = new BtcdClientImpl(httpProvider, nodeConfig);
                    daemon = new BtcdDaemonImpl(client);
                    log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
                    return client;
                } catch (IOException | BitcoindException | CommunicationException e) {
                    throw new BlockchainException(e.getMessage(), e);
                }
            } catch (URISyntaxException | IOException e) {
                throw new BlockchainException(e.getMessage(), e);
            }
        });

        Futures.addCallback(future, new FutureCallback<BtcdClientImpl>() {
            public void onSuccess(BtcdClientImpl client) {
                BlockchainRpcService.this.client = client;
                resultHandler.handleResult();
            }

            public void onFailure(@NotNull Throwable throwable) {
                errorMessageHandler.handleErrorMessage(throwable.getMessage());
            }
        });
    }

    @Override
    protected void syncFromGenesis(Consumer<Map<String, Map<Integer, SquUTXO>>> resultHandler, ErrorMessageHandler errorMessageHandler) {
        ListenableFuture<Map<String, Map<Integer, SquUTXO>>> future = rpcRequestsExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap = new HashMap<>();
            try {
                parseBlockchainFromGenesis(utxoByTxIdMap, GENESIS_BLOCK_HEIGHT, GENESIS_TX_ID);
            } catch (BlockchainException e) {
                throw new BlockchainException(e.getMessage(), e);
            }
            log.info("syncFromGenesis took {} ms", System.currentTimeMillis() - startTs);
            return utxoByTxIdMap;
        });

        Futures.addCallback(future, new FutureCallback<Map<String, Map<Integer, SquUTXO>>>() {
            public void onSuccess(Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap) {
                resultHandler.accept(utxoByTxIdMap);
            }

            public void onFailure(@NotNull Throwable throwable) {
                errorMessageHandler.handleErrorMessage(throwable.getMessage());
            }
        });
    }

    @Override
    protected void syncFromGenesisCompete() {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(Block block) {
                log.info("blockDetected " + block.getHash());
                parseBlock(new SquBlock(block.getTx(), block.getHeight()), GENESIS_TX_ID, utxoByTxIdMap);
                printUtxoMap(utxoByTxIdMap);
            }
        });
        daemon.addWalletListener(new WalletListener() {
            @Override
            public void walletChanged(Transaction transaction) {
                log.info("walletChanged " + transaction.getTxId());
                try {
                    parseTransaction(transaction.getTxId(), GENESIS_TX_ID, client.getBlockCount(), utxoByTxIdMap);
                    printUtxoMap(utxoByTxIdMap);
                } catch (BitcoindException | CommunicationException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    void parseBlockchainFromGenesis(Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap, int genesisBlockHeight, String genesisTxId) throws BlockchainException {
        try {
            int blockCount = client.getBlockCount();
            log.info("blockCount=" + blockCount);
            long startTs = System.currentTimeMillis();
            for (int i = genesisBlockHeight; i <= blockCount; i++) {
                String blockHash = client.getBlockHash(i);
                Block block = client.getBlock(blockHash);
                log.info("blockHeight=" + i);
                parseBlock(new SquBlock(block.getTx(), block.getHeight()), genesisTxId, utxoByTxIdMap);
            }
            printUtxoMap(utxoByTxIdMap);
            log.info("Took {} ms", System.currentTimeMillis() - startTs);
        } catch (BitcoindException | CommunicationException e) {
            throw new BlockchainException(e.getMessage(), e);
        }
    }

    @Override
    SquTransaction getSquTransaction(String txId) throws BlockchainException {
        try {
            RawTransaction rawTransaction = (RawTransaction) client.getRawTransaction(txId, 1);
            return new SquTransaction(txId,
                    rawTransaction.getVIn()
                            .stream()
                            .filter(e -> e != null && e.getVOut() != null && e.getTxId() != null)
                            .map(e -> new SquTxInput(e.getVOut(), e.getTxId()))
                            .collect(Collectors.toList()),
                    rawTransaction.getVOut()
                            .stream()
                            .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                            .map(e -> new SquTxOutput(e.getN(),
                                    Coin.valueOf(e.getValue().movePointRight(8).longValue()),
                                    e.getScriptPubKey().getAddresses(),
                                    new Script(HEX.decode(e.getScriptPubKey().getHex()))))
                            .collect(Collectors.toList()));
        } catch (BitcoindException | CommunicationException e) {
            throw new BlockchainException(e.getMessage(), e);
        }
    }
}
