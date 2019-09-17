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

package monero.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroBlockHeader;
import monero.daemon.model.MoneroKeyImage;
import monero.daemon.model.MoneroOutput;
import monero.daemon.model.MoneroTx;
import monero.rpc.MoneroRpcConnection;
import monero.rpc.MoneroRpcException;
import monero.utils.MoneroException;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroAccountTag;
import monero.wallet.model.MoneroAddressBookEntry;
import monero.wallet.model.MoneroCheckReserve;
import monero.wallet.model.MoneroCheckTx;
import monero.wallet.model.MoneroDestination;
import monero.wallet.model.MoneroIncomingTransfer;
import monero.wallet.model.MoneroIntegratedAddress;
import monero.wallet.model.MoneroKeyImageImportResult;
import monero.wallet.model.MoneroMultisigInfo;
import monero.wallet.model.MoneroMultisigInitResult;
import monero.wallet.model.MoneroMultisigSignResult;
import monero.wallet.model.MoneroOutgoingTransfer;
import monero.wallet.model.MoneroOutputQuery;
import monero.wallet.model.MoneroOutputWallet;
import monero.wallet.model.MoneroSendRequest;
import monero.wallet.model.MoneroSubaddress;
import monero.wallet.model.MoneroSyncListener;
import monero.wallet.model.MoneroSyncResult;
import monero.wallet.model.MoneroTransfer;
import monero.wallet.model.MoneroTransferQuery;
import monero.wallet.model.MoneroTxQuery;
import monero.wallet.model.MoneroTxSet;
import monero.wallet.model.MoneroTxWallet;

/**
 * Implements a Monero wallet using monero-wallet-rpc.
 */
public class MoneroWalletRpc extends MoneroWalletDefault {

  private String path;  // wallet's path identifier
  private MoneroRpcConnection rpc;  // handles rpc interactions
  private Map<Integer, Map<Integer, String>> addressCache;  // cache static addresses to reduce requests
  
  // static
  private static final int ERROR_CODE_INVALID_PAYMENT_ID = -5;  // invalid payment id error code
  private static final Logger LOGGER = Logger.getLogger(MoneroWalletRpc.class); // logger
  private static final TxHeightComparator TX_HEIGHT_COMPARATOR = new TxHeightComparator();
  
  public MoneroWalletRpc(URI uri) {
    this(new MoneroRpcConnection(uri));
  }
  
  public MoneroWalletRpc(String uri) {
    this(new MoneroRpcConnection(uri));
  }
  
  public MoneroWalletRpc(String uri, String username, String password) {
    this(new MoneroRpcConnection(uri, username, password));
  }
  
  public MoneroWalletRpc(URI uri, String username, String password) {
    this(new MoneroRpcConnection(uri, username, password));
  }
  
  public MoneroWalletRpc(MoneroRpcConnection rpc) {
    this.rpc = rpc;
    addressCache = new HashMap<Integer, Map<Integer, String>>();
  }
  
  // --------------------------- RPC WALLET METHODS ---------------------------
  
  /**
   * Get the wallet's RPC connection.
   * 
   * @return the wallet's rpc connection
   */
  public MoneroRpcConnection getRpcConnection() {
    return rpc;
  }
  
  /**
   * Open an existing wallet on the RPC server.
   * 
   * @param name is the name of the wallet file to open
   * @param password is the wallet's password
   */
  public void openWallet(String name, String password) {
    if (name == null || name.isEmpty()) throw new MoneroException("Filename is not initialized");
    if (password == null || password.isEmpty()) throw new MoneroException("Password is not initialized");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("filename", name);
    params.put("password", password);
    rpc.sendJsonRequest("open_wallet", params);
    addressCache.clear();
    path = name;
  }
  
  /**
   * Create and open a new wallet with a randomly generated seed on the RPC server.
   * 
   * @param name is the name of the wallet file to create
   * @param password is the wallet's password
   * @param language is the language for the wallet's mnemonic seed
   */
  public void createWalletRandom(String name, String password) { createWalletRandom(name, password, null); }
  public void createWalletRandom(String name, String password, String language) {
    if (name == null || name.isEmpty()) throw new MoneroException("Wallet name is not initialized");
    if (password == null || password.isEmpty()) throw new MoneroException("Password is not initialized");
    if (language == null || language.isEmpty()) language = DEFAULT_LANGUAGE;
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("filename", name);
    params.put("password", password);
    params.put("language", language);
    rpc.sendJsonRequest("create_wallet", params);
    path = name;
  }
  
  /**
   * Create and open a wallet from an existing mnemonic phrase on the RPC server,
   * closing the currently open wallet if applicable.
   * 
   * @param name is the name of the wallet to create on the RPC server
   * @param password is the wallet's password
   * @param mnemonic is the mnemonic of the wallet to construct
   * @param restoreHeight is the block height to restore from (default = 0)
   * @param language is the language of the mnemonic in case the old language is invalid
   * @param offset is the offset for restoring from mnemonic
   * @param saveCurrent specifies if the current RPC wallet should be saved before being closed
   */
  public void createWalletFromMnemonic(String name, String password, String mnemonic) { createWalletFromMnemonic(name, password, mnemonic, null, null, null, null); }
  public void createWalletFromMnemonic(String name, String password, String mnemonic, Long restoreHeight) { createWalletFromMnemonic(name, password, mnemonic, restoreHeight, null, null, null); }
  public void createWalletFromMnemonic(String name, String password, String mnemonic, Long restoreHeight, String language, String offset, Boolean saveCurrent) {
    if (language == null) language = DEFAULT_LANGUAGE;
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("filename", name);
    params.put("password", password);
    params.put("seed", mnemonic);
    params.put("seed_offset", offset);
    params.put("restore_height", restoreHeight);
    params.put("language", language);
    params.put("autosave_current", saveCurrent);
    rpc.sendJsonRequest("restore_deterministic_wallet", params);
    path = name;
  }
  
  /**
   * Save and close the current wallet and stop the RPC server.
   */
  public void stop() {
    rpc.sendJsonRequest("stop_wallet");
    addressCache.clear();
    path = null;
  }
  
  // -------------------------- COMMON WALLET METHODS -------------------------
  
  @Override
  public String getPath() {
    return path;
  }

  @Override
  public String getSeed() {
    throw new MoneroException("monero-wallet-rpc does not support getting the wallet seed");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getMnemonic() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_type", "mnemonic");
    Map<String, Object> resp = rpc.sendJsonRequest("query_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("key");
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getLanguages() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_languages");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (List<String>) result.get("languages");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getPrivateViewKey() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_type", "view_key");
    Map<String, Object> resp = rpc.sendJsonRequest("query_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("key");
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public String getPrivateSpendKey() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key_type", "spend_key");
    Map<String, Object> resp = rpc.sendJsonRequest("query_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("key");
  }

  @Override
  public String getAddress(int accountIdx, int subaddressIdx) {
    Map<Integer, String> subaddressMap = addressCache.get(accountIdx);
    if (subaddressMap == null) {
      getSubaddresses(accountIdx, null, true);      // cache's all addresses at this account
      return getAddress(accountIdx, subaddressIdx); // uses cache
    }
    String address = subaddressMap.get(subaddressIdx);
    if (address == null) {
      getSubaddresses(accountIdx, null, true);      // cache's all addresses at this account
      return addressCache.get(accountIdx).get(subaddressIdx);
    }
    return address;
  }

  // TODO: use cache
  @SuppressWarnings("unchecked")
  @Override
  public MoneroSubaddress getAddressIndex(String address) {
    
    // fetch result and normalize error if address does not belong to the wallet
    Map<String, Object> result;
    try {
      Map<String, Object> params =  new HashMap<String, Object>();
      params.put("address", address);
      Map<String, Object> resp = rpc.sendJsonRequest("get_address_index", params);
      result = (Map<String, Object>) resp.get("result");
    } catch (MoneroRpcException e) {
      System.out.println(e.getMessage());
      if (e.getCode() == -2) throw new MoneroException(e.getMessage(), e.getCode());
      throw e;
    }
    
    // convert rpc response
    Map<String, BigInteger> rpcIndices = (Map<String, BigInteger>) result.get("index");
    MoneroSubaddress subaddress = new MoneroSubaddress(address);
    subaddress.setAccountIndex(rpcIndices.get("major").intValue());
    subaddress.setIndex(rpcIndices.get("minor").intValue());
    return subaddress;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroIntegratedAddress getIntegratedAddress(String paymentId) {
    try {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("payment_id", paymentId);
      Map<String, Object> resp = rpc.sendJsonRequest("make_integrated_address", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      String integratedAddressStr = (String) result.get("integrated_address");
      return decodeIntegratedAddress(integratedAddressStr);
    } catch (MoneroRpcException e) {
      if (e.getMessage().contains("Invalid payment ID")) throw new MoneroException("Invalid payment ID: " + paymentId, ERROR_CODE_INVALID_PAYMENT_ID);
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroIntegratedAddress decodeIntegratedAddress(String integratedAddress) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("integrated_address", integratedAddress);
    Map<String, Object> resp = rpc.sendJsonRequest("split_integrated_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return new MoneroIntegratedAddress((String) result.get("standard_address"), (String) result.get("payment_id"), integratedAddress);
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public long getHeight() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_height");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return ((BigInteger) result.get("height")).longValue();
  }

  @Override
  public long getDaemonHeight() {
    throw new MoneroException("monero-wallet-rpc does not support getting the chain height");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroSyncResult sync(Long startHeight, MoneroSyncListener listener) {
    if (listener != null) throw new MoneroException("Monero Wallet RPC does not support reporting sync progress");
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("start_height", startHeight);
    Map<String, Object> resp = rpc.sendJsonRequest("refresh", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return new MoneroSyncResult(((BigInteger) result.get("blocks_fetched")).longValue(), (Boolean) result.get("received_money"));
  }
  
  @Override
  public void startSyncing() {
    // nothing to do because wallet rpc syncs automatically
  }
  
  @Override
  public void stopSyncing() {
    throw new MoneroException("Monero Wallet RPC does not support the ability to stop syncing");
  }
  
  @Override
  public void rescanSpent() {
    rpc.sendJsonRequest("rescan_spent");
  }
  
  public void rescanBlockchain() {
    rpc.sendJsonRequest("rescan_blockchain");
  }

  @Override
  public BigInteger getBalance() {
    return getBalances(null, null)[0];
  }

  @Override
  public BigInteger getBalance(int accountIdx) {
    return getBalances(accountIdx, null)[0];
  }

  @Override
  public BigInteger getBalance(int accountIdx, int subaddressIdx) {
    return getBalances(accountIdx, subaddressIdx)[0];
  }

  @Override
  public BigInteger getUnlockedBalance() {
    return getBalances(null, null)[1];
  }

  @Override
  public BigInteger getUnlockedBalance(int accountIdx) {
    return getBalances(accountIdx, null)[1];
  }

  @Override
  public BigInteger getUnlockedBalance(int accountIdx, int subaddressIdx) {
    return getBalances(accountIdx, subaddressIdx)[1];
  }
  
  @Override
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses, String tag) {
    return getAccounts(includeSubaddresses, tag, false);
  }

  @SuppressWarnings("unchecked")
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses, String tag, boolean skipBalances) {
    
    // fetch accounts from rpc
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tag", tag);
    Map<String, Object> resp = rpc.sendJsonRequest("get_accounts", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // build account objects and fetch subaddresses per account using get_address
    // TODO monero-wallet-rpc: get_address should support all_accounts so not called once per account
    List<MoneroAccount> accounts = new ArrayList<MoneroAccount>();
    for (Map<String, Object> rpcAccount : (List<Map<String, Object>>) result.get("subaddress_accounts")) {
      MoneroAccount account = convertRpcAccount(rpcAccount);
      if (includeSubaddresses) account.setSubaddresses(getSubaddresses(account.getIndex(), null, true));
      accounts.add(account);
    }
    
    // fetch and merge fields from get_balance across all accounts
    if (includeSubaddresses && !skipBalances) {
      
      // these fields are not initialized if subaddress is unused and therefore not returned from `get_balance`
      for (MoneroAccount account : accounts) {
        for (MoneroSubaddress subaddress : account.getSubaddresses()) {
          subaddress.setBalance(BigInteger.valueOf(0));
          subaddress.setUnlockedBalance(BigInteger.valueOf(0));
          subaddress.setNumUnspentOutputs(0l);
          subaddress.setNumBlocksToUnlock(0l);
        }
      }
      
      // fetch and merge info from get_balance
      params.clear();
      params.put("all_accounts", true);
      resp = rpc.sendJsonRequest("get_balance", params);
      result = (Map<String, Object>) resp.get("result");
      if (result.containsKey("per_subaddress")) {
        for (Map<String, Object> rpcSubaddress : (List<Map<String, Object>>) result.get("per_subaddress")) {
          MoneroSubaddress subaddress = convertRpcSubaddress(rpcSubaddress);
          
          // merge info
          MoneroAccount account = accounts.get(subaddress.getAccountIndex());
          assertEquals("RPC accounts are out of order", account.getIndex(), subaddress.getAccountIndex());  // would need to switch lookup to loop
          MoneroSubaddress tgtSubaddress = account.getSubaddresses().get(subaddress.getIndex());
          assertEquals("RPC subaddresses are out of order", tgtSubaddress.getIndex(), subaddress.getIndex());
          if (subaddress.getBalance() != null) tgtSubaddress.setBalance(subaddress.getBalance());
          if (subaddress.getUnlockedBalance() != null) tgtSubaddress.setUnlockedBalance(subaddress.getUnlockedBalance());
          if (subaddress.getNumUnspentOutputs() != null) tgtSubaddress.setNumUnspentOutputs(subaddress.getNumUnspentOutputs());
          if (subaddress.getNumBlocksToUnlock() != null) tgtSubaddress.setNumBlocksToUnlock(subaddress.getNumBlocksToUnlock());
        }
      }
    }
    
    // return accounts
    return accounts;
  }

  // TODO: getAccountByIndex(), getAccountByTag()
  @Override
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses) {
    return getAccount(accountIdx, includeSubaddresses, false);
  }
  
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses, boolean skipBalances) {
    if (accountIdx < 0) throw new MoneroException("Account index must be greater than or equal to 0");
    for (MoneroAccount account : getAccounts()) {
      if (account.getIndex() == accountIdx) {
        if (includeSubaddresses) account.setSubaddresses(getSubaddresses(accountIdx, null, skipBalances));
        return account;
      }
    }
    throw new MoneroException("Account with index " + accountIdx + " does not exist");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroAccount createAccount(String label) {
    label = label == null || label.isEmpty() ? null : label;
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("label", label);
    Map<String, Object> resp = rpc.sendJsonRequest("create_account", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return new MoneroAccount(((BigInteger) result.get("account_index")).intValue(), (String) result.get("address"), BigInteger.valueOf(0), BigInteger.valueOf(0), null);
  }
  
  @Override
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices) {
    return getSubaddresses(accountIdx, subaddressIndices, false);
  }

  @SuppressWarnings("unchecked")
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices, boolean skipBalances) {
    
    // fetch subaddresses
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    if (subaddressIndices != null && !subaddressIndices.isEmpty()) params.put("address_index", subaddressIndices);
    Map<String, Object> resp = rpc.sendJsonRequest("get_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // initialize subaddresses
    List<MoneroSubaddress> subaddresses = new ArrayList<MoneroSubaddress>();
    for (Map<String, Object> rpcSubaddress : (List<Map<String, Object>>) result.get("addresses")) {
      MoneroSubaddress subaddress = convertRpcSubaddress(rpcSubaddress);
      subaddress.setAccountIndex(accountIdx);
      subaddresses.add(subaddress);
    }
    
    // fetch and initialize subaddress balances
    if (!skipBalances) {
      
      // these fields are not initialized if subaddress is unused and therefore not returned from `get_balance`
      for (MoneroSubaddress subaddress : subaddresses) {
        subaddress.setBalance(BigInteger.valueOf(0));
        subaddress.setUnlockedBalance(BigInteger.valueOf(0));
        subaddress.setNumUnspentOutputs(0l);
        subaddress.setNumBlocksToUnlock(0l);
      }

      // fetch and initialize balances
      resp = rpc.sendJsonRequest("get_balance", params);
      result = (Map<String, Object>) resp.get("result");
      if (result.containsKey("per_subaddress")) {
        for (Map<String, Object> rpcSubaddress : (List<Map<String, Object>>) result.get("per_subaddress")) {
          MoneroSubaddress subaddress = convertRpcSubaddress(rpcSubaddress);
          
          // transfer info to existing subaddress object
          for (MoneroSubaddress tgtSubaddress : subaddresses) {
            if (!tgtSubaddress.getIndex().equals(subaddress.getIndex())) continue; // skip to subaddress with same index
            if (subaddress.getBalance() != null) tgtSubaddress.setBalance(subaddress.getBalance());
            if (subaddress.getUnlockedBalance() != null) tgtSubaddress.setUnlockedBalance(subaddress.getUnlockedBalance());
            if (subaddress.getNumUnspentOutputs() != null) tgtSubaddress.setNumUnspentOutputs(subaddress.getNumUnspentOutputs());
            if (subaddress.getNumBlocksToUnlock() != null) tgtSubaddress.setNumBlocksToUnlock(subaddress.getNumBlocksToUnlock());
          }
        }
      }
    }
    
    // cache addresses
    Map<Integer, String> subaddressMap = addressCache.get(accountIdx);
    if (subaddressMap == null) {
      subaddressMap = new HashMap<Integer, String>();
      addressCache.put(accountIdx, subaddressMap);
    }
    for (MoneroSubaddress subaddress : subaddresses) {
      subaddressMap.put(subaddress.getIndex(), subaddress.getAddress());
    }
    
    // return results
    return subaddresses;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroSubaddress createSubaddress(int accountIdx, String label) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    params.put("label", label);
    Map<String, Object> resp = rpc.sendJsonRequest("create_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // build subaddress object
    MoneroSubaddress subaddress = new MoneroSubaddress();
    subaddress.setAccountIndex(accountIdx);
    subaddress.setIndex(((BigInteger) result.get("address_index")).intValue());
    subaddress.setAddress((String) result.get("address"));
    subaddress.setLabel(label);
    subaddress.setBalance(BigInteger.valueOf(0));
    subaddress.setUnlockedBalance(BigInteger.valueOf(0));
    subaddress.setNumUnspentOutputs(0l);
    subaddress.setIsUsed(false);
    subaddress.setNumBlocksToUnlock(0l);
    return subaddress;
  }

  @Override
  public List<MoneroTxWallet> getTxs(MoneroTxQuery query) {
    
    // copy and normalize tx query
    query = query == null ? new MoneroTxQuery() : query.copy();
    if (query.getTransferQuery() == null) query.setTransferQuery(new MoneroTransferQuery());
    if (query.getOutputQuery() == null) query.setOutputQuery(new MoneroOutputQuery());
    
    // temporarily disable transfer and output queries in order to collect all tx information
    MoneroTransferQuery transferQuery = query.getTransferQuery();
    MoneroOutputQuery  outputQuery = query.getOutputQuery();
    query.setTransferQuery(null);
    query.setOutputQuery(null);
    
    // fetch all transfers that meet tx query
    List<MoneroTransfer> transfers = getTransfers(new MoneroTransferQuery().setTxQuery(query));
    
    // collect unique txs from transfers while retaining order
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    Set<MoneroTxWallet> txsSet = new HashSet<MoneroTxWallet>();
    for (MoneroTransfer transfer : transfers) {
      if (!txsSet.contains(transfer.getTx())) {
        txs.add(transfer.getTx());
        txsSet.add(transfer.getTx());
      }
    }
    
    // cache types into maps for merging and lookup
    Map<String, MoneroTxWallet> txMap = new HashMap<String, MoneroTxWallet>();
    Map<Long, MoneroBlock> blockMap = new HashMap<Long, MoneroBlock>();
    for (MoneroTxWallet tx : txs) {
      mergeTx(tx, txMap, blockMap, false);
    }
    
    // fetch and merge outputs if queried
    if (Boolean.TRUE.equals(query.getIncludeOutputs()) || !outputQuery.isDefault()) {
      List<MoneroOutputWallet> outputs = getOutputs(new MoneroOutputQuery().setTxQuery(query));
      
      // merge output txs one time while retaining order
      Set<MoneroTxWallet> outputTxs = new HashSet<MoneroTxWallet>();
      for (MoneroOutputWallet output : outputs) {
        if (!outputTxs.contains(output.getTx())) {
          mergeTx(output.getTx(), txMap, blockMap, true);
          outputTxs.add(output.getTx());
        }
      }
    }
    
    // restore transfer and output queries
    query.setTransferQuery(transferQuery);
    query.setOutputQuery(outputQuery);
    
    // filter txs that don't meet transfer and output queries
    List<MoneroTxWallet> txsQueried = new ArrayList<MoneroTxWallet>();
    for (MoneroTxWallet tx : txs) {
      if (query.meetsCriteria(tx)) txsQueried.add(tx);
      else if (tx.getBlock() != null) tx.getBlock().getTxs().remove(tx);
    }
    txs = txsQueried;
    
    // verify all specified tx ids found
    if (query.getTxIds() != null) {
      for (String txId : query.getTxIds()) {
        boolean found = false;
        for (MoneroTxWallet tx : txs) {
          if (txId.equals(tx.getId())) {
            found = true;
            break;
          }
        }
        if (!found) throw new MoneroException("Tx not found in wallet: " + txId);
      }
    }
    
    // special case: re-fetch txs if inconsistency caused by needing to make multiple rpc calls
    for (MoneroTxWallet tx : txs) {
      if (tx.isConfirmed() && tx.getBlock() == null) return getTxs(query);
    }
    
    // order txs if tx ids given
    if (query.getTxIds() != null && !query.getTxIds().isEmpty()) {
      Map<String, MoneroTxWallet> txsById = new HashMap<String, MoneroTxWallet>();  // store txs in temporary map for sorting
      for (MoneroTxWallet tx : txs) txsById.put(tx.getId(), tx);
      List<MoneroTxWallet> orderedTxs = new ArrayList<MoneroTxWallet>();
      for (String txId : query.getTxIds()) orderedTxs.add(txsById.get(txId));
      txs = orderedTxs;
    }
    return txs;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroTransfer> getTransfers(MoneroTransferQuery query) {
    
    // copy and normalize query up to block
    if (query == null) query = new MoneroTransferQuery();
    else {
      if (query.getTxQuery() == null) query = query.copy();
      else {
        MoneroTxQuery txQuery = query.getTxQuery().copy();
        if (query.getTxQuery().getTransferQuery() == query) query = txQuery.getTransferQuery();
        else {
          assertNull("Transfer query's tx query must be circular reference or null", query.getTxQuery().getTransferQuery());
          query = query.copy();
          query.setTxQuery(txQuery);
        }
      }
    }
    if (query.getTxQuery() == null) query.setTxQuery(new MoneroTxQuery());
    MoneroTxQuery txQuery = query.getTxQuery();
    txQuery.setTransferQuery(null); // break circular link for meetsCriteria()

    // build params for get_transfers rpc call
    Map<String, Object> params = new HashMap<String, Object>();
    boolean canBeConfirmed = !Boolean.FALSE.equals(txQuery.isConfirmed()) && !Boolean.TRUE.equals(txQuery.inTxPool()) && !Boolean.TRUE.equals(txQuery.isFailed()) && !Boolean.FALSE.equals(txQuery.isRelayed());
    boolean canBeInTxPool = !Boolean.TRUE.equals(txQuery.isConfirmed()) && !Boolean.FALSE.equals(txQuery.inTxPool()) && !Boolean.TRUE.equals(txQuery.isFailed()) && !Boolean.FALSE.equals(txQuery.isRelayed()) && txQuery.getHeight() == null && txQuery.getMinHeight() == null && txQuery.getMaxHeight() == null;
    boolean canBeIncoming = !Boolean.FALSE.equals(query.isIncoming()) && !Boolean.TRUE.equals(query.isOutgoing()) && !Boolean.TRUE.equals(query.hasDestinations());
    boolean canBeOutgoing = !Boolean.FALSE.equals(query.isOutgoing()) && !Boolean.TRUE.equals(query.isIncoming());
    params.put("in", canBeIncoming && canBeConfirmed);
    params.put("out", canBeOutgoing && canBeConfirmed);
    params.put("pool", canBeIncoming && canBeInTxPool);
    params.put("pending", canBeOutgoing && canBeInTxPool);
    params.put("failed", !Boolean.FALSE.equals(txQuery.isFailed()) && !Boolean.TRUE.equals(txQuery.isConfirmed()) && !Boolean.TRUE.equals(txQuery.inTxPool()));
    if (txQuery.getMinHeight() != null) {
      if (txQuery.getMinHeight() > 0) params.put("min_height", txQuery.getMinHeight() - 1); // TODO monero core: wallet2::get_payments() min_height is exclusive, so manually offset to match intended range (issues #5751, #5598)
      else params.put("min_height", txQuery.getMinHeight());
    }
    if (txQuery.getMaxHeight() != null) params.put("max_height", txQuery.getMaxHeight());
    params.put("filter_by_height", txQuery.getMinHeight() != null || txQuery.getMaxHeight() != null);
    if (query.getAccountIndex() == null) {
      assertTrue("Filter specifies a subaddress index but not an account index", query.getSubaddressIndex() == null && query.getSubaddressIndices() == null);
      params.put("all_accounts", true);
    } else {
      params.put("account_index", query.getAccountIndex());
      
      // set subaddress indices param
      Set<Integer> subaddressIndices = new HashSet<Integer>();
      if (query.getSubaddressIndex() != null) subaddressIndices.add(query.getSubaddressIndex());
      if (query.getSubaddressIndices() != null) {
        for (int subaddressIdx : query.getSubaddressIndices()) subaddressIndices.add(subaddressIdx);
      }
      if (!subaddressIndices.isEmpty()) params.put("subaddr_indices", new ArrayList<Integer>(subaddressIndices));
    }
    
    // cache unique txs and blocks
    Map<String, MoneroTxWallet> txMap = new HashMap<String, MoneroTxWallet>();
    Map<Long, MoneroBlock> blockMap = new HashMap<Long, MoneroBlock>();
    
    // build txs using `get_transfers`
    Map<String, Object> resp = rpc.sendJsonRequest("get_transfers", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    for (String key : result.keySet()) {
      for (Map<String, Object> rpcTx :((List<Map<String, Object>>) result.get(key))) {
        MoneroTxWallet tx = convertRpcTxWithTransfer(rpcTx, null, null);
        if (tx.isConfirmed()) assertTrue(tx.getBlock().getTxs().contains(tx));
//        if (tx.getId().equals("38436c710dfbebfb24a14cddfd430d422e7282bbe94da5e080643a1bd2880b44")) {
//          System.out.println(rpcTx);
//          System.out.println(tx.getOutgoingAmount().compareTo(BigInteger.valueOf(0)) == 0);
//        }
        
        // replace transfer amount with destination sum
        // TODO monero-wallet-rpc: confirmed tx from/to same account has amount 0 but cached transfers
        if (tx.getOutgoingTransfer() != null && Boolean.TRUE.equals(tx.isRelayed()) && !Boolean.TRUE.equals(tx.isFailed()) &&
            tx.getOutgoingTransfer().getDestinations() != null && tx.getOutgoingAmount().compareTo(BigInteger.valueOf(0)) == 0) {
          MoneroOutgoingTransfer outgoingTransfer = tx.getOutgoingTransfer();
          BigInteger transferTotal = BigInteger.valueOf(0);
          for (MoneroDestination destination : outgoingTransfer.getDestinations()) transferTotal = transferTotal.add(destination.getAmount());
          tx.getOutgoingTransfer().setAmount(transferTotal);
        }
        
        // merge tx
        mergeTx(tx, txMap, blockMap, false);
      }
    }
    
    // sort txs by block height
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>(txMap.values());
    Collections.sort(txs, new TxHeightComparator());
    
    // filter and return transfers
    List<MoneroTransfer> transfers = new ArrayList<MoneroTransfer>();
    for (MoneroTxWallet tx : txs) {
      
      // sort transfers
      if (tx.getIncomingTransfers() != null) Collections.sort(tx.getIncomingTransfers(), new IncomingTransferComparator());
      
      // collect outgoing transfer, erase if excluded
      if (tx.getOutgoingTransfer() != null && query.meetsCriteria(tx.getOutgoingTransfer())) transfers.add(tx.getOutgoingTransfer());
      else tx.setOutgoingTransfer(null);
      
      // collect incoming transfers, erase if excluded
      if (tx.getIncomingTransfers() != null) {
        List<MoneroIncomingTransfer> toRemoves = new ArrayList<MoneroIncomingTransfer>();
        for (MoneroIncomingTransfer transfer : tx.getIncomingTransfers()) {
          if (query.meetsCriteria(transfer)) transfers.add(transfer);
          else toRemoves.add(transfer);
        }
        tx.getIncomingTransfers().removeAll(toRemoves);
        if (tx.getIncomingTransfers().isEmpty()) tx.setIncomingTransfers(null);
      }
      
      // remove excluded txs from block
      if (tx.getBlock() != null && tx.getOutgoingTransfer() == null && tx.getIncomingTransfers() == null ) {
        tx.getBlock().getTxs().remove(tx);
      }
    }
    return transfers;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroOutputWallet> getOutputs(MoneroOutputQuery query) {
    
    // copy and normalize query up to block
    if (query == null) query = new MoneroOutputQuery();
    else {
      if (query.getTxQuery() == null) query = query.copy();
      else {
        MoneroTxQuery txQuery = query.getTxQuery().copy();
        if (query.getTxQuery().getOutputQuery() == query) query = txQuery.getOutputQuery();
        else {
          assertNull("Output request's tx request must be circular reference or null", query.getTxQuery().getOutputQuery());
          query = query.copy();
          query.setTxQuery(txQuery);
        }
      }
    }
    if (query.getTxQuery() == null) query.setTxQuery(new MoneroTxQuery());
    MoneroTxQuery txQuery = query.getTxQuery();
    txQuery.setOutputQuery(null); // break circular link for meetsCriteria()
    
    // determine account and subaddress indices to be queried
    Map<Integer, List<Integer>> indices = new HashMap<Integer, List<Integer>>();
    if (query.getAccountIndex() != null) {
      Set<Integer> subaddressIndices = new HashSet<Integer>();
      if (query.getSubaddressIndex() != null) subaddressIndices.add(query.getSubaddressIndex());
      if (query.getSubaddressIndices() != null) for (int subaddressIdx : query.getSubaddressIndices()) subaddressIndices.add(subaddressIdx);
      indices.put(query.getAccountIndex(), subaddressIndices.isEmpty() ? null : new ArrayList<Integer>(subaddressIndices));  // null will fetch from all subaddresses
    } else {
      assertEquals("Request specifies a subaddress index but not an account index", null, query.getSubaddressIndex());
      assertTrue("Request specifies subaddress indices but not an account index", query.getSubaddressIndices() == null || query.getSubaddressIndices().size() == 0);
      indices = getAccountIndices(false);  // fetch all account indices without subaddresses
    }
    
    // cache unique txs and blocks
    Map<String, MoneroTxWallet> txMap = new HashMap<String, MoneroTxWallet>();
    Map<Long, MoneroBlock> blockMap = new HashMap<Long, MoneroBlock>();
    
    // collect txs with vouts for each indicated account using `incoming_transfers` rpc call
    Map<String, Object> params = new HashMap<String, Object>();
    String transferType;
    if (Boolean.TRUE.equals(query.isSpent())) transferType = "unavailable";
    else if (Boolean.FALSE.equals(query.isSpent())) transferType = "available";
    else transferType = "all";
    params.put("transfer_type", transferType);
    params.put("verbose", true);
    for (int accountIdx : indices.keySet()) {
    
      // send request
      params.put("account_index", accountIdx);
      params.put("subaddr_indices", indices.get(accountIdx));
      Map<String, Object> resp = rpc.sendJsonRequest("incoming_transfers", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      
      // convert response to txs with vouts and merge
      if (!result.containsKey("transfers")) continue;
      for (Map<String, Object> rpcVout : (List<Map<String, Object>>) result.get("transfers")) {
        MoneroTxWallet tx = convertRpcTxWithVout(rpcVout);
        mergeTx(tx, txMap, blockMap, false);
      }
    }
    
    // sort txs by block height
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>(txMap.values());
    Collections.sort(txs, new TxHeightComparator());
    
    // collect queried vouts
    List<MoneroOutputWallet> vouts = new ArrayList<MoneroOutputWallet>();
    for (MoneroTxWallet tx : txs) {
      
      // sort vouts
      if (tx.getVouts() != null) Collections.sort(tx.getVouts(), new VoutComparator());
      
      // collect queried vouts
      List<MoneroOutput> toRemoves = new ArrayList<MoneroOutput>();
      for (MoneroOutput vout : tx.getVouts()) {
        if (query.meetsCriteria((MoneroOutputWallet) vout)) vouts.add((MoneroOutputWallet) vout);
        else toRemoves.add(vout);
      }
      
      // remove excluded vouts from tx
      tx.getVouts().removeAll(toRemoves);
      
      // remove excluded txs from block
      if (tx.getVouts().isEmpty() && tx.getBlock() != null) tx.getBlock().getTxs().remove(tx);
    }
    return vouts;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public String getOutputsHex() {
    Map<String, Object> resp = rpc.sendJsonRequest("export_outputs");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("outputs_data_hex");
  }

  @SuppressWarnings("unchecked")
  @Override
  public int importOutputsHex(String outputsHex) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("outputs_data_hex", outputsHex);
    Map<String, Object> resp = rpc.sendJsonRequest("import_outputs", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return ((BigInteger) result.get("num_imported")).intValue();
  }

  @Override
  public List<MoneroKeyImage> getKeyImages() {
    return rpcExportKeyImages(true);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroKeyImageImportResult importKeyImages(List<MoneroKeyImage> keyImages) {
    
    // convert key images to rpc parameter
    List<Map<String, Object>> rpcKeyImages = new ArrayList<Map<String, Object>>();
    for (MoneroKeyImage keyImage : keyImages) {
      Map<String, Object> rpcKeyImage = new HashMap<String, Object>();
      rpcKeyImage.put("key_image", keyImage.getHex());
      rpcKeyImage.put("signature", keyImage.getSignature());
      rpcKeyImages.add(rpcKeyImage);
    }
    
    // send rpc request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("signed_key_images", rpcKeyImages);
    Map<String, Object> resp = rpc.sendJsonRequest("import_key_images", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // build and return result
    MoneroKeyImageImportResult importResult = new MoneroKeyImageImportResult();
    importResult.setHeight(((BigInteger) result.get("height")).longValue());
    importResult.setSpentAmount((BigInteger) result.get("spent"));
    importResult.setUnspentAmount((BigInteger) result.get("unspent"));
    return importResult;
  }

  @Override
  public List<MoneroKeyImage> getNewKeyImagesFromLastImport() {
    return rpcExportKeyImages(false);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> relayTxs(Collection<String> txMetadatas) {
    if (txMetadatas == null || txMetadatas.isEmpty()) throw new MoneroException("Must provide an array of tx metadata to relay");
    List<String> txIds = new ArrayList<String>();
    for (String txMetadata : txMetadatas) {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("hex", txMetadata);
      Map<String, Object> resp = rpc.sendJsonRequest("relay_tx", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      txIds.add((String) result.get("tx_hash"));
    }
    return txIds;
  }
  
  @SuppressWarnings("unchecked")
  public MoneroTxSet sendSplit(MoneroSendRequest request) {
    
    // validate, copy, and sanitize request
    if (request == null) throw new MoneroException("Send request cannot be null");
    assertNotNull(request.getDestinations());
    assertNull(request.getSweepEachSubaddress());
    assertNull(request.getBelowAmount());
    if (request.getCanSplit() == null) {
      request = request.copy();
      request.setCanSplit(true);
    }
    
    // determine account and subaddresses to send from
    Integer accountIdx = request.getAccountIndex();
    if (accountIdx == null) throw new MoneroException("Must specify the account index to send from");
    List<Integer> subaddressIndices = request.getSubaddressIndices() == null ? null : new ArrayList<Integer>(request.getSubaddressIndices()); // fetch all or copy given indices
    
    // build request parameters
    Map<String, Object> params = new HashMap<String, Object>();
    List<Map<String, Object>> destinationMaps = new ArrayList<Map<String, Object>>();
    params.put("destinations", destinationMaps);
    for (MoneroDestination destination : request.getDestinations()) {
      assertNotNull("Destination address is not defined", destination.getAddress());
      assertNotNull("Destination amount is not defined", destination.getAmount());
      Map<String, Object> destinationMap = new HashMap<String, Object>();
      destinationMap.put("address", destination.getAddress());
      destinationMap.put("amount", destination.getAmount().toString());
      destinationMaps.add(destinationMap);
    }
    params.put("account_index", accountIdx);
    params.put("subaddr_indices", subaddressIndices);
    params.put("payment_id", request.getPaymentId());
    params.put("mixin", request.getMixin());
    params.put("ring_size", request.getRingSize());
    params.put("unlock_time", request.getUnlockTime());
    params.put("do_not_relay", request.getDoNotRelay());
    params.put("priority", request.getPriority() == null ? null : request.getPriority().ordinal());
    params.put("get_tx_hex", true);
    params.put("get_tx_metadata", true);
    if (request.getCanSplit()) params.put("get_tx_keys", true); // param to get tx key(s) depends if split
    else params.put("get_tx_key", true);
    
    // send request
    Map<String, Object> resp = rpc.sendJsonRequest(request.getCanSplit() ? "transfer_split" : "transfer", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // pre-initialize txs iff present.  multisig and watch-only wallets will have tx set without transactions
    List<MoneroTxWallet> txs = null;
    int numTxs = request.getCanSplit() ? (result.containsKey("fee_list") ? ((List<String>) result.get("fee_list")).size() : 0) : (result.containsKey("fee") ? 1 : 0);
    if (numTxs > 0) txs = new ArrayList<MoneroTxWallet>();
    for (int i = 0; i < numTxs; i++) {
      MoneroTxWallet tx = new MoneroTxWallet();
      initSentTxWallet(request, tx);
      tx.getOutgoingTransfer().setAccountIndex(accountIdx);
      if (subaddressIndices != null && subaddressIndices.size() == 1) tx.getOutgoingTransfer().setSubaddressIndices(subaddressIndices);
      txs.add(tx);
    }
    
    // initialize tx set from rpc response with pre-initialized txs
    if (request.getCanSplit()) return convertRpcSentTxsToTxSet(result, txs);
    else return convertRpcTxToTxSet(result, txs == null ? null : txs.get(0), true);
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroTxSet sweepOutput(MoneroSendRequest request) {
    
    // validate request
    assertNull(request.getSweepEachSubaddress());
    assertNull(request.getBelowAmount());
    assertNull("Splitting is not applicable when sweeping output", request.getCanSplit());
    
    // build request parameters
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", request.getDestinations().get(0).getAddress());
    params.put("account_index", request.getAccountIndex());
    params.put("subaddr_indices", request.getSubaddressIndices());
    params.put("key_image", request.getKeyImage());
    params.put("mixin", request.getMixin());
    params.put("ring_size", request.getRingSize());
    params.put("unlock_time", request.getUnlockTime());
    params.put("do_not_relay", request.getDoNotRelay());
    params.put("priority", request.getPriority() == null ? null : request.getPriority().ordinal());
    params.put("payment_id", request.getPaymentId());
    params.put("get_tx_key", true);
    params.put("get_tx_hex", true);
    params.put("get_tx_metadata", true);
    
    // send request
    Map<String, Object> resp = (Map<String, Object>) rpc.sendJsonRequest("sweep_single", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");

    // build and return tx response
    MoneroTxWallet tx = initSentTxWallet(request, null);
    MoneroTxSet txSet = convertRpcTxToTxSet(result, tx, true);
    tx.getOutgoingTransfer().getDestinations().get(0).setAmount(tx.getOutgoingTransfer().getAmount());  // initialize destination amount
    return txSet;
  }
  
  @Override
  public List<MoneroTxSet> sweepUnlocked(MoneroSendRequest request) {
    
    // validate request
    if (request == null) throw new MoneroException("Sweep request cannot be null");
    if (request.getDestinations() == null || request.getDestinations().size() != 1) throw new MoneroException("Must specify exactly one destination to sweep to");
    if (request.getDestinations().get(0).getAddress() == null) throw new MoneroException("Must specify destination address to sweep to");
    if (request.getDestinations().get(0).getAmount() != null) throw new MoneroException("Cannot specify amount in sweep request");
    if (request.getKeyImage() != null) throw new MoneroException("Key image defined; use sweepOutput() to sweep an output by its key image");
    if (request.getSubaddressIndices() != null && request.getSubaddressIndices().isEmpty()) request.setSubaddressIndices((List<Integer>) null);
    if (request.getAccountIndex() == null && request.getSubaddressIndices() != null) throw new MoneroException("Must specify account index if subaddress indices are specified");
    
    // determine account and subaddress indices to sweep; default to all with unlocked balance if not specified
    LinkedHashMap<Integer, List<Integer>> indices = new LinkedHashMap<Integer, List<Integer>>();  // java type preserves insertion order
    if (request.getAccountIndex() != null) {
      if (request.getSubaddressIndices() != null) {
        indices.put(request.getAccountIndex(), request.getSubaddressIndices());
      } else {
        List<Integer> subaddressIndices = new ArrayList<Integer>();
        indices.put(request.getAccountIndex(), subaddressIndices);
        for (MoneroSubaddress subaddress : getSubaddresses(request.getAccountIndex())) {
          if (subaddress.getUnlockedBalance().compareTo(BigInteger.valueOf(0)) > 0) subaddressIndices.add(subaddress.getIndex());
        }
      }
    } else {
      List<MoneroAccount> accounts = getAccounts(true);
      for (MoneroAccount account : accounts) {
        if (account.getUnlockedBalance().compareTo(BigInteger.valueOf(0)) > 0) {
          List<Integer> subaddressIndices = new ArrayList<Integer>();
          indices.put(account.getIndex(), subaddressIndices);
          for (MoneroSubaddress subaddress : account.getSubaddresses()) {
            if (subaddress.getUnlockedBalance().compareTo(BigInteger.valueOf(0)) > 0) subaddressIndices.add(subaddress.getIndex());
          }
        }
      }
    }
    
    // sweep from each account and collect resulting tx sets
    List<MoneroTxSet> txSets = new ArrayList<MoneroTxSet>();
    for (Integer accountIdx : indices.keySet()) {
      
      // copy and modify the original request
      MoneroSendRequest copy = request.copy();
      copy.setAccountIndex(accountIdx);
      copy.setSweepEachSubaddress(false);
      
      // sweep all subaddresses together  // TODO monero core: can this reveal outputs belong to the same wallet?
      if (!Boolean.TRUE.equals(copy.getSweepEachSubaddress())) {
        copy.setSubaddressIndices(indices.get(accountIdx));
        txSets.add(rpcSweepAccount(copy));
      }
      
      // otherwise sweep each subaddress individually
      else {
        for (int subaddressIdx : indices.get(accountIdx)) {
          copy.setSubaddressIndices(subaddressIdx);
          txSets.add(rpcSweepAccount(copy));
        }
      }
    }
    
    // return resulting tx sets
    return txSets;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroTxSet sweepDust(boolean doNotRelay) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("do_not_relay", doNotRelay);
    Map<String, Object> resp = rpc.sendJsonRequest("sweep_dust", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroTxSet txSet = convertRpcSentTxsToTxSet(result, null);
    if (txSet.getTxs() != null) {
      for (MoneroTxWallet tx : txSet.getTxs()) {
        tx.setIsRelayed(!doNotRelay);
        tx.setInTxPool(tx.isRelayed());
      }
    } else if (txSet.getMultisigTxHex() == null && txSet.getSignedTxHex() == null && txSet.getUnsignedTxHex() == null) {
      throw new MoneroException("No dust to sweep");
    }
    return txSet;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String sign(String msg) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("data", msg);
    Map<String, Object> resp = rpc.sendJsonRequest("sign", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean verify(String msg, String address, String signature) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("data", msg);
    params.put("address", address);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("verify", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (boolean) result.get("good");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getTxKey(String txId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    Map<String, Object> resp = rpc.sendJsonRequest("get_tx_key", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("tx_key");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroCheckTx checkTxKey(String txId, String txKey, String address) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("tx_key", txKey);
    params.put("address", address);
    Map<String, Object> resp = rpc.sendJsonRequest("check_tx_key", params);
    
    // interpret result
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroCheckTx check = new MoneroCheckTx();
    check.setIsGood(true);
    check.setNumConfirmations(((BigInteger) result.get("confirmations")).longValue());
    check.setInTxPool((Boolean) result.get("in_pool"));
    check.setReceivedAmount((BigInteger) result.get("received"));
    return check;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getTxProof(String txId, String address, String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("address", address);
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_tx_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroCheckTx checkTxProof(String txId, String address, String message, String signature) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("address", address);
    params.put("message", message);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("check_tx_proof", params);
    
    // interpret response
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    boolean isGood = (boolean) result.get("good");
    MoneroCheckTx check = new MoneroCheckTx();
    check.setIsGood(isGood);
    if (isGood) {
      check.setNumConfirmations(((BigInteger) result.get("confirmations")).longValue());
      check.setInTxPool((boolean) result.get("in_pool"));
      check.setReceivedAmount((BigInteger) result.get("received"));
    }
    return check;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getSpendProof(String txId, String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_spend_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean checkSpendProof(String txId, String message, String signature) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txid", txId);
    params.put("message", message);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("check_spend_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (boolean) result.get("good");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getReserveProofWallet(String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("all", true);
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_reserve_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    params.put("amount", amount.toString());
    params.put("message", message);
    Map<String, Object> resp = rpc.sendJsonRequest("get_reserve_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("signature");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroCheckReserve checkReserveProof(String address, String message, String signature) {
    
    // send request
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", address);
    params.put("message", message);
    params.put("signature", signature);
    Map<String, Object> resp = rpc.sendJsonRequest("check_reserve_proof", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // interpret results
    boolean isGood = (boolean) result.get("good");
    MoneroCheckReserve check = new MoneroCheckReserve();
    check.setIsGood(isGood);
    if (isGood) {
      check.setTotalAmount((BigInteger) result.get("total"));
      check.setUnconfirmedSpentAmount((BigInteger) result.get("spent"));
    }
    return check;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public List<String> getTxNotes(Collection<String> txIds) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txids", txIds);
    Map<String, Object> resp = rpc.sendJsonRequest("get_tx_notes", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (List<String>) result.get("notes");
  }

  @Override
  public void setTxNotes(Collection<String> txIds, Collection<String> notes) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("txids", txIds);
    params.put("notes", notes);
    rpc.sendJsonRequest("set_tx_notes", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroAddressBookEntry> getAddressBookEntries(Collection<Integer> entryIndices) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("entries", entryIndices);
    Map<String, Object> respMap = rpc.sendJsonRequest("get_address_book", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    List<MoneroAddressBookEntry> entries = new ArrayList<MoneroAddressBookEntry>();
    if (!resultMap.containsKey("entries")) return entries;
    for (Map<String, Object> entryMap : (List<Map<String, Object>>) resultMap.get("entries")) {
      MoneroAddressBookEntry entry = new MoneroAddressBookEntry(
              ((BigInteger) entryMap.get("index")).intValue(),
              (String) entryMap.get("address"),
              (String) entryMap.get("payment_id"),
              (String) entryMap.get("description")
      );
      entries.add(entry);
    }
    return entries;
  }

  @SuppressWarnings("unchecked")
  @Override
  public int addAddressBookEntry(String address, String description, String paymentId) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", address);
    params.put("payment_id", paymentId);
    params.put("description", description);
    Map<String, Object> respMap = rpc.sendJsonRequest("add_address_book", params);
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    return ((BigInteger) resultMap.get("index")).intValue();
  }

  @Override
  public void deleteAddressBookEntry(int entryIdx) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("index", entryIdx);
    rpc.sendJsonRequest("delete_address_book", params);
  }
  
  @Override
  public void tagAccounts(String tag, Collection<Integer> accountIndices) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tag", tag);
    params.put("accounts", accountIndices);
    rpc.sendJsonRequest("tag_accounts", params);
  }

  @Override
  public void untagAccounts(Collection<Integer> accountIndices) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("accounts", accountIndices);
    rpc.sendJsonRequest("untag_accounts", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<MoneroAccountTag> getAccountTags() {
    List<MoneroAccountTag> tags = new ArrayList<MoneroAccountTag>();
    Map<String, Object> respMap = rpc.sendJsonRequest("get_account_tags");
    Map<String, Object> resultMap = (Map<String, Object>) respMap.get("result");
    List<Map<String, Object>> accountTagMaps = (List<Map<String, Object>>) resultMap.get("account_tags");
    if (accountTagMaps != null) {
      for (Map<String, Object> accountTagMap : accountTagMaps) {
        MoneroAccountTag tag = new MoneroAccountTag();
        tags.add(tag);
        tag.setTag((String) accountTagMap.get("tag"));
        tag.setLabel((String) accountTagMap.get("label"));
        List<BigInteger> accountIndicesBI = (List<BigInteger>) accountTagMap.get("accounts");
        List<Integer> accountIndices = new ArrayList<Integer>();
        for (BigInteger idx : accountIndicesBI) accountIndices.add(idx.intValue());
        tag.setAccountIndices(accountIndices);
      }
    }
    return tags;
  }

  @Override
  public void setAccountTagLabel(String tag, String label) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tag", tag);
    params.put("description", label);
    rpc.sendJsonRequest("set_account_tag_description", params);
  }

  @SuppressWarnings("unchecked")
  @Override
  public String createPaymentUri(MoneroSendRequest request) {
    assertNotNull("Must provide send request to create a payment URI", request);
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("address", request.getDestinations().get(0).getAddress());
    params.put("amount", request.getDestinations().get(0).getAmount() != null ? request.getDestinations().get(0).getAmount().toString() : null);
    params.put("payment_id", request.getPaymentId());
    params.put("recipient_name", request.getRecipientName());
    params.put("tx_description", request.getNote());
    Map<String, Object> resp = rpc.sendJsonRequest("make_uri", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("uri");
  }

  @SuppressWarnings("unchecked")
  @Override
  public MoneroSendRequest parsePaymentUri(String uri) {
    assertNotNull("Must provide URI to parse", uri);
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("uri", uri);
    Map<String, Object> resp = rpc.sendJsonRequest("parse_uri", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    Map<String, Object> rpcUri = (Map<String, Object>) result.get("uri");
    MoneroSendRequest request = new MoneroSendRequest((String) rpcUri.get("address"), (BigInteger) rpcUri.get("amount"));
    request.setPaymentId((String) rpcUri.get("payment_id"));
    request.setRecipientName((String) rpcUri.get("recipient_name"));
    request.setNote((String) rpcUri.get("tx_description"));
    if ("".equals(request.getDestinations().get(0).getAddress())) request.getDestinations().get(0).setAddress(null);
    if ("".equals(request.getPaymentId())) request.setPaymentId(null);
    if ("".equals(request.getRecipientName())) request.setRecipientName(null);
    if ("".equals(request.getNote())) request.setNote(null);
    return request;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public String getAttribute(String key) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key", key);
    Map<String, Object> resp = rpc.sendJsonRequest("get_attribute", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    String value = (String) result.get("value");
    return value.isEmpty() ? null : value;
  }

  @Override
  public void setAttribute(String key, String val) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("key", key);
    params.put("value", val);
    rpc.sendJsonRequest("set_attribute", params);
  }

  @Override
  public void startMining(Long numThreads, Boolean backgroundMining, Boolean ignoreBattery) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("threads_count", numThreads);
    params.put("backgroundMining", backgroundMining);
    params.put("ignoreBattery", ignoreBattery);
    rpc.sendJsonRequest("start_mining", params);
  }

  @Override
  public void stopMining() {
    rpc.sendJsonRequest("stop_mining");
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public boolean isMultisigImportNeeded() {
    Map<String, Object> resp = rpc.sendJsonRequest("get_balance");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return Boolean.TRUE.equals((Boolean) result.get("multisig_import_needed"));
  }

  @Override
  @SuppressWarnings("unchecked")
  public MoneroMultisigInfo getMultisigInfo() {
    Map<String, Object> resp = rpc.sendJsonRequest("is_multisig");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroMultisigInfo info = new MoneroMultisigInfo();
    info.setIsMultisig((boolean) result.get("multisig"));
    info.setIsReady((boolean) result.get("ready"));
    info.setThreshold(((BigInteger) result.get("threshold")).intValue());
    info.setNumParticipants(((BigInteger) result.get("total")).intValue());
    return info;
  }

  @Override
  @SuppressWarnings("unchecked")
  public String prepareMultisig() {
    Map<String, Object> resp = rpc.sendJsonRequest("prepare_multisig");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("multisig_info");
  }

  @Override
  @SuppressWarnings("unchecked")
  public MoneroMultisigInitResult makeMultisig(List<String> multisigHexes, int threshold, String password) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("multisig_info", multisigHexes);
    params.put("threshold", threshold);
    params.put("password", password);
    Map<String, Object> resp = rpc.sendJsonRequest("make_multisig", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroMultisigInitResult msResult = new MoneroMultisigInitResult();
    msResult.setAddress((String) result.get("address"));
    msResult.setMultisigHex((String) result.get("multisig_info"));
    if (msResult.getAddress().isEmpty()) msResult.setAddress(null);
    if (msResult.getMultisigHex().isEmpty()) msResult.setMultisigHex(null);
    return msResult;
  }

  @Override
  @SuppressWarnings("unchecked")
  public MoneroMultisigInitResult exchangeMultisigKeys(List<String> multisigHexes, String password) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("multisig_info", multisigHexes);
    params.put("password", password);
    Map<String, Object> resp = rpc.sendJsonRequest("exchange_multisig_keys", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroMultisigInitResult msResult = new MoneroMultisigInitResult();
    msResult.setAddress((String) result.get("address"));
    msResult.setMultisigHex((String) result.get("multisig_info"));
    if (msResult.getAddress().isEmpty()) msResult.setAddress(null);
    if (msResult.getMultisigHex().isEmpty()) msResult.setMultisigHex(null);
    return msResult;
  }

  @Override
  @SuppressWarnings("unchecked")
  public String getMultisigHex() {
    Map<String, Object> resp = rpc.sendJsonRequest("export_multisig_info");
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (String) result.get("info");
  }

  @Override
  @SuppressWarnings("unchecked")
  public int importMultisigHex(List<String> multisigHexes) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("info", multisigHexes);
    Map<String, Object> resp = rpc.sendJsonRequest("import_multisig_info", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return ((BigInteger) result.get("n_outputs")).intValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public MoneroMultisigSignResult signMultisigTxHex(String multisigTxHex) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tx_data_hex", multisigTxHex);
    Map<String, Object> resp = rpc.sendJsonRequest("sign_multisig", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    MoneroMultisigSignResult signResult = new MoneroMultisigSignResult();
    signResult.setSignedMultisigTxHex((String) result.get("tx_data_hex"));
    signResult.setTxIds((List<String>) result.get("tx_hash_list"));
    return signResult;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> submitMultisigTxHex(String signedMultisigTxHex) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("tx_data_hex", signedMultisigTxHex);
    Map<String, Object> resp = rpc.sendJsonRequest("submit_multisig", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    return (List<String>) result.get("tx_hash_list");
  }
  
  @Override
  public void save() {
    rpc.sendJsonRequest("store");
  }
  
  @Override
  public void close(boolean save) {
    addressCache.clear();
    path = null;
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("autosave_current", save);
    rpc.sendJsonRequest("close_wallet", params);
  }
  
  // ------------------------------ PRIVATE -----------------------------------
  
  private Map<Integer, List<Integer>> getAccountIndices(boolean getSubaddressIndices) {
    Map<Integer, List<Integer>> indices = new HashMap<Integer, List<Integer>>();
    for (MoneroAccount account : getAccounts()) {
      indices.put(account.getIndex(), getSubaddressIndices ? getSubaddressIndices(account.getIndex()) : null);
    }
    return indices;
  }
  
  @SuppressWarnings("unchecked")
  private List<Integer> getSubaddressIndices(int accountIdx) {
    List<Integer> subaddressIndices = new ArrayList<Integer>();
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", accountIdx);
    Map<String, Object> resp = rpc.sendJsonRequest("get_address", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    for (Map<String, Object> address : (List<Map<String, Object>>) result.get("addresses")) {
      subaddressIndices.add(((BigInteger) address.get("address_index")).intValue());
    }
    return subaddressIndices;
  }
  
  /**
   * Common method to get key images.
   * 
   * @param all specifies to get all xor only new images from last import
   * @return {MoneroKeyImage[]} are the key images
   */
  @SuppressWarnings("unchecked")
  private List<MoneroKeyImage> rpcExportKeyImages(boolean all) {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("all", all);
    Map<String, Object> resp = rpc.sendJsonRequest("export_key_images", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    List<MoneroKeyImage> images = new ArrayList<MoneroKeyImage>();
    if (!result.containsKey("signed_key_images")) return images;
    for (Map<String, Object> rpcImage : (List<Map<String, Object>>) result.get("signed_key_images")) {
      images.add(new MoneroKeyImage((String) rpcImage.get("key_image"), (String) rpcImage.get("signature")));
    }
    return images;
  }
  
  @SuppressWarnings("unchecked")
  private BigInteger[] getBalances(Integer accountIdx, Integer subaddressIdx) {
    if (accountIdx == null) {
      assertNull("Must provide account index with subaddress index", subaddressIdx);
      BigInteger balance = BigInteger.valueOf(0);
      BigInteger unlockedBalance = BigInteger.valueOf(0);
      for (MoneroAccount account : getAccounts()) {
        balance = balance.add(account.getBalance());
        unlockedBalance = unlockedBalance.add(account.getUnlockedBalance());
      }
      return new BigInteger[] { balance, unlockedBalance };
    } else {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("account_index", accountIdx);
      params.put("address_indices", subaddressIdx == null ? null : new Integer[] { subaddressIdx });
      Map<String, Object> resp = rpc.sendJsonRequest("get_balance", params);
      Map<String, Object> result = (Map<String, Object>) resp.get("result");
      if (subaddressIdx == null) return new BigInteger[] { (BigInteger) result.get("balance"), (BigInteger) result.get("unlocked_balance") };
      else {
        List<Map<String, Object>> rpcBalancesPerSubaddress = (List<Map<String, Object>>) result.get("per_subaddress");
        return new BigInteger[] { (BigInteger) rpcBalancesPerSubaddress.get(0).get("balance"), (BigInteger) rpcBalancesPerSubaddress.get(0).get("unlocked_balance") };
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  private MoneroTxSet rpcSweepAccount(MoneroSendRequest request) {
    
    // validate request
    if (request == null) throw new MoneroException("Sweep request cannot be null");
    if (request.getAccountIndex() == null) throw new MoneroException("Must specify an account index to sweep from");
    if (request.getDestinations() == null || request.getDestinations().size() != 1) throw new MoneroException("Must specify exactly one destination to sweep to");
    if (request.getDestinations().get(0).getAddress() == null) throw new MoneroException("Must specify destination address to sweep to");
    if (request.getDestinations().get(0).getAmount() != null) throw new MoneroException("Cannot specify amount in sweep request");
    if (request.getKeyImage() != null) throw new MoneroException("Key image defined; use sweepOutput() to sweep an output by its key image");
    if (request.getSubaddressIndices() != null && request.getSubaddressIndices().isEmpty()) request.setSubaddressIndices((List<Integer>) null);
    if (Boolean.TRUE.equals(request.getSweepEachSubaddress())) throw new MoneroException("Cannot sweep each subaddress with RPC `sweep_all`");
    
    // sweep from all subaddresses if not otherwise defined
    if (request.getSubaddressIndices() == null) {
      request.setSubaddressIndices(new ArrayList<Integer>());
      for (MoneroSubaddress subaddress : getSubaddresses(request.getAccountIndex())) {
        request.getSubaddressIndices().add(subaddress.getIndex());
      }
    }
    if (request.getSubaddressIndices().size() == 0) throw new MoneroException("No subaddresses to sweep from");
    
    // common request params
    boolean doNotRelay = request.getDoNotRelay() != null && request.getDoNotRelay();
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("account_index", request.getAccountIndex());
    params.put("subaddr_indices", request.getSubaddressIndices());
    params.put("address", request.getDestinations().get(0).getAddress());
    params.put("priority", request.getPriority() == null ? null : request.getPriority().ordinal());
    params.put("mixin", request.getMixin());
    params.put("ring_size", request.getRingSize());
    params.put("unlock_time", request.getUnlockTime());
    params.put("payment_id", request.getPaymentId());
    params.put("do_not_relay", doNotRelay);
    params.put("below_amount", request.getBelowAmount());
    params.put("get_tx_keys", true);
    params.put("get_tx_hex", true);
    params.put("get_tx_metadata", true);
    
    // invoke wallet rpc `sweep_all`
    Map<String, Object> resp = rpc.sendJsonRequest("sweep_all", params);
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    
    // initialize txs from response
    MoneroTxSet txSet = convertRpcSentTxsToTxSet(result, null);
    
    // initialize remaining known fields
    for (MoneroTxWallet tx : txSet.getTxs()) {
      tx.setIsConfirmed(false);
      tx.setNumConfirmations(0l);
      tx.setDoNotRelay(doNotRelay);
      tx.setInTxPool(!doNotRelay);
      tx.setIsRelayed(!doNotRelay);
      tx.setIsMinerTx(false);
      tx.setIsFailed(false);
      tx.setMixin(request.getMixin());
      MoneroOutgoingTransfer transfer = tx.getOutgoingTransfer();
      transfer.setAccountIndex(request.getAccountIndex());
      if (request.getSubaddressIndices().size() == 1) transfer.setSubaddressIndices(new ArrayList<Integer>(request.getSubaddressIndices()));
      MoneroDestination destination = new MoneroDestination(request.getDestinations().get(0).getAddress(), transfer.getAmount());
      transfer.setDestinations(Arrays.asList(destination));
      tx.setOutgoingTransfer(transfer);
      tx.setPaymentId(request.getPaymentId());
      if (tx.getUnlockTime() == null) tx.setUnlockTime(request.getUnlockTime() == null ? 0l : request.getUnlockTime());
      if (!tx.getDoNotRelay()) {
        if (tx.getLastRelayedTimestamp() == null) tx.setLastRelayedTimestamp(System.currentTimeMillis());  // TODO (monero-wallet-rpc): provide timestamp on response; unconfirmed timestamps vary
        if (tx.isDoubleSpendSeen() == null) tx.setIsDoubleSpendSeen(false);
      }
    }
    return txSet;
  }
  
  // ---------------------------- PRIVATE STATIC ------------------------------
  
  private static MoneroAccount convertRpcAccount(Map<String, Object> rpcAccount) {
    MoneroAccount account = new MoneroAccount();
    for (String key : rpcAccount.keySet()) {
      Object val = rpcAccount.get(key);
      if (key.equals("account_index")) account.setIndex(((BigInteger) val).intValue());
      else if (key.equals("balance")) account.setBalance((BigInteger) val);
      else if (key.equals("unlocked_balance")) account.setUnlockedBalance((BigInteger) val);
      else if (key.equals("base_address")) account.setPrimaryAddress((String) val);
      else if (key.equals("tag")) account.setTag((String) val);
      else if (key.equals("label")) { } // label belongs to first subaddress
      else LOGGER.warn("WARNING: ignoring unexpected account field: " + key + ": " + val);
    }
    if ("".equals(account.getTag())) account.setTag(null);
    return account;
  }
  
  private static MoneroSubaddress convertRpcSubaddress(Map<String, Object> rpcSubaddress) {
    MoneroSubaddress subaddress = new MoneroSubaddress();
    for (String key : rpcSubaddress.keySet()) {
      Object val = rpcSubaddress.get(key);
      if (key.equals("account_index")) subaddress.setAccountIndex(((BigInteger) val).intValue());
      else if (key.equals("address_index")) subaddress.setIndex(((BigInteger) val).intValue());
      else if (key.equals("address")) subaddress.setAddress((String) val);
      else if (key.equals("balance")) subaddress.setBalance((BigInteger) val);
      else if (key.equals("unlocked_balance")) subaddress.setUnlockedBalance((BigInteger) val);
      else if (key.equals("num_unspent_outputs")) subaddress.setNumUnspentOutputs(((BigInteger) val).longValue());
      else if (key.equals("label")) { if (!"".equals(val)) subaddress.setLabel((String) val); }
      else if (key.equals("used")) subaddress.setIsUsed((Boolean) val);
      else if (key.equals("blocks_to_unlock")) subaddress.setNumBlocksToUnlock(((BigInteger) val).longValue());
      else LOGGER.warn("WARNING: ignoring unexpected subaddress field: " + key + ": " + val);
    }
    return subaddress;
  }
  
  /**
   * Initializes a sent transaction.
   * 
   * @param request is the send configuration
   * @param tx is an existing transaction to initialize (optional)
   * @return tx is the initialized send tx
   */
  private static MoneroTxWallet initSentTxWallet(MoneroSendRequest request, MoneroTxWallet tx) {
    if (tx == null) tx = new MoneroTxWallet();
    tx.setIsConfirmed(false);
    tx.setNumConfirmations(0l);
    tx.setInTxPool(Boolean.TRUE.equals(request.getDoNotRelay()) ? false : true);
    tx.setDoNotRelay(Boolean.TRUE.equals(request.getDoNotRelay()) ? true : false);
    tx.setIsRelayed(!Boolean.TRUE.equals(tx.getDoNotRelay()));
    tx.setIsMinerTx(false);
    tx.setIsFailed(false);
    tx.setMixin(request.getMixin());
    MoneroOutgoingTransfer transfer = new MoneroOutgoingTransfer().setTx(tx);
    if (request.getSubaddressIndices() != null && request.getSubaddressIndices().size() == 1) transfer.setSubaddressIndices(new ArrayList<Integer>(request.getSubaddressIndices())); // we know src subaddress indices iff request specifies 1
    List<MoneroDestination> destCopies = new ArrayList<MoneroDestination>();
    for (MoneroDestination dest : request.getDestinations()) destCopies.add(dest.copy());
    transfer.setDestinations(destCopies);
    tx.setOutgoingTransfer(transfer);
    tx.setPaymentId(request.getPaymentId());
    if (tx.getUnlockTime() == null) tx.setUnlockTime(request.getUnlockTime() == null ? 0l : request.getUnlockTime());
    if (!Boolean.TRUE.equals(tx.getDoNotRelay())) {
      if (tx.getLastRelayedTimestamp() == null) tx.setLastRelayedTimestamp(System.currentTimeMillis());  // TODO (monero-wallet-rpc): provide timestamp on response; unconfirmed timestamps vary
      if (tx.isDoubleSpendSeen() == null) tx.setIsDoubleSpendSeen(false);
    }
    return tx;
  }
  
  /**
   * Initializes a tx set from a RPC map excluding txs.
   * 
   * @param rpcMap is the map to initialize the tx set from
   * @return MoneroTxSet is the initialized tx set
   */
  private static MoneroTxSet convertRpcMapToTxSet(Map<String, Object> rpcMap) {
    MoneroTxSet txSet = new MoneroTxSet();
    txSet.setMultisigTxHex((String) rpcMap.get("multisig_txset"));
    txSet.setUnsignedTxHex((String) rpcMap.get("unsigned_txset"));
    txSet.setSignedTxHex((String) rpcMap.get("signed_txset"));
    if (txSet.getMultisigTxHex() != null && txSet.getMultisigTxHex().isEmpty()) txSet.setMultisigTxHex(null);
    if (txSet.getUnsignedTxHex() != null && txSet.getUnsignedTxHex().isEmpty()) txSet.setUnsignedTxHex(null);
    if (txSet.getSignedTxHex() != null && txSet.getSignedTxHex().isEmpty()) txSet.setSignedTxHex(null);
    return txSet;
  }
  
  /**
   * Initializes a MoneroTxSet from from a list of rpc txs.
   * 
   * @param rpcTxs are sent rpc txs to initialize the set from
   * @param txs are existing txs to further initialize (optional)
   * @return the converted tx set
   */
  @SuppressWarnings("unchecked")
  private static MoneroTxSet convertRpcSentTxsToTxSet(Map<String, Object> rpcTxs, List<MoneroTxWallet> txs) {
    
    // build shared tx set
    MoneroTxSet txSet = convertRpcMapToTxSet(rpcTxs);
    
    // done if rpc contains no txs
    if (!rpcTxs.containsKey("fee_list")) {
      assertNull(txs);
      return txSet;
    }
    
    // get lists
    List<String> ids = (List<String>) rpcTxs.get("tx_hash_list");
    List<String> keys = (List<String>) rpcTxs.get("tx_key_list");
    List<String> blobs = (List<String>) rpcTxs.get("tx_blob_list");
    List<String> metadatas = (List<String>) rpcTxs.get("tx_metadata_list");
    List<BigInteger> fees = (List<BigInteger>) rpcTxs.get("fee_list");
    List<BigInteger> amounts = (List<BigInteger>) rpcTxs.get("amount_list");
    
    // ensure all lists are the same size
    Set<Integer> sizes = new HashSet<Integer>();
    if (amounts != null) sizes.add(amounts.size());
    if (ids != null) sizes.add(ids.size());
    if (keys != null) sizes.add(keys.size());
    if (blobs != null) sizes.add(blobs.size());
    if (metadatas != null) sizes.add(metadatas.size());
    if (fees != null) sizes.add(fees.size());
    if (amounts != null) sizes.add(amounts.size());
    assertEquals("RPC lists are different sizes", 1, sizes.size());
    
    // pre-initialize txs if none given
    if (txs != null) txSet.setTxs(txs);
    else {
      txs = new ArrayList<MoneroTxWallet>();
      for (int i = 0; i < fees.size(); i++) txs.add(new MoneroTxWallet());
      txSet.setTxs(txs);
    }

    // build transactions
    for (int i = 0; i < fees.size(); i++) {
      MoneroTxWallet tx = txs.get(i);
      if (ids != null) tx.setId(ids.get(i));
      if (keys != null) tx.setKey(keys.get(i));
      if (blobs != null) tx.setFullHex(blobs.get(i));
      if (metadatas != null) tx.setMetadata(metadatas.get(i));
      tx.setFee((BigInteger) fees.get(i));
      if (tx.getOutgoingTransfer() != null) tx.getOutgoingTransfer().setAmount((BigInteger) amounts.get(i));
      else tx.setOutgoingTransfer(new MoneroOutgoingTransfer().setTx(tx).setAmount((BigInteger) amounts.get(i)));
      tx.setTxSet(txSet); // link tx to parent set
    }
    
    return txSet;
  }
  
  /**
   * Converts a rpc tx with a transfer to a tx set with a tx and transfer.
   * 
   * @param rpcTx is the rpc tx to build from
   * @param tx is an existing tx to continue initializing (optional)
   * @param isOutgoing specifies if the tx is outgoing if true, incoming if false, or decodes from type if undefined
   * @returns the initialized tx set with a tx
   */
  private static MoneroTxSet convertRpcTxToTxSet(Map<String, Object> rpcTx, MoneroTxWallet tx, Boolean isOutgoing) {
    MoneroTxSet txSet = convertRpcMapToTxSet(rpcTx);
    txSet.setTxs(Arrays.asList(convertRpcTxWithTransfer(rpcTx, tx, isOutgoing).setTxSet(txSet)));
    return txSet;
  }
  
  /**
   * Builds a MoneroTxWallet from a RPC tx.
   * 
   * @param rpcTx is the rpc tx to build from
   * @param tx is an existing tx to continue initializing (optional)
   * @param isOutgoing specifies if the tx is outgoing if true, incoming if false, or decodes from type if undefined
   * @returns the initialized tx with a transfer
   */
  @SuppressWarnings("unchecked")
  private static MoneroTxWallet convertRpcTxWithTransfer(Map<String, Object> rpcTx, MoneroTxWallet tx, Boolean isOutgoing) {  // TODO: change everything to safe set
    
    // initialize tx to return
    if (tx == null) tx = new MoneroTxWallet();
    
    // initialize tx state from rpc type
    if (rpcTx.containsKey("type")) isOutgoing = decodeRpcType((String) rpcTx.get("type"), tx);
    else {
      assertNotNull("Must indicate if tx is outgoing (true) xor incoming (false) since unknown", isOutgoing);
      assertNotNull(tx.isConfirmed());
      assertNotNull(tx.inTxPool());
      assertNotNull(tx.isMinerTx());
      assertNotNull(tx.isFailed());
      assertNotNull(tx.getDoNotRelay());
    }
    
    // TODO: safe set
    // initialize remaining fields  TODO: seems this should be part of common function with DaemonRpc._convertRpcTx
    MoneroBlockHeader header = null;
    MoneroTransfer transfer = null;
    for (String key : rpcTx.keySet()) {
      Object val = rpcTx.get(key);
      if (key.equals("txid")) tx.setId((String) val);
      else if (key.equals("tx_hash")) tx.setId((String) val);
      else if (key.equals("fee")) tx.setFee((BigInteger) val);
      else if (key.equals("note")) { if (!"".equals(val)) tx.setNote((String) val); }
      else if (key.equals("tx_key")) tx.setKey((String) val);
      else if (key.equals("type")) { } // type already handled
      else if (key.equals("tx_size")) tx.setSize(((BigInteger) val).longValue());
      else if (key.equals("unlock_time")) tx.setUnlockTime(((BigInteger) val).longValue());
      else if (key.equals("tx_blob")) tx.setFullHex((String) val);
      else if (key.equals("tx_metadata")) tx.setMetadata((String) val);
      else if (key.equals("double_spend_seen")) tx.setIsDoubleSpendSeen((Boolean) val);
      else if (key.equals("block_height") || key.equals("height")) {
        if (tx.isConfirmed()) {
          if (header == null) header = new MoneroBlockHeader();
          header.setHeight(((BigInteger) val).longValue());
        }
      }
      else if (key.equals("timestamp")) {
        if (tx.isConfirmed()) {
          if (header == null) header = new MoneroBlockHeader();
          header.setTimestamp(((BigInteger) val).longValue());
        } else {
          // timestamp of unconfirmed tx is current request time
        }
      }
      else if (key.equals("confirmations")) {
        if (!tx.isConfirmed()) tx.setNumConfirmations(0l);
        else tx.setNumConfirmations(((BigInteger) val).longValue());
      }
      else if (key.equals("suggested_confirmations_threshold")) {
        if (transfer == null) transfer = (isOutgoing ? new MoneroOutgoingTransfer() : new MoneroIncomingTransfer()).setTx(tx);
        transfer.setNumSuggestedConfirmations(((BigInteger) val).longValue());
      }
      else if (key.equals("amount")) {
        if (transfer == null) transfer = (isOutgoing ? new MoneroOutgoingTransfer() : new MoneroIncomingTransfer()).setTx(tx);
        transfer.setAmount((BigInteger) val);
      }
      else if (key.equals("address")) {
        if (!isOutgoing) {
          if (transfer == null) transfer = new MoneroIncomingTransfer().setTx(tx);
          ((MoneroIncomingTransfer) transfer).setAddress((String) val);
        }
      }
      else if (key.equals("payment_id")) {
        if (!MoneroTxWallet.DEFAULT_PAYMENT_ID.equals(val)) tx.setPaymentId((String) val);  // default is undefined
      }
      else if (key.equals("subaddr_index")) assertTrue(rpcTx.containsKey("subaddr_indices")); // handled by subaddr_indices
      else if (key.equals("subaddr_indices")) {
        if (transfer == null) transfer = (isOutgoing ? new MoneroOutgoingTransfer() : new MoneroIncomingTransfer()).setTx(tx);
        List<Map<String, BigInteger>> rpcIndices = (List<Map<String, BigInteger>>) val;
        transfer.setAccountIndex(rpcIndices.get(0).get("major").intValue());
        if (isOutgoing) {
          List<Integer> subaddressIndices = new ArrayList<Integer>();
          for (Map<String, BigInteger> rpcIndex : rpcIndices) subaddressIndices.add(rpcIndex.get("minor").intValue());
          ((MoneroOutgoingTransfer) transfer).setSubaddressIndices(subaddressIndices);
        } else {
          assertEquals(1, rpcIndices.size());
          ((MoneroIncomingTransfer) transfer).setSubaddressIndex(rpcIndices.get(0).get("minor").intValue());
        }
      }
      else if (key.equals("destinations")) {
        assertTrue(isOutgoing);
        List<MoneroDestination> destinations = new ArrayList<MoneroDestination>();
        for (Map<String, Object> rpcDestination : (List<Map<String, Object>>) val) {
          MoneroDestination destination = new MoneroDestination();
          destinations.add(destination);
          for (String destinationKey : rpcDestination.keySet()) {
            if (destinationKey.equals("address")) destination.setAddress((String) rpcDestination.get(destinationKey));
            else if (destinationKey.equals("amount")) destination.setAmount((BigInteger) rpcDestination.get(destinationKey));
            else throw new MoneroException("Unrecognized transaction destination field: " + destinationKey);
          }
        }
        if (transfer == null) transfer = new MoneroOutgoingTransfer().setTx(tx);
        ((MoneroOutgoingTransfer) transfer).setDestinations(destinations);
      }
      else if (key.equals("multisig_txset") && val != null) {}  // handled elsewhere; this method only builds a tx wallet
      else if (key.equals("unsigned_txset") && val != null) {}  // handled elsewhere; this method only builds a tx wallet
      else LOGGER.warn("WARNING: ignoring unexpected transaction field: " + key + ": " + val);
    }
    
    // link block and tx
    if (header != null) tx.setBlock(new MoneroBlock(header).setTxs(tx));
    
    // initialize final fields
    if (transfer != null) {
      if (isOutgoing) {
        if (tx.getOutgoingTransfer() != null) tx.getOutgoingTransfer().merge(transfer);
        else tx.setOutgoingTransfer((MoneroOutgoingTransfer) transfer);
      } else {
        tx.setIncomingTransfers(new ArrayList<MoneroIncomingTransfer>(Arrays.asList((MoneroIncomingTransfer) transfer)));
      }
    }
    
    // return initialized transaction
    return tx;
  }
  
  @SuppressWarnings("unchecked")
  private static MoneroTxWallet convertRpcTxWithVout(Map<String, Object> rpcVout) {
    
    // initialize tx
    MoneroTxWallet tx = new MoneroTxWallet();
    tx.setIsConfirmed(true);
    tx.setIsRelayed(true);
    tx.setIsFailed(false);
    
    // initialize vout
    MoneroOutputWallet vout = new MoneroOutputWallet().setTx(tx);
    for (String key : rpcVout.keySet()) {
      Object val = rpcVout.get(key);
      if (key.equals("amount")) vout.setAmount((BigInteger) val);
      else if (key.equals("spent")) vout.setIsSpent((Boolean) val);
      else if (key.equals("key_image")) vout.setKeyImage(new MoneroKeyImage((String) val));
      else if (key.equals("global_index")) vout.setIndex(((BigInteger) val).intValue());
      else if (key.equals("tx_hash")) tx.setId((String) val);
      else if (key.equals("unlocked")) vout.setIsUnlocked((Boolean) val);
      else if (key.equals("frozen")) vout.setIsFrozen((Boolean) val);
      else if (key.equals("subaddr_index")) {
        Map<String, BigInteger> rpcIndices = (Map<String, BigInteger>) val;
        vout.setAccountIndex(rpcIndices.get("major").intValue());
        vout.setSubaddressIndex(rpcIndices.get("minor").intValue());
      }
      else if (key.equals("block_height")) {
        long height = ((BigInteger) val).longValue();
        tx.setBlock(new MoneroBlock().setHeight(height).setTxs(tx));
      }
      else LOGGER.warn("WARNING: ignoring unexpected transaction field with vout: " + key + ": " + val);
    }
    
    // initialize tx with vout
    List<MoneroOutput> vouts = new ArrayList<MoneroOutput>();
    vouts.add((MoneroOutput) vout); // have to cast to extended type because Java paramaterized types do not recognize inheritance
    tx.setVouts(vouts);
    return tx;
  }
  
  /**
   * Decodes a "type" from monero-wallet-rpc to initialize type and state
   * fields in the given transaction.
   * 
   * TODO: these should be safe set
   * 
   * @param rpcType is the type to decode
   * @param tx is the transaction to decode known fields to
   * @return {boolean} true if the rpc type indicates outgoing xor incoming
   */
  private static boolean decodeRpcType(String rpcType, MoneroTxWallet tx) {
    boolean isOutgoing;
    if (rpcType.equals("in")) {
      isOutgoing = false;
      tx.setIsConfirmed(true);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsMinerTx(false);
    } else if (rpcType.equals("out")) {
      isOutgoing = true;
      tx.setIsConfirmed(true);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsMinerTx(false);
    } else if (rpcType.equals("pool")) {
      isOutgoing = false;
      tx.setIsConfirmed(false);
      tx.setInTxPool(true);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsMinerTx(false);  // TODO: but could it be?
    } else if (rpcType.equals("pending")) {
      isOutgoing = true;
      tx.setIsConfirmed(false);
      tx.setInTxPool(true);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsMinerTx(false);
    } else if (rpcType.equals("block")) {
      isOutgoing = false;
      tx.setIsConfirmed(true);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(false);
      tx.setIsMinerTx(true);
    } else if (rpcType.equals("failed")) {
      isOutgoing = true;
      tx.setIsConfirmed(false);
      tx.setInTxPool(false);
      tx.setIsRelayed(true);
      tx.setDoNotRelay(false);
      tx.setIsFailed(true);
      tx.setIsMinerTx(false);
    } else {
      throw new MoneroException("Unrecognized transfer type: " + rpcType);
    }
    return isOutgoing;
  }
  
  /**
   * Merges a transaction into a unique set of transactions.
   *
   * TODO monero-core: skipIfAbsent only necessary because incoming payments not returned
   * when sent from/to same account #4500
   *
   * @param tx is the transaction to merge into the existing txs
   * @param txMap maps tx ids to txs
   * @param blockMap maps block heights to blocks
   * @param skipIfAbsent specifies if the tx should not be added if it doesn't already exist
   */
  private static void mergeTx(MoneroTxWallet tx, Map<String, MoneroTxWallet> txMap, Map<Long, MoneroBlock> blockMap, boolean skipIfAbsent) {
    assertNotNull(tx.getId());

    // if tx doesn't exist, add it (unless skipped)
    MoneroTxWallet aTx = txMap.get(tx.getId());
    if (aTx == null) {
      if (!skipIfAbsent) {
        txMap.put(tx.getId(), tx);
      } else {
        LOGGER.warn("WARNING: tx does not already exist");
      }
    }

    // otherwise merge with existing tx
    else {
      if (aTx.isFailed() != null & tx.isFailed() != null && !aTx.isFailed().equals(tx.isFailed())) {
        System.out.println("ERROR: Merging these transactions will throw an error because their isFailed state is different");
        System.out.println(aTx);
        System.out.println(tx);
      }
      aTx.merge(tx);
    }

    // if confirmed, merge tx's block
    if (tx.getHeight() != null) {
      MoneroBlock aBlock = blockMap.get(tx.getHeight());
      if (aBlock == null) {
        blockMap.put(tx.getHeight(), tx.getBlock());
      } else {
        aBlock.merge(tx.getBlock());
      }
    }
  }
  
  /**
   * Compares two transactions by their height.
   */
  private static class TxHeightComparator implements Comparator<MoneroTx> {
    @Override
    public int compare(MoneroTx tx1, MoneroTx tx2) {
      if (tx1.getHeight() == null && tx2.getHeight() == null) return 0; // both unconfirmed
      else if (tx1.getHeight() == null) return 1;   // tx1 is unconfirmed
      else if (tx2.getHeight() == null) return -1;  // tx2 is unconfirmed
      int diff = tx1.getHeight().compareTo(tx2.getHeight());
      if (diff != 0) return diff;
      return tx1.getBlock().getTxs().indexOf(tx1) - tx2.getBlock().getTxs().indexOf(tx2); // txs are in the same block so retain their original order
    }
  }
  
  /**
   * Compares two transfers by ascending account and subaddress indices.
   */
  public static class IncomingTransferComparator implements Comparator<MoneroIncomingTransfer> {
    @Override
    public int compare(MoneroIncomingTransfer t1, MoneroIncomingTransfer t2) {
      
      // compare by height
      int heightComparison = TX_HEIGHT_COMPARATOR.compare(t1.getTx(), t2.getTx());
      if (heightComparison != 0) return heightComparison;
      
      // compare by account and subaddress index
      if (t1.getAccountIndex() < t2.getAccountIndex()) return -1;
      else if (t1.getAccountIndex() == t2.getAccountIndex()) return t1.getSubaddressIndex().compareTo(t2.getSubaddressIndex());
      return 1;
    }
  }
  
  /**
   * Compares two vouts by ascending account and subaddress indices.
   */
  public static class VoutComparator implements Comparator<MoneroOutput> {
    
    @Override
    public int compare(MoneroOutput o1, MoneroOutput o2) {
      MoneroOutputWallet ow1 = (MoneroOutputWallet) o1;
      MoneroOutputWallet ow2 = (MoneroOutputWallet) o2;
      
      // compare by height
      int heightComparison = TX_HEIGHT_COMPARATOR.compare(ow1.getTx(), ow2.getTx());
      if (heightComparison != 0) return heightComparison;
      
      // compare by account index, subaddress index, and output
      if (ow1.getAccountIndex() < ow2.getAccountIndex()) return -1;
      else if (ow1.getAccountIndex() == ow2.getAccountIndex()) {
        int compare = ow1.getSubaddressIndex().compareTo(ow2.getSubaddressIndex());
        if (compare != 0) return compare;
        return ow1.getIndex().compareTo(ow2.getIndex());
      }
      return 1;
    }
  }
}
