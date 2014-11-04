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

package io.bitsquare.btc;

import io.bitsquare.btc.listeners.AddressConfidenceListener;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.persistence.Persistence;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletEventListener;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;

import java.io.Serializable;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.application.Platform;
import javafx.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lighthouse.files.AppDirectory;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

/**
 * TODO: use walletextension (with protobuffer) instead of saving addressEntryList via storage
 * TODO: break that class up. maybe a bitsquarewallet
 * Wait until steve's akka version to see how to continue here
 */
public class WalletFacade {
    private static final Logger log = LoggerFactory.getLogger(WalletFacade.class);

    private final ReentrantLock lock = Threading.lock("lock");
    private final NetworkParameters params;
    private WalletAppKit walletAppKit;
    private final FeePolicy feePolicy;
    private final CryptoFacade cryptoFacade;
    private final Persistence persistence;
    private final String appName;
    //  private final List<DownloadListener> downloadListeners = new CopyOnWriteArrayList<>();
    private final List<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArrayList<>();
    private final List<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArrayList<>();
    private final List<BalanceListener> balanceListeners = new CopyOnWriteArrayList<>();
    private Wallet wallet;
    private WalletEventListener walletEventListener;
    private AddressEntry registrationAddressEntry;
    private AddressEntry arbitratorDepositAddressEntry;

    @GuardedBy("lock")
    private List<AddressEntry> addressEntryList = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletFacade(NetworkParameters params, FeePolicy feePolicy, CryptoFacade cryptoFacade,
                        Persistence persistence, @Named("appName") String appName) {
        this.params = params;
        this.feePolicy = feePolicy;
        this.cryptoFacade = cryptoFacade;
        this.persistence = persistence;
        this.appName = appName;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(org.bitcoinj.core.DownloadListener downloadListener, StartupListener startupListener) {
        // Tell bitcoinj to execute event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;

        // If seed is non-null it means we are restoring from backup.
        walletAppKit = new WalletAppKit(params, AppDirectory.dir().toFile(), appName) {
            @Override
            protected void onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                walletAppKit.wallet().allowSpendingUnconfirmedTransactions();
                if (params != RegTestParams.get())
                    walletAppKit.peerGroup().setMaxConnections(11);
                walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.00001);
                initWallet();
                Platform.runLater(startupListener::completed);
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        }
        else if (params == MainNetParams.get()) {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            try {
                walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints"));
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e.toString());
            }
            // As an example!
            // walletAppKit.useTor();
        }
        else if (params == TestNet3Params.get()) {
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints.testnet"));
            //walletAppKit.useTor();
        }
        walletAppKit.setDownloadListener(downloadListener)
                .setBlockingStartup(false)
                .setUserAgent("Bitsquare", "0.1");

        /*
        // TODO restore from DeterministicSeed
        if (seed != null)
            walletAppKit.restoreWalletFromSeed(seed);
            */

        walletAppKit.addListener(new Service.Listener() {
            @Override
            public void failed(Service.State from, Throwable failure) {
                walletAppKit = null;
                // TODO show error popup
                //crashAlert(failure);
            }
        }, Threading.SAME_THREAD);
        walletAppKit.startAsync();
    }

    private void initWallet() {
        wallet = walletAppKit.wallet();

        //walletAppKit.peerGroup().setMaxConnections(11);

       /* if (params == RegTestParams.get())
            walletAppKit.peerGroup().setMinBroadcastConnections(1);
        else
            walletAppKit.peerGroup().setMinBroadcastConnections(3);*/


        walletEventListener = new WalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                notifyBalanceListeners();
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                notifyBalanceListeners();
            }

            @Override
            public void onReorganize(Wallet wallet) {

            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                notifyConfidenceListeners(tx);
            }

            @Override
            public void onWalletChanged(Wallet wallet) {

            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts) {

            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {

            }
        };
        wallet.addEventListener(walletEventListener);

        Serializable serializable = persistence.read(this, "addressEntryList");
        if (serializable instanceof List) {
            List<AddressEntry> persistedAddressEntryList = (List<AddressEntry>) serializable;
            for (AddressEntry persistedAddressEntry : persistedAddressEntryList) {
                persistedAddressEntry.setDeterministicKey(
                        (DeterministicKey) wallet.findKeyFromPubHash(persistedAddressEntry.getPubKeyHash()));
            }
            addressEntryList = persistedAddressEntryList;
            registrationAddressEntry = addressEntryList.get(0);
        }
        else {
            // First time
            lock.lock();
            DeterministicKey registrationKey = wallet.currentReceiveKey();
            registrationAddressEntry = new AddressEntry(registrationKey, params,
                    AddressEntry.AddressContext.REGISTRATION_FEE);
            addressEntryList.add(registrationAddressEntry);
            lock.unlock();
            saveAddressInfoList();
        }
    }

    public void shutDown() {
        wallet.removeEventListener(walletEventListener);
        walletAppKit.stopAsync();
    }

    public Wallet getWallet() {
        return wallet;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

 /*   public DownloadListener addDownloadListener(DownloadListener listener) {
        downloadListeners.add(listener);
        return listener;
    }

    public void removeDownloadListener(DownloadListener listener) {
        downloadListeners.remove(listener);
    }*/

    public AddressConfidenceListener addAddressConfidenceListener(AddressConfidenceListener listener) {
        addressConfidenceListeners.add(listener);
        return listener;
    }

    public void removeAddressConfidenceListener(AddressConfidenceListener listener) {
        addressConfidenceListeners.remove(listener);
    }

    public TxConfidenceListener addTxConfidenceListener(TxConfidenceListener listener) {
        txConfidenceListeners.add(listener);
        return listener;
    }

    public void removeTxConfidenceListener(TxConfidenceListener listener) {
        txConfidenceListeners.remove(listener);
    }

    public BalanceListener addBalanceListener(BalanceListener listener) {
        balanceListeners.add(listener);
        return listener;
    }

    public void removeBalanceListener(BalanceListener listener) {
        balanceListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get AddressInfo objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<AddressEntry> getAddressEntryList() {
        return ImmutableList.copyOf(addressEntryList);
    }

    public AddressEntry getRegistrationAddressEntry() {
        return registrationAddressEntry;
    }

    public AddressEntry getArbitratorDepositAddressEntry() {
        if (arbitratorDepositAddressEntry == null)
            arbitratorDepositAddressEntry = getNewAddressEntry(AddressEntry.AddressContext.ARBITRATOR_DEPOSIT, null);

        return arbitratorDepositAddressEntry;
    }

    public AddressEntry getAddressInfoByTradeID(String offerId) {
        Optional<AddressEntry> addressEntry = getAddressEntryList().stream().filter(e ->
                offerId.equals(e.getOfferId())).findFirst();

        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return getNewAddressEntry(AddressEntry.AddressContext.TRADE, offerId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create new AddressInfo objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AddressEntry getNewAddressEntry(AddressEntry.AddressContext addressContext, String offerId) {
        lock.lock();
        wallet.getLock().lock();
        DeterministicKey key = wallet.freshReceiveKey();
        AddressEntry addressEntry = new AddressEntry(key, params, addressContext, offerId);
        addressEntryList.add(addressEntry);
        saveAddressInfoList();
        lock.unlock();
        wallet.getLock().unlock();
        return addressEntry;
    }

    private Optional<AddressEntry> getAddressEntryByAddressString(String address) {
        return getAddressEntryList().stream().filter(e -> address.equals(e.getAddressString())).findFirst();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TransactionConfidence
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TransactionConfidence getConfidenceForAddress(Address address) {
        List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();
        if (wallet != null) {
            Set<Transaction> transactions = wallet.getTransactions(true);
            if (transactions != null) {
                transactionConfidenceList.addAll(transactions.stream().map(tx ->
                        getTransactionConfidence(tx, address)).collect(Collectors.toList()));
            }
        }
        return getMostRecentConfidence(transactionConfidenceList);
    }

    public TransactionConfidence getConfidenceForTxId(String txId) {
        if (wallet != null) {
            Set<Transaction> transactions = wallet.getTransactions(true);
            for (Transaction tx : transactions) {
                if (tx.getHashAsString().equals(txId))
                    return tx.getConfidence();
            }
        }
        return null;
    }

    private void notifyConfidenceListeners(Transaction tx) {
        for (AddressConfidenceListener addressConfidenceListener : addressConfidenceListeners) {
            List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();
            transactionConfidenceList.add(getTransactionConfidence(tx, addressConfidenceListener.getAddress()));

            TransactionConfidence transactionConfidence = getMostRecentConfidence(transactionConfidenceList);
            addressConfidenceListener.onTransactionConfidenceChanged(transactionConfidence);
        }

        txConfidenceListeners.stream().filter(txConfidenceListener -> tx.getHashAsString().equals
                (txConfidenceListener.getTxID())).forEach(txConfidenceListener -> txConfidenceListener
                .onTransactionConfidenceChanged(tx.getConfidence()));
    }

    private TransactionConfidence getTransactionConfidence(Transaction tx, Address address) {
        List<TransactionOutput> mergedOutputs = getOutputsWithConnectedOutputs(tx);
        List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();

        mergedOutputs.stream().filter(e -> e.getScriptPubKey().isSentToAddress() ||
                e.getScriptPubKey().isSentToP2SH()).forEach(transactionOutput -> {
            Address outputAddress = transactionOutput.getScriptPubKey().getToAddress(params);
            if (address.equals(outputAddress)) {
                transactionConfidenceList.add(tx.getConfidence());
            }
        });
        return getMostRecentConfidence(transactionConfidenceList);
    }


    private List<TransactionOutput> getOutputsWithConnectedOutputs(Transaction tx) {
        List<TransactionOutput> transactionOutputs = tx.getOutputs();
        List<TransactionOutput> connectedOutputs = new ArrayList<>();

        // add all connected outputs from any inputs as well
        List<TransactionInput> transactionInputs = tx.getInputs();
        for (TransactionInput transactionInput : transactionInputs) {
            TransactionOutput transactionOutput = transactionInput.getConnectedOutput();
            if (transactionOutput != null) {
                connectedOutputs.add(transactionOutput);
            }
        }

        List<TransactionOutput> mergedOutputs = new ArrayList<>();
        mergedOutputs.addAll(transactionOutputs);
        mergedOutputs.addAll(connectedOutputs);
        return mergedOutputs;
    }


    private TransactionConfidence getMostRecentConfidence(List<TransactionConfidence> transactionConfidenceList) {
        TransactionConfidence transactionConfidence = null;
        for (TransactionConfidence confidence : transactionConfidenceList) {
            if (confidence != null) {
                if (transactionConfidence == null ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING) ||
                        (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) &&
                                transactionConfidence.getConfidenceType().equals(
                                        TransactionConfidence.ConfidenceType.BUILDING) &&
                                confidence.getDepthInBlocks() < transactionConfidence.getDepthInBlocks())) {
                    transactionConfidence = confidence;
                }
            }

        }
        return transactionConfidence;
    }


    public boolean isRegistrationFeeConfirmed() {
        TransactionConfidence transactionConfidence = null;
        if (getRegistrationAddressEntry() != null) {
            transactionConfidence = getConfidenceForAddress(getRegistrationAddressEntry().getAddress());
        }
        return transactionConfidence != null &&
                transactionConfidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getBalanceForAddress(Address address) {
        return wallet != null ? getBalance(wallet.calculateAllSpendCandidates(true), address) : Coin.ZERO;
    }

    private Coin getBalance(LinkedList<TransactionOutput> transactionOutputs, Address address) {
        Coin balance = Coin.ZERO;
        for (TransactionOutput transactionOutput : transactionOutputs) {
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey()
                    .isSentToP2SH()) {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address)) {
                    balance = balance.add(transactionOutput.getValue());
                }
            }
        }
        return balance;
    }

    private void notifyBalanceListeners() {
        for (BalanceListener balanceListener : balanceListeners) {
            Coin balance;
            if (balanceListener.getAddress() != null)
                balance = getBalanceForAddress(balanceListener.getAddress());
            else
                balance = getWalletBalance();

            balanceListener.onBalanceChanged(balance);
        }
    }

    public Coin getWalletBalance() {
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED);
    }

    Coin getRegistrationBalance() {
        return getBalanceForAddress(getRegistrationAddressEntry().getAddress());
    }

    public Coin getArbitratorDepositBalance() {
        return getBalanceForAddress(getArbitratorDepositAddressEntry().getAddress());
    }

    public boolean isRegistrationFeeBalanceNonZero() {
        return getRegistrationBalance().compareTo(Coin.ZERO) > 0;
    }

    public boolean isRegistrationFeeBalanceSufficient() {
        return getRegistrationBalance().compareTo(FeePolicy.REGISTRATION_FEE) >= 0;
    }

    //TODO
    public int getNumOfPeersSeenTx(String txID) {
        // TODO check from blockchain
        // will be async
        return 3;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Transactions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void payRegistrationFee(String stringifiedBankAccounts, FutureCallback<Transaction> callback) throws
            InsufficientMoneyException {
        log.debug("payRegistrationFee");
        log.trace("stringifiedBankAccounts " + stringifiedBankAccounts);

        Transaction tx = new Transaction(params);

        byte[] data = cryptoFacade.getEmbeddedAccountRegistrationData(
                getRegistrationAddressEntry().getKey(), stringifiedBankAccounts);
        tx.addOutput(Transaction.MIN_NONDUST_OUTPUT, new ScriptBuilder().op(OP_RETURN).data(data).build());

        // We don't take a fee at the moment
        // 0.0000454 BTC will get extra to miners as it is lower then durst
       /* Coin fee = FeePolicy.REGISTRATION_FEE
                .subtract(Transaction.MIN_NONDUST_OUTPUT)
                .subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + fee.toFriendlyString());
        tx.addOutput(fee, feePolicy.getAddressForRegistrationFee());*/

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;

        // We accept at the moment registration fee payment with 0 confirmations.
        // The verification will be done at the end of the trade process again, and then a double spend would be
        // detected and lead to arbitration.
        // The last param (boolean includePending) is used for indicating that we accept 0 conf tx.
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getRegistrationAddressEntry(), true);
        sendRequest.changeAddress = getRegistrationAddressEntry().getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        log.debug("Registration transaction: " + tx);
        printInputs("payRegistrationFee", tx);
    }

    public Transaction createOfferFeeTx(String offerId) throws InsufficientMoneyException {
        log.trace("createOfferFeeTx");
        Transaction tx = new Transaction(params);
        Coin fee = FeePolicy.CREATE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + fee.toFriendlyString());
        tx.addOutput(fee, feePolicy.getAddressForCreateOfferFee());
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getAddressInfoByTradeID(offerId), true);
        sendRequest.changeAddress = getAddressInfoByTradeID(offerId).getAddress();
        wallet.completeTx(sendRequest);
        printInputs("payCreateOfferFee", tx);
        return tx;
    }

    public void broadcastCreateOfferFeeTx(Transaction tx, FutureCallback<Transaction> callback) {
        log.trace("broadcast tx");
        ListenableFuture<Transaction> future = walletAppKit.peerGroup().broadcastTransaction(tx);
        Futures.addCallback(future, callback);
    }

    public String payTakeOfferFee(String offerId, FutureCallback<Transaction> callback) throws
            InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        Coin fee = FeePolicy.TAKE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + fee.toFriendlyString());
        tx.addOutput(fee, feePolicy.getAddressForTakeOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getAddressInfoByTradeID(offerId), true);
        sendRequest.changeAddress = getAddressInfoByTradeID(offerId).getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printInputs("payTakeOfferFee", tx);
        log.debug("tx=" + tx);

        return tx.getHashAsString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String sendFunds(String withdrawFromAddress,
                            String withdrawToAddress,
                            Coin amount,
                            FutureCallback<Transaction> callback) throws AddressFormatException,
            InsufficientMoneyException, IllegalArgumentException {
        Transaction tx = new Transaction(params);
        tx.addOutput(amount.subtract(FeePolicy.TX_FEE), new Address(params, withdrawToAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)

        Optional<AddressEntry> addressEntry = getAddressEntryByAddressString(withdrawFromAddress);
        if (!addressEntry.isPresent())
            throw new IllegalArgumentException("WithdrawFromAddress is not found in our wallets.");

        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry.get(), true);
        sendRequest.changeAddress = addressEntry.get().getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printInputs("sendFunds", tx);
        log.debug("tx=" + tx);

        return tx.getHashAsString();
    }


    // TODO: Trade process - use P2SH instead and optimize tx creation and data exchange

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 1. step: deposit tx
    // Offerer creates the 2of3 multiSig deposit tx with his unsigned input and change output

    public Transaction offererCreatesMSTxAndAddPayment(Coin offererInputAmount,
                                                       String offererPubKey,
                                                       String takerPubKey,
                                                       String arbitratorPubKey,
                                                       String tradeId) throws InsufficientMoneyException {
        log.debug("offererCreatesMSTxAndAddPayment");
        log.trace("inputs: ");
        log.trace("offererInputAmount=" + offererInputAmount.toFriendlyString());
        log.trace("offererPubKey=" + offererPubKey);
        log.trace("takerPubKey=" + takerPubKey);
        log.trace("arbitratorPubKey=" + arbitratorPubKey);
        log.trace("offererInputAmount=" + offererInputAmount.toFriendlyString());

        // we need to subtract the fee as it will go to the miners
        Coin amountToPay = offererInputAmount.subtract(FeePolicy.TX_FEE);
        log.trace("amountToPay=" + amountToPay.toFriendlyString());

        // We pay the offererInputAmount to a temporary MS output which will be changed later to the correct value.
        // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
        // Later with more customized coin selection we can use a custom CoinSelector implementation.
        // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
        // So it will not change the wallet balance.
        // The btc tx fee will be included by the completeTx() call, so we don't need to add it manually.
        Transaction tx = new Transaction(params);
        Script multiSigOutputScript = getMultiSigScript(offererPubKey, takerPubKey, arbitratorPubKey);
        tx.addOutput(amountToPay, multiSigOutputScript);

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;
        AddressEntry addressEntry = getAddressInfoByTradeID(tradeId);
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        wallet.completeTx(sendRequest);

        // The completeTx() call signs the input, but we don't want to pass over a signed tx so we remove the
        // signature to make sure the tx is invalid for publishing
        tx.getInput(0).setScriptSig(new Script(new byte[]{}));

        log.trace("verify tx");
        tx.verify();

        // The created tx looks like:
         /*
        IN[0]  any input > offererInputAmount + fee (unsigned)
        OUT[0] MS offererInputAmount
        OUT[1] Change = input - offererInputAmount - fee
               btc tx fee
         */

        log.trace("Check if wallet is consistent: result=" + wallet.isConsistent());
        printInputs("offererCreatesMSTxAndAddPayment", tx);
        log.debug("tx = " + tx);
        return tx;
    }

    // 2. step: deposit tx
    // Taker adds his input and change output, changes the multiSig amount to the correct value and sign his input

    public Transaction takerAddPaymentAndSignTx(Coin takerInputAmount,
                                                Coin msOutputAmount,
                                                String offererPubKey,
                                                String takerPubKey,
                                                String arbitratorPubKey,
                                                String offerersPartialDepositTxAsHex,
                                                String tradeId) throws InsufficientMoneyException {
        log.debug("takerAddPaymentAndSignTx");
        log.trace("inputs: ");
        log.trace("takerInputAmount=" + takerInputAmount.toFriendlyString());
        log.trace("msOutputAmount=" + msOutputAmount.toFriendlyString());
        log.trace("offererPubKey=" + offererPubKey);
        log.trace("takerPubKey=" + takerPubKey);
        log.trace("arbitratorPubKey=" + arbitratorPubKey);
        log.trace("offerersPartialDepositTxAsHex=" + offerersPartialDepositTxAsHex);

        // We pay the btc tx fee 2 times to the deposit tx:
        // 1. will be spent to miners when publishing the deposit tx
        // 2. will be as added to the MS amount, so when spending the payout tx the fee is already there and the
        // outputs are not changed by fee reduction
        // Both traders pay 1 times a fee, so it is equally split between them

        // We do exactly the same as in the 1. step but with the takers input.
        Transaction tempTx = new Transaction(params);
        Script multiSigOutputScript = getMultiSigScript(offererPubKey, takerPubKey, arbitratorPubKey);
        tempTx.addOutput(takerInputAmount, multiSigOutputScript);

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tempTx);
        sendRequest.shuffleOutputs = false;
        AddressEntry addressEntry = getAddressInfoByTradeID(tradeId);
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        wallet.completeTx(sendRequest);

        printInputs("tempTx", tempTx);
        log.trace("tempTx=" + tempTx);
        // That tx has signed input, but we don't need to remove it as we don't send that tx out,
        // it is just used temporary.

        // The created tempTx looks like:
         /*
        IN[0]  any input taker > takerInputAmount + fee (signed)
        OUT[0] MS takerInputAmount
        OUT[1] Change = input taker - takerInputAmount - fee
               btc tx fee
         */


        // Now we construct the real 2of3 multiSig tx from the serialized offerers tx
        Transaction tx = new Transaction(params, Utils.parseAsHexOrBase58(offerersPartialDepositTxAsHex));
        log.trace("offerersPartialDepositTx=" + tx);

        // The serialized offerers tx looks like:
        /*
        IN[0]  any input offerer > offererInputAmount + fee (unsigned)
        OUT[0] MS offererInputAmount
        OUT[1] Change = input offerer - offererInputAmount - fee
               btc tx fee
        */

        // Now we add the inputs and outputs from our temp tx and change the multiSig amount to the correct value
        tx.addInput(tempTx.getInput(0));
        if (tempTx.getOutputs().size() == 2) {
            tx.addOutput(tempTx.getOutput(1));
        }

        // We add the btc tx fee to the msOutputAmount and apply the change to the multiSig output
        msOutputAmount = msOutputAmount.add(FeePolicy.TX_FEE);
        tx.getOutput(0).setValue(msOutputAmount);

        // Now we sign our input
        TransactionInput input = tx.getInput(1);
        if (input == null || input.getConnectedOutput() == null) {
            log.error("input or input.getConnectedOutput() is null: " + input);
        }

        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        Sha256Hash hash = tx.hashForSignature(1, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature ecSig = sigKey.sign(hash);
        TransactionSignature txSig = new TransactionSignature(ecSig, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        }
        else if (scriptPubKey.isSentToAddress()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        }
        else {
            throw new ScriptException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);
        }

        log.trace("check if it can be correctly spent for input 1");
        input.getScriptSig().correctlySpends(tx, 1, scriptPubKey);

        log.trace("verify tx");
        tx.verify();

        // The resulting tx looks like:
        /*
        IN[0]  any input offerer > offererInputAmount + fee (unsigned)  e.g.: 0.1001
        IN[1]  any input taker > takerInputAmount + fee (signed)  e.g.: 1.1001
        OUT[0] MS offererInputAmount  e.g.: 1.2001
        OUT[1] Change = input offerer - offererInputAmount - fee   e.g.: 0 if input is matching correct value
        OUT[2] Change = input taker - takerInputAmount - fee   e.g.: 0 if input is matching correct value
               btc tx fee   e.g.: 0.1001
        */

        // We must not commit that tx to the wallet as we will get it over the network when the offerer
        // publishes it and it will have a different tx hash, so it would invalidate our wallet.

        log.trace("Check if wallet is consistent before commit: result=" + wallet.isConsistent());
        printInputs("takerAddPaymentAndSignTx", tx);
        log.debug("tx = " + tx);
        return tx;
    }


    // 3. step: deposit tx
    // Offerer signs tx and publishes it

    public void offererSignAndPublishTx(String offerersFirstTxAsHex,
                                        String takersSignedTxAsHex,
                                        String takersSignedConnOutAsHex,
                                        String takersSignedScriptSigAsHex,
                                        long offererTxOutIndex,
                                        long takerTxOutIndex,
                                        FutureCallback<Transaction> callback) {
        log.debug("offererSignAndPublishTx");
        log.trace("inputs: ");
        log.trace("offerersFirstTxAsHex=" + offerersFirstTxAsHex);
        log.trace("takersSignedTxAsHex=" + takersSignedTxAsHex);
        log.trace("takersSignedConnOutAsHex=" + takersSignedConnOutAsHex);
        log.trace("takersSignedScriptSigAsHex=" + takersSignedScriptSigAsHex);
        log.trace("callback=" + callback);

        // We create an empty tx (did not find a way to manipulate a tx input, otherwise the takers tx could be used
        // directly and add the offerers input and output)
        Transaction tx = new Transaction(params);

        // offerers first tx
        Transaction offerersFirstTx = new Transaction(params, Utils.parseAsHexOrBase58(offerersFirstTxAsHex));

        printInputs("offerersFirstTx", offerersFirstTx);
        log.trace("offerersFirstTx = " + offerersFirstTx);

        // add input
        Transaction offerersFirstTxConnOut = wallet.getTransaction(offerersFirstTx.getInput(0).getOutpoint().getHash());
        TransactionOutPoint offerersFirstTxOutPoint =
                new TransactionOutPoint(params, offererTxOutIndex, offerersFirstTxConnOut);
        //TransactionInput offerersFirstTxInput = new TransactionInput(params, tx,
        // offerersFirstTx.getInput(0).getScriptBytes(), offerersFirstTxOutPoint);   // pass that around!
        // getScriptBytes =
        // empty bytes array
        TransactionInput offerersFirstTxInput = new TransactionInput(params, tx, new byte[]{}, offerersFirstTxOutPoint);
        offerersFirstTxInput.setParent(tx);
        tx.addInput(offerersFirstTxInput);

        // takers signed tx
        Transaction takersSignedTx = new Transaction(params, Utils.parseAsHexOrBase58(takersSignedTxAsHex));

        printInputs("takersSignedTxInput", takersSignedTx);
        log.trace("takersSignedTx = " + takersSignedTx);

        // add input
        Transaction takersSignedTxConnOut = new Transaction(params, Utils.parseAsHexOrBase58(takersSignedConnOutAsHex));
        TransactionOutPoint takersSignedTxOutPoint =
                new TransactionOutPoint(params, takerTxOutIndex, takersSignedTxConnOut);
        TransactionInput takersSignedTxInput = new TransactionInput(
                params, tx, Utils.parseAsHexOrBase58(takersSignedScriptSigAsHex), takersSignedTxOutPoint);
        takersSignedTxInput.setParent(tx);
        tx.addInput(takersSignedTxInput);

        //TODO onResult non change output cases
        // add outputs from takers tx, they are already correct
        tx.addOutput(takersSignedTx.getOutput(0));
        if (takersSignedTx.getOutputs().size() > 1) {
            tx.addOutput(takersSignedTx.getOutput(1));
        }
        if (takersSignedTx.getOutputs().size() == 3) {
            tx.addOutput(takersSignedTx.getOutput(2));
        }

        printInputs("tx", tx);
        log.trace("tx = " + tx);
        log.trace("Wallet balance before signing: " + wallet.getBalance());

        // sign the input
        TransactionInput input = tx.getInput(0);
        if (input == null || input.getConnectedOutput() == null) {
            log.error("input or input.getConnectedOutput() is null: " + input);
        }

        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        Sha256Hash hash = tx.hashForSignature(0, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature ecSig = sigKey.sign(hash);
        TransactionSignature txSig = new TransactionSignature(ecSig, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        }
        else if (scriptPubKey.isSentToAddress()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        }
        else {
            throw new ScriptException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);
        }

        input.getScriptSig().correctlySpends(tx, 0, scriptPubKey);
        log.trace("check if it can be correctly spent for input 0 OK");

        TransactionInput input1 = tx.getInput(1);
        scriptPubKey = input1.getConnectedOutput().getScriptPubKey();
        input1.getScriptSig().correctlySpends(tx, 1, scriptPubKey);
        log.trace("check if it can be correctly spent for input 1 OK");

         /*
        IN[0] offerer signed 0.1001
        IN[1] taker signed  1.1001
        OUT[0] MS (include btc tx fee for payout tx) 1.2001
        OUT[1] offerer change
        OUT[2] taker change
               btc tx fee   0.0001
         */

        log.trace("verify ");
        tx.verify();

        printInputs("tx", tx);
        log.debug("tx = " + tx);

        log.trace("Wallet balance before broadcastTransaction: " + wallet.getBalance());
        log.trace("Check if wallet is consistent before broadcastTransaction: result=" + wallet.isConsistent());
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(tx);
        log.trace("Wallet balance after broadcastTransaction: " + wallet.getBalance());
        log.trace("Check if wallet is consistent: result=" + wallet.isConsistent());

        Futures.addCallback(broadcastComplete, callback);
        printInputs("tx", tx);
        log.debug("tx = " + tx);
    }

    // 4 step deposit tx: Offerer send deposit tx to taker
    public Transaction takerCommitDepositTx(String depositTxAsHex) {
        log.trace("takerCommitDepositTx");
        log.trace("inputs: ");
        log.trace("depositTxID=" + depositTxAsHex);
        Transaction depositTx = new Transaction(params, Utils.parseAsHexOrBase58(depositTxAsHex));
        log.trace("depositTx=" + depositTx);
        // boolean isAlreadyInWallet = wallet.maybeCommitTx(depositTx);
        //log.trace("isAlreadyInWallet=" + isAlreadyInWallet);

        try {
            // Manually add the multisigContract to the wallet, overriding the isRelevant checks so we can track
            // it and check for double-spends later
            wallet.receivePending(depositTx, null, true);
        } catch (VerificationException e) {
            throw new RuntimeException(e); // Cannot happen, we already called multisigContract.verify()
        }

        return depositTx;

    }

    // 5. step payout tx: Offerer creates payout tx and signs it

    public Pair<ECKey.ECDSASignature, String> offererCreatesAndSignsPayoutTx(String depositTxID,
                                                                             Coin offererPaybackAmount,
                                                                             Coin takerPaybackAmount,
                                                                             String takerAddress,
                                                                             String tradeID)
            throws AddressFormatException {
        log.debug("offererCreatesAndSignsPayoutTx");
        log.trace("inputs: ");
        log.trace("depositTxID=" + depositTxID);
        log.trace("offererPaybackAmount=" + offererPaybackAmount.toFriendlyString());
        log.trace("takerPaybackAmount=" + takerPaybackAmount.toFriendlyString());
        log.trace("takerAddress=" + takerAddress);

        // Offerer has published depositTx earlier, so he has it in his wallet
        Transaction depositTx = wallet.getTransaction(new Sha256Hash(depositTxID));
        String depositTxAsHex = Utils.HEX.encode(depositTx.bitcoinSerialize());

        // We create the payout tx
        Transaction tx = createPayoutTx(depositTxAsHex, offererPaybackAmount, takerPaybackAmount,
                getAddressInfoByTradeID(tradeID).getAddressString(), takerAddress);

        // We create the signature for that tx
        TransactionOutput multiSigOutput = tx.getInput(0).getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature offererSignature = getAddressInfoByTradeID(tradeID).getKey().sign(sigHash);

        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig));
        tx.getInput(0).setScriptSig(inputScript);

        log.trace("sigHash=" + sigHash);
        return new Pair<>(offererSignature, depositTxAsHex);
    }

    // 6. step payout tx: Taker signs and publish tx
    public void takerSignsAndSendsTx(String depositTxAsHex,
                                     String offererSignatureR,
                                     String offererSignatureS,
                                     Coin offererPaybackAmount,
                                     Coin takerPaybackAmount,
                                     String offererAddress,
                                     String tradeID,
                                     FutureCallback<Transaction> callback) throws AddressFormatException {
        log.debug("takerSignsAndSendsTx");
        log.trace("inputs: ");
        log.trace("depositTxAsHex=" + depositTxAsHex);
        log.trace("offererSignatureR=" + offererSignatureR);
        log.trace("offererSignatureS=" + offererSignatureS);
        log.trace("offererPaybackAmount=" + offererPaybackAmount.toFriendlyString());
        log.trace("takerPaybackAmount=" + takerPaybackAmount.toFriendlyString());
        log.trace("offererAddress=" + offererAddress);
        log.trace("callback=" + callback);

        // We create the payout tx
        Transaction tx = createPayoutTx(depositTxAsHex, offererPaybackAmount, takerPaybackAmount, offererAddress,
                getAddressInfoByTradeID(tradeID).getAddressString());

        // We sign that tx with our key and apply the signature form the offerer
        TransactionOutput multiSigOutput = tx.getInput(0).getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        log.trace("sigHash=" + sigHash);

        ECKey.ECDSASignature takerSignature = getAddressInfoByTradeID(tradeID).getKey().sign(sigHash);
        TransactionSignature takerTxSig = new TransactionSignature(takerSignature, Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature offererSignature =
                new ECKey.ECDSASignature(new BigInteger(offererSignatureR), new BigInteger(offererSignatureS));
        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);

        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig, takerTxSig));
        tx.getInput(0).setScriptSig(inputScript);

        log.trace("verify tx");
        tx.verify();

        log.trace("check if it can be correctly spent for ms input");
        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, multiSigScript);

        log.trace("verify multiSigOutput");
        tx.getInput(0).verify(multiSigOutput);

        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(tx);
        Futures.addCallback(broadcastComplete, callback);

        log.trace("getTransactions.size=" + wallet.getTransactions(true).size());
        log.trace("Check if wallet is consistent: result=" + wallet.isConsistent());
        printInputs("takerSignsAndSendsTx", tx);
        log.debug("tx = " + tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveAddressInfoList() {
        // use wallet extension?
        lock.lock();
        try {
            persistence.write(this, "addressEntryList", addressEntryList);
        } finally {
            lock.unlock();
        }
    }

    //TODO
    private Script getMultiSigScript(String offererPubKey, String takerPubKey, String arbitratorPubKey) {
        ECKey offererKey = ECKey.fromPublicOnly(Utils.parseAsHexOrBase58(offererPubKey));
        ECKey takerKey = ECKey.fromPublicOnly(Utils.parseAsHexOrBase58(takerPubKey));
        ECKey arbitratorKey = ECKey.fromPublicOnly(Utils.parseAsHexOrBase58(arbitratorPubKey));

        List<ECKey> keys = ImmutableList.of(offererKey, takerKey, arbitratorKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }


    private Transaction createPayoutTx(String depositTxAsHex, Coin offererPaybackAmount, Coin takerPaybackAmount,
                                       String offererAddress, String takerAddress) throws AddressFormatException {
        log.trace("createPayoutTx");
        log.trace("inputs: ");
        log.trace("depositTxAsHex=" + depositTxAsHex);
        log.trace("offererPaybackAmount=" + offererPaybackAmount.toFriendlyString());
        log.trace("takerPaybackAmount=" + takerPaybackAmount.toFriendlyString());
        log.trace("offererAddress=" + offererAddress);
        log.trace("takerAddress=" + takerAddress);

        Transaction depositTx = new Transaction(params, Utils.parseAsHexOrBase58(depositTxAsHex));
        TransactionOutput multiSigOutput = depositTx.getOutput(0);
        Transaction tx = new Transaction(params);
        tx.addInput(multiSigOutput);
        tx.addOutput(offererPaybackAmount, new Address(params, offererAddress));
        tx.addOutput(takerPaybackAmount, new Address(params, takerAddress));
        log.trace("tx=" + tx);
        return tx;
    }

    public static void printInputs(String tracePrefix, Transaction tx) {
        for (TransactionInput input : tx.getInputs()) {
            if (input.getConnectedOutput() != null) {
                log.trace(tracePrefix + " input value : " + input.getConnectedOutput().getValue().toFriendlyString());
            }
            else {
                log.trace(tracePrefix + ": " + "Transaction already has inputs but we don't have the connected " +
                        "outputs, so we don't know the value.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static interface StartupListener {
        void completed();
    }

    public static interface DownloadListener {
        void progress(double percent);

        void downloadComplete();
    }

   /* private class BlockChainDownloadListener extends org.bitcoinj.core.DownloadListener {
        @Override
        protected void progress(double percent, int blocksSoFar, Date date) {
            super.progress(percent, blocksSoFar, date);
            Platform.runLater(() -> onProgressInUserThread(percent));
        }

        @Override
        protected void doneDownload() {
            super.doneDownload();
            Platform.runLater(this::onDoneDownloadInUserThread);
        }

        private void onProgressInUserThread(double percent) {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.progress(percent);
            }
        }

        private void onDoneDownloadInUserThread() {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.downloadComplete();
            }
        }
    }*/

}
