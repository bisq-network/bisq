package io.bitsquare.gui.funds;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;


public class FundsController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(FundsController.class);
    private WalletFacade walletFacade;

    @FXML
    private TextField tradingAccountTextField, balanceTextField;
    @FXML
    private Label copyIcon, confirmationLabel;
    @FXML
    private ProgressIndicator progressIndicator;

    @Inject
    public FundsController(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;


    }

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        String tradingAccountAddress = walletFacade.getAddressAsString();
        tradingAccountTextField.setText(tradingAccountAddress);

        copyIcon.setId("copy-icon");
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(tradingAccountAddress);
            clipboard.setContent(content);
        });

        updateBalance(walletFacade.getBalance());

        walletFacade.getWallet().addEventListener(new WalletEventListener()
        {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
                updateBalance(newBalance);
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
            {
                updateConfidence(tx);
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
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
        });
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {

    }

    private void updateBalance(BigInteger balance)
    {
        if (balance.compareTo(BigInteger.ZERO) == 0)
        {
            confirmationLabel.setText("");
            progressIndicator.setOpacity(0);
            progressIndicator.setProgress(0);
        }
        else
        {
            progressIndicator.setOpacity(1);
            progressIndicator.setProgress(-1);
            Set<Transaction> transactions = walletFacade.getWallet().getTransactions(false);
            Transaction latestTransaction = null;
            for (Iterator<Transaction> iterator = transactions.iterator(); iterator.hasNext(); )
            {
                Transaction transaction = iterator.next();
                if (latestTransaction != null)
                {
                    if (transaction.getUpdateTime().compareTo(latestTransaction.getUpdateTime()) > 0)
                    {
                        latestTransaction = transaction;
                    }
                }
                else
                {
                    latestTransaction = transaction;
                }
            }
            if (latestTransaction != null)
            {
                updateConfidence(latestTransaction);
            }
        }
        balanceTextField.setText(Utils.bitcoinValueToFriendlyString(balance));
    }

    private void updateConfidence(Transaction tx)
    {
        TransactionConfidence confidence = tx.getConfidence();
        double progressIndicatorSize = 50;
        switch (confidence.getConfidenceType())
        {
            case UNKNOWN:
                confirmationLabel.setText("");
                progressIndicator.setProgress(0);
                break;
            case PENDING:
                confirmationLabel.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                progressIndicator.setProgress(-1.0);
                progressIndicatorSize = 20;
                break;
            case BUILDING:
                confirmationLabel.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                break;
            case DEAD:
                confirmationLabel.setText("Transaction is invalid.");
                break;
        }

        progressIndicator.setMaxHeight(progressIndicatorSize);
        progressIndicator.setPrefHeight(progressIndicatorSize);
        progressIndicator.setMaxWidth(progressIndicatorSize);
        progressIndicator.setPrefWidth(progressIndicatorSize);
    }

}

