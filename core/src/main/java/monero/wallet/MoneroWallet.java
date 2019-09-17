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

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

import monero.daemon.model.MoneroKeyImage;
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
import monero.wallet.model.MoneroOutgoingTransfer;
import monero.wallet.model.MoneroOutputQuery;
import monero.wallet.model.MoneroOutputWallet;
import monero.wallet.model.MoneroSendPriority;
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
 * Monero wallet interface.
 */
public interface MoneroWallet {
  
  public static final String DEFAULT_LANGUAGE = "English";
  
  /**
   * Get the wallet's path.
   * 
   * @return the path the wallet can be opened with
   */
  public String getPath();
  
  /**
   * Get the wallet's seed.
   * 
   * @return the wallet's seed
   */
  public String getSeed();
  
  /**
   * Get the wallet's mnemonic phrase derived from the seed.
   * 
   * @return the wallet's mnemonic phrase
   */
  public String getMnemonic();
  
  /**
   * Get a list of available languages for the wallet's mnemonic phrase.
   * 
   * @return the available languages for the wallet's mnemonic phrase
   */
  public List<String> getLanguages();
  
  /**
   * Get the wallet's private view key.
   * 
   * @return the wallet's private view key
   */
  public String getPrivateViewKey();
  
  /**
   * Get the wallet's private spend key.
   * 
   * @return the wallet's private spend key
   */
  public String getPrivateSpendKey();
    
  /**
   * Get the wallet's primary address.
   * 
   * @return the wallet's primary address
   */
  public String getPrimaryAddress();
  
  /**
   * Get the address of a specific subaddress.
   * 
   * @param accountIdx specifies the account index of the address's subaddress
   * @param subaddressIdx specifies the subaddress index within the account
   * @return the receive address of the specified subaddress
   */
  public String getAddress(int accountIdx, int subaddressIdx);
  
  /**
   * Get the account and subaddress index of the given address.
   * 
   * @param address is the address to get the account and subaddress index from
   * @return the account and subaddress indices
   */
  public MoneroSubaddress getAddressIndex(String address);
  
  /**
   * Get an integrated address based on this wallet's primary address and a
   * randomly generated payment ID.  Generates a random payment ID if none is
   * given.
   * 
   * @return the integrated address
   */
  public MoneroIntegratedAddress getIntegratedAddress();
  
  /**
   * Get an integrated address based on this wallet's primary address and the
   * given payment ID.  Generates a random payment ID if none is given.
   * 
   * @param paymentId is the payment ID to generate an integrated address from (randomly generated if null)
   * @return the integrated address
   */
  public MoneroIntegratedAddress getIntegratedAddress(String paymentId);
  
  /**
   * Decode an integrated address to get its standard address and payment id.
   * 
   * @param integratedAddress is an integrated address to decode
   * @return the decoded integrated address including standard address and payment id
   */
  public MoneroIntegratedAddress decodeIntegratedAddress(String integratedAddress);
  
  /**
   * Get the height of the last block processed by the wallet (its index + 1).
   * 
   * @return the height of the last block processed by the wallet
   */
  public long getHeight();
  
  /**
   * Get the blockchain's height.
   * 
   * @return the blockchain's height
   */
  public long getDaemonHeight();
  
  /**
   * Synchronize the wallet with the daemon as a one-time synchronous process.
   * 
   * @return the sync result
   */
  public MoneroSyncResult sync();
  
  /**
   * Synchronize the wallet with the daemon as a one-time synchronous process.
   * 
   * @param listener is invoked as sync progress is made
   * @return the sync result
   */
  public MoneroSyncResult sync(MoneroSyncListener listener);
  
  /**
   * Synchronize the wallet with the daemon as a one-time synchronous process.
   * 
   * @param startHeight is the start height to sync from (defaults to the last synced block)
   * @return the sync result
   */
  public MoneroSyncResult sync(Long startHeight);
  
  /**
   * Synchronize the wallet with the daemon as a one-time synchronous process.
   * 
   * @param startHeight is the start height to sync from (defaults to the last synced block)
   * @param listener is invoked as sync progress is made
   * @return the sync result
   */
  public MoneroSyncResult sync(Long startHeight, MoneroSyncListener listener);
  
  /**
   * Start an asynchronous thread to continuously synchronize the wallet with the daemon.
   */
  public void startSyncing();
  
  /**
   * Stop the asynchronous thread to continuously synchronize the wallet with the daemon.
   */
  public void stopSyncing();
  
  /**
   * Rescan the blockchain for spent outputs.
   *
   * Note: this can only be called with a trusted daemon.
   *
   * Example use case: peer multisig hex is import when connected to an untrusted daemon,
   * so the wallet will not rescan spent outputs.  Then the wallet connects to a trusted
   * daemon.  This method should be manually invoked to rescan outputs.
   */
  public void rescanSpent();
  
  /**
   * Rescan the blockchain from scratch, losing any information which cannot be recovered from
   * the blockchain itself.
   * 
   * WARNING: This method discards local wallet data like destination addresses, tx secret keys,
   * tx notes, etc.
   */
  public void rescanBlockchain();
  
  /**
   * Get the wallet's balance.
   * 
   * @return the wallet's balance
   */
  public BigInteger getBalance();
  
  /**
   * Get an account's balance.
   * 
   * @param accountIdx is the index of the account to get the balance of
   * @return the account's balance
   */
  public BigInteger getBalance(int accountIdx);
  
  /**
   * Get a subaddress's balance.
   * 
   * @param accountIdx is the index of the subaddress's account to get the balance of
   * @param subaddressIdx is the index of the subaddress to get the balance of
   * @return the subaddress's balance
   */
  public BigInteger getBalance(int accountIdx, int subaddressIdx);
  
  /**
   * Get the wallet's unlocked balance.
   * 
   * @return the wallet's unlocked balance
   */
  public BigInteger getUnlockedBalance();
  
  /**
   * Get an account's unlocked balance.
   * 
   * @param accountIdx is the index of the account to get the unlocked balance of
   * @return the account's unlocked balance
   */
  public BigInteger getUnlockedBalance(int accountIdx);
  
  /**
   * Get a subaddress's unlocked balance.
   * 
   * @param accountIdx is the index of the subaddress's account to get the unlocked balance of
   * @param subaddressIdx is the index of the subaddress to get the unlocked balance of
   * @return the subaddress's balance
   */
  public BigInteger getUnlockedBalance(int accountIdx, int subaddressIdx);
  
  /**
   * Get all accounts.
   * 
   * @return all accounts
   */
  public List<MoneroAccount> getAccounts();
  
  /**
   * Get all accounts.
   * 
   * @param includeSubaddresses specifies if subaddresses should be included
   * @return all accounts
   */
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses);
  
  /**
   * Get accounts with a given tag.
   * 
   * @param tag is the tag for filtering accounts, all accounts if null
   * @return all accounts with the given tag
   */
  public List<MoneroAccount> getAccounts(String tag);
  
  /**
   * Get accounts with a given tag.
   * 
   * @param includeSubaddresses specifies if subaddresses should be included
   * @param tag is the tag for filtering accounts, all accounts if null
   * @return all accounts with the given tag
   */
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses, String tag);
  
  /**
   * Get an account without subaddress information.
   * 
   * @param accountIdx specifies the account to get
   * @return the retrieved account
   */
  public MoneroAccount getAccount(int accountIdx);
  
  /**
   * Get an account.
   * 
   * @param accountIdx specifies the account to get
   * @param includeSubaddresses specifies if subaddresses should be included
   * @return the retrieved account
   */
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses);
  
  /**
   * Create a new account.
   * 
   * @return the created account
   */
  public MoneroAccount createAccount();

  /**
   * Create a new account with a label for the first subaddress.
   * 
   * @param label specifies the label for account's first subaddress (optional)
   * @return the created account
   */
  public MoneroAccount createAccount(String label);
  
  /**
   * Get all subaddresses in an account.
   * 
   * @param accountIdx specifies the account to get subaddresses within
   * @return the retrieved subaddresses
   */
  public List<MoneroSubaddress> getSubaddresses(int accountIdx);
  
  /**
   * Get subaddresses in an account.
   * 
   * @param accountIdx specifies the account to get subaddresses within
   * @param subaddressIndices are specific subaddresses to get (optional)
   * @return the retrieved subaddresses
   */
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices);
  
  /**
   * Get a subaddress.
   * 
   * @param accountIdx specifies the index of the subaddress's account
   * @param subaddressIdx specifies index of the subaddress within the account
   * @return the retrieved subaddress
   */
  public MoneroSubaddress getSubaddress(int accountIdx, int subaddressIdx);
  
  /**
   * Create a subaddress within an account and without a label.
   * 
   * @param accountIdx specifies the index of the account to create the subaddress within
   * @return the created subaddress
   */
  public MoneroSubaddress createSubaddress(int accountIdx);
  
  /**
   * Create a subaddress within an account.
   * 
   * @param accountIdx specifies the index of the account to create the subaddress within
   * @param label specifies the the label for the subaddress (optional)
   * @return the created subaddress
   */
  public MoneroSubaddress createSubaddress(int accountIdx, String label);
  
  /**
   * Get a wallet transaction by id.
   * 
   * @param txId is an id of a transaction to get
   * @return the identified transactions
   */
  public MoneroTxWallet getTx(String txId);
  
  /**
   * Get all wallet transactions.  Wallet transactions contain one or more
   * transfers that are either incoming or outgoing to the wallet.
   * 
   * @return all wallet transactions
   */
  public List<MoneroTxWallet> getTxs();
  
  /**
   * Get wallet transactions by id.
   * 
   * @param txIds are ids of transactions to get
   * @return the identified transactions
   */
  public List<MoneroTxWallet> getTxs(String... txIds);
  
  /**
   * Get wallet transactions by id.
   * 
   * @param txIds are ids of transactions to get
   * @return the identified transactions
   */
  public List<MoneroTxWallet> getTxs(List<String> txIds);
  
  /**
   * Get wallet transactions.  Wallet transactions contain one or more
   * transfers that are either incoming or outgoing to the wallet.
   * 
   * Query results can be filtered by passing a transaction query.
   * Transactions must meet every criteria defined in the query in order to
   * be returned.  All filtering is optional and no filtering is applied when
   * not defined.
   * 
   * @param query specifies attributes of transactions to get
   * @return wallet transactions per the query
   */
  public List<MoneroTxWallet> getTxs(MoneroTxQuery query);
  
  /**
   * Get all incoming and outgoing transfers to and from this wallet.  An
   * outgoing transfer represents a total amount sent from one or more
   * subaddresses within an account to individual destination addresses, each
   * with their own amount.  An incoming transfer represents a total amount
   * received into a subaddress within an account.  Transfers belong to
   * transactions which are stored on the blockchain.
   * 
   * @return all wallet transfers
   */
  public List<MoneroTransfer> getTransfers();
  
  /**
   * Get incoming and outgoing transfers to and from an account.  An outgoing
   * transfer represents a total amount sent from one or more subaddresses
   * within an account to individual destination addresses, each with their
   * own amount.  An incoming transfer represents a total amount received into
   * a subaddress within an account.  Transfers belong to transactions which
   * are stored on the blockchain.
   * 
   * @param accountIdx is the index of the account to get transfers from
   * @return transfers to/from the account
   */
  public List<MoneroTransfer> getTransfers(int accountIdx);
  
  /**
   * Get incoming and outgoing transfers to and from a subaddress.  An outgoing
   * transfer represents a total amount sent from one or more subaddresses
   * within an account to individual destination addresses, each with their
   * own amount.  An incoming transfer represents a total amount received into
   * a subaddress within an account.  Transfers belong to transactions which
   * are stored on the blockchain.
   * 
   * @param accountIdx is the index of the account to get transfers from
   * @param subaddressIdx is the index of the subaddress to get transfers from
   * @return transfers to/from the subaddress
   */
  public List<MoneroTransfer> getTransfers(int accountIdx, int subaddressIdx);
  
  /**
   * Get incoming and outgoing transfers to and from this wallet.  An outgoing
   * transfer represents a total amount sent from one or more subaddresses
   * within an account to individual destination addresses, each with their
   * own amount.  An incoming transfer represents a total amount received into
   * a subaddress within an account.  Transfers belong to transactions which
   * are stored on the blockchain.
   * 
   * Query results can be filtered by passing in a MoneroTransferQuery.
   * Transfers must meet every criteria defined in the query in order to be
   * returned.  All filtering is optional and no filtering is applied when not
   * defined.
   * 
   * @param query specifies attributes of transfers to get
   * @return wallet transfers per the query
   */
  public List<MoneroTransfer> getTransfers(MoneroTransferQuery query);
  
  /**
   * Get all of the wallet's incoming transfers.
   * 
   * @return the wallet's incoming transfers
   */
  public List<MoneroIncomingTransfer> getIncomingTransfers();
  
  /**
   * Get the wallet's incoming transfers according to the given query.
   * 
   * @param query specifies which incoming transfers to get
   * @return the wallet's incoming transfers according to the given query
   */
  public List<MoneroIncomingTransfer> getIncomingTransfers(MoneroTransferQuery query);
  
  /**
   * Get all of the wallet's outgoing transfers.
   * 
   * @return the wallet's outgoing transfers
   */
  public List<MoneroOutgoingTransfer> getOutgoingTransfers();
  
  /**
   * Get the wallet's outgoing transfers according to the given query.
   * 
   * @param query specifies which outgoing transfers to get
   * @return the wallet's outgoing transfers according to the given query
   */
  public List<MoneroOutgoingTransfer> getOutgoingTransfers(MoneroTransferQuery query);
  
  /**
   * Get outputs created from previous transactions that belong to the wallet
   * (i.e. that the wallet can spend one time).  Outputs are part of
   * transactions which are stored in blocks on the blockchain.
   * 
   * @return List<MoneroOutputWallet> are all wallet outputs
   */
  public List<MoneroOutputWallet> getOutputs();
  
  /**
   * Get outputs created from previous transactions that belong to the wallet
   * (i.e. that the wallet can spend one time).  Outputs are part of
   * transactions which are stored in blocks on the blockchain.
   * 
   * Results can be configured by passing a MoneroOutputQuery.  Outputs must
   * meet every criteria defined in the query in order to be returned.  All
   * filtering is optional and no filtering is applied when not defined.
   * 
   * @param query specifies attributes of outputs to get
   * @return List<MoneroOutputWallet> are wallet outputs per the query
   */
  public List<MoneroOutputWallet> getOutputs(MoneroOutputQuery query);
  
  /**
   * Export all outputs in hex format.
   * 
   * @return all outputs in hex format, null if no outputs
   */
  public String getOutputsHex();
  
  /**
   * Import outputs in hex format.
   * 
   * @param outputsHex are outputs in hex format
   * @return the number of outputs imported
   */
  public int importOutputsHex(String outputsHex);
  
  /**
   * Get all signed key images.
   * 
   * @return the wallet's signed key images
   */
  public List<MoneroKeyImage> getKeyImages();
  
  /**
   * Import signed key images and verify their spent status.
   * 
   * @param keyImages are key images to import and verify (requires hex and signature)
   * @return results of the import
   */
  public MoneroKeyImageImportResult importKeyImages(List<MoneroKeyImage> keyImages);
  
  /**
   * Get new key images from the last imported outputs.
   * 
   * @return the key images from the last imported outputs
   */
  public List<MoneroKeyImage> getNewKeyImagesFromLastImport();
  
  /**
   * Create a transaction to transfer funds from this wallet according to the
   * given request.  The transaction may be relayed later.
   * 
   * @param request configures the transaction to create
   * @return a tx set for the requested transaction if possible
   */
  public MoneroTxSet createTx(MoneroSendRequest request);
  
  /**
   * Create a transaction to transfers funds from this wallet to a destination address.
   * The transaction may be relayed later.
   * 
   * @param accountIndex is the index of the account to withdraw funds from
   * @param address is the destination address to send funds to
   * @param amount is the amount being sent
   * @return a tx set for the requested transaction if possible
   */
  public MoneroTxSet createTx(int accountIndex, String address, BigInteger amount);
  
  /**
   * Create a transaction to transfers funds from this wallet to a destination address.
   * The transaction may be relayed later.
   * 
   * @param accountIndex is the index of the account to withdraw funds from
   * @param address is the destination address to send funds to
   * @param amount is the amount being sent
   * @param priority is the send priority (default normal)
   * @return a tx set for the requested transaction if possible
   */
  public MoneroTxSet createTx(int accountIndex, String address, BigInteger amount, MoneroSendPriority priority);
  
  /**
   * Create one or more transactions to transfer funds from this wallet
   * according to the given request.  The transactions may later be relayed.
   * 
   * @param request configures the transactions to create
   * @return a tx set for the requested transactions if possible
   */
  public MoneroTxSet createTxs(MoneroSendRequest request);
  
  /**
   * Relay a previously created transaction.
   * 
   * @param txMetadata is transaction metadata previously created without relaying
   * @return the id of the relayed tx
   */
  public String relayTx(String txMetadata);
  
  /**
   * Relay a previously created transaction.
   * 
   * @param tx is the transaction to relay
   * @return the id of the relayed tx
   */
  public String relayTx(MoneroTxWallet tx);
  
  /**
   * Relay previously created transactions.
   * 
   * @param txMetadatas are transaction metadata previously created without relaying
   * @return the ids of the relayed txs
   */
  public List<String> relayTxs(Collection<String> txMetadatas);
  
  /**
   * Relay previously created transactions.
   * 
   * @param txs are the transactions to relay
   * @return the ids of the relayed txs
   */
  public List<String> relayTxs(List<MoneroTxWallet> txs);
  
  /**
   * Create and relay a transaction to transfer funds from this wallet
   * according to the given request.
   * 
   * @param request configures the transaction
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet send(MoneroSendRequest request);
  
  /**
   * Create and relay a transaction to transfers funds from this wallet to
   * a destination address.
   * 
   * @param accountIndex is the index of the account to withdraw funds from
   * @param address is the destination address to send funds to
   * @param amount is the amount being sent
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet send(int accountIndex, String address, BigInteger amount);
  
  /**
   * Create and relay a transaction to transfers funds from this wallet to
   * a destination address.
   * 
   * @param accountIndex is the index of the account to withdraw funds from
   * @param address is the destination address to send funds to
   * @param amount is the amount being sent
   * @param priority is the send priority (default normal)
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet send(int accountIndex, String address, BigInteger amount, MoneroSendPriority priority);
  
  /**
   * Create and relay one or more transactions to transfer funds from this
   * wallet according to the given request.
   * 
   * @param request configures the transactions
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet sendSplit(MoneroSendRequest request);
  
  /**
   * Create and relay one or more transactions which transfer funds from this
   * wallet to a destination address.
   * 
   * @param accountIndex is the index of the account to withdraw funds from
   * @param address is the destination address to send funds to
   * @param amount is the amount being sent
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet sendSplit(int accountIndex, String address, BigInteger amount);
  
  /**
   * Create and relay one or more transactions to transfer funds from this
   * wallet to a destination address with a priority.
   * 
   * @param accountIndex is the index of the account to withdraw funds from
   * @param address is the destination address to send funds to
   * @param amount is the amount being sent
   * @param priority is the send priority (default normal)
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet sendSplit(int accountIndex, String address, BigInteger amount, MoneroSendPriority priority);
  
  /**
   * Sweep an output with a given key image.
   * 
   * @param request configures the sweep transaction
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet sweepOutput(MoneroSendRequest request);
  
  /**
   * Sweep an output with a given key image.
   * 
   * @param address is the destination address to send to
   * @param keyImage is the key image hex of the output to sweep
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet sweepOutput(String address, String keyImage);
  
  /**
   * Sweep an output with a given key image.
   * 
   * @param address is the destination address to send to
   * @param keyImage is the key image hex of the output to sweep
   * @param priority is the transaction priority (optional)
   * @return a tx set with the requested transaction if possible
   */
  public MoneroTxSet sweepOutput(String address, String keyImage, MoneroSendPriority priority);
  
  /**
   * Sweep a subaddress's unlocked funds to an address.
   * 
   * @param accountIdx is the index of the account
   * @param subaddressIdx is the index of the subaddress
   * @param address is the address to sweep the subaddress's funds to
   * @return a tx set with the requested transactions if possible
   */
  public MoneroTxSet sweepSubaddress(int accountIdx, int subaddressIdx, String address);
  
  /**
   * Sweep an acount's unlocked funds to an address.
   * 
   * @param accountIdx is the index of the account
   * @param address is the address to sweep the account's funds to
   * @return a tx set with the requested transactions if possible
   */
  public MoneroTxSet sweepAccount(int accountIdx, String address);
  
  /**
   * Sweep the wallet's unlocked funds to an address.
   * 
   * @param address is the address to sweep the wallet's funds to
   * @return the tx sets with the transactions which sweep the wallet
   */
  public List<MoneroTxSet> sweepWallet(String address);

  /**
   * Sweep all unlocked funds according to the given request.
   * 
   * @param request is the sweep configuration
   * @return the tx sets with the requested transactions
   */
  public List<MoneroTxSet> sweepUnlocked(MoneroSendRequest request);
  
  /**
   * Sweep all unmixable dust outputs back to the wallet to make them easier to spend and mix.
   * 
   * NOTE: Dust only exists pre RCT, so this method will throw "no dust to sweep" on new wallets.
   * 
   * @return a tx set with the requested transactions if possible
   */
  public MoneroTxSet sweepDust();
  
  /**
   * Sweep all unmixable dust outputs back to the wallet to make them easier to spend and mix.
   * 
   * @param doNotRelay specifies if the resulting transaction should not be relayed (defaults to false i.e. relayed)
   * @return a tx set with the requested transactions if possible
   */
  public MoneroTxSet sweepDust(boolean doNotRelay);
  
  /**
   * Sign a message.
   * 
   * @param message is the message to sign
   * @return the signature
   */
  public String sign(String message);
  
  /**
   * Verify a signature on a message.
   * 
   * @param message is the signed message
   * @param address is the signing address
   * @param signature is the signature
   * @return true if the signature is good, false otherwise
   */
  public boolean verify(String message, String address, String signature);
  
  /**
   * Get a transaction's secret key from its id.
   * 
   * @param txId is the transaction's id
   * @return is the transaction's secret key
   */
  public String getTxKey(String txId);
  
  /**
   * Check a transaction in the blockchain with its secret key.
   * 
   * @param txId specifies the transaction to check
   * @param txKey is the transaction's secret key
   * @param address is the destination public address of the transaction
   * @return the result of the check
   */
  public MoneroCheckTx checkTxKey(String txId, String txKey, String address);
  
  /**
   * Get a transaction signature to prove it.
   * 
   * @param txId specifies the transaction to prove
   * @param address is the destination public address of the transaction
   * @return the transaction signature
   */
  public String getTxProof(String txId, String address);
  
  /**
   * Get a transaction signature to prove it.
   * 
   * @param txId specifies the transaction to prove
   * @param address is the destination public address of the transaction
   * @param message is a message to include with the signature to further authenticate the proof (optional)
   * @return the transaction signature
   */
  public String getTxProof(String txId, String address, String message);
  
  /**
   * Prove a transaction by checking its signature.
   * 
   * @param txId specifies the transaction to prove
   * @param address is the destination public address of the transaction
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @param signature is the transaction signature to confirm
   * @return the result of the check
   */
  public MoneroCheckTx checkTxProof(String txId, String address, String message, String signature);
  
  /**
   * Generate a signature to prove a spend. Unlike proving a transaction, it does not require the destination public address.
   * 
   * @param txId specifies the transaction to prove
   * @return the transaction signature
   */
  public String getSpendProof(String txId);
  
  /**
   * Generate a signature to prove a spend. Unlike proving a transaction, it does not require the destination public address.
   * 
   * @param txId specifies the transaction to prove
   * @param message is a message to include with the signature to further authenticate the proof (optional)
   * @return the transaction signature
   */
  public String getSpendProof(String txId, String message);
  
  /**
   * Prove a spend using a signature. Unlike proving a transaction, it does not require the destination public address.
   * 
   * @param txId specifies the transaction to prove
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @param signature is the transaction signature to confirm
   * @return true if the signature is good, false otherwise
   */
  public boolean checkSpendProof(String txId, String message, String signature);
  
  /**
   * Generate a signature to prove the entire balance of the wallet.
   * 
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @return the reserve proof signature
   */
  public String getReserveProofWallet(String message);
  
  /**
   * Generate a signature to prove an available amount in an account.
   * 
   * @param accountIdx specifies the account to prove ownership of the amount
   * @param amount is the minimum amount to prove as available in the account
   * @param message is a message to include with the signature to further authenticate the proof (optional)
   * @return the reserve proof signature
   */
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message);

  /**
   * Proves a wallet has a disposable reserve using a signature.
   * 
   * @param address is the public wallet address
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @param signature is the reserve proof signature to check
   * @return the result of checking the signature proof
   */
  public MoneroCheckReserve checkReserveProof(String address, String message, String signature);
  
  /**
   * Get a transaction note.
   * 
   * @param txId specifies the transaction to get the note of
   * @return the tx note
   */
  public String getTxNote(String txId);
  
  /**
   * Get notes for multiple transactions.
   * 
   * @param txIds identify the transactions to get notes for
   * @return notes for the transactions
   */
  public List<String> getTxNotes(Collection<String> txIds);
  
  /**
   * Set a note for a specific transaction.
   * 
   * @param txId specifies the transaction
   * @param note specifies the note
   */
  public void setTxNote(String txId, String note);
  
  /**
   * Set notes for multiple transactions.
   * 
   * @param txIds specify the transactions to set notes for
   * @param notes are the notes to set for the transactions
   */
  public void setTxNotes(Collection<String> txIds, Collection<String> notes);
  
  /**
   * Get all address book entries.
   * 
   * @return the address book entries
   */
  public List<MoneroAddressBookEntry> getAddressBookEntries();
  
  /**
   * Get address book entries.
   * 
   * @param entryIndices are indices of the entries to get
   * @return the address book entries
   */
  public List<MoneroAddressBookEntry> getAddressBookEntries(Collection<Integer> entryIndices);
  
  /**
   * Add an address book entry.
   * 
   * @param address is the entry address
   * @param description is the entry description (optional)
   * @return the index of the added entry
   */
  public int addAddressBookEntry(String address, String description);
  
  /**
   * Add an address book entry.
   * 
   * @param address is the entry address
   * @param description is the entry description (optional)
   * @param paymentId is the entry paymet id (optional)
   * @return the index of the added entry
   */
  public int addAddressBookEntry(String address, String description, String paymentId);
  
  /**
   * Delete an address book entry.
   * 
   * @param entryIdx is the index of the entry to delete
   */
  public void deleteAddressBookEntry(int entryIdx);
  
  /**
   * Tag accounts.
   * 
   * @param tag is the tag to apply to the specified accounts
   * @param accountIndices are the indices of the accounts to tag
   */
  public void tagAccounts(String tag, Collection<Integer> accountIndices);

  /**
   * Untag acconts.
   * 
   * @param accountIndices are the indices of the accounts to untag
   */
  public void untagAccounts(Collection<Integer> accountIndices);

  /**
   * Return all account tags.
   * 
   * @return the wallet's account tags
   */
  public List<MoneroAccountTag> getAccountTags();

  /**
   * Sets a human-readable description for a tag.
   * 
   * @param tag is the tag to set a description for
   * @param label is the label to set for the tag
   */
  public void setAccountTagLabel(String tag, String label);
  
  /**
   * Creates a payment URI from a send configuration.
   * 
   * @param request specifies configuration for a potential tx
   * @return the payment uri
   */
  public String createPaymentUri(MoneroSendRequest request);
  
  /**
   * Parses a payment URI to a send request.
   * 
   * @param uri is the payment uri to parse
   * @return the send configuration parsed from the uri
   */
  public MoneroSendRequest parsePaymentUri(String uri);
  
  /**
   * Get an attribute.
   * 
   * @param key is the attribute to get the value of
   * @return the attribute's value
   */
  public String getAttribute(String key);
  
  /**
   * Set an arbitrary attribute.
   * 
   * @param key is the attribute key
   * @param val is the attribute value
   */
  public void setAttribute(String key, String val);
  
  /**
   * Start mining.
   * 
   * @param numThreads is the number of threads created for mining (optional)
   * @param backgroundMining specifies if mining should occur in the background (optional)
   * @param ignoreBattery specifies if the battery should be ignored for mining (optional)
   */
  public void startMining(Long numThreads, Boolean backgroundMining, Boolean ignoreBattery);
  
  /**
   * Stop mining.
   */
  public void stopMining();
  
  /**
   * Indicates if importing multisig data is needed for returning a correct balance.
   * 
   * @return true if importing multisig data is needed for returning a correct balance, false otherwise
   */
  public boolean isMultisigImportNeeded();
  
  /**
   * Indicates if this wallet is a multisig wallet.
   * 
   * @return true if this is a multisig wallet, false otherwise
   */
  public boolean isMultisig();
  
  /**
   * Get multisig info about this wallet.
   * 
   * @return multisig info about this wallet
   */
  public MoneroMultisigInfo getMultisigInfo();
  
  /**
   * Get multisig info as hex to share with participants to begin creating a
   * multisig wallet.
   * 
   * @return this wallet's multisig hex to share with participants
   */
  public String prepareMultisig();
  
  /**
   * Make this wallet multisig by importing multisig hex from participants.
   * 
   * @param multisigHexes are multisig hex from each participant
   * @param threshold is the number of signatures needed to sign transfers
   * @param password is the wallet password
   * @return the result which has the multisig's address xor this wallet's multisig hex to share with participants iff not N/N
   */
  public MoneroMultisigInitResult makeMultisig(List<String> multisigHexes, int threshold, String password);
  
  /**
   * Exchange multisig hex with participants in a M/N multisig wallet.
   * 
   * This process must be repeated with participants exactly N-M times.
   * 
   * @param multisigHexes are multisig hex from each participant
   * @param password is the wallet's password // TODO monero core: redundant? wallet is created with password
   * @return the result which has the multisig's address xor this wallet's multisig hex to share with participants iff not done
   */
  public MoneroMultisigInitResult exchangeMultisigKeys(List<String> multisigHexes, String password);
  
  /**
   * Export this wallet's multisig info as hex for other participants.
   * 
   * @return this wallet's multisig info as hex for other participants
   */
  public String getMultisigHex();
  
  /**
   * Import multisig info as hex from other participants.
   * 
   * @param multisigHexes are multisig hex from each participant
   * @return the number of outputs signed with the given multisig hex
   */
  public int importMultisigHex(List<String> multisigHexes);
  
  /**
   * Sign previously created multisig transactions as represented by hex.
   * 
   * @param multisigTxHex is the hex shared among the multisig transactions when they were created
   * @return the result of signing the multisig transactions
   */
  public MoneroMultisigSignResult signMultisigTxHex(String multisigTxHex);
  
  /**
   * Submit signed multisig transactions as represented by a hex string.
   * 
   * @param signedMultisigTxHex is the signed multisig hex returned from signMultisigTxs()
   * @return the resulting transaction ids
   */
  public List<String> submitMultisigTxHex(String signedMultisigTxHex);

  /**
   * Save the wallet at its current path.
   */
  public void save();
  
  /**
   * Close the wallet (does not save).
   */
  public void close();
  
  /**
   * Optionally save then close the wallet.
   *
   * @param save specifies if the wallet should be saved before being closed (default false)
   */
  public void close(boolean save);
}