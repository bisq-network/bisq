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

import io.bitsquare.btc.exceptions.SigningException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
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
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
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

import javafx.util.Pair;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

import static com.google.inject.internal.util.$Preconditions.checkState;
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
        if (serializable instanceof List) {
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

    public AddressEntry getAddressInfo(String offerId) {
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

    private void notifyConfidenceListeners(Transaction tx) {
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

        byte[] data = signatureService.digestMessageWithSignature(
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
        AddressEntry addressEntry = getAddressInfo(offerId);
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        wallet.completeTx(sendRequest);
        printInputs("payCreateOfferFee", tx);
        return tx;
    }

    public void broadcastCreateOfferFeeTx(Transaction tx, FutureCallback<Transaction> callback) {
        log.trace("broadcast tx");
        ListenableFuture<Transaction> future = walletAppKit.peerGroup().broadcastTransaction(tx);
        Futures.addCallback(future, callback);
    }

    public String payTakeOfferFee(String offerId, FutureCallback<Transaction> callback) throws InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        Coin fee = FeePolicy.TAKE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + fee.toFriendlyString());
        tx.addOutput(fee, feePolicy.getAddressForTakeOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getAddressInfo(offerId), true);
        sendRequest.changeAddress = getAddressInfo(offerId).getAddress();
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

        printInputs("sendFunds", tx);
        log.debug("tx=" + tx);

        return tx.getHashAsString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 1. step: Define offerers inputs and outputs for the deposit tx
    public TransactionDataResult offererCreatesDepositTxInputs(Coin inputAmount, AddressEntry addressInfo) throws InsufficientMoneyException,
            TransactionVerificationException, WalletException {

        // We pay the tx fee 2 times to the deposit tx:
        // 1. Will be spent when publishing the deposit tx (paid by offerer)
        // 2. Will be added to the MS amount, so when publishing the payout tx the fee is already there and the outputs are not changed by fee reduction
        // The fee for the payout will be paid by the taker.

        // inputAmount includes the tx fee. So we subtract the fee to get the dummyOutputAmount.
        Coin dummyOutputAmount = inputAmount.subtract(FeePolicy.TX_FEE);

        Transaction dummyTX = new Transaction(params);
        // The output is just used to get the right inputs and change outputs, so we use an anonymous ECKey, as it will never be used for anything.
        // We don't care about fee calculation differences between the real tx and that dummy tx as we use a static tx fee.
        TransactionOutput msOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, new ECKey());
        dummyTX.addOutput(msOutput);

        // Fin the needed inputs to pay the output, optional add change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and outputs. Our spending transaction output is from the create
        // offer fee payment. In future changes (in case of no offer fee) multiple inputs might become used.
        addAvailableInputsAndChangeOutputs(dummyTX, addressInfo);

        // The completeTx() call signs the input, but we don't want to pass over signed tx inputs
        // But to be safe and to support future changes (in case of no offer fee) we handle potential multiple inputs
        removeSignatures(dummyTX);

        verifyTransaction(dummyTX);
        checkWalletConsistency();

        // The created tx looks like:
         /*
        IN[0]  any input > inputAmount (including tx fee) (unsigned)
        IN[1...n] optional inputs supported, but currently there is just 1 input (unsigned)
        OUT[0] dummyOutputAmount (inputAmount - tx fee)
        OUT[1] Optional Change = inputAmount - dummyOutputAmount - tx fee
        OUT[2...n] optional more outputs are supported, but currently there is just max. 1 optional change output
         */

        printInputs("dummyTX", dummyTX);
        log.debug("dummyTX created: " + dummyTX);

        List<TransactionOutput> connectedOutputsForAllInputs = new ArrayList<>();
        for (TransactionInput input : dummyTX.getInputs()) {
            connectedOutputsForAllInputs.add(input.getConnectedOutput());
        }

        // Only save offerer outputs, the MS output is ignored
        List<TransactionOutput> outputs = new ArrayList<>();
        for (TransactionOutput output : dummyTX.getOutputs()) {
            if (output.equals(msOutput))
                continue;
            outputs.add(output);
        }

        return new TransactionDataResult(connectedOutputsForAllInputs, outputs);
    }

    // 2. step: Taker creates a deposit tx and signs his inputs
    public TransactionDataResult takerCreatesAndSignsDepositTx(Coin inputAmount,
                                                               Coin msOutputAmount,
                                                               List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                                               List<TransactionOutput> offererOutputs,
                                                               AddressEntry addressInfo,
                                                               byte[] offererPubKey,
                                                               byte[] takerPubKey,
                                                               byte[] arbitratorPubKey) throws InsufficientMoneyException, SigningException,
            TransactionVerificationException, WalletException {

        // TODO verify amounts, addresses, MS
        
        Transaction depositTx = new Transaction(params);
        Script multiSigOutputScript = getMultiSigOutputScript(offererPubKey, takerPubKey, arbitratorPubKey);
        // We use temporary inputAmount as the value for the output amount to get the correct inputs from the takers side. 
        // Later when we add the offerer inputs we replace the output amount with the real msOutputAmount
        // Tx fee for deposit tx will be paid by offerer.
        TransactionOutput msOutput = new TransactionOutput(params, depositTx, inputAmount, multiSigOutputScript.getProgram());
        depositTx.addOutput(msOutput);

        // Not lets find the inputs to satisfy that output and add an optional change output
        addAvailableInputsAndChangeOutputs(depositTx, addressInfo);

        // Now as we have the takers inputs and outputs we replace the temporary output amount with the real msOutputAmount
        msOutput.setValue(msOutputAmount);

        // Save reference to inputs for signing, before we add offerer inputs
        List<TransactionInput> takerInputs = new ArrayList<>(depositTx.getInputs());
        List<TransactionOutput> connectedOutputsForAllTakerInputs = new ArrayList<>();
        for (TransactionInput input : takerInputs) {
            connectedOutputsForAllTakerInputs.add(input.getConnectedOutput());
        }

        // Lets save the takerOutputs for passing later to the result, the MS output is ignored
        List<TransactionOutput> takerOutputs = new ArrayList<>();
        for (TransactionOutput output : depositTx.getOutputs()) {
            if (output.equals(msOutput))
                continue;
            takerOutputs.add(output);
        }

        // Add all inputs from offerer (normally its just 1 input)
        for (TransactionOutput connectedOutputForInput : offererConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());
            TransactionInput transactionInput = new TransactionInput(params, depositTx, new byte[]{}, outPoint);
            depositTx.addInput(transactionInput);
        }

        // Add optional outputs 
        for (TransactionOutput output : offererOutputs) {
            depositTx.addOutput(output);
        }

        printInputs("depositTx", depositTx);
        log.debug("depositTx = " + depositTx);

        // Sign taker inputs
        // Taker inputs are the first inputs (0 -n), so the index of takerInputs and depositTx.getInputs() matches for the number of takerInputs.
        int index = 0;
        for (TransactionInput input : takerInputs) {
            log.debug("signInput input "+input.toString());
            log.debug("signInput index "+index);
            signInput(depositTx, input, index);
            checkScriptSig(depositTx, input, index);
            index++;
        }

        verifyTransaction(depositTx);
        checkWalletConsistency();

        printInputs("depositTx", depositTx);
        log.debug("depositTx = " + depositTx);

        return new TransactionDataResult(depositTx, connectedOutputsForAllTakerInputs, takerOutputs);
    }

    // 3. step: deposit tx
    // Offerer signs tx and publishes it
    public void offererSignAndPublishTx(Transaction takersDepositTx,
                                        List<TransactionOutput> takersConnectedOutputsForAllInputs,
                                        List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                        byte[] offererPubKey,
                                        byte[] takerPubKey,
                                        byte[] arbitratorPubKey,
                                        FutureCallback<Transaction> callback) throws SigningException, TransactionVerificationException, WalletException {

        // TODO verify amounts, addresses, MS
        
        // The outpoints are not available from the serialized takersDepositTx, so we cannot use that tx directly, but we use it to construct a new depositTx
        Transaction depositTx = new Transaction(params);

        // We save offererInputs for later signing when tx is fully constructed
        List<TransactionInput> offererInputs = new ArrayList<>();

        // Add all inputs from offerer (normally its just 1 input)
        for (TransactionOutput connectedOutputForInput : offererConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());
            TransactionInput input = new TransactionInput(params, depositTx, new byte[]{}, outPoint);
            offererInputs.add(input);
            depositTx.addInput(input);
        }

        // Add all inputs from taker and apply signature
        for (TransactionOutput connectedOutputForInput : takersConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());

            // We grab the signatures from the takersDepositTx and apply it to the new tx input
            Optional<TransactionInput> result = takersDepositTx.getInputs().stream()
                    .filter(e -> e.getConnectedOutput().hashCode() == connectedOutputForInput.hashCode()).findAny();
            if (result.isPresent()) {
                TransactionInput signedInput = result.get();
                Script script = signedInput.getScriptSig();

                TransactionInput transactionInput = new TransactionInput(params, depositTx, script.getProgram(), outPoint);
                depositTx.addInput(transactionInput);
            }
        }

        // Add all outputs from takersDepositTx to depositTx
        takersDepositTx.getOutputs().forEach(depositTx::addOutput);

        printInputs("depositTx", depositTx);
        log.debug("depositTx = " + depositTx);
        
        // Offerer inputs are the first inputs (0 -n), so the index of offererInputs and depositTx.getInputs() matches for the number of offererInputs.
        int index = 0;
        for (TransactionInput input : offererInputs) {
            signInput(depositTx, input, index);
            checkScriptSig(depositTx, input, index);
            index++;
        }

        // TODO verify MS, amounts
        //Script multiSigOutputScript = getMultiSigOutputScript(offererPubKey, takerPubKey, arbitratorPubKey);

        verifyTransaction(depositTx);
        checkWalletConsistency();
       
        // Broadcast depositTx
        log.trace("Wallet balance before broadcastTransaction: " + wallet.getBalance());
        log.trace("Check if wallet is consistent before broadcastTransaction: result=" + wallet.isConsistent());
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(depositTx);
        log.trace("Wallet balance after broadcastTransaction: " + wallet.getBalance());
        log.trace("Check if wallet is consistent after broadcastTransaction: result=" + wallet.isConsistent());
        Futures.addCallback(broadcastComplete, callback);
    }

    // 4 step deposit tx: Offerer send deposit tx to taker
    public Transaction takerCommitDepositTx(Transaction depositTx) throws WalletException {
        log.trace("takerCommitDepositTx");
        log.trace("inputs: ");
        log.trace("depositTx=" + depositTx);
        // If not recreate the tx we get a null pointer at receivePending
        //depositTx = new Transaction(params, depositTx.bitcoinSerialize());
        log.trace("depositTx=" + depositTx);
        // boolean isAlreadyInWallet = wallet.maybeCommitTx(depositTx);
        //log.trace("isAlreadyInWallet=" + isAlreadyInWallet);

        // Manually add the multisigContract to the wallet, overriding the isRelevant checks so we can track
        // it and check for double-spends later
        try {
            wallet.receivePending(depositTx, null, true);
        } catch (Throwable t) {
            log.error(t.getMessage());
            t.printStackTrace();
            throw new WalletException(t);
        }

        return depositTx;

    }

    // 5. step payout tx: Offerer creates payout tx and signs it
    public Pair<ECKey.ECDSASignature, Transaction> offererCreatesAndSignsPayoutTx(String depositTxID,
                                                                                  Coin offererPaybackAmount,
                                                                                  Coin takerPaybackAmount,
                                                                                  String takerAddress,
                                                                                  String tradeID) throws AddressFormatException {
        log.debug("offererCreatesAndSignsPayoutTx");
        log.trace("inputs: ");
        log.trace("depositTxID=" + depositTxID);
        log.trace("offererPaybackAmount=" + offererPaybackAmount.toFriendlyString());
        log.trace("takerPaybackAmount=" + takerPaybackAmount.toFriendlyString());
        log.trace("takerAddress=" + takerAddress);

        // Offerer has published depositTx earlier, so he has it in his wallet
        Transaction depositTx = wallet.getTransaction(new Sha256Hash(depositTxID));
        // String depositTxAsHex = Utils.HEX.encode(depositTx.bitcoinSerialize());

        // We create the payout tx
        Transaction tx = createPayoutTx(depositTx, offererPaybackAmount, takerPaybackAmount,
                getAddressInfo(tradeID).getAddressString(), takerAddress);

        // We create the signature for that tx
        TransactionOutput multiSigOutput = tx.getInput(0).getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature offererSignature = getAddressInfo(tradeID).getKey().sign(sigHash);

        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig));
        tx.getInput(0).setScriptSig(inputScript);

        log.trace("sigHash=" + sigHash);
        return new Pair<>(offererSignature, depositTx);
    }

    // 6. step payout tx: Taker signs and publish tx
    public void takerSignsAndSendsTx(Transaction depositTx,
                                     ECKey.ECDSASignature offererSignature,
                                     Coin offererPaybackAmount,
                                     Coin takerPaybackAmount,
                                     String offererAddress,
                                     String tradeID,
                                     FutureCallback<Transaction> callback) throws AddressFormatException {
        log.debug("takerSignsAndSendsTx");
        log.trace("inputs: ");
        log.trace("depositTx=" + depositTx);
        log.trace("offererSignature=" + offererSignature);
        log.trace("offererPaybackAmount=" + offererPaybackAmount.toFriendlyString());
        log.trace("takerPaybackAmount=" + takerPaybackAmount.toFriendlyString());
        log.trace("offererAddress=" + offererAddress);
        log.trace("callback=" + callback);

        // We create the payout tx
        Transaction tx = createPayoutTx(depositTx, offererPaybackAmount, takerPaybackAmount, offererAddress, getAddressInfo(tradeID).getAddressString());

        // We sign that tx with our key and apply the signature form the offerer
        TransactionOutput multiSigOutput = tx.getInput(0).getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        log.trace("sigHash=" + sigHash);

        ECKey.ECDSASignature takerSignature = getAddressInfo(tradeID).getKey().sign(sigHash);
        TransactionSignature takerTxSig = new TransactionSignature(takerSignature, Transaction.SigHash.ALL, false);

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

    private Script getMultiSigOutputScript(byte[] offererPubKey, byte[] takerPubKey, byte[] arbitratorPubKey) {
        ECKey offererKey = ECKey.fromPublicOnly(offererPubKey);
        ECKey takerKey = ECKey.fromPublicOnly(takerPubKey);
        ECKey arbitratorKey = ECKey.fromPublicOnly(arbitratorPubKey);

        List<ECKey> keys = ImmutableList.of(offererKey, takerKey, arbitratorKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    private Transaction createPayoutTx(Transaction depositTx, Coin offererPaybackAmount, Coin takerPaybackAmount,
                                       String offererAddress, String takerAddress) throws AddressFormatException {
        log.trace("createPayoutTx");
        log.trace("inputs: ");
        log.trace("depositTx=" + depositTx);
        log.trace("offererPaybackAmount=" + offererPaybackAmount.toFriendlyString());
        log.trace("takerPaybackAmount=" + takerPaybackAmount.toFriendlyString());
        log.trace("offererAddress=" + offererAddress);
        log.trace("takerAddress=" + takerAddress);

        // Transaction depositTx = new Transaction(params, Utils.parseAsHexOrBase58(depositTx));
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

    private void checkWalletConsistency() throws WalletException {
        try {
            log.trace("Check if wallet is consistent before commit.");
            checkState(wallet.isConsistent());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new WalletException(t);
        }
    }

    private void verifyTransaction(Transaction transaction) throws TransactionVerificationException {
        try {
            log.trace("Verify transaction");
            transaction.verify();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException, TransactionVerificationException {
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        Sha256Hash hash = transaction.hashForSignature(inputIndex, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = sigKey.sign(hash);
        TransactionSignature txSig = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        }
        else if (scriptPubKey.isSentToAddress()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        }
        else {
            throw new SigningException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);
        }
    }

    private void checkScriptSig(Transaction transaction, TransactionInput input, int inputIndex) throws TransactionVerificationException {
        try {
            log.trace("Verifies that this script (interpreted as a scriptSig) correctly spends the given scriptPubKey.");
            input.getScriptSig().correctlySpends(transaction, inputIndex, input.getConnectedOutput().getScriptPubKey());
            inputIndex++;
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

 /*   private void checkScriptSigForAllInputs(Transaction transaction) throws TransactionVerificationException {
        int inputIndex = 0;
        for (TransactionInput input : transaction.getInputs()) {
            checkScriptSig(transaction, input, inputIndex);
        }
    }*/

    private void removeSignatures(Transaction transaction) throws InsufficientMoneyException {
        for (TransactionInput input : transaction.getInputs()) {
            input.setScriptSig(new Script(new byte[]{}));
        }
    }

    private void addAvailableInputsAndChangeOutputs(Transaction transaction, AddressEntry addressEntry) throws InsufficientMoneyException {
        // Lets let the framework do the work to find the right inputs
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(transaction);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
        // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
        // So it will not change the wallet balance.
        wallet.completeTx(sendRequest);

        printInputs("transaction", transaction);
        log.trace("transaction=" + transaction);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////


    public class TransactionDataResult {
        private final List<TransactionOutput> connectedOutputsForAllInputs;
        private final List<TransactionOutput> outputs;
        private Transaction depositTx;

        public TransactionDataResult(List<TransactionOutput> connectedOutputsForAllInputs, List<TransactionOutput> outputs) {
            this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
            this.outputs = outputs;
        }

        public TransactionDataResult(Transaction depositTx, List<TransactionOutput> connectedOutputsForAllInputs, List<TransactionOutput> outputs) {
            this.depositTx = depositTx;
            this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
            this.outputs = outputs;
        }

        public List<TransactionOutput> getOutputs() {
            return outputs;
        }

        public List<TransactionOutput> getConnectedOutputsForAllInputs() {
            return connectedOutputsForAllInputs;
        }

        public Transaction getDepositTx() {
            return depositTx;
        }
    }

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
