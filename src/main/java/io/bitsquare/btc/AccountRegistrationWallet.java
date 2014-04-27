package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.bitcoin.script.ScriptOpCodes.OP_RETURN;

public class AccountRegistrationWallet extends Wallet implements WalletEventListener
{
    private static final Logger log = LoggerFactory.getLogger(AccountRegistrationWallet.class);

    private NetworkParameters networkParameters;

    public AccountRegistrationWallet(NetworkParameters networkParameters, BlockChain chain, PeerGroup peerGroup)
    {
        super(networkParameters);

        this.networkParameters = networkParameters;

        File walletFile = new File(".", "bitsquare_account_reg" + ".wallet");
        if (walletFile.exists())
        {
            try
            {
                FileInputStream walletStream = new FileInputStream(walletFile);
                new WalletProtobufSerializer().readWallet(WalletProtobufSerializer.parseToProto(walletStream), this);
            } catch (FileNotFoundException e)
            {
                e.printStackTrace();
            } catch (UnreadableWalletException e)
            {
                e.printStackTrace();
            } catch (IOException e)
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
        autosaveToFile(walletFile, 1, TimeUnit.SECONDS, null);
    }

    public Address getAddress()
    {
        return getKey().toAddress(networkParameters);
    }

    public ECKey getKey()
    {
        return getKeys().get(0);
    }

    public void saveToBlockchain(byte[] dataToEmbed) throws InsufficientMoneyException
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

    public int getConfirmations()
    {
        // TODO just a quick impl. need to be checked if it works for all cases...
        Set<Transaction> transactions = getTransactions(true);
        if (transactions != null && transactions.size() == 1)
        {
            Transaction transaction = transactions.iterator().next();
            final int lastBlockSeenHeight = getLastBlockSeenHeight();
            int appearedAtChainHeight = 0;
            if (transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                appearedAtChainHeight = transaction.getConfidence().getAppearedAtChainHeight();

            final int numberOfBlocksEmbedded = lastBlockSeenHeight - appearedAtChainHeight + 1;

            if (numberOfBlocksEmbedded > 0)
                return numberOfBlocksEmbedded;
            else
                return 0;
        }
        return 0;
    }

    public int getNumberOfPeersSeenTx()
    {
        // TODO just a quick impl. need to be checked if it works for all cases...
        Set<Transaction> transactions = getTransactions(true);
        if (transactions != null && transactions.size() == 1)
        {
            Transaction transaction = transactions.iterator().next();
            return (transaction == null || transaction.getConfidence() == null) ? 0 : transaction.getConfidence().numBroadcastPeers();
        }
        return 0;
    }

    //TODO those handlers are not called yet...
    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
    {
        log.info("onCoinsReceived");
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
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
    {
        log.info("onTransactionConfidenceChanged");
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


}
