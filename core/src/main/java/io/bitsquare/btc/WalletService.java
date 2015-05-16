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
import io.bitsquare.crypto.CryptoService;
import io.bitsquare.user.Preferences;

import org.bitcoinj.core.AbstractWalletEventListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DownloadProgressTracker;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerEventListener;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletEventListener;
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

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public static final String DIR_KEY = "wallet.dir";
    public static final String PREFIX_KEY = "wallet.prefix";
    private static final long STARTUP_TIMEOUT = 60;

    private final List<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArrayList<>();
    private final List<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArrayList<>();
    private final List<BalanceListener> balanceListeners = new CopyOnWriteArrayList<>();

    private final DownloadListener downloadListener = new DownloadListener();
    private final WalletEventListener walletEventListener = new BitsquareWalletEventListener();

    private final RegTestHost regTestHost;
    private final TradeWalletService tradeWalletService;
    private final AddressEntryList addressEntryList;
    private final NetworkParameters params;
    private final CryptoService cryptoService;
    private final File walletDir;
    private final String walletPrefix;
    private final UserAgent userAgent;

    private WalletAppKit walletAppKit;
    private Wallet wallet;
    private AddressEntry registrationAddressEntry;
    private AddressEntry arbitratorDepositAddressEntry;
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletService(RegTestHost regTestHost, CryptoService cryptoService,
                         TradeWalletService tradeWalletService, AddressEntryList addressEntryList, UserAgent userAgent,
                         @Named(DIR_KEY) File walletDir, @Named(PREFIX_KEY) String walletPrefix, Preferences preferences) {
        this.regTestHost = regTestHost;
        this.tradeWalletService = tradeWalletService;
        this.addressEntryList = addressEntryList;
        this.params = preferences.getBitcoinNetwork().getParameters();
        this.cryptoService = cryptoService;
        this.walletDir = new File(walletDir, preferences.getBitcoinNetwork().toString().toLowerCase());
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

                // set after wallet is ready
                tradeWalletService.setWalletAppKit(walletAppKit);

                status.onCompleted();
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            if (regTestHost == RegTestHost.REG_TEST_SERVER) {
                try {
                    walletAppKit.setPeerNodes(new PeerAddress(InetAddress.getByName(RegTestHost.SERVER_IP), params.getPort()));
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            }
            else if (regTestHost == RegTestHost.LOCALHOST) {
                walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.}
            }
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
        }
        else if (params == TestNet3Params.get()) {
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints.testnet"));
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
                log.error("walletAppKit failed");
                status.onError(failure);
            }
        }, Threading.USER_THREAD);
        walletAppKit.startAsync();
        return status.timeout(STARTUP_TIMEOUT, TimeUnit.SECONDS);
    }

    private void initWallet() {
        wallet = walletAppKit.wallet();
        wallet.addEventListener(walletEventListener);

        addressEntryList.onWalletReady(wallet);
        registrationAddressEntry = addressEntryList.getRegistrationAddressEntry();

        walletAppKit.peerGroup().addEventListener(new PeerEventListener() {
            @Override
            public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
            }

            @Override
            public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
            }

            @Override
            public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            }

            @Override
            public void onPeerConnected(Peer peer, int peerCount) {
                log.trace("onPeerConnected " + peerCount);
                Threading.USER_THREAD.execute(() -> numPeers.set(peerCount));
            }

            @Override
            public void onPeerDisconnected(Peer peer, int peerCount) {
                log.trace("onPeerDisconnected " + peerCount);
                Threading.USER_THREAD.execute(() -> numPeers.set(peerCount));
            }

            @Override
            public Message onPreMessageReceived(Peer peer, Message m) {
                return null;
            }

            @Override
            public void onTransaction(Peer peer, Transaction t) {
            }

            @Nullable
            @Override
            public List<Message> getData(Peer peer, GetDataMessage m) {
                return null;
            }
        });
    }

    public void shutDown() {
        if (wallet != null)
            wallet.removeEventListener(walletEventListener);
        if (walletAppKit != null)
            walletAppKit.stopAsync();
    }

    public ReadOnlyDoubleProperty downloadPercentageProperty() {
        return downloadListener.percentageProperty();
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
            arbitratorDepositAddressEntry = addressEntryList.getNewAddressEntry(AddressEntry.Context.ARBITRATOR_DEPOSIT, null);

        return arbitratorDepositAddressEntry;
    }

    public AddressEntry getAddressEntry(String offerId) {
        log.trace("getAddressEntry called with offerId " + offerId);
        Optional<AddressEntry> addressEntry = getAddressEntryList().stream().filter(e -> offerId.equals(e.getOfferId())).findFirst();

        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.getNewAddressEntry(AddressEntry.Context.TRADE, offerId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create new AddressInfo objects
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        assert getRegistrationAddressEntry() != null;
        TransactionConfidence transactionConfidence = getConfidenceForAddress(getRegistrationAddressEntry().getAddress());
        return TransactionConfidence.ConfidenceType.BUILDING.equals(transactionConfidence.getConfidenceType());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getBalanceForAddress(Address address) {
        return wallet != null ? getBalance(wallet.calculateAllSpendCandidates(true), address) : Coin.ZERO;
    }

    private Coin getBalance(List<TransactionOutput> transactionOutputs, Address address) {
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

    public void payRegistrationFee(String stringifiedFiatAccounts, FutureCallback<Transaction> callback) throws
            InsufficientMoneyException {
        log.debug("payRegistrationFee");
        log.trace("stringifiedFiatAccounts " + stringifiedFiatAccounts);

        Transaction tx = new Transaction(params);

        byte[] data = cryptoService.digestMessageWithSignature(getRegistrationAddressEntry().getKeyPair(), stringifiedFiatAccounts);
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

    private static void printTxWithInputs(String tracePrefix, Transaction tx) {
        log.trace(tracePrefix + ": " + tx.toString());
        for (TransactionInput input : tx.getInputs()) {
            if (input.getConnectedOutput() != null)
                log.trace(tracePrefix + " input value: " + input.getConnectedOutput().getValue().toFriendlyString());
            else
                log.trace(tracePrefix + ": Transaction already has inputs but we don't have the connected outputs, so we don't know the value.");
        }
    }

    public int getNumPeers() {
        return numPeers.get();
    }

    public ReadOnlyIntegerProperty numPeersProperty() {
        return numPeers;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static class DownloadListener extends DownloadProgressTracker {
        private final DoubleProperty percentage = new SimpleDoubleProperty(-1);

        @Override
        protected void progress(double percentage, int blocksLeft, Date date) {
            super.progress(percentage, blocksLeft, date);
            Threading.USER_THREAD.execute(() -> this.percentage.set(percentage / 100d));
        }

        @Override
        protected void doneDownload() {
            super.doneDownload();
            Threading.USER_THREAD.execute(() -> this.percentage.set(1d));
        }

        public ReadOnlyDoubleProperty percentageProperty() {
            return percentage;
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
