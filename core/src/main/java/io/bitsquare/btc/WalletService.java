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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.Service;
import io.bitsquare.btc.listeners.AddressConfidenceListener;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.user.Preferences;
import javafx.beans.property.*;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public static final String DIR_KEY = "wallet.dir";
    public static final String PREFIX_KEY = "wallet.prefix";
    private static final long STARTUP_TIMEOUT_SEC = 60;

    private final CopyOnWriteArraySet<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<BalanceListener> balanceListeners = new CopyOnWriteArraySet<>();

    private final DownloadListener downloadListener = new DownloadListener();
    private final WalletEventListener walletEventListener = new BitsquareWalletEventListener();

    private final RegTestHost regTestHost;
    private final TradeWalletService tradeWalletService;
    private final AddressEntryList addressEntryList;
    private final NetworkParameters params;
    private final File walletDir;
    private final UserAgent userAgent;

    private WalletAppKit walletAppKit;
    private Wallet wallet;
    private AddressEntry arbitratorAddressEntry;
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);
    private final ObjectProperty<List<Peer>> connectedPeers = new SimpleObjectProperty<>();
    public final BooleanProperty shutDownDone = new SimpleBooleanProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletService(RegTestHost regTestHost, TradeWalletService tradeWalletService, AddressEntryList addressEntryList, UserAgent userAgent,
                         @Named(DIR_KEY) File walletDir, Preferences preferences) {
        this.regTestHost = regTestHost;
        this.tradeWalletService = tradeWalletService;
        this.addressEntryList = addressEntryList;
        this.params = preferences.getBitcoinNetwork().getParameters();
        this.walletDir = new File(walletDir, "bitcoin");
        this.userAgent = userAgent;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(@Nullable DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        // Tell bitcoinj to execute event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.

        Threading.USER_THREAD = UserThread.getExecutor();

        Timer timeoutTimer = UserThread.runAfter(() -> {
            Utilities.setThreadName("WalletService:StartupTimeout");
            exceptionHandler.handleException(new TimeoutException("Wallet did not initialize in " + STARTUP_TIMEOUT_SEC / 1000 + " seconds."));
        }, STARTUP_TIMEOUT_SEC);

        // If seed is non-null it means we are restoring from backup.
        walletAppKit = new WalletAppKit(params, walletDir, "Bitsquare") {
            @Override
            protected void onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                walletAppKit.wallet().allowSpendingUnconfirmedTransactions();
                if (params != RegTestParams.get())
                    walletAppKit.peerGroup().setMaxConnections(11);
                walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.00001);
                wallet = walletAppKit.wallet();
                wallet.addEventListener(walletEventListener);

                addressEntryList.onWalletReady(wallet);
                arbitratorAddressEntry = addressEntryList.getArbitratorAddressEntry();

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
                        numPeers.set(peerCount);
                        connectedPeers.set(walletAppKit.peerGroup().getConnectedPeers());
                    }

                    @Override
                    public void onPeerDisconnected(Peer peer, int peerCount) {
                        numPeers.set(peerCount);
                        connectedPeers.set(walletAppKit.peerGroup().getConnectedPeers());
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

                // set after wallet is ready
                tradeWalletService.setWalletAppKit(walletAppKit);
                timeoutTimer.cancel();

                // onSetupCompleted in walletAppKit is not the called on the last invocations, so we add a bit of delay
                UserThread.runAfter(() -> resultHandler.handleResult(), 100, TimeUnit.MILLISECONDS);
            }
        };

        // TODO try to get bitcoinj running over our tor proxy
       /* if (!params.getId().equals(NetworkParameters.ID_REGTEST))
            walletAppKit.useTor();*/

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            if (regTestHost == RegTestHost.REG_TEST_SERVER) {
                try {
                    walletAppKit.setPeerNodes(new PeerAddress(InetAddress.getByName(RegTestHost.SERVER_IP), params.getPort()));
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            } else if (regTestHost == RegTestHost.LOCALHOST) {
                walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.}
            }
        } else if (params == MainNetParams.get()) {

            // reduce for mainnet testing
            // TODO revert later when tested enough
            FeePolicy.setSecurityDeposit(Coin.parseCoin("0.01")); // 4 EUR @ 400 EUR/BTC 
            Restrictions.setMaxTradeAmount(Coin.parseCoin("0.01")); // 4 EUR @ 400 EUR/BTC 

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
        } else if (params == TestNet3Params.get()) {
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints.testnet"));
        }

        walletAppKit.setDownloadListener(downloadListener)
                .setBlockingStartup(false)
                .setUserAgent(userAgent.getName(), userAgent.getVersion())
                .restoreWalletFromSeed(seed);

        walletAppKit.addListener(new Service.Listener() {
            @Override
            public void failed(@NotNull Service.State from, @NotNull Throwable failure) {
                walletAppKit = null;
                log.error("walletAppKit failed");
                timeoutTimer.cancel();
                UserThread.execute(() -> exceptionHandler.handleException(failure));
            }
        }, Threading.USER_THREAD);
        walletAppKit.startAsync();
    }

    public void shutDown() {
        if (wallet != null)
            wallet.removeEventListener(walletEventListener);

        if (walletAppKit != null) {
            try {
                walletAppKit.stopAsync();
                walletAppKit.awaitTerminated(5, TimeUnit.SECONDS);
            } catch (Throwable e) {
                // ignore
            }
            shutDownDone.set(true);
        }
    }

    public void restoreSeedWords(DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        Context ctx = Context.get();
        new Thread(() -> {
            try {
                Context.propagate(ctx);
                walletAppKit.stopAsync();
                walletAppKit.awaitTerminated();
                initialize(seed, resultHandler, exceptionHandler);
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }, "RestoreWallet-%d").start();
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
    // AddressInfo 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<AddressEntry> getAddressEntryList() {
        return ImmutableList.copyOf(addressEntryList);
    }

    public AddressEntry getArbitratorAddressEntry() {
        return arbitratorAddressEntry;
    }

    public AddressEntry getAddressEntryByOfferId(String offerId) {
        Optional<AddressEntry> addressEntry = getAddressEntryList().stream().filter(e -> offerId.equals(e.getOfferId())).findFirst();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.getNewAddressEntry(AddressEntry.Context.TRADE, offerId);
    }

    private Optional<AddressEntry> getAddressEntryByAddress(String address) {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    // BalanceType.AVAILABLE
    public Coin getAvailableBalance() {
        return wallet != null ? wallet.getBalance(Wallet.BalanceType.AVAILABLE) : Coin.ZERO;
    }

    public Coin getBalanceForAddress(Address address) {
        return wallet != null ? getBalance(wallet.calculateAllSpendCandidates(), address) : Coin.ZERO;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String sendFunds(String fromAddress,
                            String toAddress,
                            Coin amount,
                            KeyParameter aesKey,
                            FutureCallback<Transaction> callback) throws AddressFormatException, IllegalArgumentException, InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isMinSpendableAmount(amount),
                "You cannot send an amount which are smaller than the fee + dust output.");
        tx.addOutput(amount.subtract(FeePolicy.TX_FEE), new Address(params, toAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Optional<AddressEntry> addressEntry = getAddressEntryByAddress(fromAddress);
        if (!addressEntry.isPresent())
            throw new IllegalArgumentException("WithdrawFromAddress is not found in our wallets.");

        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry.get());
        sendRequest.changeAddress = addressEntry.get().getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTxWithInputs("sendFunds", tx);
        log.debug("tx=" + tx);

        return tx.getHashAsString();
    }

    public void emptyWallet(String toAddress, KeyParameter aesKey, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler)
            throws InsufficientMoneyException, AddressFormatException {
        Wallet.SendRequest sendRequest = Wallet.SendRequest.emptyWallet(new Address(params, toAddress));
        sendRequest.aesKey = aesKey;
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction result) {
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                errorMessageHandler.handleErrorMessage(t.getMessage());
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyDoubleProperty downloadPercentageProperty() {
        return downloadListener.percentageProperty();
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Transaction getTransactionFromSerializedTx(byte[] tx) {
        return new Transaction(params, tx);
    }

    public ReadOnlyIntegerProperty numPeersProperty() {
        return numPeers;
    }

    public ReadOnlyObjectProperty<List<Peer>> connectedPeersProperty() {
        return connectedPeers;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static class DownloadListener extends DownloadProgressTracker {
        private final DoubleProperty percentage = new SimpleDoubleProperty(-1);

        @Override
        protected void progress(double percentage, int blocksLeft, Date date) {
            super.progress(percentage, blocksLeft, date);
            UserThread.execute(() -> this.percentage.set(percentage / 100d));
        }

        @Override
        protected void doneDownload() {
            super.doneDownload();
            UserThread.execute(() -> this.percentage.set(1d));
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
                    balance = getAvailableBalance();

                balanceListener.onBalanceChanged(balance);
            }
        }
    }
    
     /* // TODO
    private class BloomFilterForForeignTx extends AbstractPeerEventListener implements PeerFilterProvider {
        private final String txId;

        public BloomFilterForForeignTx(String txId) {
            this.txId = txId;
        }

        @Override
        public long getEarliestKeyCreationTime() {
            return Utils.currentTimeSeconds();
        }

        @Override
        public void beginBloomFilterCalculation() {
        }

        @Override
        public int getBloomFilterElementCount() {
            return 1;
        }

        @Override
        public BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak) {
            BloomFilter filter = new BloomFilter(size, falsePositiveRate, nTweak);
           *//* for (TransactionOutPoint pledge : allPledges.keySet()) {
                filter.insert(pledge.bitcoinSerialize());
            }*//*
            // how to add txid ???
            return filter;
        }

        @Override
        public boolean isRequiringUpdateAllBloomFilter() {
            return false;
        }

        @Override
        public void endBloomFilterCalculation() {
        }

        @Override
        public void onTransaction(Peer peer, Transaction t) {
            // executor.checkOnThread();
            // TODO: Gate this logic on t being announced by multiple peers.
            //  checkForRevocation(t);
            // TODO: Watch out for the confirmation. If no confirmation of the revocation occurs within N hours, alert the user.
        }
    }

    // TODO
    public void findTxInBlockChain(String txId) {
        // https://groups.google.com/forum/?hl=de#!topic/bitcoinj/kinFP7lLsRE
        // https://groups.google.com/forum/?hl=de#!topic/bitcoinj/f7m87kCWdb8
        // https://groups.google.com/forum/?hl=de#!topic/bitcoinj/jNE5ohLExVM
        walletAppKit.peerGroup().addPeerFilterProvider(new BloomFilterForForeignTx(txId));
    }*/

}
