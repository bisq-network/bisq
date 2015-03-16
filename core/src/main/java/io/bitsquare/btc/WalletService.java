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
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.persistence.Persistence;

import org.bitcoinj.core.AbstractWalletEventListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DownloadListener;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletEventListener;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.Service;

import java.io.File;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;

import javax.inject.Inject;
import javax.inject.Named;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final String LOCK_NAME = "lock";

    public static final String DIR_KEY = "wallet.dir";
    public static final String PREFIX_KEY = "wallet.prefix";

    private final List<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArrayList<>();
    private final List<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArrayList<>();
    private final List<BalanceListener> balanceListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock lock = Threading.lock(LOCK_NAME);

    private final ObservableDownloadListener downloadListener = new ObservableDownloadListener();
    private final Observable<Double> downloadProgress = downloadListener.getObservable();
    private final WalletEventListener walletEventListener = new BitsquareWalletEventListener();

    private final NetworkParameters params;
    private final FeePolicy feePolicy;
    private final SignatureService signatureService;
    private final Persistence persistence;
    private final File walletDir;
    private final String walletPrefix;
    private final UserAgent userAgent;

    private WalletAppKit walletAppKit;
    private Wallet wallet;
    private AddressEntry registrationAddressEntry;
    private AddressEntry arbitratorDepositAddressEntry;
    private @GuardedBy(LOCK_NAME) List<AddressEntry> addressEntryList = new ArrayList<>();

    private TradeService tradeService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletService(BitcoinNetwork bitcoinNetwork, FeePolicy feePolicy, SignatureService signatureService,
                         Persistence persistence, UserAgent userAgent,
                         @Named(DIR_KEY) File walletDir, @Named(PREFIX_KEY) String walletPrefix) {
        this.params = bitcoinNetwork.getParameters();
        this.feePolicy = feePolicy;
        this.signatureService = signatureService;
        this.persistence = persistence;
        this.walletDir = walletDir;
        this.walletPrefix = walletPrefix;
        this.userAgent = userAgent;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Observable<Object> initialize(Executor executor) {
        Subject<Object, Object> status = BehaviorSubject.create();

        // Tell bitcoinj to execute event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = executor;

        // If seed is non-null it means we are restoring from backup.
        walletAppKit = new WalletAppKit(params, walletDir, walletPrefix) {
            @Override
            protected void onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                walletAppKit.wallet().allowSpendingUnconfirmedTransactions();
                if (params != RegTestParams.get())
                    walletAppKit.peerGroup().setMaxConnections(11);
                walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.00001);
                initWallet();

                tradeService = new TradeService(params, wallet, walletAppKit, feePolicy);

                status.onCompleted();
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
                .setUserAgent(userAgent.getName(), userAgent.getVersion());

        /*
        // TODO restore from DeterministicSeed
        if (seed != null)
            walletAppKit.restoreWalletFromSeed(seed);
            */

        walletAppKit.addListener(new Service.Listener() {
            @Override
            public void failed(@NotNull Service.State from, @NotNull Throwable failure) {
                walletAppKit = null;
                status.onError(failure);
            }
        }, Threading.USER_THREAD);
        walletAppKit.startAsync();

        return status.mergeWith(downloadProgress).timeout(30, TimeUnit.SECONDS);
    }

    private void initWallet() {
        wallet = walletAppKit.wallet();
        wallet.addEventListener(walletEventListener);

        Serializable serializable = persistence.read(this, "addressEntryList");
        if (serializable instanceof List<?>) {
            List<AddressEntry> persistedAddressEntryList = (List<AddressEntry>) serializable;
            for (AddressEntry persistedAddressEntry : persistedAddressEntryList) {
                persistedAddressEntry.setDeterministicKey((DeterministicKey) wallet.findKeyFromPubHash(persistedAddressEntry.getPubKeyHash()));
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
        if (wallet != null)
            wallet.removeEventListener(walletEventListener);
        if (walletAppKit != null)
            walletAppKit.stopAsync();
    }

    public Observable<Double> getDownloadProgress() {
        return downloadProgress;
    }

    public Wallet getWallet() {
        return wallet;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    public AddressEntry getAddressEntry(String offerId) {
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
        DeterministicKey key = wallet.freshReceiveKey();
        AddressEntry addressEntry = new AddressEntry(key, params, addressContext, offerId);
        addressEntryList.add(addressEntry);
        saveAddressInfoList();
        lock.unlock();
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

    private TransactionConfidence getTransactionConfidence(Transaction tx, Address address) {
        List<TransactionOutput> mergedOutputs = getOutputsWithConnectedOutputs(tx);
        List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();

        mergedOutputs.stream().filter(e -> e.getScriptPubKey().isSentToAddress() ||
                e.getScriptPubKey().isPayToScriptHash()).forEach(transactionOutput -> {
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


    @SuppressWarnings("UnusedDeclaration")
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
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address))
                    balance = balance.add(transactionOutput.getValue());
            }
        }
        return balance;
    }

    Coin getWalletBalance() {
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED);
    }

    Coin getRegistrationBalance() {
        return getBalanceForAddress(getRegistrationAddressEntry().getAddress());
    }

    public Coin getArbitratorDepositBalance() {
        return getBalanceForAddress(getArbitratorDepositAddressEntry().getAddress());
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isRegistrationFeeBalanceNonZero() {
        return getRegistrationBalance().compareTo(Coin.ZERO) > 0;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isRegistrationFeeBalanceSufficient() {
        return getRegistrationBalance().compareTo(FeePolicy.REGISTRATION_FEE) >= 0;
    }

    //TODO
    @SuppressWarnings("SameReturnValue")
    public int getNumOfPeersSeenTx(String txId) {
        // TODO check from blockchain
        // will be async
        return 3;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Transactions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeService getTradeService() {
        return tradeService;
    }

    public void payRegistrationFee(String stringifiedBankAccounts, FutureCallback<Transaction> callback) throws
            InsufficientMoneyException {
        log.debug("payRegistrationFee");
        log.trace("stringifiedBankAccounts " + stringifiedBankAccounts);

        Transaction tx = new Transaction(params);

        byte[] data = signatureService.digestMessageWithSignature(getRegistrationAddressEntry().getKeyPair(), stringifiedBankAccounts);
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
        printTxWithInputs("payRegistrationFee", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String sendFunds(String withdrawFromAddress,
                            String withdrawToAddress,
                            Coin amount,
                            FutureCallback<Transaction> callback) throws AddressFormatException, InsufficientMoneyException, IllegalArgumentException {
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

        printTxWithInputs("sendFunds", tx);
        log.debug("tx=" + tx);

        return tx.getHashAsString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveAddressInfoList() {
        lock.lock();
        try {
            persistence.write(this, "addressEntryList", addressEntryList);
        } finally {
            lock.unlock();
        }
    }

    private static void printTxWithInputs(String tracePrefix, Transaction tx) {
        log.trace(tracePrefix + ": " + tx.toString());
        for (TransactionInput input : tx.getInputs()) {
            if (input.getConnectedOutput() != null)
                log.trace(tracePrefix + " input value: " + input.getConnectedOutput().getValue().toFriendlyString());
            else
                log.trace(tracePrefix + ": Transaction already has inputs but we don't have the connected outputs, so we don't know the value.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static class ObservableDownloadListener extends DownloadListener {

        private final Subject<Double, Double> subject = BehaviorSubject.create(0d);

        @Override
        protected void progress(double percentage, int blocksLeft, Date date) {
            super.progress(percentage, blocksLeft, date);
            subject.onNext(percentage);
        }

        @Override
        protected void doneDownload() {
            super.doneDownload();
            subject.onCompleted();
        }

        public Observable<Double> getObservable() {
            return subject.asObservable();
        }
    }


    private class BitsquareWalletEventListener extends AbstractWalletEventListener {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners();
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners();
        }

        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            for (AddressConfidenceListener addressConfidenceListener : addressConfidenceListeners) {
                List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();
                transactionConfidenceList.add(getTransactionConfidence(tx, addressConfidenceListener.getAddress()));

                TransactionConfidence transactionConfidence = getMostRecentConfidence(transactionConfidenceList);
                addressConfidenceListener.onTransactionConfidenceChanged(transactionConfidence);
            }

            txConfidenceListeners.stream()
                    .filter(txConfidenceListener -> tx.getHashAsString().equals(txConfidenceListener.getTxID()))
                    .forEach(txConfidenceListener ->
                            txConfidenceListener.onTransactionConfidenceChanged(tx.getConfidence()));
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
    }
}
