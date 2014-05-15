package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bitsquare.BitSquare;
import io.bitsquare.util.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.bitcoin.script.ScriptOpCodes.OP_RETURN;

public class AccountRegistrationWallet extends Wallet implements WalletEventListener
{
    private static final Logger log = LoggerFactory.getLogger(AccountRegistrationWallet.class);
    private final File walletFile;

    private NetworkParameters networkParameters;
    private BlockChain chain;
    private PeerGroup peerGroup;
    private List<WalletFacade.WalletListener> walletListeners = new ArrayList<>();

    AccountRegistrationWallet(NetworkParameters networkParameters, BlockChain chain, PeerGroup peerGroup)
    {
        super(networkParameters);

        this.networkParameters = networkParameters;
        this.chain = chain;
        this.peerGroup = peerGroup;

        walletFile = new File(Utilities.getRootDir() + "account_reg_" + BitSquare.ID + ".wallet");
        if (walletFile.exists())
        {
            FileInputStream walletStream = null;
            try
            {
                walletStream = new FileInputStream(walletFile);
            } catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }

            try
            {
                new WalletProtobufSerializer().readWallet(WalletProtobufSerializer.parseToProto(walletStream), this);
            } catch (UnreadableWalletException | IOException e)
            {
                e.printStackTrace();
            }

        }
        else
        {
            addKey(new ECKey());
        }

        chain.addWallet(this);
        peerGroup.addWallet(this);
        try
        {
            saveToFile(walletFile);
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        autosaveToFile(walletFile, 1, TimeUnit.SECONDS, null);

        allowSpendingUnconfirmedTransactions();
    }

    void shutDown()
    {
        try
        {
            saveToFile(walletFile);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    Address getAddress()
    {
        return getKey().toAddress(networkParameters);
    }

    ECKey getKey()
    {
        return getKeys().get(0);
    }

    void addWalletListener(WalletFacade.WalletListener listener)
    {
        if (walletListeners.size() == 0)
            addEventListener(this);

        walletListeners.add(listener);
    }

    void removeWalletListener(WalletFacade.WalletListener listener)
    {
        walletListeners.remove(listener);

        if (walletListeners.size() == 0)
            removeEventListener(this);
    }

    void saveToBlockchain(byte[] dataToEmbed) throws InsufficientMoneyException
    {
        Script script = new ScriptBuilder()
                .op(OP_RETURN)
                .data(dataToEmbed)
                .build();
        Transaction transaction = new Transaction(networkParameters);
        TransactionOutput dataOutput = new TransactionOutput(networkParameters,
                transaction,
                Transaction.MIN_NONDUST_OUTPUT,
                script.getProgram());
        transaction.addOutput(dataOutput);
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(transaction);

        // give fee to miners yet. Later it could be spent to other traders via lottery...
        sendRequest.fee = Fees.ACCOUNT_REGISTRATION_FEE;

        Wallet.SendResult sendResult = sendCoins(sendRequest);

        //TODO
        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction result)
            {
                log.info("sendResult onSuccess:" + result.toString());
                // Platform.runLater(overlayUi::done);
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.warn("sendResult onFailure:" + t.toString());
                // We died trying to empty the wallet.
                // crashAlert(t);
            }
        });

        //TODO
        sendResult.tx.getConfidence().addEventListener((tx, reason) -> {
            //if (reason == TransactionConfidence.Listener.ChangeReason.SEEN_PEERS)
            //updateTitleForBroadcast();
        });
    }


    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
    {
        for (WalletFacade.WalletListener walletListener : walletListeners)
            walletListener.onCoinsReceived(newBalance);

        // log.info("onCoinsReceived");
    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
    {
        for (WalletFacade.WalletListener walletListener : walletListeners)
            walletListener.onConfidenceChanged(tx.getConfidence().numBroadcastPeers(), WalletUtil.getConfDepthInBlocks(this));

        // log.info("onTransactionConfidenceChanged " + tx.getConfidence().toString());
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
        // log.info("onWalletChanged");
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

    int getConfNumBroadcastPeers()
    {
        Transaction transaction = WalletUtil.getTransaction(this);
        return (transaction == null || transaction.getConfidence() == null) ? 0 : transaction.getConfidence().numBroadcastPeers();
    }

}
