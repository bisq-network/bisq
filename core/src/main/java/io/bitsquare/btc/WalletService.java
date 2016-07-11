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
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.storage.FileUtil;
import io.bitsquare.storage.Storage;
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
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public static final String DIR_KEY = "walletDir";
    public static final String PREFIX_KEY = "walletPrefix";
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
    private final boolean useTor;

    private WalletAppKit walletAppKit;
    private Wallet wallet;
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);
    private final ObjectProperty<List<Peer>> connectedPeers = new SimpleObjectProperty<>();
    public final BooleanProperty shutDownDone = new SimpleBooleanProperty();
    private final Storage<Long> storage;
    private final Long bloomFilterTweak;
    private KeyParameter aesKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletService(RegTestHost regTestHost, TradeWalletService tradeWalletService, AddressEntryList addressEntryList, UserAgent userAgent,
                         @Named(DIR_KEY) File appDir, Preferences preferences) {
        this.regTestHost = regTestHost;
        this.tradeWalletService = tradeWalletService;
        this.addressEntryList = addressEntryList;
        this.params = preferences.getBitcoinNetwork().getParameters();
        this.walletDir = new File(appDir, "bitcoin");
        this.userAgent = userAgent;
        useTor = preferences.getUseTorForBitcoinJ();

        storage = new Storage<>(walletDir);
        Long persisted = storage.initAndGetPersisted("BloomFilterNonce");
        if (persisted != null) {
            bloomFilterTweak = persisted;
        } else {
            bloomFilterTweak = new Random().nextLong();
            storage.queueUpForSave(bloomFilterTweak, 100);
        }
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

        Timer timeoutTimer = UserThread.runAfter(() ->
                exceptionHandler.handleException(new TimeoutException("Wallet did not initialize in " +
                        STARTUP_TIMEOUT_SEC + " seconds.")), STARTUP_TIMEOUT_SEC);


        backupWallet();

        // If seed is non-null it means we are restoring from backup.
        walletAppKit = new WalletAppKit(params, walletDir, "Bitsquare") {
            @Override
            protected void onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                walletAppKit.wallet().allowSpendingUnconfirmedTransactions();
                if (params != RegTestParams.get())
                    walletAppKit.peerGroup().setMaxConnections(11);

                wallet = walletAppKit.wallet();
                wallet.addEventListener(walletEventListener);

                addressEntryList.onWalletReady(wallet);

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
                tradeWalletService.setAddressEntryList(addressEntryList);
                timeoutTimer.stop();

                // onSetupCompleted in walletAppKit is not the called on the last invocations, so we add a bit of delay
                UserThread.runAfter(resultHandler::handleResult, 100, TimeUnit.MILLISECONDS);
            }
        };

        // Bloom filters in BitcoinJ are completely broken
        // See: https://jonasnick.github.io/blog/2015/02/12/privacy-in-bitcoinj/
        // Here are a few improvements to fix a few vulnerabilities.

        // Bitsquare's BitcoinJ fork has added a bloomFilterTweak (nonce) setter to reuse the same seed avoiding the trivial vulnerability
        // by getting the real pub keys by intersections of several filters sent at each startup.
        walletAppKit.setBloomFilterTweak(bloomFilterTweak);

        // Avoid the simple attack (see: https://jonasnick.github.io/blog/2015/02/12/privacy-in-bitcoinj/) due to the 
        // default implementation using both pubkey and hash of pubkey
        walletAppKit.setInsertPubKey(false);

        // Default only 266 keys are generated (2 * 100+33). That would trigger new bloom filters when we are reaching 
        // the threshold. To avoid reaching the threshold we create much more keys which are unlikely to cause update of the
        // filter for most users. With lookaheadSize of 500 we get 1333 keys which should be enough for most users to 
        // never need to update a bloom filter, which would weaken privacy.
        walletAppKit.setLookaheadSize(500);

        // Calculation is derived from: https://www.reddit.com/r/Bitcoin/comments/2vrx6n/privacy_in_bitcoinj_android_wallet_multibit_hive/coknjuz
        // Nr of false positives (56M keys in the blockchain): 
        // First attempt for FP rate:
        // FP rate = 0,0001;  Nr of false positives: 0,0001 * 56 000 000  = 5600
        // We have 1333keys: 1333 / (5600 + 1333) = 0.19 -> 19 % probability that a pub key is in our wallet
        // After tests I found out that the bandwidth consumption varies widely related to the generated filter.
        // About 20- 40 MB for upload and 30-130 MB for download at first start up (spv chain).
        // Afterwards its about 1 MB for upload and 20-80 MB for download.
        // Probably better then a high FP rate would be to include foreign pubKeyHashes which are tested to not be used 
        // in many transactions. If we had a pool of 100 000 such keys (2 MB data dump) to random select 4000 we could mix it with our
        // 1000 own keys and get a similar probability rate as with the current setup but less variation in bandwidth 
        // consumption.

        // For now to reduce risks with high bandwidth consumption we reduce the FP rate by half.
        // FP rate = 0,00005;  Nr of false positives: 0,00005 * 56 000 000  = 2800
        // 1333 / (2800 + 1333) = 0.32 -> 32 % probability that a pub key is in our wallet
        walletAppKit.setBloomFilterFalsePositiveRate(0.00005);


        // TODO Get bitcoinj running over our tor proxy. BlockingClientManager need to be used to use the socket  
        // from jtorproxy. To get supported it via nio / netty will be harder
        if (useTor && params.getId().equals(NetworkParameters.ID_MAINNET))
            walletAppKit.useTor();

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
                timeoutTimer.stop();
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

    public void backupWallet() {
        FileUtil.rollingBackup(walletDir, "Bitsquare.wallet");
    }

    public void clearBackup() {
        try {
            FileUtil.deleteDirectory(new File(Paths.get(walletDir.getAbsolutePath(), "backup").toString()));
        } catch (IOException e) {
            log.error("Could not delete directory " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setAesKey(KeyParameter aesKey) {
        this.aesKey = aesKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAddressConfidenceListener(AddressConfidenceListener listener) {
        addressConfidenceListeners.add(listener);
    }

    public void removeAddressConfidenceListener(AddressConfidenceListener listener) {
        addressConfidenceListeners.remove(listener);
    }

    public void addTxConfidenceListener(TxConfidenceListener listener) {
        txConfidenceListeners.add(listener);
    }

    public void removeTxConfidenceListener(TxConfidenceListener listener) {
        txConfidenceListeners.remove(listener);
    }

    public void addBalanceListener(BalanceListener listener) {
        balanceListeners.add(listener);
    }

    public void removeBalanceListener(BalanceListener listener) {
        balanceListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AddressEntry
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressEntry getOrCreateAddressEntry(String offerId, AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.addAddressEntry(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), context, offerId));
    }

    public AddressEntry getOrCreateAddressEntry(AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> context == e.getContext())
                .findAny();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.addAddressEntry(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), context));
    }

    public AddressEntry getOrCreateUnusedAddressEntry(AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> context == e.getContext())
                .filter(e -> getNumTxOutputsForAddress(e.getAddress()) == 0)
                .findAny();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.addAddressEntry(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), context));
    }

    public Optional<AddressEntry> findAddressEntry(String address, AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(e -> address.equals(e.getAddressString()))
                .filter(e -> context == e.getContext())
                .findAny();
    }

    public List<AddressEntry> getAvailableAddressEntries() {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> AddressEntry.Context.AVAILABLE == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntries(AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> context == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getFundedAvailableAddressEntries() {
        return getAvailableAddressEntries().stream()
                .filter(addressEntry -> getBalanceForAddress(addressEntry.getAddress()).isPositive())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntryListAsImmutableList() {
        return ImmutableList.copyOf(addressEntryList);
    }

    public void swapTradeEntryToAvailableEntry(String offerId, AddressEntry.Context context) {
        Optional<AddressEntry> addressEntryOptional = getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
        addressEntryOptional.ifPresent(addressEntryList::swapToAvailable);
    }

    public void swapAnyTradeEntryContextToAvailableEntry(String offerId) {
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.OFFER_FUNDING);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.RESERVED_FOR_TRADE);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.MULTI_SIG);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.TRADE_PAYOUT);
    }

    public void saveAddressEntryList() {
        addressEntryList.queueUpForSave();
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

    public Coin getSavingWalletBalance() {
        return Coin.valueOf(getFundedAvailableAddressEntries().stream()
                .mapToLong(addressEntry -> getBalanceForAddress(addressEntry.getAddress()).value)
                .sum());
    }

    public int getNumTxOutputsForAddress(Address address) {
        List<TransactionOutput> transactionOutputs = new ArrayList<>();
        wallet.getTransactions(true).stream().forEach(t -> transactionOutputs.addAll(t.getOutputs()));
        int outputs = 0;
        for (TransactionOutput transactionOutput : transactionOutputs) {
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address))
                    outputs++;
            }
        }
        return outputs;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Double spend unconfirmed transaction (unlock in case we got into a tx with a too low mining fee)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void doubleSpendTransaction(String txId, Runnable resultHandler, ErrorMessageHandler errorMessageHandler) throws InsufficientMoneyException, AddressFormatException, AddressEntryException {
        AddressEntry addressEntry = getOrCreateUnusedAddressEntry(AddressEntry.Context.AVAILABLE);
        checkNotNull(addressEntry.getAddress(), "addressEntry.getAddress() must not be null");
        Optional<Transaction> transactionOptional = wallet.getTransactions(true).stream()
                .filter(t -> t.getHashAsString().equals(txId))
                .findAny();
        if (transactionOptional.isPresent())
            doubleSpendTransaction(transactionOptional.get(), addressEntry.getAddress(), resultHandler, errorMessageHandler);
    }

    public void doubleSpendTransaction(Transaction txToDoubleSpend, Address toAddress, Runnable resultHandler, ErrorMessageHandler errorMessageHandler) throws InsufficientMoneyException, AddressFormatException, AddressEntryException {
        final TransactionConfidence.ConfidenceType confidenceType = txToDoubleSpend.getConfidence().getConfidenceType();
        if (confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
            log.info("txToDoubleSpend nr. of inputs " + txToDoubleSpend.getInputs().size());

            Transaction newTransaction = new Transaction(params);
            txToDoubleSpend.getInputs().stream().forEach(input -> {
                        final TransactionOutput connectedOutput = input.getConnectedOutput();
                        if (connectedOutput != null &&
                                connectedOutput.isMine(wallet) &&
                                connectedOutput.getParentTransaction() != null &&
                                connectedOutput.getParentTransaction().getConfidence() != null &&
                                input.getValue() != null) {
                            if (connectedOutput.getParentTransaction().getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                                newTransaction.addInput(new TransactionInput(params,
                                        newTransaction,
                                        new byte[]{},
                                        new TransactionOutPoint(params, input.getOutpoint().getIndex(),
                                                new Transaction(params, connectedOutput.getParentTransaction().bitcoinSerialize())),
                                        Coin.valueOf(input.getValue().value)));
                            } else {
                                log.warn("Confidence of parent tx is not of type BUILDING: ConfidenceType=" +
                                        connectedOutput.getParentTransaction().getConfidence().getConfidenceType());
                            }
                        }
                    }
            );

            log.info("newTransaction nr. of inputs " + newTransaction.getInputs().size());
            log.info("newTransaction size in kB " + newTransaction.bitcoinSerialize().length / 1024);

            if (!newTransaction.getInputs().isEmpty()) {
                Coin amount = Coin.valueOf(newTransaction.getInputs().stream()
                        .mapToLong(input -> input.getValue() != null ? input.getValue().value : 0)
                        .sum());
                newTransaction.addOutput(amount, toAddress);

                Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(newTransaction);
                sendRequest.aesKey = aesKey;
                sendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress, false);
                sendRequest.changeAddress = toAddress;
                sendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();

                Coin requiredFee = getFeeForDoubleSpend(sendRequest,
                        toAddress,
                        amount,
                        FeePolicy.getFixedTxFeeForTrades());

                amount = (amount.subtract(requiredFee)).subtract(FeePolicy.getFixedTxFeeForTrades());
                newTransaction.clearOutputs();
                newTransaction.addOutput(amount, toAddress);

                sendRequest = Wallet.SendRequest.forTx(newTransaction);
                sendRequest.aesKey = aesKey;
                sendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress, false);
                // We don't expect change but set it just in case
                sendRequest.changeAddress = toAddress;
                sendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();

                Wallet.SendResult sendResult = null;
                try {
                    sendResult = wallet.sendCoins(sendRequest);
                } catch (InsufficientMoneyException e) {
                    // in some cases getFee did not calculate correctly and we still get an InsufficientMoneyException
                    log.warn("We still have a missing fee " + (e.missing != null ? e.missing.toFriendlyString() : ""));

                    amount = (amount.subtract(e.missing));
                    newTransaction.clearOutputs();
                    newTransaction.addOutput(amount, toAddress);

                    sendRequest = Wallet.SendRequest.forTx(newTransaction);
                    sendRequest.aesKey = aesKey;
                    sendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress, false);
                    sendRequest.changeAddress = toAddress;
                    sendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();

                    try {
                        sendResult = wallet.sendCoins(sendRequest);
                    } catch (InsufficientMoneyException e2) {
                        errorMessageHandler.handleErrorMessage("We did not get the correct fee calculated. " + (e2.missing != null ? e2.missing.toFriendlyString() : ""));
                    }
                }
                if (sendResult != null) {
                    log.info("Broadcasting double spending transaction. " + newTransaction);
                    Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction result) {
                            log.info("Double spending transaction published. " + result);
                            resultHandler.run();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            log.info("Broadcasting double spending transaction failed. " + t.getMessage());
                            errorMessageHandler.handleErrorMessage(t.getMessage());
                        }
                    });
                }
            } else {
                errorMessageHandler.handleErrorMessage("We could not find inputs we control in the transaction we want to double spend.");
            }
        } else if (confidenceType == TransactionConfidence.ConfidenceType.BUILDING) {
            errorMessageHandler.handleErrorMessage("That transaction is already in the blockchain so we cannot double spend it.");
        } else if (confidenceType == TransactionConfidence.ConfidenceType.DEAD) {
            errorMessageHandler.handleErrorMessage("One of the inputs of that transaction has been already double spent.");
        }
    }

    private Coin getFeeForDoubleSpend(Wallet.SendRequest sendRequest,
                                      Address toAddress,
                                      Coin amount,
                                      Coin fee) throws AddressEntryException, AddressFormatException {
        try {
            sendRequest.tx.clearOutputs();
            sendRequest.tx.addOutput(amount, toAddress);

            Wallet.SendRequest newSendRequest = Wallet.SendRequest.forTx(sendRequest.tx);
            newSendRequest.aesKey = aesKey;
            newSendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress);
            newSendRequest.changeAddress = toAddress;
            newSendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();
            wallet.completeTx(newSendRequest);

            log.info("After fee check: amount  " + amount.toFriendlyString());
            log.info("Output fee  " + sendRequest.tx.getFee().toFriendlyString());
            sendRequest.tx.getOutputs().stream().forEach(o -> log.info("Output value " + o.getValue().toFriendlyString()));
        } catch (InsufficientMoneyException e) {
            if (e.missing != null) {
                log.trace("missing fee " + e.missing.toFriendlyString());
                fee = fee.add(e.missing);
                amount = amount.subtract(fee);
                return getFeeForDoubleSpend(sendRequest,
                        toAddress,
                        amount,
                        fee);
            }
        }
        return fee;
    }
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Fee calculation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getRequiredFee(String fromAddress,
                               String toAddress,
                               Coin amount,
                               AddressEntry.Context context)
            throws AddressFormatException, AddressEntryException {
        Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must nto be null");
        return getFee(fromAddress,
                toAddress,
                amount,
                context,
                Coin.ZERO);
    }

    public Coin getRequiredFeeForMultipleAddresses(Set<String> fromAddresses,
                                                   String toAddress,
                                                   Coin amount)
            throws AddressFormatException, AddressEntryException {
        Set<AddressEntry> addressEntries = fromAddresses.stream()
                .map(address -> {
                    Optional<AddressEntry> addressEntryOptional = findAddressEntry(address, AddressEntry.Context.AVAILABLE);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.OFFER_FUNDING);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.TRADE_PAYOUT);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.ARBITRATOR);
                    return addressEntryOptional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (addressEntries.isEmpty())
            throw new AddressEntryException("No Addresses for withdraw  found in our wallet");
        return getFeeForMultipleAddresses(fromAddresses,
                toAddress,
                amount,
                Coin.ZERO);
    }


    private Coin getFee(String fromAddress,
                        String toAddress,
                        Coin amount,
                        AddressEntry.Context context,
                        Coin fee) throws AddressEntryException, AddressFormatException {
        try {
            wallet.completeTx(getSendRequest(fromAddress, toAddress, amount, aesKey, context));
        } catch (InsufficientMoneyException e) {
            if (e.missing != null) {
                log.trace("missing fee " + e.missing.toFriendlyString());
                fee = fee.add(e.missing);
                amount = amount.subtract(fee);
                return getFee(fromAddress,
                        toAddress,
                        amount,
                        context,
                        fee);
            }
        }
        log.trace("result fee " + fee.toFriendlyString());
        return fee;
    }

    private Coin getFeeForMultipleAddresses(Set<String> fromAddresses,
                                            String toAddress,
                                            Coin amount,
                                            Coin fee) throws AddressEntryException, AddressFormatException {
        try {
            wallet.completeTx(getSendRequestForMultipleAddresses(fromAddresses, toAddress, amount, null, aesKey));
        } catch (InsufficientMoneyException e) {
            if (e.missing != null) {
                log.trace("missing fee " + e.missing.toFriendlyString());
                fee = fee.add(e.missing);
                amount = amount.subtract(fee);
                return getFeeForMultipleAddresses(fromAddresses,
                        toAddress,
                        amount,
                        fee);
            }
        }
        log.trace("result fee " + fee.toFriendlyString());
        return fee;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Send
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String sendFunds(String fromAddress,
                            String toAddress,
                            Coin receiverAmount,
                            @Nullable KeyParameter aesKey,
                            AddressEntry.Context context,
                            FutureCallback<Transaction> callback) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {
        Wallet.SendResult sendResult = wallet.sendCoins(getSendRequest(fromAddress, toAddress, receiverAmount, aesKey, context));
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTxWithInputs("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    public String sendFundsForMultipleAddresses(Set<String> fromAddresses,
                                                String toAddress,
                                                Coin receiverAmount,
                                                @Nullable String changeAddress,
                                                @Nullable KeyParameter aesKey,
                                                FutureCallback<Transaction> callback) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {
        Wallet.SendResult sendResult = wallet.sendCoins(getSendRequestForMultipleAddresses(fromAddresses, toAddress,
                receiverAmount, changeAddress, aesKey));
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTxWithInputs("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    public void emptyWallet(String toAddress, KeyParameter aesKey, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler)
            throws InsufficientMoneyException, AddressFormatException {
        Wallet.SendRequest sendRequest = Wallet.SendRequest.emptyWallet(new Address(params, toAddress));
        sendRequest.aesKey = aesKey;
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        sendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();
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

    private Wallet.SendRequest getSendRequest(String fromAddress,
                                              String toAddress,
                                              Coin amount,
                                              @Nullable KeyParameter aesKey,
                                              AddressEntry.Context context) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isAboveDust(amount),
                "The amount is too low (dust limit).");
        tx.addOutput(amount, new Address(params, toAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must nto be null");
        sendRequest.coinSelector = new TradeWalletCoinSelector(params, addressEntry.get().getAddress());
        sendRequest.changeAddress = addressEntry.get().getAddress();
        sendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();
        return sendRequest;
    }

    private Wallet.SendRequest getSendRequestForMultipleAddresses(Set<String> fromAddresses,
                                                                  String toAddress,
                                                                  Coin amount,
                                                                  @Nullable String changeAddress,
                                                                  @Nullable KeyParameter aesKey) throws
            AddressFormatException, AddressEntryException, InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isAboveDust(amount),
                "The amount is too low (dust limit).");
        tx.addOutput(amount, new Address(params, toAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Set<AddressEntry> addressEntries = fromAddresses.stream()
                .map(address -> {
                    Optional<AddressEntry> addressEntryOptional = findAddressEntry(address, AddressEntry.Context.AVAILABLE);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.OFFER_FUNDING);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.TRADE_PAYOUT);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.ARBITRATOR);
                    return addressEntryOptional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (addressEntries.isEmpty())
            throw new AddressEntryException("No Addresses for withdraw  found in our wallet");

        sendRequest.coinSelector = new MultiAddressesCoinSelector(params, addressEntries);
        Optional<AddressEntry> addressEntryOptional = Optional.empty();
        AddressEntry changeAddressAddressEntry = null;
        if (changeAddress != null)
            addressEntryOptional = findAddressEntry(changeAddress, AddressEntry.Context.AVAILABLE);

        if (addressEntryOptional.isPresent()) {
            changeAddressAddressEntry = addressEntryOptional.get();
        } else {
            ArrayList<AddressEntry> list = new ArrayList<>(addressEntries);
            if (!list.isEmpty())
                changeAddressAddressEntry = list.get(0);
        }
        checkNotNull(changeAddressAddressEntry, "change address must not be null");
        sendRequest.changeAddress = changeAddressAddressEntry.getAddress();
        sendRequest.feePerKb = FeePolicy.getNonTradeFeePerKb();
        return sendRequest;
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
            notifyBalanceListeners(tx);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
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
                    .filter(txConfidenceListener -> tx != null &&
                            tx.getHashAsString() != null &&
                            txConfidenceListener != null &&
                            tx.getHashAsString().equals(txConfidenceListener.getTxID()))
                    .forEach(txConfidenceListener ->
                            txConfidenceListener.onTransactionConfidenceChanged(tx.getConfidence()));
        }

        private void notifyBalanceListeners(Transaction tx) {
            for (BalanceListener balanceListener : balanceListeners) {
                Coin balance;
                if (balanceListener.getAddress() != null)
                    balance = getBalanceForAddress(balanceListener.getAddress());
                else
                    balance = getAvailableBalance();

                balanceListener.onBalanceChanged(balance, tx);
            }
        }
    }
}
