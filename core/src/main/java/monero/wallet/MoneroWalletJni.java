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

import static org.junit.Assert.assertNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;

import common.utils.GenUtils;
import common.utils.JsonUtils;
import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroKeyImage;
import monero.daemon.model.MoneroNetworkType;
import monero.daemon.model.MoneroTx;
import monero.rpc.MoneroRpcConnection;
import monero.utils.MoneroException;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroAccountTag;
import monero.wallet.model.MoneroAddressBookEntry;
import monero.wallet.model.MoneroCheckReserve;
import monero.wallet.model.MoneroCheckTx;
import monero.wallet.model.MoneroIncomingTransfer;
import monero.wallet.model.MoneroIntegratedAddress;
import monero.wallet.model.MoneroKeyImageImportResult;
import monero.wallet.model.MoneroMultisigInfo;
import monero.wallet.model.MoneroMultisigInitResult;
import monero.wallet.model.MoneroMultisigSignResult;
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
import monero.wallet.model.MoneroWalletListener;
import monero.wallet.model.MoneroWalletListenerI;

/**
 * Implements a Monero wallet using JNI to bridge to Monero Core C++.
 */
public class MoneroWalletJni extends MoneroWalletDefault {
  
  // ----------------------------- PRIVATE SETUP ------------------------------

  // load Monero Core C++ as a dynamic library
  static {
    System.loadLibrary("monero-java");
  }
  
  // logger
  private static final Logger LOGGER = Logger.getLogger(MoneroWalletJni.class);
  
  // instance variables
  private long jniWalletHandle;                 // memory address of the wallet in c++; this variable is read directly by name in c++
  private long jniListenerHandle;               // memory address of the wallet listener in c++; this variable is read directly by name in c++
  private WalletJniListener jniListener;        // receives notifications from jni c++
  private Set<MoneroWalletListenerI> listeners; // externally subscribed wallet listeners
  private boolean isClosed;                     // whether or not wallet is closed
  
  /**
   * Private constructor with a handle to the memory address of the wallet in c++.
   * 
   * @param jniWalletHandle is the memory address of the wallet in c++
   */
  private MoneroWalletJni(long jniWalletHandle) {
    this.jniWalletHandle = jniWalletHandle;
    this.jniListener = new WalletJniListener();
    this.listeners = new LinkedHashSet<MoneroWalletListenerI>();
    this.isClosed = false;
  }
  
  // --------------------- WALLET MANAGEMENT UTILITIES ------------------------
  
  /**
   * Indicates if a wallet exists at the given path.
   * 
   * @param path is the path to check for a wallet
   * @return true if a wallet exists at the given path, false otherwise
   */
  public static boolean walletExists(String path) {
    return walletExistsJni(path);
  }
  
  /**
   * Open an existing wallet.
   * 
   * @param path is the path to the wallet file to open
   * @param password is the password of the wallet file to open
   * @param networkType is the wallet's network type
   * @param daemonConnection is connection configuration to a daemon (default = an unconnected wallet)
   * @return the opened wallet
   */
  public static MoneroWalletJni openWallet(String path, String password, MoneroNetworkType networkType) { return openWallet(path, password, networkType, (MoneroRpcConnection) null); }
  public static MoneroWalletJni openWallet(String path, String password, MoneroNetworkType networkType, String daemonUri) { return openWallet(path, password, networkType, daemonUri == null ? null : new MoneroRpcConnection(daemonUri)); }
  public static MoneroWalletJni openWallet(String path, String password, MoneroNetworkType networkType, MoneroRpcConnection daemonConnection) {
    if (!walletExistsJni(path)) throw new MoneroException("Wallet does not exist at path: " + path);
    if (networkType == null) throw new MoneroException("Must provide a network type");
    long jniWalletHandle = openWalletJni(path, password, networkType.ordinal());
    MoneroWalletJni wallet = new MoneroWalletJni(jniWalletHandle);
    if (daemonConnection != null) wallet.setDaemonConnection(daemonConnection);
    return wallet;
  }
  
  /**
   * Create a new wallet with a randomly generated seed.
   * 
   * @param path is the path to create the wallet
   * @param password is the password encrypt the wallet
   * @param networkType is the wallet's network type (default = MoneroNetworkType.MAINNET)
   * @param daemonConnection is connection configuration to a daemon (default = an unconnected wallet)
   * @param language is the wallet and mnemonic's language (default = "English")
   * @return the newly created wallet
   */
  public static MoneroWalletJni createWalletRandom(String path, String password) { return createWalletRandom(path, password, null, null, null); }
  public static MoneroWalletJni createWalletRandom(String path, String password, MoneroNetworkType networkType) { return createWalletRandom(path, password, networkType, null, null); }
  public static MoneroWalletJni createWalletRandom(String path, String password, MoneroNetworkType networkType, String daemonUri) { return createWalletRandom(path, password, networkType, daemonUri == null ? null : new MoneroRpcConnection(daemonUri), null); }
  public static MoneroWalletJni createWalletRandom(String path, String password, MoneroNetworkType networkType, MoneroRpcConnection daemonConnection) { return createWalletRandom(path, password, networkType, daemonConnection, null); }
  public static MoneroWalletJni createWalletRandom(String path, String password, MoneroNetworkType networkType, MoneroRpcConnection daemonConnection, String language) {
    if (networkType == null) networkType = MoneroNetworkType.MAINNET;
    if (language == null) language = DEFAULT_LANGUAGE;
    long jniWalletHandle;
    if (daemonConnection == null) jniWalletHandle = createWalletRandomJni(path, password, networkType.ordinal(), null, null, null, language);
    else jniWalletHandle = createWalletRandomJni(path, password, networkType.ordinal(), daemonConnection.getUri(), daemonConnection.getUsername(), daemonConnection.getPassword(), language);
    return new MoneroWalletJni(jniWalletHandle);
  }
  
  /**
   * Create a wallet from an existing mnemonic phrase.
   * 
   * @param path is the path to create the wallet
   * @param password is the password encrypt the wallet
   * @param networkType is the wallet's network type
   * @param mnemonic is the mnemonic of the wallet to construct
   * @param daemonConnection is connection configuration to a daemon (default = an unconnected wallet)
   * @param restoreHeight is the block height to restore from (default = 0)
   */
  public static MoneroWalletJni createWalletFromMnemonic(String path, String password, MoneroNetworkType networkType, String mnemonic) { return createWalletFromMnemonic(path, password, networkType, mnemonic, null, null); }
  public static MoneroWalletJni createWalletFromMnemonic(String path, String password, MoneroNetworkType networkType, String mnemonic, MoneroRpcConnection daemonConnection) { return createWalletFromMnemonic(path, password, networkType, mnemonic, daemonConnection, null); }
  public static MoneroWalletJni createWalletFromMnemonic(String path, String password, MoneroNetworkType networkType, String mnemonic, MoneroRpcConnection daemonConnection, Long restoreHeight) {
    if (networkType == null) throw new MoneroException("Must provide a network type");
    if (restoreHeight == null) restoreHeight = 0l;
    long jniWalletHandle = createWalletFromMnemonicJni(path, password, networkType.ordinal(), mnemonic, restoreHeight);
    MoneroWalletJni wallet = new MoneroWalletJni(jniWalletHandle);
    wallet.setDaemonConnection(daemonConnection);
    return wallet;
  }
  
  /**
   * Create a wallet from an address, view key, and spend key.
   * 
   * @param path is the path to create the wallet
   * @param password is the password encrypt the wallet
   * @param networkType is the wallet's network type
   * @param address is the address of the wallet to construct
   * @param viewKey is the view key of the wallet to construct
   * @param spendKey is the spend key of the wallet to construct
   * @param daemonConnection is connection configuration to a daemon (default = an unconnected wallet)
   * @param restoreHeight is the block height to restore (i.e. scan the chain) from (default = 0)
   * @param language is the wallet and mnemonic's language (default = "English")
   */
  public static MoneroWalletJni createWalletFromKeys(String path, String password, MoneroNetworkType networkType, String address, String viewKey, String spendKey) { return createWalletFromKeys(path, password, networkType, address, viewKey, spendKey, null, null, null); }
  public static MoneroWalletJni createWalletFromKeys(String path, String password, MoneroNetworkType networkType, String address, String viewKey, String spendKey, MoneroRpcConnection daemonConnection, Long restoreHeight) { return createWalletFromKeys(path, password, networkType, address, viewKey, spendKey, daemonConnection, restoreHeight, null); }
  public static MoneroWalletJni createWalletFromKeys(String path, String password, MoneroNetworkType networkType, String address, String viewKey, String spendKey, MoneroRpcConnection daemonConnection, Long restoreHeight, String language) {
    if (restoreHeight == null) restoreHeight = 0l;
    if (networkType == null) throw new MoneroException("Must provide a network type");
    if (language == null) language = DEFAULT_LANGUAGE;
    try {
      long jniWalletHandle = createWalletFromKeysJni(path, password, networkType.ordinal(), address, viewKey, spendKey, restoreHeight, language);
      MoneroWalletJni wallet = new MoneroWalletJni(jniWalletHandle);
      wallet.setDaemonConnection(daemonConnection);
      return wallet;
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  // ------------ WALLET METHODS SPECIFIC TO JNI IMPLEMENTATION ---------------
  
  /**
   * Set the wallet's daemon connection.
   * 
   * @param uri is the uri of the daemon for the wallet to use
   */
  public void setDaemonConnection(String uri) {
    setDaemonConnection(uri, null, null);
  }
  
  /**
   * Set the wallet's daemon connection.
   * 
   * @param uri is the daemon's URI
   * @param username is the username to authenticate with the daemon (optional)
   * @param password is the password to authenticate with the daemon (optional)
   */
  public void setDaemonConnection(String uri, String username, String password) {
    if (uri == null) setDaemonConnection((MoneroRpcConnection) null);
    else setDaemonConnection(new MoneroRpcConnection(uri, username, password));
  }
  
  /**
   * Set the wallet's daemon connection
   * 
   * @param daemonConnection manages daemon connection information
   */
  public void setDaemonConnection(MoneroRpcConnection daemonConnection) {
    assertNotClosed();
    if (daemonConnection == null) setDaemonConnectionJni("", "", "");
    else {
      try {
        setDaemonConnectionJni(daemonConnection.getUri() == null ? "" : daemonConnection.getUri().toString(), daemonConnection.getUsername(), daemonConnection.getPassword());
      } catch (Exception e) {
        throw new MoneroException(e.getMessage());
      }
    }
  }
  
  /**
   * Get the wallet's daemon connection.
   * 
   * @return the wallet's daemon connection
   */
  public MoneroRpcConnection getDaemonConnection() {
    assertNotClosed();
    try {
      String[] vals = getDaemonConnectionJni();
      return vals == null ? null : new MoneroRpcConnection(vals[0], vals[1], vals[2]);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  /**
   * Indicates if the wallet is connected a daemon.
   * 
   * @return true if the wallet is connected to a daemon, false otherwise
   */
  public boolean isConnected() {
    assertNotClosed();
    try {
      return isConnectedJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  /**
   * Get the maximum height of the peers the wallet's daemon is connected to.
   *
   * @return the maximum height of the peers the wallet's daemon is connected to
   */
  public long getDaemonMaxPeerHeight() {
    assertNotClosed();
    try {
      return getDaemonMaxPeerHeightJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  /**
   * Indicates if the wallet's daemon is synced with the network.
   * 
   * @return true if the daemon is synced with the network, false otherwise
   */
  public boolean isDaemonSynced() {
    assertNotClosed();
    try {
      return isDaemonSyncedJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  /**
   * Indicates if the wallet is synced with the daemon.
   * 
   * @return true if the wallet is synced with the daemon, false otherwise
   */
  public boolean isSynced() {
    assertNotClosed();
    try {
      return isSyncedJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  /**
   * Get the wallet's network type (mainnet, testnet, or stagenet).
   * 
   * @return the wallet's network type
   */
  public MoneroNetworkType getNetworkType() {
    assertNotClosed();
    return MoneroNetworkType.values()[getNetworkTypeJni()];
  }
  
  /**
   * Get the height of the first block that the wallet scans.
   * 
   * @return the height of the first block that the wallet scans
   */
  public long getRestoreHeight() {
    assertNotClosed();
    return getRestoreHeightJni();
  }
  
  /**
   * Set the height of the first block that the wallet scans.
   * 
   * @param restoreHeight is the height of the first block that the wallet scans
   */
  public void setRestoreHeight(long restoreHeight) {
    assertNotClosed();
    setRestoreHeightJni(restoreHeight);
  }
  
  /**
   * Get the language of the wallet's mnemonic phrase.
   * 
   * @return the language of the wallet's mnemonic phrase
   */
  public String getLanguage() {
    assertNotClosed();
    return getLanguageJni();
  }
  
  /**
   * Get the wallet's public view key.
   * 
   * @return the wallet's public view key
   */
  public String getPublicViewKey() {
    assertNotClosed();
    return getPublicViewKeyJni();
  }
  
  /**
   * Get the wallet's public spend key.
   * 
   * @return the wallet's public spend key
   */
  public String getPublicSpendKey() {
    assertNotClosed();
    return getPublicSpendKeyJni();
  }
  
  /**
   * Register a listener receive wallet notifications.
   * 
   * @param listener is the listener to receive wallet notifications
   */
  public void addListener(MoneroWalletListenerI listener) {
    assertNotClosed();
    listeners.add(listener);
    jniListener.setIsListening(true);
  }
  
  /**
   * Unregister a listener to receive wallet notifications.
   * 
   * @param listener is the listener to unregister
   */
  public void removeListener(MoneroWalletListenerI listener) {
    assertNotClosed();
    if (!listeners.contains(listener)) throw new MoneroException("Listener is not registered to wallet");
    listeners.remove(listener);
    if (listeners.isEmpty()) jniListener.setIsListening(false);
  }
  
  /**
   * Get the listeners registered with the wallet.
   */
  public Set<MoneroWalletListenerI> getListeners() {
    assertNotClosed();
    return listeners;
  }

  /**
   * Move the wallet from its current path to the given path.
   * 
   * @param path is the new wallet's path
   * @param password is the new wallet's password
   */
  public void moveTo(String path, String password) {
    assertNotClosed();
    moveToJni(path, password);
  }
  
  /**
   * Indicates if this wallet is closed or not.
   * 
   * @return true if the wallet is closed, false otherwise
   */
  public boolean isClosed() {
    return isClosed;
  }
  
  // -------------------------- COMMON WALLET METHODS -------------------------
  
  @Override
  public String getPath() {
    assertNotClosed();
    String path = getPathJni();
    return path.isEmpty() ? null : path;
  }
  
  @Override
  public String getSeed() {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public String getMnemonic() {
    assertNotClosed();
    return getMnemonicJni();
  }
  
  @Override
  public List<String> getLanguages() {
    assertNotClosed();
    return Arrays.asList(getLanguagesJni());
  }

  @Override
  public String getPrivateViewKey() {
    assertNotClosed();
    return getPrivateViewKeyJni();
  }
  
  @Override
  public String getPrivateSpendKey() {
    assertNotClosed();
    return getPrivateSpendKeyJni();
  }

  @Override
  public MoneroIntegratedAddress getIntegratedAddress(String paymentId) {
    assertNotClosed();
    try {
      String integratedAddressJson = getIntegratedAddressJni("", paymentId);
      return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, integratedAddressJson, MoneroIntegratedAddress.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroIntegratedAddress decodeIntegratedAddress(String integratedAddress) {
    assertNotClosed();
    try {
      String integratedAddressJson = decodeIntegratedAddressJni(integratedAddress);
      return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, integratedAddressJson, MoneroIntegratedAddress.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public long getHeight() {
    assertNotClosed();
    return getHeightJni();
  }

  @Override
  public long getDaemonHeight() {
    assertNotClosed();
    try {
      return getDaemonHeightJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroSyncResult sync(Long startHeight, MoneroSyncListener listener) {
    assertNotClosed();
    if (startHeight == null) startHeight = Math.max(getHeight(), getRestoreHeight());
    
    // wrap and register sync listener as wallet listener if given
    SyncListenerWrapper syncListenerWrapper = null;
    if (listener != null) {
      syncListenerWrapper = new SyncListenerWrapper(listener);
      addListener(syncListenerWrapper);
    }
    
    // sync wallet and handle exception
    try {
      Object[] results = syncJni(startHeight);
      return new MoneroSyncResult((long) results[0], (boolean) results[1]);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    } finally {
      if (syncListenerWrapper != null) removeListener(syncListenerWrapper); // unregister sync listener
    }
  }
  
  @Override
  public void startSyncing() {
    assertNotClosed();
    try {
      startSyncingJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  public void stopSyncing() {
    assertNotClosed();
    try {
      stopSyncingJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  @Override
  public void rescanSpent() {
    assertNotClosed();
    try {
      rescanSpentJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public void rescanBlockchain() {
    assertNotClosed();
    try {
      rescanBlockchainJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses, String tag) {
    assertNotClosed();
    String accountsJson = getAccountsJni(includeSubaddresses, tag);
    List<MoneroAccount> accounts = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, accountsJson, AccountsContainer.class).accounts;
    for (MoneroAccount account : accounts) sanitizeAccount(account);
    return accounts;
  }
  
  @Override
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses) {
    assertNotClosed();
    String accountJson = getAccountJni(accountIdx, includeSubaddresses);
    MoneroAccount account = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, accountJson, MoneroAccount.class);
    sanitizeAccount(account);
    return account;
  }

  @Override
  public MoneroAccount createAccount(String label) {
    assertNotClosed();
    String accountJson = createAccountJni(label);
    MoneroAccount account = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, accountJson, MoneroAccount.class);
    sanitizeAccount(account);
    return account;
  }

  @Override
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices) {
    assertNotClosed();
    String subaddresses_json = getSubaddressesJni(accountIdx, GenUtils.listToIntArray(subaddressIndices));
    List<MoneroSubaddress> subaddresses = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, subaddresses_json, SubaddressesContainer.class).subaddresses;
    for (MoneroSubaddress subaddress : subaddresses) sanitizeSubaddress(subaddress);
    return subaddresses;
  }

  @Override
  public MoneroSubaddress createSubaddress(int accountIdx, String label) {
    assertNotClosed();
    String subaddressJson = createSubaddressJni(accountIdx, label);
    MoneroSubaddress subaddress = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, subaddressJson, MoneroSubaddress.class);
    sanitizeSubaddress(subaddress);
    return subaddress;
  }

  @Override
  public String getAddress(int accountIdx, int subaddressIdx) {
    assertNotClosed();
    return getAddressJni(accountIdx, subaddressIdx);
  }

  @Override
  public MoneroSubaddress getAddressIndex(String address) {
    assertNotClosed();
    try {
      String subaddressJson = getAddressIndexJni(address);
      MoneroSubaddress subaddress = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, subaddressJson, MoneroSubaddress.class);
      return sanitizeSubaddress(subaddress);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public BigInteger getBalance() {
    assertNotClosed();
    try {
      return new BigInteger(getBalanceWalletJni());
    } catch (MoneroException e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public BigInteger getBalance(int accountIdx) {
    assertNotClosed();
    try {
      return new BigInteger(getBalanceAccountJni(accountIdx));
    } catch (MoneroException e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public BigInteger getBalance(int accountIdx, int subaddressIdx) {
    assertNotClosed();
    try {
      return new BigInteger(getBalanceSubaddressJni(accountIdx, subaddressIdx));
    } catch (MoneroException e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public BigInteger getUnlockedBalance() {
    assertNotClosed();
    try {
      return new BigInteger(getUnlockedBalanceWalletJni());
    } catch (MoneroException e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public BigInteger getUnlockedBalance(int accountIdx) {
    assertNotClosed();
    try {
      return new BigInteger(getUnlockedBalanceAccountJni(accountIdx));
    } catch (MoneroException e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public BigInteger getUnlockedBalance(int accountIdx, int subaddressIdx) {
    assertNotClosed();
    try {
      return new BigInteger(getUnlockedBalanceSubaddressJni(accountIdx, subaddressIdx));
    } catch (MoneroException e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public List<MoneroTxWallet> getTxs(MoneroTxQuery query) {
    assertNotClosed();
    
    // copy and normalize tx query up to block
    query = query == null ? new MoneroTxQuery() : query.copy();
    if (query.getBlock() == null) query.setBlock(new MoneroBlock().setTxs(query));
    
    // serialize query from block and fetch txs from jni
    String blocksJson;
    try {
      blocksJson = getTxsJni(JsonUtils.serialize(query.getBlock()));
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
    
    // deserialize blocks
    List<MoneroBlock> blocks = deserializeBlocks(blocksJson);
    
    // collect txs
    List<MoneroTxWallet> txs = new ArrayList<MoneroTxWallet>();
    for (MoneroBlock block : blocks) {
      sanitizeBlock(block);
      for (MoneroTx tx : block.getTxs()) {
        if (block.getHeight() == null) tx.setBlock(null); // dereference placeholder block for unconfirmed txs
        txs.add((MoneroTxWallet) tx);
      }
    }
    
    // re-sort txs which is lost over jni serialization
    if (query.getTxIds() != null) {
      Map<String, MoneroTxWallet> txMap = new HashMap<String, MoneroTxWallet>();
      for (MoneroTxWallet tx : txs) txMap.put(tx.getId(), tx);
      List<MoneroTxWallet> txsSorted = new ArrayList<MoneroTxWallet>();
      for (String txId : query.getTxIds()) txsSorted.add(txMap.get(txId));
      txs = txsSorted;
    }
    LOGGER.debug("getTxs() returning " + txs.size() + " transactions");
    return txs;
  }

  @Override
  public List<MoneroTransfer> getTransfers(MoneroTransferQuery query) {
    assertNotClosed();
    
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
    query.getTxQuery().setTransferQuery(query);
    if (query.getTxQuery().getBlock() == null) query.getTxQuery().setBlock(new MoneroBlock().setTxs(query.getTxQuery()));
    
    // serialize query from block and fetch transfers from jni
    String blocksJson;
    try {
      blocksJson = getTransfersJni(JsonUtils.serialize(query.getTxQuery().getBlock()));
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
    
    // deserialize blocks
    List<MoneroBlock> blocks = deserializeBlocks(blocksJson);
    
    // collect transfers
    List<MoneroTransfer> transfers = new ArrayList<MoneroTransfer>();
    for (MoneroBlock block : blocks) {
      sanitizeBlock(block);
      for (MoneroTx tx : block.getTxs()) {
        if (block.getHeight() == null) tx.setBlock(null); // dereference placeholder block for unconfirmed txs
        MoneroTxWallet txWallet = (MoneroTxWallet) tx;
        if (txWallet.getOutgoingTransfer() != null) transfers.add(txWallet.getOutgoingTransfer());
        if (txWallet.getIncomingTransfers() != null) {
          for (MoneroIncomingTransfer transfer : txWallet.getIncomingTransfers()) transfers.add(transfer);
        }
      }
    }
    return transfers;
  }

  @Override
  public List<MoneroOutputWallet> getOutputs(MoneroOutputQuery query) {
    assertNotClosed();
    
    // copy and normalize query up to block
    if (query == null) query = new MoneroOutputQuery();
    else {
      if (query.getTxQuery() == null) query = query.copy();
      else {
        MoneroTxQuery txQuery = query.getTxQuery().copy();
        if (query.getTxQuery().getOutputQuery() == query) query = txQuery.getOutputQuery();
        else {
          assertNull("Output query's tx query must be circular reference or null", query.getTxQuery().getOutputQuery());
          query = query.copy();
          query.setTxQuery(txQuery);
        }
      }
    }
    if (query.getTxQuery() == null) query.setTxQuery(new MoneroTxQuery());
    query.getTxQuery().setOutputQuery(query);
    if (query.getTxQuery().getBlock() == null) query.getTxQuery().setBlock(new MoneroBlock().setTxs(query.getTxQuery()));
    
    // serialize query from block and fetch outputs from jni
    String blocksJson = getOutputsJni(JsonUtils.serialize(query.getTxQuery().getBlock()));
    
    // deserialize blocks
    List<MoneroBlock> blocks = deserializeBlocks(blocksJson);
    
    // collect outputs
    List<MoneroOutputWallet> outputs = new ArrayList<MoneroOutputWallet>();
    for (MoneroBlock block : blocks) {
      sanitizeBlock(block);
      for (MoneroTx tx : block.getTxs()) {
        MoneroTxWallet txWallet = (MoneroTxWallet) tx;
        outputs.addAll(txWallet.getVoutsWallet());
      }
    }
    return outputs;
  }
  
  @Override
  public String getOutputsHex() {
    assertNotClosed();
    String outputsHex = getOutputsHexJni();
    return outputsHex.isEmpty() ? null : outputsHex;
  }

  @Override
  public int importOutputsHex(String outputsHex) {
    assertNotClosed();
    return importOutputsHexJni(outputsHex);
  }

  @Override
  public List<MoneroKeyImage> getKeyImages() {
    assertNotClosed();
    String keyImagesJson = getKeyImagesJni();
    List<MoneroKeyImage> keyImages = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, keyImagesJson, KeyImagesContainer.class).keyImages;
    return keyImages;
  }

  @Override
  public MoneroKeyImageImportResult importKeyImages(List<MoneroKeyImage> keyImages) {
    assertNotClosed();
    
    // wrap and serialize key images in container for jni
    KeyImagesContainer keyImageContainer = new KeyImagesContainer(keyImages);
    String importResultJson = importKeyImagesJni(JsonUtils.serialize(keyImageContainer));
    
    // deserialize response
    return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, importResultJson, MoneroKeyImageImportResult.class);
  }

  @Override
  public List<MoneroKeyImage> getNewKeyImagesFromLastImport() {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }
  
  @Override
  public List<String> relayTxs(Collection<String> txMetadatas) {
    assertNotClosed();
    String[] txMetadatasArr = txMetadatas.toArray(new String[txMetadatas.size()]);  // convert to array for jni
    try {
      return Arrays.asList(relayTxsJni(txMetadatasArr));
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroTxSet sendSplit(MoneroSendRequest request) {
    assertNotClosed();
    LOGGER.debug("java sendSplit(request)");
    LOGGER.debug("Send request: " + JsonUtils.serialize(request));
    
    // validate request
    if (request == null) throw new MoneroException("Send request cannot be null");
    
    // submit send request to JNI and get response as json rooted at tx set
    String txSetJson;
    try {
      txSetJson = sendSplitJni(JsonUtils.serialize(request));
      LOGGER.debug("Received sendSplit() response from JNI: " + txSetJson.substring(0, Math.min(5000, txSetJson.length())) + "...");
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
    
    // deserialize and return tx set
    MoneroTxSet txSet = JsonUtils.deserialize(txSetJson, MoneroTxSet.class);
    if (txSet.getTxs() == null) LOGGER.debug("Created tx set without txs: " + JsonUtils.serialize(txSet) + " in sendSplit()");
    else LOGGER.debug("Created " + txSet.getTxs().size() + " transaction(s) in last send request");
    return txSet;
  }
  
  @Override
  public List<MoneroTxSet> sweepUnlocked(MoneroSendRequest request) {
    assertNotClosed();
    
    // validate request
    if (request == null) throw new MoneroException("Send request cannot be null");
    
    // submit send request to JNI and get response as json rooted at tx set
    String txSetsJson;
    try {
      txSetsJson = sweepUnlockedJni(JsonUtils.serialize(request));
      LOGGER.debug("Received sweepUnlocked() response from JNI: " + txSetsJson.substring(0, Math.min(5000, txSetsJson.length())) + "...");
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
    
    // deserialize and return tx sets
    return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, txSetsJson, TxSetsContainer.class).txSets;
  }

  @Override
  public MoneroTxSet sweepOutput(MoneroSendRequest request) {
    assertNotClosed();
    try {
      String txSetJson = sweepOutputJni(JsonUtils.serialize(request));
      MoneroTxSet txSet = JsonUtils.deserialize(txSetJson, MoneroTxSet.class);
      return txSet;
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroTxSet sweepDust(boolean doNotRelay) {
    assertNotClosed();
    String txSetJson;
    try { txSetJson = sweepDustJni(doNotRelay); }
    catch (Exception e) { throw new MoneroException(e.getMessage()); }
    MoneroTxSet txSet = JsonUtils.deserialize(txSetJson, MoneroTxSet.class);
    return txSet;
  }

  @Override
  public MoneroCheckTx checkTxKey(String txId, String txKey, String address) {
    assertNotClosed();
    try {
      String checkStr = checkTxKeyJni(txId, txKey, address);
      return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, checkStr, MoneroCheckTx.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public String getTxProof(String txId, String address, String message) {
    assertNotClosed();
    try {
      return getTxProofJni(txId, address, message);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroCheckTx checkTxProof(String txId, String address, String message, String signature) {
    assertNotClosed();
    try {
      String checkStr = checkTxProofJni(txId, address, message, signature);
      return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, checkStr, MoneroCheckTx.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public String getSpendProof(String txId, String message) {
    assertNotClosed();
    try {
      return getSpendProofJni(txId, message);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public boolean checkSpendProof(String txId, String message, String signature) {
    assertNotClosed();
    try {
      return checkSpendProofJni(txId, message, signature);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public String getReserveProofWallet(String message) {
    try {
      return getReserveProofWalletJni(message);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message) {
    assertNotClosed();
    try {
      return getReserveProofAccountJni(accountIdx, amount.toString(), message);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage(), -1);
    }
  }

  @Override
  public MoneroCheckReserve checkReserveProof(String address, String message, String signature) {
    assertNotClosed();
    try {
      String checkStr = checkReserveProofJni(address, message, signature);
      return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, checkStr, MoneroCheckReserve.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage(), -1);
    }
  }

  @Override
  public String sign(String msg) {
    assertNotClosed();
    return signJni(msg);
  }

  @Override
  public boolean verify(String msg, String address, String signature) {
    assertNotClosed();
    return verifyJni(msg, address, signature);
  }

  @Override
  public String getTxKey(String txId) {
    assertNotClosed();
    try {
      return getTxKeyJni(txId);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public List<String> getTxNotes(Collection<String> txIds) {
    assertNotClosed();
    return Arrays.asList(getTxNotesJni(txIds.toArray(new String[txIds.size()])));  // convert to array for jni
  }

  @Override
  public void setTxNotes(Collection<String> txIds, Collection<String> notes) {
    assertNotClosed();
    setTxNotesJni(txIds.toArray(new String[txIds.size()]), notes.toArray(new String[notes.size()]));
  }

  @Override
  public List<MoneroAddressBookEntry> getAddressBookEntries(Collection<Integer> entryIndices) {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public int addAddressBookEntry(String address, String description, String paymentId) {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void deleteAddressBookEntry(int entryIdx) {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void tagAccounts(String tag, Collection<Integer> accountIndices) {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void untagAccounts(Collection<Integer> accountIndices) {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public List<MoneroAccountTag> getAccountTags() {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void setAccountTagLabel(String tag, String label) {
    assertNotClosed();
    throw new RuntimeException("Not implemented");
  }

  @Override
  public String createPaymentUri(MoneroSendRequest request) {
    assertNotClosed();
    try {
      return createPaymentUriJni(JsonUtils.serialize(request));
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroSendRequest parsePaymentUri(String uri) {
    assertNotClosed();
    try {
      String sendRequestJson = parsePaymentUriJni(uri);
      return JsonUtils.deserialize(MoneroRpcConnection.MAPPER, sendRequestJson, MoneroSendRequest.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  @Override
  public String getAttribute(String key) {
    assertNotClosed();
    String value = getAttributeJni(key);
    return value.isEmpty() ? null : value;
  }

  @Override
  public void setAttribute(String key, String val) {
    assertNotClosed();
    setAttributeJni(key, val);
  }

  @Override
  public void startMining(Long numThreads, Boolean backgroundMining, Boolean ignoreBattery) {
    assertNotClosed();
    try {
      startMiningJni(numThreads == null ? 0l : (long) numThreads, backgroundMining, ignoreBattery);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public void stopMining() {
    assertNotClosed();
    try {
      stopMiningJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  
  @Override
  public boolean isMultisigImportNeeded() {
    assertNotClosed();
    return isMultisigImportNeededJni();
  }
  
  @Override
  public MoneroMultisigInfo getMultisigInfo() {
    try {
      String multisigInfoJson = getMultisigInfoJni();
      return JsonUtils.deserialize(multisigInfoJson, MoneroMultisigInfo.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public String prepareMultisig() {
    return prepareMultisigJni();
  }

  @Override
  public MoneroMultisigInitResult makeMultisig(List<String> multisigHexes, int threshold, String password) {
    try {
      String initMultisigResultJson = makeMultisigJni(multisigHexes.toArray(new String[multisigHexes.size()]), threshold, password);
      return JsonUtils.deserialize(initMultisigResultJson, MoneroMultisigInitResult.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroMultisigInitResult exchangeMultisigKeys(List<String> multisigHexes, String password) {
    try {
      String initMultisigResultJson = exchangeMultisigKeysJni(multisigHexes.toArray(new String[multisigHexes.size()]), password);
      return JsonUtils.deserialize(initMultisigResultJson, MoneroMultisigInitResult.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public String getMultisigHex() {
    try {
      return getMultisigHexJni();
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  @Override
  public int importMultisigHex(List<String> multisigHexes) {
    try {
      return importMultisigHexJni(multisigHexes.toArray(new String[multisigHexes.size()]));
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public MoneroMultisigSignResult signMultisigTxHex(String multisigTxHex) {
    try {
      String signMultisigResultJson = signMultisigTxHexJni(multisigTxHex);
      return JsonUtils.deserialize(signMultisigResultJson, MoneroMultisigSignResult.class);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }

  @Override
  public List<String> submitMultisigTxHex(String signedMultisigTxHex) {
    try {
      return Arrays.asList(submitMultisigTxHexJni(signedMultisigTxHex));
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  @Override
  public void save() {
    assertNotClosed();
    saveJni();
  }
  
  @Override
  public void close(boolean save) {
    if (isClosed) return; // closing a closed wallet has no effect
    isClosed = true;
    try {
      closeJni(save);
    } catch (Exception e) {
      throw new MoneroException(e.getMessage());
    }
  }
  
  // ------------------------------ NATIVE METHODS ----------------------------
  
  private native static boolean walletExistsJni(String path);
  
  private native static long openWalletJni(String path, String password, int networkType);
  
  private native static long createWalletRandomJni(String path, String password, int networkType, String daemonUrl, String daemonUsername, String daemonPassword, String language);
  
  private native static long createWalletFromMnemonicJni(String path, String password, int networkType, String mnemonic, long restoreHeight);
  
  private native static long createWalletFromKeysJni(String path, String password, int networkType, String address, String viewKey, String spendKey, long restoreHeight, String language);
  
  private native long getHeightJni();
  
  private native long getRestoreHeightJni();
  
  private native void setRestoreHeightJni(long height);
  
  private native long getDaemonHeightJni();
  
  private native long getDaemonMaxPeerHeightJni();
  
  private native String[] getDaemonConnectionJni(); // returns [uri, username, password]
  
  private native void setDaemonConnectionJni(String uri, String username, String password);
  
  private native boolean isConnectedJni();
  
  private native boolean isDaemonSyncedJni();
  
  private native boolean isSyncedJni();
  
  private native int getNetworkTypeJni();
  
  private native String getPathJni();
  
  private native String getMnemonicJni();
  
  private native String getLanguageJni();
  
  private native String[] getLanguagesJni();
  
  private native String getPublicViewKeyJni();
  
  private native String getPrivateViewKeyJni();
  
  private native String getPublicSpendKeyJni();
  
  private native String getPrivateSpendKeyJni();
  
  private native String getAddressJni(int accountIdx, int subaddressIdx);
  
  private native String getAddressIndexJni(String address);
  
  private native String getIntegratedAddressJni(String standardAddress, String paymentId);
  
  private native String decodeIntegratedAddressJni(String integratedAddress);
  
  private native long setListenerJni(WalletJniListener listener);
  
  private native Object[] syncJni(long startHeight);
  
  private native void startSyncingJni();
  
  private native void stopSyncingJni();
  
  private native void rescanSpentJni();
  
  private native void rescanBlockchainJni();
  
  private native String getBalanceWalletJni();
  
  private native String getBalanceAccountJni(int accountIdx);
  
  private native String getBalanceSubaddressJni(int accountIdx, int subaddressIdx);
  
  private native String getUnlockedBalanceWalletJni();
  
  private native String getUnlockedBalanceAccountJni(int accountIdx);
  
  private native String getUnlockedBalanceSubaddressJni(int accountIdx, int subaddressIdx);
  
  private native String getAccountsJni(boolean includeSubaddresses, String tag);
  
  private native String getAccountJni(int accountIdx, boolean includeSubaddresses);
  
  private native String createAccountJni(String label);
  
  private native String getSubaddressesJni(int accountIdx, int[] subaddressIndices);
  
  private native String createSubaddressJni(int accountIdx, String label);
  
  /**
   * Gets txs from the native layer using strings to communicate.
   * 
   * @param txQueryJson is a tx query serialized to a json string
   * @return a serialized BlocksContainer to preserve model relationships
   */
  private native String getTxsJni(String txQueryJson);
  
  private native String getTransfersJni(String transferQueryJson);
  
  private native String getOutputsJni(String outputQueryJson);
  
  private native String getOutputsHexJni();
  
  private native int importOutputsHexJni(String outputsHex);
  
  private native String getKeyImagesJni();
  
  private native String importKeyImagesJni(String keyImagesJson);
  
  private native String[] relayTxsJni(String[] txMetadatas);
  
  private native String sendSplitJni(String sendRequestJson);
  
  private native String sweepUnlockedJni(String sendRequestJson);
  
  private native String sweepOutputJni(String sendRequestJson);
  
  private native String sweepDustJni(boolean doNotRelay);
  
  private native String[] getTxNotesJni(String[] txIds);
  
  private native void setTxNotesJni(String[] txIds, String[] notes);
  
  private native String signJni(String msg);
  
  private native boolean verifyJni(String msg, String address, String signature);
  
  private native String getTxKeyJni(String txId);
  
  private native String checkTxKeyJni(String txId, String txKey, String address);
  
  private native String getTxProofJni(String txId, String address, String message);
  
  private native String checkTxProofJni(String txId, String address, String message, String signature);
  
  private native String getSpendProofJni(String txId, String message);
  
  private native boolean checkSpendProofJni(String txId, String message, String signature);
  
  private native String getReserveProofWalletJni(String message);
  
  private native String getReserveProofAccountJni(int accountIdx, String amount, String message);
  
  private native String checkReserveProofJni(String address, String message, String signature);
  
  private native String createPaymentUriJni(String sendRequestJson);
  
  private native String parsePaymentUriJni(String uri);
  
  private native String getAttributeJni(String key);
  
  private native void setAttributeJni(String key, String val);

  private native void startMiningJni(long numThreads, Boolean backgroundMining, Boolean ignoreBattery);
  
  private native void stopMiningJni();
  
  private native boolean isMultisigImportNeededJni();
  
  private native String getMultisigInfoJni();
  
  private native String prepareMultisigJni();
  
  private native String makeMultisigJni(String[] multisigHexes, int threshold, String password);
  
  private native String exchangeMultisigKeysJni(String[] multisigHexes, String password);
  
  private native String getMultisigHexJni();
  
  private native int importMultisigHexJni(String[] multisigHexes);
  
  private native String signMultisigTxHexJni(String multisigTxHex);
  
  private native String[] submitMultisigTxHexJni(String signedMultisigTxHex);
  
  private native void saveJni();
  
  private native void moveToJni(String path, String password);
  
  private native void closeJni(boolean save);
  
  // ------------------------------- LISTENERS --------------------------------
  
  /**
   * Receives notifications directly from jni c++.
   */
  @SuppressWarnings("unused") // called directly from jni c++
  private class WalletJniListener {
    
    /**
     * Enables or disables listening in the c++ wallet.
     */
    public void setIsListening(boolean isEnabled) {
      jniListenerHandle = setListenerJni(isEnabled ? this : null);
    }
    
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      for (MoneroWalletListenerI listener : listeners) {
        listener.onSyncProgress(height, startHeight, endHeight, percentDone, message);
      }
    }
    
    public void onNewBlock(long height) {
      for (MoneroWalletListenerI listener : listeners) listener.onNewBlock(height);
    }
    
    public void onOutputReceived(long height, String txId, String amountStr, int accountIdx, int subaddressIdx, int version, long unlockTime) {
      
      // build received output
      MoneroOutputWallet output = new MoneroOutputWallet();
      output.setAmount(new BigInteger(amountStr));
      output.setAccountIndex(accountIdx);
      output.setSubaddressIndex(subaddressIdx);
      MoneroTxWallet tx = new MoneroTxWallet();
      tx.setId(txId);
      tx.setVersion(version);
      tx.setUnlockTime(unlockTime);
      output.setTx(tx);
      tx.setVouts(Arrays.asList(output));
      if (height > 0) {
        MoneroBlock block = new MoneroBlock().setHeight(height);
        block.setTxs(Arrays.asList(tx));
        tx.setBlock(block);
      }
      
      // announce output
      for (MoneroWalletListenerI listener : listeners) listener.onOutputReceived((MoneroOutputWallet) tx.getVouts().get(0));
    }
    
    public void onOutputSpent(long height, String txId, String amountStr, int accountIdx, int subaddressIdx, int version) {
      
      // build spent output
      MoneroOutputWallet output = new MoneroOutputWallet();
      output.setAmount(new BigInteger(amountStr));
      output.setAccountIndex(accountIdx);
      output.setSubaddressIndex(subaddressIdx);
      MoneroTxWallet tx = new MoneroTxWallet();
      tx.setId(txId);
      tx.setVersion(version);
      output.setTx(tx);
      tx.setVins(Arrays.asList(output));
      if (height > 0) {
        MoneroBlock block = new MoneroBlock().setHeight(height);
        block.setTxs(Arrays.asList(tx));
        tx.setBlock(block);
      }
      
      // announce output
      for (MoneroWalletListenerI listener : listeners) listener.onOutputSpent((MoneroOutputWallet) tx.getVins().get(0));
    }
  }
  
  /**
   * Wraps a sync listener as a general wallet listener.
   */
  private class SyncListenerWrapper extends MoneroWalletListener {
    
    private MoneroSyncListener listener;
    
    public SyncListenerWrapper(MoneroSyncListener listener) {
      this.listener = listener;
    }
    
    @Override
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      listener.onSyncProgress(height, startHeight, endHeight, percentDone, message);
    }
  }
  
  // ------------------------ RESPONSE DESERIALIZATION ------------------------
  
  /**
   * Override MoneroBlock with wallet types for polymorphic deserialization.
   */
  private static class MoneroBlockWallet extends MoneroBlock {
    
    // default constructor necessary for serialization
    @SuppressWarnings("unused")
    public MoneroBlockWallet() {
      super();
    }
    
    @JsonProperty("txs")
    public MoneroBlockWallet setTxWallets(List<MoneroTxWallet> txs) {
      super.setTxs(new ArrayList<MoneroTx>(txs));
      return this;
    }
    
    /**
     * Initializes a new MoneroBlock with direct references to this block.
     * 
     * TODO: more efficient way to deserialize directly into MoneroBlock?
     * 
     * @return MoneroBlock is the newly initialized block with direct references to this block
     */
    public MoneroBlock toBlock() {
      MoneroBlock block = new MoneroBlock();
      block.setId(getId());
      block.setHeight(getHeight());
      block.setTimestamp(getTimestamp());
      block.setSize(getSize());
      block.setWeight(getWeight());
      block.setLongTermWeight(getLongTermWeight());
      block.setDepth(getDepth());
      block.setDifficulty(getDifficulty());
      block.setCumulativeDifficulty(getCumulativeDifficulty());
      block.setMajorVersion(getMajorVersion());
      block.setMinorVersion(getMinorVersion());
      block.setNonce(getNonce());
      block.setMinerTxId(getMinerTxId());
      block.setNumTxs(getNumTxs());
      block.setOrphanStatus(getOrphanStatus());
      block.setPrevId(getPrevId());
      block.setReward(getReward());
      block.setPowHash(getPowHash());
      block.setHex(getHex());
      block.setMinerTx(getMinerTx());
      block.setTxs(getTxs());
      block.setTxIds(getTxIds());
      for (MoneroTx tx : getTxs()) tx.setBlock(block);  // re-assign tx block references
      return block;
    }
  }
  
  private static class AccountsContainer {
    public List<MoneroAccount> accounts;
  };
  
  private static class SubaddressesContainer {
    public List<MoneroSubaddress> subaddresses;
  };
  
  private static class BlocksContainer {
    public List<MoneroBlockWallet> blocks;
  }
  
  private static class TxSetsContainer {
    public List<MoneroTxSet> txSets;
  }
  
  private static class KeyImagesContainer {
    public List<MoneroKeyImage> keyImages;
    @SuppressWarnings("unused") public KeyImagesContainer() { } // necessary for serialization
    public KeyImagesContainer(List<MoneroKeyImage> keyImages) { this.keyImages = keyImages; };
  }
  
  private static List<MoneroBlock> deserializeBlocks(String blocksJson) {
    List<MoneroBlockWallet> blockWallets =  JsonUtils.deserialize(MoneroRpcConnection.MAPPER, blocksJson, BlocksContainer.class).blocks;
    List<MoneroBlock> blocks = new ArrayList<MoneroBlock>();
    if (blockWallets == null) return blocks;
    for (MoneroBlockWallet blockWallet: blockWallets) blocks.add(blockWallet.toBlock());
    return blocks;
  }
  
  // ---------------------------- PRIVATE HELPERS -----------------------------
  
  private void assertNotClosed() {
    if (isClosed) throw new MoneroException("Wallet is closed");
  }
  
  private static MoneroAccount sanitizeAccount(MoneroAccount account) {
    if (account.getSubaddresses() != null) {
      for (MoneroSubaddress subaddress : account.getSubaddresses()) sanitizeSubaddress(subaddress);
    }
    return account;
  }
  
  private static MoneroSubaddress sanitizeSubaddress(MoneroSubaddress subaddress) {
    if ("".equals(subaddress.getLabel())) subaddress.setLabel(null);
    return subaddress;
  }
  
  private static MoneroBlock sanitizeBlock(MoneroBlock block) {
    for (MoneroTx tx : block.getTxs()) sanitizeTxWallet((MoneroTxWallet) tx);
    return block;
  }
  
  private static MoneroTxWallet sanitizeTxWallet(MoneroTxWallet tx) {
    return tx;
  }
}
