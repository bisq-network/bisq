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

package io.bisq.core.dao.blockchain.parse;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.client.BtcdClient;
import com.neemre.btcdcli4j.core.client.BtcdClientImpl;
import com.neemre.btcdcli4j.core.domain.Block;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.neemre.btcdcli4j.core.domain.Transaction;
import com.neemre.btcdcli4j.core.domain.enums.ScriptTypes;
import com.neemre.btcdcli4j.daemon.BtcdDaemon;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.btcd.PubKeyScript;
import io.bisq.core.dao.blockchain.exceptions.BsqBlockchainException;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxInput;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxVo;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// Blocking access to Bitcoin Core via RPC requests
// See the rpc.md file in the doc directory for more info about the setup.
public class RpcService {
    private static final Logger log = LoggerFactory.getLogger(RpcService.class);

    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcPort;
    private final String rpcBlockPort;
    private final boolean dumpBlockchainData;

    private BtcdClient client;
    private BtcdDaemon daemon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public RpcService(@Named(DaoOptionKeys.RPC_USER) String rpcUser,
                      @Named(DaoOptionKeys.RPC_PASSWORD) String rpcPassword,
                      @Named(DaoOptionKeys.RPC_PORT) String rpcPort,
                      @Named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) String rpcBlockPort,
                      @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
        this.rpcPort = rpcPort;
        this.rpcBlockPort = rpcBlockPort;
        this.dumpBlockchainData = dumpBlockchainData;
    }

    void setup() throws BsqBlockchainException {
        try {
            long startTs = System.currentTimeMillis();
            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            CloseableHttpClient httpProvider = HttpClients.custom().setConnectionManager(cm).build();
            Properties nodeConfig = new Properties();
            nodeConfig.setProperty("node.bitcoind.rpc.protocol", "http");
            nodeConfig.setProperty("node.bitcoind.rpc.host", "127.0.0.1");
            nodeConfig.setProperty("node.bitcoind.rpc.auth_scheme", "Basic");
            nodeConfig.setProperty("node.bitcoind.rpc.user", rpcUser);
            nodeConfig.setProperty("node.bitcoind.rpc.password", rpcPassword);
            nodeConfig.setProperty("node.bitcoind.rpc.port", rpcPort);
            nodeConfig.setProperty("node.bitcoind.notification.block.port", rpcBlockPort);
            nodeConfig.setProperty("node.bitcoind.notification.alert.port", "64647");
            nodeConfig.setProperty("node.bitcoind.notification.wallet.port", "64648");
            nodeConfig.setProperty("node.bitcoind.http.auth_scheme", "Basic");
            BtcdClientImpl client = new BtcdClientImpl(httpProvider, nodeConfig);
            daemon = new BtcdDaemonImpl(client);
            log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
            this.client = client;
        } catch (BitcoindException | CommunicationException e) {
            if (e instanceof CommunicationException)
                log.error("Probably Bitcoin core is not running or the rpc port is not set correctly. rpcPort=" + rpcPort);
            log.error(e.toString());
            e.printStackTrace();
            log.error(e.getCause() != null ? e.getCause().toString() : "e.getCause()=null");
            throw new BsqBlockchainException(e.getMessage(), e);
        } catch (Throwable e) {
            log.error(e.toString());
            e.printStackTrace();
            throw new BsqBlockchainException(e.toString(), e);
        }
    }

    void registerBlockHandler(Consumer<Block> blockHandler) {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(Block block) {
                if (block != null) {
                    log.info("New block received: height={}, id={}", block.getHeight(), block.getHash());
                    if (block.getHeight() != null && block.getHash() != null) {
                        blockHandler.accept(block);
                    } else {
                        log.warn("We received a block with block.getHeight()=null or block.getHash()=null. That should not happen.");
                    }
                } else {
                    log.warn("We received a block with value null. That should not happen.");
                }
            }
        });
    }

    int requestChainHeadHeight() throws BitcoindException, CommunicationException {
        return client.getBlockCount();
    }

    Block requestBlock(int blockHeight) throws BitcoindException, CommunicationException {
        final String blockHash = client.getBlockHash(blockHeight);
        return client.getBlock(blockHash);
    }

    void requestFees(String txId, int blockHeight, Map<Integer, Long> feesByBlock) throws BsqBlockchainException {
        try {
            Transaction transaction = requestTx(txId);
            final BigDecimal fee = transaction.getFee();
            if (fee != null)
                feesByBlock.put(blockHeight, Math.abs(fee.multiply(BigDecimal.valueOf(Coin.COIN.value)).longValue()));
        } catch (BitcoindException | CommunicationException e) {
            log.error("error at requestFees with txId={}, blockHeight={}", txId, blockHeight);
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    Tx requestTx(String txId, int blockHeight) throws BsqBlockchainException {
        try {
            RawTransaction rawTransaction = requestRawTransaction(txId);
            // rawTransaction.getTime() is in seconds but we keep it in ms internally
            final long time = rawTransaction.getTime() * 1000;
            final List<TxInput> txInputs = rawTransaction.getVIn()
                    .stream()
                    .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                    .map(rawInput -> new TxInput(rawInput.getTxId(), rawInput.getVOut()))
                    .collect(Collectors.toList());

            final List<TxOutput> txOutputs = rawTransaction.getVOut()
                    .stream()
                    .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                    .map(rawOutput -> {
                                byte[] opReturnData = null;
                                final com.neemre.btcdcli4j.core.domain.PubKeyScript scriptPubKey = rawOutput.getScriptPubKey();
                                if (scriptPubKey.getType().equals(ScriptTypes.NULL_DATA)) {
                                    String[] chunks = scriptPubKey.getAsm().split(" ");
                                    // TODO only store BSQ OP_RETURN date filtered by type byte

                                    // We get on testnet a lot of "OP_RETURN 0" data, so we filter those away
                                    if (chunks.length == 2 && chunks[0].equals("OP_RETURN") && !"0".equals(chunks[1])) {
                                        try {
                                            opReturnData = Utils.HEX.decode(chunks[1]);
                                        } catch (Throwable t) {
                                            // We get sometimes exceptions, seems BitcoinJ 
                                            // cannot handle all existing OP_RETURN data, but we ignore them
                                            // anyway as our OP_RETURN data is valid in BitcoinJ
                                            log.warn("Error at Utils.HEX.decode(chunks[1]): " + t.toString() + " / chunks[1]=" + chunks[1]);
                                        }
                                    }
                                }
                                // We dont support raw MS which are the only case where scriptPubKey.getAddresses()>1
                                String address = scriptPubKey.getAddresses() != null &&
                                        scriptPubKey.getAddresses().size() == 1 ? scriptPubKey.getAddresses().get(0) : null;
                                final PubKeyScript pubKeyScript = dumpBlockchainData ? new PubKeyScript(scriptPubKey) : null;
                                return new TxOutput(rawOutput.getN(),
                                        rawOutput.getValue().movePointRight(8).longValue(),
                                        rawTransaction.getTxId(),
                                        pubKeyScript,
                                        address,
                                        opReturnData,
                                        blockHeight);
                            }
                    )
                    .collect(Collectors.toList());

            final TxVo txVo = new TxVo(txId,
                    blockHeight,
                    rawTransaction.getBlockHash(),
                    time);
            return new Tx(txVo,
                    ImmutableList.copyOf(txInputs),
                    ImmutableList.copyOf(txOutputs));
        } catch (BitcoindException | CommunicationException e) {
            log.error("error at requestTx with txId={}, blockHeight={}", txId, blockHeight);
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return (RawTransaction) client.getRawTransaction(txId, 1);
    }

    Transaction requestTx(String txId) throws BitcoindException, CommunicationException {
        return client.getTransaction(txId);
    }
}
