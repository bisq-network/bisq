/**
 * Copyright (c) 2017-2019 woodser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package monero.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;

import common.utils.GenUtils;
import common.utils.JsonUtils;
import monero.daemon.model.MoneroAltChain;
import monero.daemon.model.MoneroBan;
import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroBlockHeader;
import monero.daemon.model.MoneroBlockTemplate;
import monero.daemon.model.MoneroDaemonConnection;
import monero.daemon.model.MoneroDaemonConnectionSpan;
import monero.daemon.model.MoneroDaemonInfo;
import monero.daemon.model.MoneroDaemonListener;
import monero.daemon.model.MoneroDaemonPeer;
import monero.daemon.model.MoneroDaemonSyncInfo;
import monero.daemon.model.MoneroDaemonUpdateCheckResult;
import monero.daemon.model.MoneroDaemonUpdateDownloadResult;
import monero.daemon.model.MoneroHardForkInfo;
import monero.daemon.model.MoneroKeyImage;
import monero.daemon.model.MoneroKeyImageSpentStatus;
import monero.daemon.model.MoneroMinerTxSum;
import monero.daemon.model.MoneroMiningStatus;
import monero.daemon.model.MoneroNetworkType;
import monero.daemon.model.MoneroOutput;
import monero.daemon.model.MoneroOutputDistributionEntry;
import monero.daemon.model.MoneroOutputHistogramEntry;
import monero.daemon.model.MoneroSubmitTxResult;
import monero.daemon.model.MoneroTx;
import monero.daemon.model.MoneroTxBacklogEntry;
import monero.daemon.model.MoneroTxPoolStats;
import monero.rpc.MoneroRpcConnection;
import monero.rpc.MoneroRpcException;
import monero.utils.MoneroCppUtils;
import monero.utils.MoneroException;
import monero.utils.MoneroUtils;

/**
 * Implements a Monero daemon using monero-daemon-rpc.
 * 
 * TODO: every call needs to checkResponseStatus
 */
public class MoneroDaemonRpc extends MoneroDaemonDefault {
  
  // static variables
  private static final Logger LOGGER = Logger.getLogger(MoneroDaemonRpc.class);
  private static final String DEFAULT_ID = "0000000000000000000000000000000000000000000000000000000000000000";
  private static long MAX_REQ_SIZE = 3000000;  // max request size when fetching blocks from daemon
  private static int NUM_HEADERS_PER_REQ = 750;
  
  // instance variables
  private MoneroRpcConnection rpc;
  private MoneroDaemonPoller daemonPoller;
  private Map<Long, MoneroBlockHeader> cachedHeaders;
  
  public MoneroDaemonRpc(URI uri) {
    this(new MoneroRpcConnection(uri));
  }
  
  public MoneroDaemonRpc(String uri) {
    this(new MoneroRpcConnection(uri));
  }
  
  public MoneroDaemonRpc(String uri, String username, String password) {
    this(new MoneroRpcConnection(uri, username, password));
  }
  
  public MoneroDaemonRpc(URI uri, String username, String password) {
    this(new MoneroRpcConnection(uri, username, password));
  }

  public MoneroDaemonRpc(MoneroRpcConnection rpc) {
    assertNotNull(rpc);
    this.rpc = rpc;
    this.daemonPoller = new MoneroDaemonPoller(this);
    this.cachedHeaders = new HashMap<Long, MoneroBlockHeader>();
  }
  
  /**
   * Get the daemon's RPC connection.
   * 
   * @return the daemon's rpc connection
   */
  public MoneroRpcConnection getRpcConnection() {
    return this.rpc;
  }
  
  /**
   * Indicates if the client is connected to the daemon via RPC.
   * 
   * @return true if the client is connected to the daemon, false otherwise
   */
  public boolean isConnected() {
    try {
      getHeight();
      return true;
    } catch (MoneroException e) {
      return false;
    }
  }

  @Override
  public boolean isTrusted() {
    Map<String, Object> resp = rpc.sendPathRequest("get_height");
    checkResponseStatus(resp);
    return !(boolean) resp.get("untrusted");
  }

  @SuppressWarnings("unchecked")
  @Override
  public long getHeight() {
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block_count");
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    return ((BigInteger) resultMap.get("count")).intValue();
  }

  @Override
  public String getBlockId(long height) {
    Map<String, Object> respMap = rpc.sendJsonRequest("on_get_block_hash", Arrays.asList(height));
    return (String) respMap.get("result");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroBlockTemplate getBlockTemplate(String walletAddress, Integer reserveSize) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("wallet_address", walletAddress);
    params.put("reserve_size", reserveSize);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block_template", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    MoneroBlockTemplate template = convertRpcBlockTemplate(resultMap);
    return template;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public MoneroBlockHeader getLastBlockHeader() {
    Map<String, Object> respMap = rpc.sendJsonRequest("get_last_block_header");
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    MoneroBlockHeader header = convertRpcBlockHeader((Map<String, Object>) resultMap.get("block_header"));
    return header;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroBlockHeader getBlockHeaderById(String blockId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("hash", blockId);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block_header_by_hash", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    MoneroBlockHeader header = convertRpcBlockHeader((Map<String, Object>) resultMap.get("block_header"));
    return header;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroBlockHeader getBlockHeaderByHeight(long height) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("height", height);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block_header_by_height", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    MoneroBlockHeader header = convertRpcBlockHeader((Map<String, Object>) resultMap.get("block_header"));
    return header;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroBlockHeader> getBlockHeadersByRange(Long startHeight, Long endHeight) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("start_height", startHeight);
    params.put("end_height", endHeight);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block_headers_range", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    List<Map<String, Object>> rpcHeaders = (List<Map<String, Object>>) resultMap.get("headers");
    List<MoneroBlockHeader> headers = new ArrayList<MoneroBlockHeader>();
    for (Map<String, Object> rpcHeader : rpcHeaders) {
      MoneroBlockHeader header = convertRpcBlockHeader(rpcHeader);
      headers.add(header);
    }
    return headers;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroBlock getBlockById(String blockId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("hash", blockId);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    MoneroBlock block = convertRpcBlock(resultMap);
    return block;
  }

  @Override
  public List<MoneroBlock> getBlocksById(List<String> blockIds, Long startHeight, Boolean prune) {
    throw new RuntimeException("Not implemented");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroBlock getBlockByHeight(long height) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("height", height);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_block", params);
    Map<String, Object> rpcBlock = (Map<String, Object>) respMap.get("result");
    MoneroBlock block = convertRpcBlock((Map<String, Object>) rpcBlock);
    return block;
  }

  @SuppressWarnings({ "unchecked" })
  @Override
  public List<MoneroBlock> getBlocksByHeight(List<Long> heights) {
    
    // fetch blocks in binary
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("heights", heights);
    byte[] respBin = rpc.sendBinaryRequest("get_blocks_by_height.bin", params);
    
    // convert binary blocks to map
    Map<String, Object> rpcResp = MoneroCppUtils.binaryBlocksToMap(respBin);
    checkResponseStatus(rpcResp);
    
    // build blocks with transactions
    List<MoneroBlock> blocks = new ArrayList<MoneroBlock>();
    List<Map<String, Object>> rpcBlocks = (List<Map<String, Object>>) rpcResp.get("blocks");
    List<List<Map<String, Object>>> rpcTxs = (List<List<Map<String, Object>>>) rpcResp.get("txs");
    assertEquals(rpcBlocks.size(), rpcTxs.size());
    for (int blockIdx = 0; blockIdx < rpcBlocks.size(); blockIdx++) {
      
      // build block
      MoneroBlock block = convertRpcBlock(rpcBlocks.get(blockIdx));
      block.setHeight(heights.get(blockIdx));
      blocks.add(block);

      // build transactions
      List<MoneroTx> txs = new ArrayList<MoneroTx>();
      for (int txIdx = 0; txIdx < rpcTxs.get(blockIdx).size(); txIdx++) {
        MoneroTx tx = new MoneroTx();
        txs.add(tx);
        List<String> txIds = (List<String>) rpcBlocks.get(blockIdx).get("tx_hashes");
        tx.setId(txIds.get(txIdx));
        tx.setIsConfirmed(true);
        tx.setInTxPool(false);
        tx.setIsMinerTx(false);
        tx.setDoNotRelay(false);
        tx.setIsRelayed(true);
        tx.setIsFailed(false);
        tx.setIsDoubleSpendSeen(false);
        List<Map<String, Object>> blockTxs = (List<Map<String, Object>>) rpcTxs.get(blockIdx);
        convertRpcTx(blockTxs.get(txIdx), tx);
      }
      
      // merge into one block
      block.setTxs(new ArrayList<MoneroTx>());
      for (MoneroTx tx : txs) {
        if (tx.getBlock() != null) block.merge(tx.getBlock());
        else block.getTxs().add(tx.setBlock(block));
      }
    }
    
    return blocks;
  }
  
  @Override
  public List<MoneroBlock> getBlocksByRange(Long startHeight, Long endHeight) {
    if (startHeight == null) startHeight = 0l;
    if (endHeight == null) endHeight = getHeight() - 1;
    List<Long> heights = new ArrayList<Long>();
    for (long height = startHeight; height <= endHeight; height++) heights.add(height);
    return getBlocksByHeight(heights);
  }

  @Override
  public List<MoneroBlock> getBlocksByRangeChunked(Long startHeight, Long endHeight, Long maxChunkSize) {
    if (startHeight == null) startHeight = 0l;
    if (endHeight == null) endHeight = getHeight() - 1;
    long lastHeight = startHeight - 1;
    List<MoneroBlock> blocks = new ArrayList<MoneroBlock>();
    while (lastHeight < endHeight) {
      blocks.addAll(getMaxBlocks(lastHeight + 1, endHeight, maxChunkSize));
      lastHeight = blocks.get(blocks.size() - 1).getHeight();
    }
    return blocks;
  }
  
  @Override
  public List<String> getBlockIds(List<String> blockIds, Long startHeight) {
    throw new RuntimeException("Not implemented");
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroTx> getTxs(Collection<String> txIds, Boolean prune) {
    
    // validate input
    if (txIds.isEmpty()) throw new MoneroException("Must provide an array of transaction ids");
    
    // fetch transactions
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txs_hashes", txIds);
    params.put("decode_as_json", true);
    params.put("prune", prune);
    Map<String, Object> respMap = rpc.sendPathRequest("get_transactions", params);
    try {
      checkResponseStatus(respMap);
    } catch (MoneroException e) {
      if (e.getMessage().indexOf("Failed to parse hex representation of transaction hash") >= 0) throw new MoneroException("Invalid transaction id", e.getCode());
      throw e;
    }
    
    //  interpret response
    List<Map<String, Object>> rpcTxs = (List<Map<String, Object>>) respMap.get("txs");
    
    // build transaction models
    List<MoneroTx> txs = new ArrayList<MoneroTx>();
    if (rpcTxs != null) {
      for (int i = 0; i < rpcTxs.size(); i++) {
        MoneroTx tx = new MoneroTx();
        tx.setIsMinerTx(false);
        txs.add(convertRpcTx(rpcTxs.get(i), tx));
      }
    }
    
    // fetch unconfirmed txs from pool and merge additional fields  // TODO monero-daemon-rpc: merge rpc calls so this isn't necessary?
    //System.out.println("Fetching from pool...");  // TODO monero core: getTxPool() can get stuck under certain conditions (observed it before coordinating tx pool as part of tests, so double spend related?)
    List<MoneroTx> poolTxs = getTxPool();
    for (MoneroTx tx : txs) {
      for (MoneroTx poolTx : poolTxs) {
        if (tx.getId().equals(poolTx.getId())) tx.merge(poolTx);
      }
    }
    
    return txs;
  }

  @Override
  public List<String> getTxHexes(Collection<String> txIds, Boolean prune) {
    List<String> hexes = new ArrayList<String>();
    for (MoneroTx tx : getTxs(txIds, prune)) hexes.add(Boolean.TRUE.equals(prune) ? tx.getPrunedHex() : tx.getFullHex());
    return hexes;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroMinerTxSum getMinerTxSum(long height, Long numBlocks) {
    assertTrue("Height must be an integer >= 0", height >= 0);
    if (numBlocks == null) numBlocks = getHeight();
    else assertTrue("Count must be an integer >= 0", numBlocks >= 0);
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("height", height);
    params.put("count", numBlocks);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_coinbase_tx_sum", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    checkResponseStatus(resultMap);
    MoneroMinerTxSum txSum = new MoneroMinerTxSum();
    txSum.setEmissionSum((BigInteger) resultMap.get("emission_amount"));
    txSum.setFeeSum((BigInteger) resultMap.get("fee_amount"));
    return txSum;
  }

  @SuppressWarnings("unchecked")
  @Override
  public BigInteger getFeeEstimate(Integer graceBlocks) {
    Map<String, Object> resp = rpc.sendJsonRequest("get_fee_estimate");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    return (BigInteger) result.get("fee");
  }

  @Override
  public MoneroSubmitTxResult submitTxHex(String txHex, Boolean doNotRelay) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tx_as_hex", txHex);
    params.put("do_not_relay", doNotRelay);
    Map<String, Object> resp = rpc.sendPathRequest("send_raw_transaction", params);
    MoneroSubmitTxResult submitResult = convertRpcSubmitTxResult(resp);
    
    // set isGood based on status
    try {
      checkResponseStatus(resp);
      submitResult.setIsGood(true);
    } catch (MoneroException e) {
      submitResult.setIsGood(false);
    }
    return submitResult;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void relayTxsById(Collection<String> txIds) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txids", txIds);
    Map<String, Object> resp = rpc.sendJsonRequest("relay_tx", params);
    checkResponseStatus((Map<String, Object>) resp.get("result"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroTx> getTxPool() {
    
    
    // send rpc request
    Map<String, Object> resp = rpc.sendPathRequest("get_transaction_pool");
    checkResponseStatus(resp);
    
    // build txs
    List<MoneroTx> txs = new ArrayList<MoneroTx>();
    if (resp.containsKey("transactions")) {
      for (Map<String, Object> rpcTx : (List<Map<String, Object>>) resp.get("transactions")) {
        MoneroTx tx = new MoneroTx();
        txs.add(tx);
        tx.setIsConfirmed(false);
        tx.setIsMinerTx(false);
        tx.setInTxPool(true);
        tx.setNumConfirmations(0l);
        convertRpcTx(rpcTx, tx);
      }
    }
    
    return txs;
  }

  @Override
  public List<String> getTxPoolIds() {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public List<MoneroTxBacklogEntry> getTxPoolBacklog() {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public MoneroTxPoolStats getTxPoolStats() {
    throw new MoneroException("Response contains field 'histo' which is binary'");
//    let resp = await this.config.rpc.sendPathRequest("get_transaction_pool_stats");
//    MoneroDaemonRpc._checkResponseStatus(resp);
//    let stats = MoneroDaemonRpc._convertRpcTxPoolStats(resp.pool_stats);
//    
//    // uninitialize some stats if not applicable
//    if (stats.getHisto98pc() === 0) stats.setHisto98pc(undefined);
//    if (stats.getNumTxs() === 0) {
//      stats.setBytesMin(undefined);
//      stats.setBytesMed(undefined);
//      stats.setBytesMax(undefined);
//      stats.setHisto98pc(undefined);
//      stats.setOldestTimestamp(undefined);
//    }
//    
//    return stats;
  }

  @Override
  public void flushTxPool() {
    flushTxPool(new String[0]);
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public void flushTxPool(String... ids) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txids", ids);
    Map<String, Object> resp = rpc.sendJsonRequest("flush_txpool", params);
    checkResponseStatus((Map<String, Object>) resp.get("result"));
  }
  
  @Override
  public void flushTxPool(Collection<String> ids) {
    flushTxPool(ids.toArray(new String[0]));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroKeyImageSpentStatus> getKeyImageSpentStatuses(Collection<String> keyImages) {
    if (keyImages == null || keyImages.isEmpty()) throw new MoneroException("Must provide key images to check the status of");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_images", keyImages);
    Map<String, Object> resp = rpc.sendPathRequest("is_key_image_spent", params);
    checkResponseStatus(resp);
    List<MoneroKeyImageSpentStatus> statuses = new ArrayList<MoneroKeyImageSpentStatus>();
    for (BigInteger bi : (List<BigInteger>) resp.get("spent_status")) {
      statuses.add(MoneroKeyImageSpentStatus.valueOf(bi.intValue()));
    }
    return statuses;
  }

  @Override
  public List<MoneroOutput> getOutputs(Collection<MoneroOutput> outputs) {
    throw new RuntimeException("Not implemented");
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroOutputHistogramEntry> getOutputHistogram(Collection<BigInteger> amounts, Integer minCount, Integer maxCount, Boolean isUnlocked, Integer recentCutoff) {
    
    // build request params
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("amounts", amounts);
    params.put("min_count", minCount);
    params.put("max_count", maxCount);
    params.put("unlocked", isUnlocked);
    params.put("recent_cutoff", recentCutoff);
    
    // send rpc request
    Map<String, Object> resp = rpc.sendJsonRequest("get_output_histogram", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    
    // build histogram entries from response
    List<MoneroOutputHistogramEntry> entries = new ArrayList<MoneroOutputHistogramEntry>();
    if (!result.containsKey("histogram")) return entries;
    for (Map<String, Object> rpcEntry : (List<Map<String, Object>>) result.get("histogram")) {
      entries.add(convertRpcOutputHistogramEntry(rpcEntry));
    }
    return entries;
  }

  @Override
  public List<MoneroOutputDistributionEntry> getOutputDistribution(Collection<BigInteger> amounts, Boolean isCumulative, Long startHeight, Long endHeight) {
    throw new RuntimeException("Not implemented (response 'distribution' field is binary)");
//  let amountStrs = [];
//  for (let amount of amounts) amountStrs.push(amount.toJSValue());
//  console.log(amountStrs);
//  console.log(cumulative);
//  console.log(startHeight);
//  console.log(endHeight);
//  
//  // send rpc request
//  console.log("*********** SENDING REQUEST *************");
//  if (startHeight === undefined) startHeight = 0;
//  let resp = await this.config.rpc.sendJsonRequest("get_output_distribution", {
//    amounts: amountStrs,
//    cumulative: cumulative,
//    from_height: startHeight,
//    to_height: endHeight
//  });
//  
//  console.log("RESPONSE");
//  console.log(resp);
//  
//  // build distribution entries from response
//  let entries = [];
//  if (!resp.result.distributions) return entries; 
//  for (let rpcEntry of resp.result.distributions) {
//    let entry = MoneroDaemonRpc._convertRpcOutputDistributionEntry(rpcEntry);
//    entries.push(entry);
//  }
//  return entries;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroDaemonInfo getInfo() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_info");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    return convertRpcInfo(result);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroDaemonSyncInfo getSyncInfo() {
    Map<String, Object> resp = rpc.sendJsonRequest("sync_info");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    return convertRpcSyncInfo(result);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroHardForkInfo getHardForkInfo() {
    Map<String, Object> resp = rpc.sendJsonRequest("hard_fork_info");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    return convertRpcHardForkInfo(result);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroAltChain> getAltChains() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_alternate_chains");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    List<MoneroAltChain> chains = new ArrayList<MoneroAltChain>();
    if (!result.containsKey("chains")) return chains;
    for (Map<String, Object> rpcChain : (List<Map<String, Object>>) result.get("chains")) chains.add(convertRpcAltChain(rpcChain));
    return chains;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getAltBlockIds() {
    Map<String, Object> resp = rpc.sendPathRequest("get_alt_blocks_hashes");
    checkResponseStatus(resp);
    if (!resp.containsKey("blks_hashes")) return new ArrayList<String>();
    return (List<String>) resp.get("blks_hashes");
  }

  @Override
  public int getDownloadLimit() {
    return getBandwidthLimits()[0];
  }

  @Override
  public int setDownloadLimit(int limit) {
    if (limit == -1) return resetDownloadLimit();
    if (limit <= 0) throw new MoneroException("Download limit must be an integer greater than 0");
    return setBandwidthLimits(limit, 0)[0];
  }

  @Override
  public int resetDownloadLimit() {
    return setBandwidthLimits(-1, 0)[0];
  }

  @Override
  public int getUploadLimit() {
    return getBandwidthLimits()[1];
  }

  @Override
  public int setUploadLimit(int limit) {
    if (limit == -1) return resetUploadLimit();
    if (limit <= 0) throw new MoneroException("Upload limit must be an integer greater than 0");
    return setBandwidthLimits(0, limit)[1];
  }

  @Override
  public int resetUploadLimit() {
    return setBandwidthLimits(0, -1)[1];
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroDaemonConnection> getConnections() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_connections");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    List<MoneroDaemonConnection> connections = new ArrayList<MoneroDaemonConnection>();
    if (!result.containsKey("connections")) return connections;
    for (Map<String, Object> rpcConnection : (List<Map<String, Object>>) result.get("connections")) {
      connections.add(convertRpcConnection(rpcConnection));
    }
    return connections;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroDaemonPeer> getKnownPeers() {
    
    // send request
    Map<String, Object> respMap = rpc.sendPathRequest("get_peer_list");
    checkResponseStatus(respMap);
    
    // build peers
    List<MoneroDaemonPeer> peers = new ArrayList<MoneroDaemonPeer>();
    if (respMap.containsKey("gray_list")) {
      for (Map<String, Object> rpcPeer : (List<Map<String, Object>>) respMap.get("gray_list")) {
        MoneroDaemonPeer peer = convertRpcPeer(rpcPeer);
        peer.setIsOnline(false); // gray list means offline last checked
        peers.add(peer);
      }
    }
    if (respMap.containsKey("white_list")) {
      for (Map<String, Object> rpcPeer :  (List<Map<String, Object>>) respMap.get("white_list")) {
        MoneroDaemonPeer peer = convertRpcPeer(rpcPeer);
        peer.setIsOnline(true); // white list means online last checked
        peers.add(peer);
      }
    }
    return peers;
  }

  @Override
  public void setOutgoingPeerLimit(int limit) {
    if (limit < 0) throw new MoneroException("Outgoing peer limit must be >= 0");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("out_peers", limit);
    Map<String, Object> resp = rpc.sendPathRequest("out_peers", params);
    checkResponseStatus(resp);
  }

  @Override
  public void setIncomingPeerLimit(int limit) {
    if (limit < 0) throw new MoneroException("Incoming peer limit must be >= 0");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("in_peers", limit);
    Map<String, Object> resp = rpc.sendPathRequest("in_peers", params);
    checkResponseStatus(resp);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroBan> getPeerBans() {
    Map<String, Object> resp = (Map<String, Object>) rpc.sendJsonRequest("get_bans");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    checkResponseStatus(result);
    List<MoneroBan> bans = new ArrayList<MoneroBan>();
    for (Map<String, Object> rpcBan : (List<Map<String, Object>>) result.get("bans")) {
      MoneroBan ban = new MoneroBan();
      ban.setHost((String) rpcBan.get("host"));
      ban.setIp(((BigInteger) rpcBan.get("ip")).intValue());
      ban.setSeconds(((BigInteger) rpcBan.get("seconds")).longValue());
      bans.add(ban);
    }
    return bans;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setPeerBans(List<MoneroBan> bans) {
    List<Map<String, Object>> rpcBans = new ArrayList<Map<String,  Object>>();
    for (MoneroBan ban : bans) rpcBans.add(convertToRpcBan(ban));
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("bans", rpcBans);
    Map<String, Object> resp = rpc.sendJsonRequest("set_bans", params);
    checkResponseStatus((Map<String, Object>) resp.get("result"));
  }
  
//  async setOutgoingPeerLimit(limit) {
//    assert(GenUtils.isInt(limit) && limit >= 0, "Outgoing peer limit must be >= 0");
//    let resp = this.config.rpc.sendPathRequest("out_peers", {out_peers: limit});
//    MoneroDaemonRpc._checkResponseStatus(resp);
//  }
//  
//  async setIncomingPeerLimit(limit) {
//    assert(GenUtils.isInt(limit) && limit >= 0, "Incoming peer limit must be >= 0");
//    let resp = this.config.rpc.sendPathRequest("in_peers", {in_peers: limit});
//    MoneroDaemonRpc._checkResponseStatus(resp);
//  }
//  
//  async getPeerBans() {
//    Map<String> resp = rpc.sendJsonRequest("get_bans");
//    MoneroDaemonRpc._checkResponseStatus(resp.result);
//    let bans = [];
//    for (let rpcBan of resp.result.bans) {
//      let ban = new MoneroBan();
//      ban.setHost(rpcBan.host);
//      ban.setIp(rpcBan.ip);
//      ban.setSeconds(rpcBan.seconds);
//      bans.push(ban);
//    }
//    return bans;
//  }
//  
//  async setPeerBan(ban) {
//    return this.setPeerBans([ban]);
//  }
//  
//  async setPeerBans(bans) {
//    let rpcBans = [];
//    for (let ban of bans) rpcBans.push(MoneroDaemonRpc._convertRpcBan(ban));
//    List<Map<String> resp = rpc.sendJsonRequest("set_bans", {bans: rpcBans});
//    MoneroDaemonRpc._checkResponseStatus(resp.result);
//  }

  @Override
  public void startMining(String address, Long numThreads, Boolean isBackground, Boolean ignoreBattery) {
    if (address == null || address.isEmpty()) throw new MoneroException("Must provide address to mine to");
    if (numThreads == null || numThreads <= 0) throw new MoneroException("Number of threads must be an integer greater than 0");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("miner_address", address);
    params.put("threads_count", numThreads);
    params.put("do_background_mining", isBackground);
    params.put("ignore_battery", ignoreBattery);
    Map<String, Object> resp = rpc.sendPathRequest("start_mining", params);
    checkResponseStatus(resp);
  }

  @Override
  public void stopMining() {
    Map<String, Object> resp = rpc.sendPathRequest("stop_mining");
    checkResponseStatus(resp);
  }

  @Override
  public MoneroMiningStatus getMiningStatus() {
    Map<String, Object> resp = rpc.sendPathRequest("mining_status");
    checkResponseStatus(resp);
    return convertRpcMiningStatus(resp);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void submitBlocks(Collection<String> blockBlobs) {
    if (blockBlobs.isEmpty()) throw new MoneroException("Must provide an array of mined block blobs to submit");
    Map<String, Object> resp = rpc.sendJsonRequest("submit_block", blockBlobs);
    checkResponseStatus((Map<String, Object>) resp.get("result"));
  }

  @Override
  public MoneroDaemonUpdateCheckResult checkForUpdate() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("command", "check");
    Map<String, Object> respMap = rpc.sendPathRequest("update", params);
    checkResponseStatus(respMap);
    return convertRpcUpdateCheckResult(respMap);
  }

  @Override
  public MoneroDaemonUpdateDownloadResult downloadUpdate(String path) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("command", "download");
    params.put("path", path);
    Map<String, Object> resp = rpc.sendPathRequest("update", params);
    checkResponseStatus(resp);
    return convertRpcUpdateDownloadResult(resp);
  }

  @Override
  public void stop() {
    Map<String, Object> resp = rpc.sendPathRequest("stop_daemon");
    checkResponseStatus(resp);
  }

  @Override
  public MoneroBlockHeader getNextBlockHeader() {
    Object syncObject  = new Object();
    synchronized(syncObject) {
      try {
        MoneroDaemonListener customListener = new MoneroDaemonListener() {
          @Override
          public void onBlockHeader(MoneroBlockHeader header) {
            super.onBlockHeader(header);
            synchronized(syncObject) {
              syncObject.notifyAll();
            }
          }
        };
        addListener(customListener);
        syncObject.wait();
        removeListener(customListener);
        return customListener.getLastBlockHeader();
      } catch (InterruptedException e) {
        throw new MoneroException(e);
      }
    }
  }

  @Override
  public void addListener(MoneroDaemonListener listener) {
    daemonPoller.addListener(listener);
  }

  @Override
  public void removeListener(MoneroDaemonListener listener) {
    daemonPoller.removeListener(listener);
  }
  
  // ------------------------------- PRIVATE INSTANCE  ----------------------------
  
  private int[] getBandwidthLimits() {
    Map<String, Object> resp = rpc.sendPathRequest("get_limit");
    checkResponseStatus(resp);
    return new int[] { ((BigInteger) resp.get("limit_down")).intValue(), ((BigInteger) resp.get("limit_up")).intValue() };
  }
  
  private int[] setBandwidthLimits(Integer downLimit, Integer upLimit) {
    if (downLimit == null) downLimit = 0;
    if (upLimit == null) upLimit = 0;
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("limit_down", downLimit);
    params.put("limit_up", upLimit);
    Map<String, Object> resp = rpc.sendPathRequest("set_limit", params);
    checkResponseStatus(resp);
    return new int[] { ((BigInteger) resp.get("limit_down")).intValue(), ((BigInteger) resp.get("limit_up")).intValue() };
  }
  
  /**
   * Get a contiguous chunk of blocks starting from a given height up to a maximum
   * height or maximum amount of block data fetched from the blockchain, whichever comes first.
   * 
   * @param startHeight is the start height to retrieve blocks (default 0)
   * @param maxHeight is the maximum end height to retrieve blocks (default blockchain height)
   * @param chunkSize is the maximum chunk size in any one request (default 3,000,000 bytes)
   * @return List<MoneroBlock> are the resulting chunk of blocks
   */
  private List<MoneroBlock> getMaxBlocks(Long startHeight, Long maxHeight, Long chunkSize) {
    if (startHeight == null) startHeight = 0l;
    if (maxHeight == null) maxHeight = getHeight() - 1;
    if (chunkSize == null) chunkSize = MAX_REQ_SIZE;
    
    // determine end height to fetch
    int reqSize = 0;
    long endHeight = startHeight - 1;
    while (reqSize < chunkSize && endHeight < maxHeight) {
      
      // get header of next block
      MoneroBlockHeader header = getBlockHeaderByHeightCached(endHeight + 1, maxHeight);
      
      // block cannot be bigger than max request size
      assertTrue("Block exceeds maximum request size: " + header.getSize(), header.getSize() <= chunkSize);
      
      // done iterating if fetching block would exceed max request size
      if (reqSize + header.getSize() > chunkSize) break;
      
      // otherwise block is included
      reqSize += header.getSize();
      endHeight++;
    }
    return endHeight >= startHeight ? getBlocksByRange(startHeight, endHeight) : new ArrayList<MoneroBlock>();
  }
  
  /**
   * Retrieves a header by height from the cache or fetches and caches a header
   * range if not already in the cache.
   * 
   * @param height is the height of the header to retrieve from the cache
   * @param maxHeight is the maximum height of headers to cache
   */
  private MoneroBlockHeader getBlockHeaderByHeightCached(long height, long maxHeight) {
    
    // get header from cache
    MoneroBlockHeader cachedHeader = cachedHeaders.get(height);
    if (cachedHeader != null) return cachedHeader;
    
    // fetch and cache headers if not in cache
    long endHeight = Math.min(maxHeight, height + NUM_HEADERS_PER_REQ - 1);  // TODO: could specify end height to cache to optimize small requests (would like to have time profiling in place though)
    List<MoneroBlockHeader> headers = getBlockHeadersByRange(height, endHeight);
    for (MoneroBlockHeader header : headers) {
      cachedHeaders.put(header.getHeight(), header);
    }
    
    // return the cached header
    return cachedHeaders.get(height);
  }
  
  //---------------------------------- PRIVATE STATIC -------------------------------
  
  private static void checkResponseStatus(Map<String, Object> resp) {
    String status = (String) resp.get("status");
    if (!"OK".equals(status)) throw new MoneroRpcException(status, null, null, null);
  }
  
  private static MoneroBlockTemplate convertRpcBlockTemplate(Map<String, Object> rpcTemplate) {
    MoneroBlockTemplate template = new MoneroBlockTemplate();
    for (String key : rpcTemplate.keySet()) {
      Object val = rpcTemplate.get(key);
      if (key.equals("blockhashing_blob")) template.setBlockTemplateBlob((String) val);
      else if (key.equals("blocktemplate_blob")) template.setBlockHashingBlob((String) val);
      else if (key.equals("difficulty")) template.setDifficulty((BigInteger) val);
      else if (key.equals("expected_reward")) template.setExpectedReward((BigInteger) val);
      else if (key.equals("difficulty")) { }  // handled by wide_difficulty
      else if (key.equals("difficulty_top64")) { }  // handled by wide_difficulty
      else if (key.equals("wide_difficulty")) template.setDifficulty(MoneroUtils.reconcile(template.getDifficulty(), prefixedHexToBI((String) val)));
      else if (key.equals("height")) template.setHeight(((BigInteger) val).longValue());
      else if (key.equals("prev_hash")) template.setPrevId((String) val);
      else if (key.equals("reserved_offset")) template.setReservedOffset(((BigInteger) val).longValue());
      else if (key.equals("status")) {}  // handled elsewhere
      else if (key.equals("untrusted")) {}  // handled elsewhere
      else LOGGER.warn("WARNING: ignoring unexpected field in block template: " + key + ": " + val);
    }
    return template;
  }
  
  private static MoneroBlockHeader convertRpcBlockHeader(Map<String, Object> rpcHeader) {
    return convertRpcBlockHeader(rpcHeader, null);
  }
  
  private static MoneroBlockHeader convertRpcBlockHeader(Map<String, Object> rpcHeader, MoneroBlockHeader header) {
    if (header == null) header = new MoneroBlockHeader();
    for (String key : rpcHeader.keySet()) {
      Object val = rpcHeader.get(key);
      if (key.equals("block_size")) header.setSize(MoneroUtils.reconcile(header.getSize(), ((BigInteger) val).longValue()));
      else if (key.equals("depth")) header.setDepth(MoneroUtils.reconcile(header.getDepth(), ((BigInteger) val).longValue()));
      else if (key.equals("difficulty")) { }  // handled by wide_difficulty
      else if (key.equals("cumulative_difficulty")) { } // handled by wide_cumulative_difficulty
      else if (key.equals("difficulty_top64")) { }  // handled by wide_difficulty
      else if (key.equals("cumulative_difficulty_top64")) { } // handled by wide_cumulative_difficulty
      else if (key.equals("wide_difficulty")) header.setDifficulty(MoneroUtils.reconcile(header.getDifficulty(), prefixedHexToBI((String) val)));
      else if (key.equals("wide_cumulative_difficulty")) header.setCumulativeDifficulty(MoneroUtils.reconcile(header.getCumulativeDifficulty(), prefixedHexToBI((String) val)));
      else if (key.equals("hash")) header.setId(MoneroUtils.reconcile(header.getId(), (String) val));
      else if (key.equals("height")) header.setHeight(MoneroUtils.reconcile(header.getHeight(), ((BigInteger) val).longValue()));
      else if (key.equals("major_version")) header.setMajorVersion(MoneroUtils.reconcile(header.getMajorVersion(), ((BigInteger) val).intValue()));
      else if (key.equals("minor_version")) header.setMinorVersion(MoneroUtils.reconcile(header.getMinorVersion(), ((BigInteger) val).intValue()));
      else if (key.equals("nonce")) header.setNonce(MoneroUtils.reconcile(header.getNonce(), ((BigInteger) val).intValue()));
      else if (key.equals("num_txes")) header.setNumTxs(MoneroUtils.reconcile(header.getNumTxs(), ((BigInteger) val).intValue()));
      else if (key.equals("orphan_status")) header.setOrphanStatus(MoneroUtils.reconcile(header.getOrphanStatus(), (Boolean) val));
      else if (key.equals("prev_hash") || key.equals("prev_id")) header.setPrevId(MoneroUtils.reconcile(header.getPrevId(), (String) val));
      else if (key.equals("reward")) header.setReward(MoneroUtils.reconcile(header.getReward(), (BigInteger) val));
      else if (key.equals("timestamp")) header.setTimestamp(MoneroUtils.reconcile(header.getTimestamp(), ((BigInteger) val).longValue()));
      else if (key.equals("block_weight")) header.setWeight(MoneroUtils.reconcile(header.getWeight(), ((BigInteger) val).longValue()));
      else if (key.equals("long_term_weight")) header.setLongTermWeight(MoneroUtils.reconcile(header.getLongTermWeight(), ((BigInteger) val).longValue()));
      else if (key.equals("pow_hash")) header.setPowHash(MoneroUtils.reconcile(header.getPowHash(), "".equals(val) ? null : (String) val));
      else if (key.equals("tx_hashes")) {}  // used in block model, not header model
      else if (key.equals("miner_tx")) {}   // used in block model, not header model
      else if (key.equals("miner_tx_hash")) header.setMinerTxId((String) val);
      else LOGGER.warn("WARNING: ignoring unexpected block header field: '" + key + "': " + val);
    }
    return header;
  }
  
  @SuppressWarnings("unchecked")
  private static MoneroBlock convertRpcBlock(Map<String, Object> rpcBlock) {
    
    // build block
    MoneroBlock block = new MoneroBlock();
    convertRpcBlockHeader(rpcBlock.containsKey("block_header") ? (Map<String, Object>) rpcBlock.get("block_header") : rpcBlock, block);
    block.setHex((String) rpcBlock.get("blob"));
    block.setTxIds(rpcBlock.containsKey("tx_hashes") ? (List<String>) rpcBlock.get("tx_hashes") : new ArrayList<String>());
    
    // build miner tx
    Map<String, Object> rpcMinerTx = (Map<String, Object>) (rpcBlock.containsKey("json") ? JsonUtils.deserialize(MoneroRpcConnection.MAPPER, (String) rpcBlock.get("json"), new TypeReference<Map<String, Object>>(){}).get("miner_tx") : rpcBlock.get("miner_tx")); // may need to be parsed from json
    MoneroTx minerTx = new MoneroTx().setIsConfirmed(true).setIsMinerTx(true);
    MoneroDaemonRpc.convertRpcTx(rpcMinerTx, minerTx);
    block.setMinerTx(minerTx);
    
    return block;
  }
  
  /**
   * Transfers RPC tx fields to a given MoneroTx without overwriting previous values.
   * 
   * TODO: switch from safe set
   * 
   * @param rpcTx is the RPC map containing transaction fields
   * @param tx is the MoneroTx to populate with values (optional)
   * @returns tx is the same tx that was passed in or a new one if none given
   */
  @SuppressWarnings("unchecked")
  private static MoneroTx convertRpcTx(Map<String, Object> rpcTx, MoneroTx tx) {
    if (rpcTx == null) return null;
    if (tx == null) tx = new MoneroTx();
    
//    System.out.println("******** BUILDING TX ***********");
//    System.out.println(rpcTx);
//    System.out.println(tx.toString());
    
    // initialize from rpc map
    MoneroBlock block = null;
    for (String key : rpcTx.keySet()) {
      Object val = rpcTx.get(key);
      if (key.equals("tx_hash") || key.equals("id_hash")) tx.setId(MoneroUtils.reconcile(tx.getId(), (String) val));
      else if (key.equals("block_timestamp")) {
        if (block == null) block = new MoneroBlock();
        block.setTimestamp(MoneroUtils.reconcile(block.getTimestamp(), ((BigInteger) val).longValue()));
      }
      else if (key.equals("block_height")) {
        if (block == null) block = new MoneroBlock();
        block.setHeight(MoneroUtils.reconcile(block.getHeight(), ((BigInteger) val).longValue()));
      }
      else if (key.equals("last_relayed_time")) tx.setLastRelayedTimestamp(MoneroUtils.reconcile(tx.getLastRelayedTimestamp(), ((BigInteger) val).longValue()));
      else if (key.equals("receive_time")) tx.setReceivedTimestamp(MoneroUtils.reconcile(tx.getReceivedTimestamp(), ((BigInteger) val).longValue()));
      else if (key.equals("in_pool")) {
        tx.setIsConfirmed(MoneroUtils.reconcile(tx.isConfirmed(), !(Boolean) val));
        tx.setInTxPool(MoneroUtils.reconcile(tx.inTxPool(), (Boolean) val));
      }
      else if (key.equals("double_spend_seen")) tx.setIsDoubleSpendSeen(MoneroUtils.reconcile(tx.isDoubleSpendSeen(), (Boolean) val));
      else if (key.equals("version")) tx.setVersion(MoneroUtils.reconcile(tx.getVersion(), ((BigInteger) val).intValue()));
      else if (key.equals("extra")) {
        List<Integer> ints = new ArrayList<Integer>();
        for (BigInteger bi : (List<BigInteger>) val) ints.add(bi.intValue());
        tx.setExtra(MoneroUtils.reconcile(tx.getExtra(), GenUtils.listToIntArray(ints)));
      }
      else if (key.equals("vin")) {
        List<Map<String, Object>> rpcVins = (List<Map<String, Object>>) val;
        if (rpcVins.size() != 1 || !rpcVins.get(0).containsKey("gen")) {  // ignore miner vin TODO: why? probably needs re-enabled
          List<MoneroOutput> vins = new ArrayList<MoneroOutput>();
          for (Map<String, Object> rpcVin : rpcVins) vins.add(convertRpcOutput(rpcVin, tx));
          tx.setVins(vins);
        }
      }
      else if (key.equals("vout")) {
        List<Map<String, Object>> rpcVouts = (List<Map<String, Object>>) val;
        List<MoneroOutput> vouts = new ArrayList<MoneroOutput>();
        for (Map<String, Object> rpcVout : rpcVouts) vouts.add(convertRpcOutput(rpcVout, tx));
        tx.setVouts(vouts);
      }
      else if (key.equals("rct_signatures")) tx.setRctSignatures(MoneroUtils.reconcile(tx.getRctSignatures(), (Map<String, Object>) val));
      else if (key.equals("rctsig_prunable")) tx.setRctSigPrunable(MoneroUtils.reconcile(tx.getRctSigPrunable(), val));
      else if (key.equals("unlock_time")) tx.setUnlockTime(MoneroUtils.reconcile(tx.getUnlockTime(), ((BigInteger) val).longValue()));
      else if (key.equals("as_json") || key.equals("tx_json")) { }  // handled last so tx is as initialized as possible
      else if (key.equals("as_hex") || key.equals("tx_blob")) tx.setFullHex(MoneroUtils.reconcile(tx.getFullHex(), "".equals((String) val) ? null : (String) val));
      else if (key.equals("blob_size")) tx.setSize(MoneroUtils.reconcile(tx.getSize(), ((BigInteger) val).longValue()));
      else if (key.equals("weight")) tx.setWeight(MoneroUtils.reconcile(tx.getWeight(), ((BigInteger) val).longValue()));
      else if (key.equals("fee")) tx.setFee(MoneroUtils.reconcile(tx.getFee(), (BigInteger) val));
      else if (key.equals("relayed")) tx.setIsRelayed(MoneroUtils.reconcile(tx.isRelayed(), (Boolean) val));
      else if (key.equals("output_indices")) {
        List<Integer> indices = new ArrayList<Integer>();
        for (BigInteger bi : (List<BigInteger>) val) indices.add(bi.intValue());
        tx.setOutputIndices(MoneroUtils.reconcile(tx.getOutputIndices(), indices));
      }
      else if (key.equals("do_not_relay")) tx.setDoNotRelay(MoneroUtils.reconcile(tx.getDoNotRelay(), (Boolean) val));
      else if (key.equals("kept_by_block")) tx.setIsKeptByBlock(MoneroUtils.reconcile(tx.isKeptByBlock(), (Boolean) val));
      else if (key.equals("signatures")) tx.setSignatures(MoneroUtils.reconcile(tx.getSignatures(), (List<String>) val));
      else if (key.equals("last_failed_height")) {
        long lastFailedHeight = ((BigInteger) val).longValue();
        if (lastFailedHeight == 0) tx.setIsFailed(MoneroUtils.reconcile(tx.isFailed(), false));
        else {
          tx.setIsFailed(MoneroUtils.reconcile(tx.isFailed(), true));
          tx.setLastFailedHeight(MoneroUtils.reconcile(tx.getLastFailedHeight(), lastFailedHeight));
        }
      }
      else if (key.equals("last_failed_id_hash")) {
        if (DEFAULT_ID.equals((String) val)) tx.setIsFailed(MoneroUtils.reconcile(tx.isFailed(), false));
        else {
          tx.setIsFailed(MoneroUtils.reconcile(tx.isFailed(), true));
          tx.setLastFailedId(MoneroUtils.reconcile(tx.getLastFailedId(), (String) val));
        }
      }
      else if (key.equals("max_used_block_height")) tx.setMaxUsedBlockHeight(MoneroUtils.reconcile(tx.getMaxUsedBlockHeight(), ((BigInteger) val).longValue()));
      else if (key.equals("max_used_block_id_hash")) tx.setMaxUsedBlockId(MoneroUtils.reconcile(tx.getMaxUsedBlockId(), (String) val));
      else if (key.equals("prunable_hash")) tx.setPrunableHash(MoneroUtils.reconcile(tx.getPrunableHash(), "".equals((String) val) ? null : (String) val));
      else if (key.equals("prunable_as_hex")) tx.setPrunableHex(MoneroUtils.reconcile(tx.getPrunableHex(), "".equals((String) val) ? null : (String) val));
      else if (key.equals("pruned_as_hex")) tx.setPrunedHex(MoneroUtils.reconcile(tx.getPrunedHex(), "".equals((String) val) ? null : (String) val));
      else LOGGER.warn("WARNING: ignoring unexpected field in rpc tx: " + key + ": " + val);
    }
    
    // link block and tx
    if (block != null) tx.setBlock(block.setTxs(Arrays.asList(tx)));
    
    // TODO monero-daemon-rpc: unconfirmed txs misreport block height and timestamp
    if (tx.getBlock() != null && tx.getBlock().getHeight() != null && (long) tx.getBlock().getHeight() == tx.getBlock().getTimestamp()) {
      tx.setBlock(null);
      tx.setIsConfirmed(false);
    }
    
    // initialize remaining known fields
    if (tx.isConfirmed()) {
      tx.setIsRelayed(MoneroUtils.reconcile(tx.isRelayed(), true));
      tx.setDoNotRelay(MoneroUtils.reconcile(tx.getDoNotRelay(), false));
      tx.setIsFailed(MoneroUtils.reconcile(tx.isFailed(), false));
    } else {
      tx.setNumConfirmations(0l);
    }
    if (tx.isFailed() == null) tx.setIsFailed(false);
    if (tx.getOutputIndices() != null && tx.getVouts() != null)  {
      assertEquals(tx.getOutputIndices().size(), (int) tx.getVouts().size());
      for (int i = 0; i < tx.getVouts().size(); i++) {
        tx.getVouts().get(i).setIndex(tx.getOutputIndices().get(i));  // transfer output indices to vouts
      }
    }
    if (rpcTx.containsKey("as_json") && !"".equals(rpcTx.get("as_json"))) convertRpcTx(JsonUtils.deserialize(MoneroRpcConnection.MAPPER, (String) rpcTx.get("as_json"), new TypeReference<Map<String, Object>>(){}), tx);
    if (rpcTx.containsKey("tx_json") && !"".equals(rpcTx.get("tx_json"))) convertRpcTx(JsonUtils.deserialize(MoneroRpcConnection.MAPPER, (String) rpcTx.get("tx_json"), new TypeReference<Map<String, Object>>(){}), tx);
    if (!Boolean.TRUE.equals(tx.isRelayed())) tx.setLastRelayedTimestamp(null);  // TODO monero-daemon-rpc: returns last_relayed_timestamp despite relayed: false, self inconsistent
    
    // return built transaction
    return tx;
  }
  
  @SuppressWarnings("unchecked")
  private static MoneroOutput convertRpcOutput(Map<String, Object> rpcOutput, MoneroTx tx) {
    MoneroOutput output = new MoneroOutput();
    output.setTx(tx);
    for (String key : rpcOutput.keySet()) {
      Object val = rpcOutput.get(key);
      if (key.equals("gen")) throw new Error("Output with 'gen' from daemon rpc is miner tx which we ignore (i.e. each miner vin is null)");
      else if (key.equals("key")) {
        Map<String, Object> rpcKey = (Map<String, Object>) val;
        output.setAmount(MoneroUtils.reconcile(output.getAmount(), (BigInteger) rpcKey.get("amount")));
        output.setKeyImage(MoneroUtils.reconcile(output.getKeyImage(), new MoneroKeyImage((String) rpcKey.get("k_image"))));
        List<Integer> ringOutputIndices = new ArrayList<Integer>();
        for (BigInteger bi : (List<BigInteger>) rpcKey.get("key_offsets")) ringOutputIndices.add(bi.intValue());
        output.setRingOutputIndices(MoneroUtils.reconcile(output.getRingOutputIndices(), ringOutputIndices));
      }
      else if (key.equals("amount")) output.setAmount(MoneroUtils.reconcile(output.getAmount(), (BigInteger) val));
      else if (key.equals("target")) output.setStealthPublicKey(MoneroUtils.reconcile(output.getStealthPublicKey(), (String) ((Map<String, Object>) val).get("key")));
      else LOGGER.warn("WARNING: ignoring unexpected field output: " + key + ": " + val);
    }
    return output;
  }
  
  private static MoneroDaemonUpdateCheckResult convertRpcUpdateCheckResult(Map<String, Object> rpcResult) {
    MoneroDaemonUpdateCheckResult result = new MoneroDaemonUpdateCheckResult();
    for (String key : rpcResult.keySet()) {
      Object val = rpcResult.get(key);
      if (key.equals("auto_uri")) result.setAutoUri((String) val);
      else if (key.equals("hash")) result.setHash((String) val);
      else if (key.equals("path")) {} // handled elsewhere
      else if (key.equals("status")) {} // handled elsewhere
      else if (key.equals("update")) result.setIsUpdateAvailable((Boolean) val);
      else if (key.equals("user_uri")) result.setUserUri((String) val);
      else if (key.equals("version")) result.setVersion((String) val);
      else LOGGER.warn("WARNING: ignoring unexpected field in rpc check update result: " + key + ": " + val);
    }
    if ("".equals(result.getAutoUri())) result.setAutoUri(null);
    if ("".equals(result.getUserUri())) result.setUserUri(null);
    if ("".equals(result.getVersion())) result.setVersion(null);
    if ("".equals(result.getHash())) result.setHash(null);
    return result;
  }
  
  private static MoneroDaemonUpdateDownloadResult convertRpcUpdateDownloadResult(Map<String, Object> rpcResult) {
    MoneroDaemonUpdateDownloadResult result = new MoneroDaemonUpdateDownloadResult(convertRpcUpdateCheckResult(rpcResult));
    result.setDownloadPath((String) rpcResult.get("path"));
    if ("".equals(result.getDownloadPath())) result.setDownloadPath(null);
    return result;
  }
  
  private static MoneroDaemonPeer convertRpcPeer(Map<String, Object> rpcPeer) {
    assertNotNull(rpcPeer);
    MoneroDaemonPeer peer = new MoneroDaemonPeer();
    for (String key : rpcPeer.keySet()) {
      Object val = rpcPeer.get(key);
      if (key.equals("host")) peer.setHost((String) val);
      else if (key.equals("id")) peer.setId("" + val);  // TODO monero-wallet-rpc: peer id is big integer but string in `get_connections`
      else if (key.equals("ip")) {} // host used instead which is consistently a string
      else if (key.equals("last_seen")) peer.setLastSeenTimestamp(((BigInteger) val).longValue());
      else if (key.equals("port")) peer.setPort(((BigInteger) val).intValue());
      else if (key.equals("rpc_port")) peer.setRpcPort(((BigInteger) val).intValue());
      else if (key.equals("pruning_seed")) peer.setPruningSeed(((BigInteger) val).intValue());
      else LOGGER.warn("WARNING: ignoring unexpected field in rpc peer: " + key + ": " + val);
    }
    return peer;
  }
  
  private static MoneroSubmitTxResult convertRpcSubmitTxResult(Map<String, Object> rpcResult) {
    assertNotNull(rpcResult);
    MoneroSubmitTxResult result = new MoneroSubmitTxResult();
    for (String key : rpcResult.keySet()) {
      Object val = rpcResult.get(key);
      if (key.equals("double_spend")) result.setIsDoubleSpend((Boolean) val);
      else if (key.equals("fee_too_low")) result.setIsFeeTooLow((Boolean) val);
      else if (key.equals("invalid_input")) result.setHasInvalidInput((Boolean) val);
      else if (key.equals("invalid_output")) result.setHasInvalidOutput((Boolean) val);
      else if (key.equals("low_mixin")) result.setIsMixinTooLow((Boolean) val);
      else if (key.equals("not_rct")) result.setIsRct(!Boolean.TRUE.equals(val));
      else if (key.equals("not_relayed")) result.setIsRelayed(!Boolean.TRUE.equals(val));
      else if (key.equals("overspend")) result.setIsOverspend((Boolean) val);
      else if (key.equals("reason")) result.setReason("".equals((String) val) ? null : (String) val);
      else if (key.equals("too_big")) result.setIsTooBig((Boolean) val);
      else if (key.equals("sanity_check_failed")) result.setSanityCheckFailed((Boolean) val);
      else if (key.equals("status") || key.equals("untrusted")) {}  // handled elsewhere
      else LOGGER.warn("WARNING: ignoring unexpected field in submit tx hex result: " + key + ": " + val);
    }
    return result;
  }
  
  private static MoneroDaemonConnection convertRpcConnection(Map<String, Object> rpcConnection) {
    MoneroDaemonConnection connection = new MoneroDaemonConnection();
    MoneroDaemonPeer peer = new MoneroDaemonPeer();
    connection.setPeer(peer);
    peer.setIsOnline(true);
    for (String key : rpcConnection.keySet()) {
      Object val = rpcConnection.get(key);
      if (key.equals("address")) peer.setAddress((String) val);
      else if (key.equals("avg_download")) connection.setAvgDownload(((BigInteger) val).longValue());
      else if (key.equals("avg_upload")) connection.setAvgUpload(((BigInteger) val).longValue());
      else if (key.equals("connection_id")) connection.setId((String) val);
      else if (key.equals("current_download")) connection.setCurrentDownload(((BigInteger) val).longValue());
      else if (key.equals("current_upload")) connection.setCurrentUpload(((BigInteger) val).longValue());
      else if (key.equals("height")) connection.setHeight(((BigInteger) val).longValue());
      else if (key.equals("host")) peer.setHost((String) val);
      else if (key.equals("ip")) {} // host used instead which is consistently a string
      else if (key.equals("incoming")) connection.setIsIncoming((Boolean) val);
      else if (key.equals("live_time")) connection.setLiveTime(((BigInteger) val).longValue());
      else if (key.equals("local_ip")) connection.setIsLocalIp((Boolean) val);
      else if (key.equals("localhost")) connection.setIsLocalHost((Boolean) val);
      else if (key.equals("peer_id")) peer.setId((String) val);
      else if (key.equals("port")) peer.setPort(Integer.parseInt((String) val));
      else if (key.equals("rpc_port")) peer.setRpcPort(((BigInteger) val).intValue());
      else if (key.equals("recv_count")) connection.setNumReceives(((BigInteger) val).intValue());
      else if (key.equals("recv_idle_time")) connection.setReceiveIdleTime(((BigInteger) val).longValue());
      else if (key.equals("send_count")) connection.setNumSends(((BigInteger) val).intValue());
      else if (key.equals("send_idle_time")) connection.setSendIdleTime(((BigInteger) val).longValue());
      else if (key.equals("state")) connection.setState((String) val);
      else if (key.equals("support_flags")) connection.setNumSupportFlags(((BigInteger) val).intValue());
      else if (key.equals("pruning_seed")) peer.setPruningSeed(((BigInteger) val).intValue());
      else LOGGER.warn("WARNING: ignoring unexpected field in connection: " + key + ": " + val);
    }
    return connection;
  }
  
  private static MoneroOutputHistogramEntry convertRpcOutputHistogramEntry(Map<String, Object> rpcEntry) {
    MoneroOutputHistogramEntry entry = new MoneroOutputHistogramEntry();
    for (String key : rpcEntry.keySet()) {
      Object val = rpcEntry.get(key);
      if (key.equals("amount")) entry.setAmount((BigInteger) val);
      else if (key.equals("total_instances")) entry.setNumInstances(((BigInteger) val).longValue());
      else if (key.equals("unlocked_instances")) entry.setNumUnlockedInstances(((BigInteger) val).longValue());
      else if (key.equals("recent_instances")) entry.setNumRecentInstances(((BigInteger) val).longValue());
      else LOGGER.warn("WARNING: ignoring unexpected field in output histogram: " + key + ": " + val);
    }
    return entry;
  }
  
  private static MoneroDaemonInfo convertRpcInfo(Map<String, Object> rpcInfo) {
    if (rpcInfo == null) return null;
    MoneroDaemonInfo info = new MoneroDaemonInfo();
    for (String key : rpcInfo.keySet()) {
      Object val = rpcInfo.get(key);
      if (key.equals("version")) info.setVersion((String) val);
      else if (key.equals("alt_blocks_count")) info.setNumAltBlocks(((BigInteger) val).longValue());
      else if (key.equals("block_size_limit")) info.setBlockSizeLimit(((BigInteger) val).longValue());
      else if (key.equals("block_size_median")) info.setBlockSizeMedian(((BigInteger) val).longValue());
      else if (key.equals("block_weight_limit")) info.setBlockWeightLimit(((BigInteger) val).longValue());
      else if (key.equals("block_weight_median")) info.setBlockWeightMedian(((BigInteger) val).longValue());
      else if (key.equals("bootstrap_daemon_address")) { if (!((String) val).isEmpty()) info.setBootstrapDaemonAddress((String) val); }
      else if (key.equals("difficulty")) { }  // handled by wide_difficulty
      else if (key.equals("cumulative_difficulty")) { } // handled by wide_cumulative_difficulty
      else if (key.equals("difficulty_top64")) { }  // handled by wide_difficulty
      else if (key.equals("cumulative_difficulty_top64")) { } // handled by wide_cumulative_difficulty
      else if (key.equals("wide_difficulty")) info.setDifficulty(MoneroUtils.reconcile(info.getDifficulty(), prefixedHexToBI((String) val)));
      else if (key.equals("wide_cumulative_difficulty")) info.setCumulativeDifficulty(MoneroUtils.reconcile(info.getCumulativeDifficulty(), prefixedHexToBI((String) val)));
      else if (key.equals("free_space")) info.setFreeSpace((BigInteger) val);
      else if (key.equals("database_size")) info.setDatabaseSize(((BigInteger) val).longValue());
      else if (key.equals("grey_peerlist_size")) info.setNumOfflinePeers(((BigInteger) val).intValue());
      else if (key.equals("height")) info.setHeight(((BigInteger) val).longValue());
      else if (key.equals("height_without_bootstrap")) info.setHeightWithoutBootstrap(((BigInteger) val).longValue());
      else if (key.equals("incoming_connections_count")) info.setNumIncomingConnections(((BigInteger) val).intValue());
      else if (key.equals("offline")) info.setIsOffline((Boolean) val);
      else if (key.equals("outgoing_connections_count")) info.setNumOutgoingConnections(((BigInteger) val).intValue());
      else if (key.equals("rpc_connections_count")) info.setNumRpcConnections(((BigInteger) val).intValue());
      else if (key.equals("start_time")) info.setStartTimestamp(((BigInteger) val).longValue());
      else if (key.equals("status")) {}  // handled elsewhere
      else if (key.equals("target")) info.setTarget(((BigInteger) val).longValue());
      else if (key.equals("target_height")) info.setTargetHeight(((BigInteger) val).longValue());
      else if (key.equals("top_block_hash")) info.setTopBlockId((String) val);
      else if (key.equals("tx_count")) info.setNumTxs(((BigInteger) val).intValue());
      else if (key.equals("tx_pool_size")) info.setNumTxsPool(((BigInteger) val).intValue());
      else if (key.equals("untrusted")) {} // handled elsewhere
      else if (key.equals("was_bootstrap_ever_used")) info.setWasBootstrapEverUsed((Boolean) val);
      else if (key.equals("white_peerlist_size")) info.setNumOnlinePeers(((BigInteger) val).intValue());
      else if (key.equals("update_available")) info.setUpdateAvailable((Boolean) val);
      else if (key.equals("nettype")) info.setNetworkType(MoneroUtils.reconcile(info.getNetworkType(), MoneroDaemon.parseNetworkType((String) val)));
      else if (key.equals("mainnet")) { if ((Boolean) val) info.setNetworkType(MoneroUtils.reconcile(info.getNetworkType(), MoneroNetworkType.MAINNET)); }
      else if (key.equals("testnet")) { if ((Boolean) val) info.setNetworkType(MoneroUtils.reconcile(info.getNetworkType(), MoneroNetworkType.TESTNET)); }
      else if (key.equals("stagenet")) { if ((Boolean) val) info.setNetworkType(MoneroUtils.reconcile(info.getNetworkType(), MoneroNetworkType.STAGENET)); }
      else LOGGER.warn("WARNING: Ignoring unexpected info field: " + key + ": " + val);
    }
    return info;
  }
  
  /**
   * Initializes sync info from RPC sync info.
   * 
   * @param rpcSyncInfo is the rpc map to initialize the sync info from
   * @return {MoneroDaemonSyncInfo} is sync info initialized from the map
   */
  @SuppressWarnings("unchecked")
  private static MoneroDaemonSyncInfo convertRpcSyncInfo(Map<String, Object> rpcSyncInfo) {
    MoneroDaemonSyncInfo syncInfo = new MoneroDaemonSyncInfo();
    for (String key : rpcSyncInfo.keySet()) {
      Object val = rpcSyncInfo.get(key);
      if (key.equals("height")) syncInfo.setHeight(((BigInteger) val).longValue());
      else if (key.equals("peers")) {
        syncInfo.setConnections(new ArrayList<MoneroDaemonConnection>());
        List<Map<String, Object>> rpcConnections = (List<Map<String, Object>>) val;
        for (Map<String, Object> rpcConnection : rpcConnections) {
          syncInfo.getConnections().add(convertRpcConnection((Map<String, Object>) rpcConnection.get("info")));
        }
      } else if (key.equals("spans")) {
        syncInfo.setSpans(new ArrayList<MoneroDaemonConnectionSpan>());
        List<Map<String, Object>> rpcSpans = (List<Map<String, Object>>) val;
        for (Map<String, Object> rpcSpan : rpcSpans) {
          syncInfo.getSpans().add(convertRpcConnectionSpan(rpcSpan));
        }
      }
      else if (key.equals("status")) {}   // handled elsewhere
      else if (key.equals("target_height")) syncInfo.setTargetHeight(((BigInteger) val).longValue());
      else if (key.equals("next_needed_pruning_seed")) syncInfo.setNextNeededPruningSeed(((BigInteger) val).intValue());
      else if (key.equals("overview")) {  // this returns [] without pruning
        try {
          List<Object> overview = JsonUtils.deserialize((String) val, new TypeReference<List<Object>>(){});
          if (!overview.isEmpty()) LOGGER.warn("WARNING: ignoring non-empty 'overview' field (not implemented): " + overview); // TODO
        } catch (Exception e) {
          //e.printStackTrace();
          LOGGER.warn("WARNING: failed to parse 'overview' field: " + val);
        }
      }
      else LOGGER.warn("WARNING: ignoring unexpected field in sync info: " + key + ": " + val);
    }
    return syncInfo;
  }
  
  private static MoneroHardForkInfo convertRpcHardForkInfo(Map<String, Object> rpcHardForkInfo) {
    MoneroHardForkInfo info = new MoneroHardForkInfo();
    for (String key : rpcHardForkInfo.keySet()) {
      Object val = rpcHardForkInfo.get(key);
      if (key.equals("earliest_height")) info.setEarliestHeight(((BigInteger) val).longValue());
      else if (key.equals("enabled")) info.setIsEnabled((Boolean) val);
      else if (key.equals("state")) info.setState(((BigInteger) val).intValue());
      else if (key.equals("status")) {}     // handled elsewhere
      else if (key.equals("untrusted")) {}  // handled elsewhere
      else if (key.equals("threshold")) info.setThreshold(((BigInteger) val).intValue());
      else if (key.equals("version")) info.setVersion(((BigInteger) val).intValue());
      else if (key.equals("votes")) info.setNumVotes(((BigInteger) val).intValue());
      else if (key.equals("voting")) info.setVoting(((BigInteger) val).intValue());
      else if (key.equals("window")) info.setWindow(((BigInteger) val).intValue());
      else LOGGER.warn("WARNING: ignoring unexpected field in hard fork info: " + key + ": " + val);
    }
    return info;
  }
  
  private static MoneroDaemonConnectionSpan convertRpcConnectionSpan(Map<String, Object> rpcConnectionSpan) {
    MoneroDaemonConnectionSpan span = new MoneroDaemonConnectionSpan();
    for (String key : rpcConnectionSpan.keySet()) {
      Object val = rpcConnectionSpan.get(key);
      if (key.equals("connection_id")) span.setConnectionId((String) val);
      else if (key.equals("nblocks")) span.setNumBlocks(((BigInteger) val).longValue());
      else if (key.equals("rate")) span.setRate(((BigInteger) val).longValue());
      else if (key.equals("remote_address")) { if (!"".equals(val)) span.setRemoteAddress((String) val); }
      else if (key.equals("size")) span.setSize(((BigInteger) val).longValue());
      else if (key.equals("speed")) span.setSpeed(((BigInteger) val).longValue());
      else if (key.equals("start_block_height")) span.setStartHeight(((BigInteger) val).longValue());
      else LOGGER.warn("WARNING: ignoring unexpected field in daemon connection span: " + key + ": " + val);
    }
    return span;
  }
  
  private static Map<String, Object> convertToRpcBan(MoneroBan ban) {
    Map<String, Object> rpcBan = new HashMap<String, Object>();
    rpcBan.put("host", ban.getHost());
    rpcBan.put("ip", ban.getIp());
    rpcBan.put("ban", ban.isBanned());
    rpcBan.put("seconds", ban.getSeconds());
    return rpcBan;
  }
  
  private static MoneroMiningStatus convertRpcMiningStatus(Map<String, Object> rpcStatus) {
    MoneroMiningStatus status = new MoneroMiningStatus();
    status.setIsActive((Boolean) rpcStatus.get("active"));
    status.setSpeed(((BigInteger) rpcStatus.get("speed")).longValue());
    status.setNumThreads(((BigInteger) rpcStatus.get("threads_count")).intValue());
    if (status.isActive()) {
      status.setAddress((String) rpcStatus.get("address"));
      status.setIsBackground((Boolean) rpcStatus.get("is_background_mining_enabled"));
    }
    return status;
  }
  
  @SuppressWarnings("unchecked")
  private static MoneroAltChain convertRpcAltChain(Map<String, Object> rpcChain) {
    MoneroAltChain chain = new MoneroAltChain();
    for (String key : rpcChain.keySet()) {
      Object val = rpcChain.get(key);
      if (key.equals("block_hash")) {}  // using block_hashes instead
      else if (key.equals("difficulty")) { } // handled by wide_difficulty
      else if (key.equals("difficulty_top64")) { }  // handled by wide_difficulty
      else if (key.equals("wide_difficulty")) chain.setDifficulty(MoneroUtils.reconcile(chain.getDifficulty(), prefixedHexToBI((String) val)));
      else if (key.equals("height")) chain.setHeight(((BigInteger) val).longValue());
      else if (key.equals("length")) chain.setLength(((BigInteger) val).longValue());
      else if (key.equals("block_hashes")) chain.setBlockIds((List<String>) val);
      else if (key.equals("main_chain_parent_block")) chain.setMainChainParentBlockId((String) val);
      else LOGGER.warn("WARNING: ignoring unexpected field in alternative chain: " + key + ": " + val);
    }
    return chain;
  }
  
  /**
   * Converts a '0x' prefixed hexidecimal string to a BigInteger.
   * 
   * @param hex is the '0x' prefixed hexidecimal string to convert
   * @return BigInteger is the hexicedimal converted to decimal
   */
  private static BigInteger prefixedHexToBI(String hex) {
    assertTrue("Given hex does not start with \"0x\": " + hex, hex.startsWith("0x"));
    return new BigInteger(hex.substring(2), 16);
  }
  
  /**
   * Polls a Monero daemon for updates and notifies listeners as they occur.
   */
  private class MoneroDaemonPoller {
    
    private MoneroDaemon daemon;
    private MoneroDaemonPollerRunnable runnable;
    private List<MoneroDaemonListener> listeners;
    private static final long POLL_INTERVAL_MS = 10000; // poll every X ms  TODO: poll interval should come from configuration
    
    public MoneroDaemonPoller(MoneroDaemon daemon) {
      this.daemon = daemon;
      this.listeners = new ArrayList<MoneroDaemonListener>();
    }

    public void addListener(MoneroDaemonListener listener) {
      
      // register listener
      listeners.add(listener);
      
      // start polling thread
      if (runnable == null) {
        runnable = new MoneroDaemonPollerRunnable(daemon, POLL_INTERVAL_MS);
        Thread thread = new Thread(runnable);
        thread.setDaemon(true); // daemon thread does not prevent JVM from halting
        thread.start();
      }
    }
    
    public void removeListener(MoneroDaemonListener listener) {
      boolean found = listeners.remove(listener);
      if (!found) throw new MoneroException("Listener is not registered");
      if (listeners.isEmpty()) {
        runnable.terminate();
        runnable = null;
      }
    }
    
    private class MoneroDaemonPollerRunnable implements Runnable {
      
      private MoneroDaemon daemon;
      private long interval;
      private boolean isTerminated;
      
      public MoneroDaemonPollerRunnable(MoneroDaemon daemon, long interval) {
        this.daemon = daemon;
        this.interval = interval;
        this.isTerminated = false;
      }

      @Override
      public void run() {
        
        // get header to detect changes while polling
        MoneroBlockHeader lastHeader = daemon.getLastBlockHeader();
        
        // poll until stopped
        while (!isTerminated) {
          
          // pause for interval ms
          try {
            TimeUnit.MILLISECONDS.sleep(interval);
          } catch (InterruptedException e) {
            e.printStackTrace();
            terminate();
          }
          
          // fetch and compare latest block header
          MoneroBlockHeader header = daemon.getLastBlockHeader();
          if (!header.getId().equals(lastHeader.getId())) {
            lastHeader = header;
            for (MoneroDaemonListener listener : listeners) {
              listener.onBlockHeader(header); // notify listener
            }
          }
        }
      }
      
      public void terminate() {
        isTerminated = true;
      }
    }
  }
}
