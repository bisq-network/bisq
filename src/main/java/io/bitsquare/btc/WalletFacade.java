package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.bitcoin.crypto.TransactionSignature;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.utils.Threading;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.gui.util.Popups;
import javafx.application.Platform;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * That facade delivers wallet functionality from the bitcoinJ library
 * Code from BitcoinJ must not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
public class WalletFacade implements WalletEventListener
{
    public static final String MAIN_NET = "MAIN_NET";
    public static final String TEST_NET = "TEST_NET";

    public static String WALLET_PREFIX;

    // for testing trade process between offerer and taker
    //public static String WALLET_PREFIX = "offerer"; // offerer
    //public static String WALLET_PREFIX = "taker"; // offerer

    //public static  String WALLET_PREFIX = "bitsquare";


    private static final Logger log = LoggerFactory.getLogger(WalletFacade.class);

    private NetworkParameters params;
    private WalletAppKit walletAppKit;
    private CryptoFacade cryptoFacade;

    // that wallet is used only for the registration process
    private AccountRegistrationWallet accountRegistrationWallet = null;

    private List<DownloadListener> downloadListeners = new ArrayList<>();
    private List<WalletListener> walletListeners = new ArrayList<>();
    private Wallet wallet;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletFacade(NetworkParameters params, WalletAppKit walletAppKit, CryptoFacade cryptoFacade)
    {
        this.params = params;
        this.walletAppKit = walletAppKit;
        this.cryptoFacade = cryptoFacade;
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

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        walletAppKit.setDownloadListener(new BlockChainDownloadListener())
                .setBlockingStartup(false)
                .setUserAgent("BitSquare", "0.1");
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();

        wallet = walletAppKit.wallet();

        //wallet.allowSpendingUnconfirmedTransactions();
        walletAppKit.peerGroup().setMaxConnections(20);

        wallet.addEventListener(this);

        // testTradeProcessDepositTx();
        // testTradeProcessPayOutTx();
    }

    public void shutDown()
    {
        if (accountRegistrationWallet != null)
            accountRegistrationWallet.shutDown();
        walletAppKit.stopAsync();
        walletAppKit.awaitTerminated();
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

    public void addWalletListener(WalletListener listener)
    {
        walletListeners.add(listener);
    }

    public void removeWalletListener(WalletListener listener)
    {
        walletListeners.remove(listener);
    }

    public void addRegistrationWalletListener(WalletListener listener)
    {
        getAccountRegistrationWallet().addWalletListener(listener);
    }

    public void removeRegistrationWalletListener(WalletListener listener)
    {
        getAccountRegistrationWallet().removeWalletListener(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trading wallet
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BigInteger getBalance()
    {
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED);
    }

    public String getAddress()
    {
        return wallet.getKeys().get(0).toAddress(params).toString();
    }

    public String payFee(BigInteger fee, FutureCallback<Transaction> callback) throws InsufficientMoneyException
    {
        Transaction tx = new Transaction(params);
        //TransactionOutput output = new TransactionOutput(params, tx, Transaction.MIN_NONDUST_OUTPUT, WalletUtil.getEmptyOP_RETURNScript());
        tx.addOutput(Transaction.MIN_NONDUST_OUTPUT, WalletUtil.getEmptyOP_RETURNScript());
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);

        // give fee to miners yet. Later it could be spent to other traders via lottery...
        sendRequest.fee = fee;

        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        return tx.getHashAsString();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Account registration
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Address getAccountRegistrationAddress()
    {
        return getAccountRegistrationWallet().getAddress();
    }

    public String getAccountRegistrationPubKey()
    {
        return Utils.bytesToHexString(getAccountRegistrationWallet().getKey().getPubKey());
    }

    public BigInteger getAccountRegistrationBalance()
    {
        return getAccountRegistrationWallet().getBalance(Wallet.BalanceType.ESTIMATED);
    }

    public void sendRegistrationTx(String stringifiedBankAccounts) throws InsufficientMoneyException
    {
        getAccountRegistrationWallet().saveToBlockchain(cryptoFacade.getEmbeddedAccountRegistrationData(getAccountRegistrationWallet().getKey(), stringifiedBankAccounts));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getRegConfNumBroadcastPeers()
    {
        return getAccountRegistrationWallet().getConfNumBroadcastPeers();
    }

    public int getRegConfDepthInBlocks()
    {
        return WalletUtil.getConfDepthInBlocks(getAccountRegistrationWallet());
    }

    public ECKey getAccountKey()
    {
        return getAccountRegistrationWallet().getKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: WalletEventListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
    {
        for (WalletListener walletListener : walletListeners)
            walletListener.onCoinsReceived(newBalance);

        log.info("onCoinsReceived");
    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
    {
        for (WalletListener walletListener : walletListeners)
            walletListener.onConfidenceChanged(tx.getConfidence().numBroadcastPeers(), WalletUtil.getConfDepthInBlocks(wallet));

        log.info("onTransactionConfidenceChanged " + tx.getConfidence().toString());
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
    {
        log.info("onCoinsSent");
    }

    @Override
    public void onReorganize(Wallet wallet)
    {
        log.info("onReorganize");
    }

    @Override
    public void onWalletChanged(Wallet wallet)
    {
        log.info("onWalletChanged");
    }

    @Override
    public void onKeysAdded(Wallet wallet, List<ECKey> keys)
    {
        log.info("onKeysAdded");
    }

    @Override
    public void onScriptsAdded(Wallet wallet, List<Script> scripts)
    {
        log.info("onScriptsAdded");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AccountRegistrationWallet getAccountRegistrationWallet()
    {
        if (accountRegistrationWallet == null)
            accountRegistrationWallet = new AccountRegistrationWallet(params, walletAppKit.chain(), walletAppKit.peerGroup());

        return accountRegistrationWallet;
    }

    private Script getMultiSigScript(String offererPubKey, String takerPubKey, String arbitratorPubKey)
    {
        ECKey offererKey = new ECKey(null, Utils.parseAsHexOrBase58(offererPubKey));
        ECKey takerKey = new ECKey(null, Utils.parseAsHexOrBase58(takerPubKey));
        ECKey arbitratorKey = new ECKey(null, Utils.parseAsHexOrBase58(arbitratorPubKey));

        List<ECKey> keys = ImmutableList.of(offererKey, takerKey, arbitratorKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // testTradeProcess methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO only temp. for testing trade process between offerer and taker
    // deposit
    private void testTradeProcessDepositTx()
    {
        try
        {
            String tx1AsHex, tx2AsHex, tx2ScriptSigAsHex, tx2ConnOutAsHex;

            BigInteger offererAmount = Utils.toNanoCoins("0.01");
            BigInteger takerAmount = Utils.toNanoCoins("0.02");

            String takerPubKey = "0207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b6568";
            String offererPubKey = "0352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7";
            String arbitratorPubKey = "";

            // 1 offerer creates MS TX and pay in
          /* Transaction tx1 = offererCreatesMSTxAndAddPayment(offererAmount, offererPubKey, takerPubKey, arbitratorPubKey);
            tx1AsHex = Utils.bytesToHexString(tx1.bitcoinSerialize());
              */

            tx1AsHex = "01000000014378dfcd19add18eb6f118a1e35ced127ff23c9dc5034eee1cda5b9caeb814f0000000006b4830450221008e599dd7bb7223c7b036869198b14f08009f9bc117709d23c249d0bdd6b483be022047be181f467782ea277b36890feb2f6de3ceddcedf8730a9f505bac36b3b015b01210352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7ffffffff0240420f00000000004852210352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7210207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b65680053aeb077e605000000001976a9149fc3d8e0371b6eab89a8c3c015839f9e493ccf6588ac00000000";

            // 2. taker pay in and sign
           /* Transaction tx2 = takerAddPaymentAndSign(takerAmount, msOutputAmount, offererPubKey, takerPubKey, arbitratorPubKey, tx1AsHex);
            tx2AsHex = Utils.bytesToHexString(tx2.bitcoinSerialize());
            tx2ScriptSigAsHex = Utils.bytesToHexString(tx2.getInput(1).getScriptBytes());
            tx2ConnOutAsHex =  Utils.bytesToHexString(tx2.getInput(1).getConnectedOutput().getParentTransaction().bitcoinSerialize());
                    */

            tx2AsHex = "01000000024378dfcd19add18eb6f118a1e35ced127ff23c9dc5034eee1cda5b9caeb814f0000000006b4830450221008e599dd7bb7223c7b036869198b14f08009f9bc117709d23c249d0bdd6b483be022047be181f467782ea277b36890feb2f6de3ceddcedf8730a9f505bac36b3b015b01210352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7ffffffffa58b22a93a0fcf99ba48aa3b96d842284b2b3d24f72d045cc192ea8a6b89435c010000006a47304402207f4beeb1a86432be0b4c3d4f4db7416b52b66c84383d1980d39e21d547a1762f02200405d0d4b80d1094e3a08cb39ef6f1161be163026d417af08d54c5a1cfdbbbeb01210207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b6568ffffffff03c0c62d00000000004852210352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7210207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b65680053aeb077e605000000001976a9149fc3d8e0371b6eab89a8c3c015839f9e493ccf6588ac7035d705000000001976a914e5175c1f71c28218306d4a27c8cec0269dddbbde88ac00000000";
            tx2ScriptSigAsHex = "47304402207f4beeb1a86432be0b4c3d4f4db7416b52b66c84383d1980d39e21d547a1762f02200405d0d4b80d1094e3a08cb39ef6f1161be163026d417af08d54c5a1cfdbbbeb01210207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b6568";
            tx2ConnOutAsHex = "01000000014378dfcd19add18eb6f118a1e35ced127ff23c9dc5034eee1cda5b9caeb814f0010000006a473044022011431387fc19b093b26a6d2371995c828179aae68e94ad5804e5d0986a6b471302206abc2b698375620e65fc9970b7781da0af2179d1bdc4ebc82a13e285359a3ce7012103c7b9e9ef657705522c85b8429bb2b42c04f0fd4a09e0605cd7dd62ffecb57944ffffffff02c0ce823e000000001976a9142d1b4347ae850805f3badbb4b2949674f46c4ccd88ac00e1f505000000001976a914e5175c1f71c28218306d4a27c8cec0269dddbbde88ac00000000";

            // 3. offerer sign and send
            Transaction tx3 = offererSignAndSendTx(tx1AsHex, tx2AsHex, tx2ConnOutAsHex, tx2ScriptSigAsHex);

            log.info(tx3.toString());  // tx has 453 Bytes

        } catch (AddressFormatException | InsufficientMoneyException | InterruptedException | ExecutionException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (Exception e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    // payout
    private void testTradeProcessPayOutTx()
    {
        String depositTxAsHex, offererSignatureR, offererSignatureS;
        BigInteger offererPaybackAmount = Utils.toNanoCoins("0.029");
        BigInteger takerPaybackAmount = Utils.toNanoCoins("0.001");

        ECKey takerKey = new ECKey(null, Utils.parseAsHexOrBase58("0207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b6568"));
        String takerAddress = takerKey.toAddress(params).toString();

        ECKey offererKey = new ECKey(null, Utils.parseAsHexOrBase58("0352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7"));
        String offererAddress = offererKey.toAddress(params).toString();

        String depositTxID = "4142ee4877eb116abf955a7ec6ef2dc38133b793df762b76d75e3d7d4d8badc9";

        try
        {
            // 1. offerer create and sign payout tx
           /* Pair<ECKey.ECDSASignature, Transaction> result = offererCreateAndSignPayoutTx(depositTxID,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    takerAddress);

            ECKey.ECDSASignature offererSignature = result.getKey();
            Transaction depositTx = result.getValue();
            offererSignatureR = offererSignature.r.toString();
            offererSignatureS = offererSignature.s.toString();
            depositTxAsHex = Utils.bytesToHexString(depositTx.bitcoinSerialize()); */


            depositTxAsHex = "01000000024378dfcd19add18eb6f118a1e35ced127ff23c9dc5034eee1cda5b9caeb814f0000000006a473044022008addf33a37f8f058e629020d73c3873953e1eca559fcb59d9db651f2c9b524f02203cbfd319b2675974adfbff2cb605fc9b89a92f2f3197ee634b39c892fb57d1be01210352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7ffffffffa58b22a93a0fcf99ba48aa3b96d842284b2b3d24f72d045cc192ea8a6b89435c010000006a47304402207f4beeb1a86432be0b4c3d4f4db7416b52b66c84383d1980d39e21d547a1762f02200405d0d4b80d1094e3a08cb39ef6f1161be163026d417af08d54c5a1cfdbbbeb01210207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b6568ffffffff03c0c62d00000000004852210352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7210207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b65680053aeb077e605000000001976a9149fc3d8e0371b6eab89a8c3c015839f9e493ccf6588ac7035d705000000001976a914e5175c1f71c28218306d4a27c8cec0269dddbbde88ac00000000";
            offererSignatureR = "64562406184784382000330465585294975882418886289667123258365244289311131050102";
            offererSignatureS = "9282449224979858852843663340827968850695102089943078462704407873774672151491";

            // 2. taker sign and publish tx
            Transaction takerTx = takerSignAndSendTx(depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererAddress);

            log.info(takerTx.toString());    // tx has 265 Bytes

            /*
        6606c366a487bff9e412d0b6c09c14916319932db5954bf5d8719f43f828a3ba: Unknown confidence level.
     in   [] [30450221008ebd06e53ce1ab7b599098b3117f2073b1d224baae21190ef7839b70a638207602201485ae19965f2d954398f691aa40fecb4d08d625ae96037a4fc5eecb7a4803c301] [30440220521c485045a46fbb61c634ebf456c470f9c2f7307a743e3fce10b50f7a69115d0220249ff5f9796a9fcd12984afcfd3f7d16d2f8c49ddcef245db7c7b02b909af9a601]
          outpoint:4142ee4877eb116abf955a7ec6ef2dc38133b793df762b76d75e3d7d4d8badc9:0 hash160:[exception: Script not in the standard scriptPubKey form]
     out  DUP HASH160 [9fc3d8e0371b6eab89a8c3c015839f9e493ccf65] EQUALVERIFY CHECKSIG 0.0289 BTC
     out  DUP HASH160 [e5175c1f71c28218306d4a27c8cec0269dddbbde] EQUALVERIFY CHECKSIG 0.0009 BTC

           2 [0352f2e34760514099f90b03aab91239466924c3b06047d3cf0e011f26ef96ceb7] [0207cf5fb65d6923d5d41db21ceac9567a0fc3eb92c6137f274018381ced7b6568] [] 3 CHECKMULTISIG
         */

        } catch (InsufficientMoneyException | AddressFormatException e)
        {
            e.printStackTrace();
        }

    }

    // deposit 1. offerer
    private Transaction offererCreatesMSTxAndAddPayment(BigInteger offererAmount, String offererPubKey, String takerPubKey, String arbitratorPubKey) throws InsufficientMoneyException
    {
        // use that to use the convenient api for getting the best coin selection and fee calculation
        // TODO should be constructed manually
        Transaction tx = new Transaction(params);
        Script multiSigOutputScript = getMultiSigScript(offererPubKey, takerPubKey, arbitratorPubKey);
        tx.addOutput(offererAmount, multiSigOutputScript);
        Wallet.SendRequest request = Wallet.SendRequest.forTx(tx);
        wallet.completeTx(request);
        // TODO remove sig or use SigHash.NONE
        //tx.getInput(0).setScriptSig(null);

         /*
        IN[0] offerer
        OUT[0] MS
        OUT[1] offerer change
         */
        return tx;
    }

    // deposit 2. taker
    public Transaction takerAddPaymentAndSign(BigInteger takerAmount,
                                              BigInteger msOutputAmount,
                                              String offererPubKey,
                                              String takerPubKey,
                                              String arbitratorPubKey,
                                              String tx1AsHex
    ) throws InsufficientMoneyException, ExecutionException, InterruptedException, AddressFormatException
    {
        Script multiSigOutputScript = getMultiSigScript(offererPubKey, takerPubKey, arbitratorPubKey);

        // use that to use the convenient api for getting the best coin selection and fee calculation
        // TODO should be constructed manually
        Transaction dummyTx = new Transaction(params);
        dummyTx.addOutput(takerAmount, multiSigOutputScript);
        wallet.completeTx(Wallet.SendRequest.forTx(dummyTx));

        Transaction tx = new Transaction(params, Utils.parseAsHexOrBase58(tx1AsHex));
        tx.addInput(dummyTx.getInput(0));
        tx.addOutput(dummyTx.getOutput(1));
        tx.getOutput(0).setValue(msOutputAmount);

        TransactionInput input = tx.getInput(1);
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

        input.getScriptSig().correctlySpends(tx, 1, scriptPubKey, false);

        /*
        IN[0] offerer
        IN[1] taker signed
        OUT[0] MS
        OUT[1] offerer change
        OUT[2] taker change
         */

        return tx;
    }

    // deposit 3. offerer
    public Transaction offererSignAndSendTx(String tx1AsHex,
                                            String tx2AsHex,
                                            String tx2ConnOutAsHex,
                                            String tx2ScriptSigAsHex) throws Exception
    {
        Transaction tx = new Transaction(params);

        Transaction tx1 = new Transaction(params, Utils.parseAsHexOrBase58(tx1AsHex));
        Transaction tx1ConnOut = wallet.getTransaction(tx1.getInput(0).getOutpoint().getHash());
        TransactionOutPoint tx1OutPoint = new TransactionOutPoint(params, 0, tx1ConnOut);
        TransactionInput tx1Input = new TransactionInput(params, tx, tx1.getInput(0).getScriptBytes(), tx1OutPoint);
        tx1Input.setParent(tx);
        tx.addInput(tx1Input);

        Transaction tx2 = new Transaction(params, Utils.parseAsHexOrBase58(tx2AsHex));
        Transaction tx2ConnOut = new Transaction(params, Utils.parseAsHexOrBase58(tx2ConnOutAsHex));
        TransactionOutPoint tx2OutPoint = new TransactionOutPoint(params, 1, tx2ConnOut);
        TransactionInput tx2Input = new TransactionInput(params, tx, Utils.parseAsHexOrBase58(tx2ScriptSigAsHex), tx2OutPoint);
        tx2Input.setParent(tx);
        tx.addInput(tx2Input);

        tx.addOutput(tx2.getOutput(0));
        tx.addOutput(tx2.getOutput(1));
        tx.addOutput(tx2.getOutput(2));

        TransactionInput input = tx.getInput(0);
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

        input = tx.getInput(1);
        scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        input.getScriptSig().correctlySpends(tx, 1, scriptPubKey, false);

         /*
        IN[0] offerer signed
        IN[1] taker signed
        OUT[0] MS
        OUT[1] offerer change
        OUT[2] taker change
         */

        tx.verify();

        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(tx);

        FutureCallback callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.info("sendResult onSuccess:" + transaction.toString());
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.warn("sendResult onFailure:" + t.toString());
                Popups.openErrorPopup("Fee payment failed", "Fee payment failed. " + t.toString());
            }
        };
        Futures.addCallback(broadcastComplete, callback);

        return tx;
    }

    // payout 1. offerer
    private Pair<ECKey.ECDSASignature, Transaction> offererCreateAndSignPayoutTx(String depositTxID,
                                                                                 BigInteger offererPaybackAmount,
                                                                                 BigInteger takerPaybackAmount,
                                                                                 String takerAddress) throws InsufficientMoneyException, AddressFormatException
    {
        ECKey key = wallet.getKeys().get(0);
        // offerer has published depositTx so he has it in wallet
        Transaction depositTx = wallet.getTransaction(new Sha256Hash(depositTxID));
        TransactionOutput multiSigOutput = depositTx.getOutput(0);
        Script multiSigScript = multiSigOutput.getScriptPubKey();

        Transaction tx = new Transaction(params);
        tx.addInput(multiSigOutput);
        //TODO fee calculation
        tx.addOutput(offererPaybackAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE), key.toAddress(params));
        tx.addOutput(takerPaybackAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE), new Address(params, takerAddress));

        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = key.sign(sigHash);

        return new Pair<>(signature, depositTx);
    }

    // payout 2. taker
    private Transaction takerSignAndSendTx(String depositTxAsHex,
                                           String offererSignatureR,
                                           String offererSignatureS,
                                           BigInteger offererPaybackAmount,
                                           BigInteger takerPaybackAmount,
                                           String offererAddress) throws InsufficientMoneyException, AddressFormatException
    {
        ECKey key = wallet.getKeys().get(0);

        Transaction depositTx = new Transaction(params, Utils.parseAsHexOrBase58(depositTxAsHex));
        TransactionOutput multiSigOutput = depositTx.getOutput(0);
        Script multiSigScript = multiSigOutput.getScriptPubKey();

        Transaction tx = new Transaction(params);
        tx.addInput(multiSigOutput);
        //TODO fee calculation
        tx.addOutput(offererPaybackAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE), new Address(params, offererAddress));
        tx.addOutput(takerPaybackAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE), key.toAddress(params));

        Sha256Hash sigHash = tx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature takerSignature = key.sign(sigHash);
        TransactionSignature takerTxSig = new TransactionSignature(takerSignature, Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature offererSignature = new ECKey.ECDSASignature(new BigInteger(offererSignatureR), new BigInteger(offererSignatureS));
        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);

        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig, takerTxSig));
        tx.getInput(0).setScriptSig(inputScript);

        tx.verify();
        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, multiSigScript, false);
        tx.getInput(0).verify(multiSigOutput);

        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(tx);

        FutureCallback callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.info("sendResult onSuccess:" + transaction.toString());
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.warn("sendResult onFailure:" + t.toString());
                Popups.openErrorPopup("Fee payment failed", "Fee payment failed. " + t.toString());
            }
        };
        Futures.addCallback(broadcastComplete, callback);

        return tx;
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
            for (DownloadListener downloadListener : downloadListeners)
                downloadListener.progress(percent, blocksSoFar, date);
        }

        @Override
        protected void doneDownload()
        {
            super.doneDownload();
            for (DownloadListener downloadListener : downloadListeners)
                downloadListener.doneDownload();
        }
    }

    public static interface DownloadListener
    {
        void progress(double percent, int blocksSoFar, Date date);

        void doneDownload();
    }

    public static interface WalletListener
    {
        void onConfidenceChanged(int numBroadcastPeers, int depthInBlocks);

        void onCoinsReceived(BigInteger newBalance);
    }

}