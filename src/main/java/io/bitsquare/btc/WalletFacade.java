package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.bitcoin.crypto.TransactionSignature;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.utils.Threading;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.bitsquare.BitSquare;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.storage.Storage;
import javafx.application.Platform;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.google.bitcoin.script.ScriptOpCodes.OP_RETURN;

public class WalletFacade
{
    public static final String MAIN_NET = "MAIN_NET";
    public static final String TEST_NET = "TEST_NET";
    public static final String REG_TEST_NET = "REG_TEST_NET";

    public static String WALLET_PREFIX = BitSquare.ID;

    private static final Logger log = LoggerFactory.getLogger(WalletFacade.class);

    private String saveAddressEntryListId;
    private NetworkParameters params;
    private BitSquareWalletAppKit walletAppKit;
    private FeePolicy feePolicy;
    private CryptoFacade cryptoFacade;
    private Storage storage;
    private BitSquareWallet wallet;
    private WalletEventListener walletEventListener;
    private List<DownloadListener> downloadListeners = new ArrayList<>();
    private List<ConfidenceListener> confidenceListeners = new ArrayList<>();
    private List<BalanceListener> balanceListeners = new ArrayList<>();
    private List<AddressEntry> addressEntryList = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletFacade(NetworkParameters params, BitSquareWalletAppKit walletAppKit, FeePolicy feePolicy, CryptoFacade cryptoFacade, Storage storage)
    {
        this.params = params;
        this.walletAppKit = walletAppKit;
        this.feePolicy = feePolicy;
        this.cryptoFacade = cryptoFacade;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWallet()
    {
        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;

        if (params == RegTestParams.get())
        {
            walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        }
        else if (params == MainNetParams.get())
        {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints"));
        }

        walletAppKit.setAutoSave(true);

        // add well known stable nodes
        //walletAppKit.peerGroup().addAddress();

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        walletAppKit.setDownloadListener(new BlockChainDownloadListener());
        walletAppKit.setBlockingStartup(false);
        walletAppKit.setUserAgent("BitSquare", "0.1");
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();

        wallet = (BitSquareWallet) walletAppKit.wallet();

        wallet.allowSpendingUnconfirmedTransactions();
        //walletAppKit.peerGroup().setMaxConnections(11);

        if (params == RegTestParams.get())
            walletAppKit.peerGroup().setMinBroadcastConnections(1);
       /* else
            walletAppKit.peerGroup().setMinBroadcastConnections(2);  */


        walletEventListener = new WalletEventListener()
        {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
                notifyBalanceListeners(tx);
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
            {
                log.debug("onTransactionConfidenceChanged " + tx.getConfidence());
                log.debug("onTransactionConfidenceChanged " + tx);
                notifyConfidenceListeners(tx);
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
                notifyBalanceListeners(tx);
            }

            @Override
            public void onReorganize(Wallet wallet)
            {
            }

            @Override
            public void onWalletChanged(Wallet wallet)
            {
            }

            @Override
            public void onKeysAdded(Wallet wallet, List<ECKey> keys)
            {
            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts)
            {
            }
        };
        wallet.addEventListener(walletEventListener);

        saveAddressEntryListId = this.getClass().getName() + ".addressEntryList";
        List<AddressEntry> savedAddressEntryList = (List<AddressEntry>) storage.read(saveAddressEntryListId);
        if (savedAddressEntryList != null)
        {
            addressEntryList = savedAddressEntryList;
        }
        else
        {
            ECKey registrationKey = wallet.getKeys().get(0);
            AddressEntry registrationAddressEntry = new AddressEntry(registrationKey, params, AddressEntry.AddressContext.REGISTRATION_FEE);
            addressEntryList.add(registrationAddressEntry);
            saveAddressInfoList();

            getNewTradeAddressInfo();
        }
    }

    public void shutDown()
    {
        wallet.removeEventListener(walletEventListener);

        walletAppKit.stopAsync();
        walletAppKit.awaitTerminated();
    }

    public Wallet getWallet()
    {
        return wallet;
    }

    private void saveAddressInfoList()
    {
        // use wallet extension?
        storage.write(saveAddressEntryListId, addressEntryList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addDownloadListener(DownloadListener listener)
    {
        downloadListeners.add(listener);
    }

    public void removeDownloadListener(DownloadListener listener)
    {
        downloadListeners.remove(listener);
    }

    public ConfidenceListener addConfidenceListener(ConfidenceListener listener)
    {
        log.debug("addConfidenceListener " + listener.getAddress().toString());
        confidenceListeners.add(listener);
        return listener;
    }

    public void removeConfidenceListener(ConfidenceListener listener)
    {
        log.debug("removeConfidenceListener " + listener.getAddress().toString());
        confidenceListeners.remove(listener);
    }

    public void addBalanceListener(BalanceListener listener)
    {
        balanceListeners.add(listener);
    }

    public void removeBalanceListener(BalanceListener listener)
    {
        balanceListeners.remove(listener);
    }

    private void notifyConfidenceListeners(Transaction tx)
    {
        for (int i = 0; i < confidenceListeners.size(); i++)
        {
            ConfidenceListener confidenceListener = confidenceListeners.get(i);
            List<TransactionOutput> transactionOutputs = tx.getOutputs();
            for (int n = 0; n < transactionOutputs.size(); n++)
            {
                TransactionOutput transactionOutput = transactionOutputs.get(n);
                if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
                {
                    Address address = transactionOutput.getScriptPubKey().getToAddress(params);
                    if (address.equals(confidenceListener.getAddress()))
                    {
                        log.debug("notifyConfidenceListeners address " + address.toString() + " / " + tx.getConfidence());
                        confidenceListener.onTransactionConfidenceChanged(tx.getConfidence());
                    }

                }
            }
        }
    }

    private void notifyBalanceListeners(Transaction tx)
    {
        for (int i = 0; i < balanceListeners.size(); i++)
        {
            BalanceListener balanceListener = balanceListeners.get(i);
            List<TransactionOutput> transactionOutputs = tx.getOutputs();
            for (int n = 0; n < transactionOutputs.size(); n++)
            {
                TransactionOutput transactionOutput = transactionOutputs.get(n);
                if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
                {
                    Address address = transactionOutput.getScriptPubKey().getToAddress(params);
                    if (address.equals(balanceListener.getAddress()))
                    {
                        balanceListener.onBalanceChanged(getBalanceForAddress(address));
                    }
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get AddressInfo objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<AddressEntry> getAddressEntryList()
    {
        return addressEntryList;
    }

    public AddressEntry getRegistrationAddressInfo()
    {
        return getAddressInfoByAddressContext(AddressEntry.AddressContext.REGISTRATION_FEE);
    }

    public AddressEntry getArbitratorDepositAddressInfo()
    {
        AddressEntry arbitratorDepositAddressEntry = getAddressInfoByAddressContext(AddressEntry.AddressContext.ARBITRATOR_DEPOSIT);
        if (arbitratorDepositAddressEntry == null)
            arbitratorDepositAddressEntry = getNewArbitratorDepositAddressInfo();

        return arbitratorDepositAddressEntry;
    }

    public AddressEntry getUnusedTradeAddressInfo()
    {
        List<AddressEntry> filteredList = Lists.newArrayList(Collections2.filter(addressEntryList, new Predicate<AddressEntry>()
        {
            @Override
            public boolean apply(@Nullable AddressEntry addressInfo)
            {
                return (addressInfo != null && addressInfo.getAddressContext().equals(AddressEntry.AddressContext.TRADE) && addressInfo.getTradeId() == null);
            }
        }));

        if (filteredList != null && filteredList.size() > 0)
            return filteredList.get(0);
        else
            return getNewTradeAddressInfo();
    }

    private AddressEntry getAddressInfoByAddressContext(AddressEntry.AddressContext addressContext)
    {
        List<AddressEntry> filteredList = Lists.newArrayList(Collections2.filter(addressEntryList, new Predicate<AddressEntry>()
        {
            @Override
            public boolean apply(@Nullable AddressEntry addressInfo)
            {
                return (addressInfo != null && addressContext != null && addressInfo.getAddressContext() != null && addressInfo.getAddressContext().equals(addressContext));
            }
        }));

        if (filteredList != null && filteredList.size() > 0)
            return filteredList.get(0);
        else
            return null;
    }

    public AddressEntry getAddressInfoByTradeID(String tradeId)
    {
        for (AddressEntry addressEntry : addressEntryList)
        {
            if (addressEntry.getTradeId() != null && addressEntry.getTradeId().equals(tradeId))
                return addressEntry;
        }

        AddressEntry addressEntry = getUnusedTradeAddressInfo();
        addressEntry.setTradeId(tradeId);
        return addressEntry;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create new AddressInfo objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressEntry getNewTradeAddressInfo()
    {
        return getNewAddressInfo(AddressEntry.AddressContext.TRADE);
    }

    private AddressEntry getNewAddressInfo(AddressEntry.AddressContext addressContext)
    {
        ECKey key = new ECKey();
        wallet.addKey(key);
        wallet.addWatchedAddress(key.toAddress(params));
        AddressEntry addressEntry = new AddressEntry(key, params, addressContext);
        addressEntryList.add(addressEntry);
        saveAddressInfoList();
        return addressEntry;
    }

    private AddressEntry getNewArbitratorDepositAddressInfo()
    {
        return getNewAddressInfo(AddressEntry.AddressContext.ARBITRATOR_DEPOSIT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TransactionConfidence
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TransactionConfidence getConfidenceForAddress(Address address)
    {
        Set<Transaction> transactions = wallet.getTransactions(true);
        if (transactions != null)
        {
            for (Transaction tx : transactions)
            {
                List<TransactionOutput> transactionOutputs = tx.getOutputs();
                for (int n = 0; n < transactionOutputs.size(); n++)
                {
                    TransactionOutput transactionOutput = transactionOutputs.get(n);
                    if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
                    {
                        Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                        //log.debug("addressOutput / address " + addressOutput.toString() + " / " + address.toString());
                        if (addressOutput.equals(address))
                        {
                            return tx.getConfidence();
                        }
                    }
                }


            }
        }
        return null;
    }

    public boolean isRegistrationFeeConfirmed()
    {
        TransactionConfidence transactionConfidence = getConfidenceForAddress(getRegistrationAddressInfo().getAddress());
        return transactionConfidence != null && transactionConfidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BigInteger getBalanceForAddress(Address address)
    {
        LinkedList<TransactionOutput> all = wallet.calculateAllSpendCandidates(true);
        BigInteger value = BigInteger.ZERO;
        for (TransactionOutput transactionOutput : all)
        {
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
            {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address))
                {
                    value = value.add(transactionOutput.getValue());
                }
            }
        }
        return value;
    }

    public BigInteger getWalletBalance()
    {
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED);
    }

    public BigInteger getRegistrationBalance()
    {
        return getBalanceForAddress(getRegistrationAddressInfo().getAddress());
    }

    public BigInteger getArbitratorDepositBalance()
    {
        return getBalanceForAddress(getArbitratorDepositAddressInfo().getAddress());
    }

    public boolean isRegistrationFeeBalanceNonZero()
    {
        return getRegistrationBalance().compareTo(BigInteger.ZERO) > 0;
    }

    public boolean isRegistrationFeeBalanceSufficient()
    {
        return getRegistrationBalance().compareTo(FeePolicy.ACCOUNT_REGISTRATION_FEE) >= 0;
    }

    public boolean isUnusedTradeAddressBalanceAboveCreationFee()
    {
        AddressEntry unUsedAddressEntry = getUnusedTradeAddressInfo();
        BigInteger unUsedAddressInfoBalance = getBalanceForAddress(unUsedAddressEntry.getAddress());
        return unUsedAddressInfoBalance.compareTo(FeePolicy.CREATE_OFFER_FEE) > 0;
    }

    public boolean isUnusedTradeAddressBalanceAboveTakeOfferFee()
    {
        AddressEntry unUsedAddressEntry = getUnusedTradeAddressInfo();
        BigInteger unUsedAddressInfoBalance = getBalanceForAddress(unUsedAddressEntry.getAddress());
        return unUsedAddressInfoBalance.compareTo(FeePolicy.TAKE_OFFER_FEE) > 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TODO
    ///////////////////////////////////////////////////////////////////////////////////////////


    //TODO
    public int getNumOfPeersSeenTx(String txID)
    {
        // TODO check from blockchain
        // will be async
        return 3;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Transactions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void payRegistrationFee(String stringifiedBankAccounts, FutureCallback<Transaction> callback) throws InsufficientMoneyException
    {
        log.debug("payRegistrationFee");
        log.trace("stringifiedBankAccounts " + stringifiedBankAccounts);

        Transaction tx = new Transaction(params);

        byte[] data = cryptoFacade.getEmbeddedAccountRegistrationData(getRegistrationAddressInfo().getKey(), stringifiedBankAccounts);
        tx.addOutput(Transaction.MIN_NONDUST_OUTPUT, new ScriptBuilder().op(OP_RETURN).data(data).build());

        BigInteger fee = FeePolicy.ACCOUNT_REGISTRATION_FEE.subtract(Transaction.MIN_NONDUST_OUTPUT).subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + BtcFormatter.btcToString(fee));
        tx.addOutput(fee, feePolicy.getAddressForRegistrationFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        // we don't allow spending of unconfirmed tx as with fake registrations we would open up doors for spam and market manipulation with fake offers
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getRegistrationAddressInfo(), false);
        sendRequest.changeAddress = getRegistrationAddressInfo().getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        log.debug("Registration transaction: " + tx.toString());
        printInputs("payRegistrationFee", tx);
    }

    public String payCreateOfferFee(String offerId, FutureCallback<Transaction> callback) throws InsufficientMoneyException
    {
        Transaction tx = new Transaction(params);
        BigInteger fee = FeePolicy.CREATE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + BtcFormatter.btcToString(fee));
        tx.addOutput(fee, feePolicy.getAddressForCreateOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getAddressInfoByTradeID(offerId), true);
        sendRequest.changeAddress = getAddressInfoByTradeID(offerId).getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printInputs("payTakeOfferFee", tx);
        log.debug("tx=" + tx.toString());

        return tx.getHashAsString();
    }

    public String payTakeOfferFee(String offerId, FutureCallback<Transaction> callback) throws InsufficientMoneyException
    {
        Transaction tx = new Transaction(params);
        BigInteger fee = FeePolicy.TAKE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + BtcFormatter.btcToString(fee));
        tx.addOutput(fee, feePolicy.getAddressForTakeOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, getAddressInfoByTradeID(offerId), true);
        sendRequest.changeAddress = getAddressInfoByTradeID(offerId).getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printInputs("payTakeOfferFee", tx);
        log.debug("tx=" + tx.toString());

        return tx.getHashAsString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    // 1. step: deposit tx
    // Offerer creates the 2of3 multiSig deposit tx with his unsigned input and change output
    public Transaction offererCreatesMSTxAndAddPayment(BigInteger offererInputAmount, String offererPubKey, String takerPubKey, String arbitratorPubKey, String tradeId) throws InsufficientMoneyException
    {
        log.debug("offererCreatesMSTxAndAddPayment");
        log.trace("inputs: ");
        log.trace("offererInputAmount=" + BtcFormatter.btcToString(offererInputAmount));
        log.trace("offererPubKey=" + offererPubKey);
        log.trace("takerPubKey=" + takerPubKey);
        log.trace("arbitratorPubKey=" + arbitratorPubKey);

        // We pay the offererInputAmount to a temporary MS output which will be changed later to the correct value.
        // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
        // Later with more customized coin selection we can use a custom CoinSelector implementation.
        // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
        // So it will not change the wallet balance.
        // The btc tx fee will be included by the completeTx() call, so we don't need to add it manually.
        Transaction tx = new Transaction(params);
        Script multiSigOutputScript = getMultiSigScript(offererPubKey, takerPubKey, arbitratorPubKey);
        tx.addOutput(offererInputAmount, multiSigOutputScript);

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        AddressEntry addressEntry = getAddressInfoByTradeID(tradeId);
        addressEntry.setTradeId(tradeId);
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
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
        log.debug("tx = " + tx.toString());
        return tx;
    }

    // 2. step: deposit tx
    // Taker adds his input and change output, changes the multiSig amount to the correct value and sign his input
    public Transaction takerAddPaymentAndSignTx(BigInteger takerInputAmount,
                                                BigInteger msOutputAmount,
                                                String offererPubKey,
                                                String takerPubKey,
                                                String arbitratorPubKey,
                                                String offerersPartialDepositTxAsHex,
                                                String tradeId
    ) throws InsufficientMoneyException, ExecutionException, InterruptedException, AddressFormatException
    {
        log.debug("takerAddPaymentAndSignTx");
        log.trace("inputs: ");
        log.trace("takerInputAmount=" + BtcFormatter.btcToString(takerInputAmount));
        log.trace("msOutputAmount=" + BtcFormatter.btcToString(msOutputAmount));
        log.trace("offererPubKey=" + offererPubKey);
        log.trace("takerPubKey=" + takerPubKey);
        log.trace("arbitratorPubKey=" + arbitratorPubKey);
        log.trace("offerersPartialDepositTxAsHex=" + offerersPartialDepositTxAsHex);

        // We pay the btc tx fee 2 times to the deposit tx:
        // 1. will be spent to miners when publishing the deposit tx
        // 2. will be as added to the MS amount, so when spending the payout tx the fee is already there and the outputs are not changed by fee reduction
        // Both traders pay 1 times a fee, so it is equally split between them

        // We do exactly the same as in the 1. step but with the takers input.
        Transaction tempTx = new Transaction(params);
        Script multiSigOutputScript = getMultiSigScript(offererPubKey, takerPubKey, arbitratorPubKey);
        tempTx.addOutput(takerInputAmount, multiSigOutputScript);

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tempTx);
        AddressEntry addressEntry = getAddressInfoByTradeID(tradeId);
        addressEntry.setTradeId(tradeId);
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        wallet.completeTx(sendRequest);

        printInputs("tempTx", tempTx);
        log.trace("tempTx=" + tempTx);
        // That tx has signed input, but we don't need to remove it as we don't send that tx out, it is just used temporary.

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
        if (tempTx.getOutputs().size() == 2)
            tx.addOutput(tempTx.getOutput(1));

        // We add the btc tx fee to the msOutputAmount and apply the change to the multiSig output
        msOutputAmount = msOutputAmount.add(FeePolicy.TX_FEE);
        tx.getOutput(0).setValue(msOutputAmount);

        // Now we sign our input
        TransactionInput input = tx.getInput(1);
        if (input == null || input.getConnectedOutput() == null)
            log.error("input or input.getConnectedOutput() is null: " + input);

        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        Sha256Hash hash = tx.hashForSignature(1, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature ecSig = sigKey.sign(hash);
        TransactionSignature txSig = new TransactionSignature(ecSig, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey())
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        else if (scriptPubKey.isSentToAddress())
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        else
            throw new ScriptException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);

        log.trace("check if it can be correctly spent for input 1");
        input.getScriptSig().correctlySpends(tx, 1, scriptPubKey, false);

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
        log.debug("tx = " + tx.toString());
        return tx;
    }


    // 3. step: deposit tx
    // Offerer signs tx and publishes it
    public Transaction offererSignAndPublishTx(String offerersFirstTxAsHex,
                                               String takersSignedTxAsHex,
                                               String takersSignedConnOutAsHex,
                                               String takersSignedScriptSigAsHex,
                                               long offererTxOutIndex,
                                               long takerTxOutIndex,
                                               FutureCallback<Transaction> callback) throws Exception
    {
        log.debug("offererSignAndPublishTx");
        log.trace("inputs: ");
        log.trace("offerersFirstTxAsHex=" + offerersFirstTxAsHex);
        log.trace("takersSignedTxAsHex=" + takersSignedTxAsHex);
        log.trace("takersSignedConnOutAsHex=" + takersSignedConnOutAsHex);
        log.trace("takersSignedScriptSigAsHex=" + takersSignedScriptSigAsHex);
        log.trace("callback=" + callback.toString());

        // We create an empty tx (did not find a way to manipulate a tx input, otherwise the takers tx could be used directly and add the offerers input and output)
        Transaction tx = new Transaction(params);

        // offerers first tx
        Transaction offerersFirstTx = new Transaction(params, Utils.parseAsHexOrBase58(offerersFirstTxAsHex));

        printInputs("offerersFirstTx", offerersFirstTx);
        log.trace("offerersFirstTx = " + offerersFirstTx.toString());

        // add input
        Transaction offerersFirstTxConnOut = wallet.getTransaction(offerersFirstTx.getInput(0).getOutpoint().getHash());    // pass that around!
        TransactionOutPoint offerersFirstTxOutPoint = new TransactionOutPoint(params, offererTxOutIndex, offerersFirstTxConnOut);
        //TransactionInput offerersFirstTxInput = new TransactionInput(params, tx, offerersFirstTx.getInput(0).getScriptBytes(), offerersFirstTxOutPoint);   // pass that around!  getScriptBytes = empty bytes aray
        TransactionInput offerersFirstTxInput = new TransactionInput(params, tx, new byte[]{}, offerersFirstTxOutPoint);   // pass that around!  getScriptBytes = empty bytes array
        offerersFirstTxInput.setParent(tx);
        tx.addInput(offerersFirstTxInput);

        // takers signed tx
        Transaction takersSignedTx = new Transaction(params, Utils.parseAsHexOrBase58(takersSignedTxAsHex));

        printInputs("takersSignedTxInput", takersSignedTx);
        log.trace("takersSignedTx = " + takersSignedTx.toString());

        // add input
        Transaction takersSignedTxConnOut = new Transaction(params, Utils.parseAsHexOrBase58(takersSignedConnOutAsHex));
        TransactionOutPoint takersSignedTxOutPoint = new TransactionOutPoint(params, takerTxOutIndex, takersSignedTxConnOut);
        TransactionInput takersSignedTxInput = new TransactionInput(params, tx, Utils.parseAsHexOrBase58(takersSignedScriptSigAsHex), takersSignedTxOutPoint);
        takersSignedTxInput.setParent(tx);
        tx.addInput(takersSignedTxInput);

        //TODO handle non change output cases
        // add outputs from takers tx, they are already correct
        tx.addOutput(takersSignedTx.getOutput(0));
        if (takersSignedTx.getOutputs().size() > 1)
            tx.addOutput(takersSignedTx.getOutput(1));
        if (takersSignedTx.getOutputs().size() == 3)
            tx.addOutput(takersSignedTx.getOutput(2));

        printInputs("tx", tx);
        log.trace("tx = " + tx.toString());
        log.trace("Wallet balance before signing: " + wallet.getBalance());

        // sign the input
        TransactionInput input = tx.getInput(0);
        if (input == null || input.getConnectedOutput() == null)
            log.error("input or input.getConnectedOutput() is null: " + input);

        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        Sha256Hash hash = tx.hashForSignature(0, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature ecSig = sigKey.sign(hash);
        TransactionSignature txSig = new TransactionSignature(ecSig, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey())
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        else if (scriptPubKey.isSentToAddress())
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        else
            throw new ScriptException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);

        input.getScriptSig().correctlySpends(tx, 0, scriptPubKey, false);
        log.trace("check if it can be correctly spent for input 0 OK");

        TransactionInput input1 = tx.getInput(1);
        scriptPubKey = input1.getConnectedOutput().getScriptPubKey();
        input1.getScriptSig().correctlySpends(tx, 1, scriptPubKey, false);
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
        log.debug("tx = " + tx.toString());

        log.trace("Wallet balance before broadcastTransaction: " + wallet.getBalance());
        log.trace("Check if wallet is consistent before broadcastTransaction: result=" + wallet.isConsistent());
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(tx);
        log.trace("Wallet balance after broadcastTransaction: " + wallet.getBalance());
        log.trace("Check if wallet is consistent: result=" + wallet.isConsistent());

        Futures.addCallback(broadcastComplete, callback);
        printInputs("tx", tx);
        log.debug("tx = " + tx.toString());
        return tx;
    }

    // 4 step deposit tx: Offerer send deposit tx to taker
    public String takerCommitDepositTx(String depositTxAsHex)
    {
        log.trace("takerCommitDepositTx");
        log.trace("inputs: ");
        log.trace("depositTxID=" + depositTxAsHex);
        Transaction depositTx = new Transaction(params, Utils.parseAsHexOrBase58(depositTxAsHex));
        log.trace("depositTx=" + depositTx);
        // boolean isAlreadyInWallet = wallet.maybeCommitTx(depositTx);
        //log.trace("isAlreadyInWallet=" + isAlreadyInWallet);

        try
        {
            // Manually add the multisigContract to the wallet, overriding the isRelevant checks so we can track
            // it and check for double-spends later
            wallet.receivePending(depositTx, null, true);
        } catch (VerificationException e)
        {
            throw new RuntimeException(e); // Cannot happen, we already called multisigContract.verify()
        }

        return depositTx.getHashAsString();

    }

    // 5. step payout tx: Offerer creates payout tx and signs it
    public Pair<ECKey.ECDSASignature, String> offererCreatesAndSignsPayoutTx(String depositTxID,
                                                                             BigInteger offererPaybackAmount,
                                                                             BigInteger takerPaybackAmount,
                                                                             String takerAddress,
                                                                             String tradeID) throws InsufficientMoneyException, AddressFormatException
    {
        log.debug("offererCreatesAndSignsPayoutTx");
        log.trace("inputs: ");
        log.trace("depositTxID=" + depositTxID);
        log.trace("offererPaybackAmount=" + BtcFormatter.btcToString(offererPaybackAmount));
        log.trace("takerPaybackAmount=" + BtcFormatter.btcToString(takerPaybackAmount));
        log.trace("takerAddress=" + takerAddress);

        // Offerer has published depositTx earlier, so he has it in his wallet
        Transaction depositTx = wallet.getTransaction(new Sha256Hash(depositTxID));
        String depositTxAsHex = Utils.bytesToHexString(depositTx.bitcoinSerialize());

        // We create the payout tx
        Transaction tx = createPayoutTx(depositTxAsHex, offererPaybackAmount, takerPaybackAmount, getAddressInfoByTradeID(tradeID).getAddressString(), takerAddress);

        // We create the signature for that tx
        TransactionOutput multiSigOutput = tx.getInput(0).getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature offererSignature = getAddressInfoByTradeID(tradeID).getKey().sign(sigHash);

        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig));
        tx.getInput(0).setScriptSig(inputScript);

        log.trace("sigHash=" + sigHash.toString());
        return new Pair<>(offererSignature, depositTxAsHex);
    }

    // 6. step payout tx: Taker signs and publish tx
    public Transaction takerSignsAndSendsTx(String depositTxAsHex,
                                            String offererSignatureR,
                                            String offererSignatureS,
                                            BigInteger offererPaybackAmount,
                                            BigInteger takerPaybackAmount,
                                            String offererAddress,
                                            String tradeID,
                                            FutureCallback<Transaction> callback) throws InsufficientMoneyException, AddressFormatException
    {
        log.debug("takerSignsAndSendsTx");
        log.trace("inputs: ");
        log.trace("depositTxAsHex=" + depositTxAsHex);
        log.trace("offererSignatureR=" + offererSignatureR);
        log.trace("offererSignatureS=" + offererSignatureS);
        log.trace("offererPaybackAmount=" + BtcFormatter.btcToString(offererPaybackAmount));
        log.trace("takerPaybackAmount=" + BtcFormatter.btcToString(takerPaybackAmount));
        log.trace("offererAddress=" + offererAddress);
        log.trace("callback=" + callback.toString());

        // We create the payout tx
        Transaction tx = createPayoutTx(depositTxAsHex, offererPaybackAmount, takerPaybackAmount, offererAddress, getAddressInfoByTradeID(tradeID).getAddressString());

        // We sign that tx with our key and apply the signature form the offerer
        TransactionOutput multiSigOutput = tx.getInput(0).getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        log.trace("sigHash=" + sigHash.toString());

        ECKey.ECDSASignature takerSignature = getAddressInfoByTradeID(tradeID).getKey().sign(sigHash);
        TransactionSignature takerTxSig = new TransactionSignature(takerSignature, Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature offererSignature = new ECKey.ECDSASignature(new BigInteger(offererSignatureR), new BigInteger(offererSignatureS));
        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);

        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig, takerTxSig));
        tx.getInput(0).setScriptSig(inputScript);

        log.trace("verify tx");
        tx.verify();

        log.trace("check if it can be correctly spent for ms input");
        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, multiSigScript, false);

        log.trace("verify multiSigOutput");
        tx.getInput(0).verify(multiSigOutput);

        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(tx);
        Futures.addCallback(broadcastComplete, callback);

        log.trace("getTransactions.size=" + wallet.getTransactions(true).size());
        log.trace("Check if wallet is consistent: result=" + wallet.isConsistent());
        printInputs("takerSignsAndSendsTx", tx);
        log.debug("tx = " + tx.toString());
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Script getMultiSigScript(String offererPubKey, String takerPubKey, String arbitratorPubKey)
    {
        ECKey offererKey = new ECKey(null, Utils.parseAsHexOrBase58(offererPubKey));
        ECKey takerKey = new ECKey(null, Utils.parseAsHexOrBase58(takerPubKey));
        ECKey arbitratorKey = new ECKey(null, Utils.parseAsHexOrBase58(arbitratorPubKey));

        List<ECKey> keys = ImmutableList.of(offererKey, takerKey, arbitratorKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    public Transaction createPayoutTx(String depositTxAsHex,
                                      BigInteger offererPaybackAmount,
                                      BigInteger takerPaybackAmount,
                                      String offererAddress,
                                      String takerAddress) throws AddressFormatException
    {
        log.trace("createPayoutTx");
        log.trace("inputs: ");
        log.trace("depositTxAsHex=" + depositTxAsHex);
        log.trace("offererPaybackAmount=" + BtcFormatter.btcToString(offererPaybackAmount));
        log.trace("takerPaybackAmount=" + BtcFormatter.btcToString(takerPaybackAmount));
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

    private void printInputs(String tracePrefix, Transaction tx)
    {
        for (TransactionInput input : tx.getInputs())
            if (input.getConnectedOutput() != null)
                log.trace(tracePrefix + ": " + BtcFormatter.btcToString(input.getConnectedOutput().getValue()));
            else
                log.trace(tracePrefix + ": " + "Transaction already has inputs but we don't have the connected outputs, so we don't know the value.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    private class BlockChainDownloadListener extends com.google.bitcoin.core.DownloadListener
    {
        @Override
        protected void progress(double percent, int blocksSoFar, Date date)
        {
            super.progress(percent, blocksSoFar, date);
            Platform.runLater(() -> onProgressInUserThread(percent, blocksSoFar, date));
        }

        @Override
        protected void doneDownload()
        {
            super.doneDownload();
            Platform.runLater(() -> onDoneDownloadInUserThread());
        }

        private void onProgressInUserThread(double percent, int blocksSoFar, final Date date)
        {
            for (DownloadListener downloadListener : downloadListeners)
                downloadListener.progress(percent, blocksSoFar, date);
        }

        private void onDoneDownloadInUserThread()
        {
            for (DownloadListener downloadListener : downloadListeners)
                downloadListener.doneDownload();
        }
    }

    public static interface DownloadListener
    {
        void progress(double percent, int blocksSoFar, Date date);

        void doneDownload();
    }

}