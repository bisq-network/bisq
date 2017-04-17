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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.client.BtcdClient;
import com.neemre.btcdcli4j.core.client.BtcdClientImpl;
import com.neemre.btcdcli4j.core.domain.Block;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.neemre.btcdcli4j.daemon.BtcdDaemon;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import io.bisq.core.dao.blockchain.exceptions.BsqBlockchainException;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxInput;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
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

// Blocking access to Bitcoin Core via RPC requests
// We are in threaded context. We map our results to the client into the UserThread to not extend thread contexts.
// See the rpc.md file in the doc directory for more info about the setup.
public class BsqBlockchainRpcService implements BsqBlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainRpcService.class);

    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcPort;
    private final String rpcBlockPort;

    private BtcdClient client;
    private BtcdDaemon daemon;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqBlockchainRpcService(@Named(RpcOptionKeys.RPC_USER) String rpcUser,
                                   @Named(RpcOptionKeys.RPC_PASSWORD) String rpcPassword,
                                   @Named(RpcOptionKeys.RPC_PORT) String rpcPort,
                                   @Named(RpcOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) String rpcBlockPort) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
        this.rpcPort = rpcPort;
        this.rpcBlockPort = rpcBlockPort;
    }

    @Override
    public void setup() throws BsqBlockchainException {
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
                this.client = client;
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
    }

    @Override
    public void registerBlockHandler(Consumer<Block> blockHandler) {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(Block block) {
                if (block != null) {
                    log.info("New block received: height={}, id={}", block.getHeight(), block.getHash());
                    blockHandler.accept(block);
                } else {
                    log.error("We received a block with value null. That should not happen.");
                }
            }
        });
    }

    @Override
    public int requestChainHeadHeight() throws BitcoindException, CommunicationException {
        return client.getBlockCount();
    }

    @Override
    public Block requestBlock(int blockHeight) throws BitcoindException, CommunicationException {
        final String blockHash = client.getBlockHash(blockHeight);
        return client.getBlock(blockHash);
    }

    @Override
    public Tx requestTransaction(String txId, int blockHeight) throws BsqBlockchainException {
        try {
            RawTransaction rawTransaction = requestRawTransaction(txId);
            // rawTransaction.getTime() is in seconds but we keep it in ms internally
            final long time = rawTransaction.getTime() * 1000;
            final List<TxInput> txInputs = rawTransaction.getVIn()
                    .stream()
                    .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                    .map(rawInput -> new TxInput(rawInput.getVOut(), rawInput.getTxId()))
                    .collect(Collectors.toList());

            final List<TxOutput> txOutputs = rawTransaction.getVOut()
                    .stream()
                    .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                    .map(rawOutput -> new TxOutput(rawOutput.getN(),
                                    rawOutput.getValue().movePointRight(8).longValue(),
                                    rawTransaction.getTxId(),
                                    new PubKeyScript(rawOutput.getScriptPubKey()),
                                    blockHeight,
                                    time)
                    )
                    .collect(Collectors.toList());

            return new Tx(txId,
                    rawTransaction.getBlockHash(),
                    ImmutableList.copyOf(txInputs),
                    ImmutableList.copyOf(txOutputs),
                    false);
        } catch (BitcoindException | CommunicationException e) {
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    @Override
    public RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return (RawTransaction) client.getRawTransaction(txId, 1);
    }
}
