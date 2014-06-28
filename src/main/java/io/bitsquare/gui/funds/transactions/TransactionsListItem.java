package io.bitsquare.gui.funds.transactions;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionOutput;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BitSquareFormatter;
import java.math.BigInteger;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Tooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionsListItem
{
    private static final Logger log = LoggerFactory.getLogger(TransactionsListItem.class);
    private final StringProperty date = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final Transaction transaction;
    private final WalletFacade walletFacade;
    private final ConfidenceProgressIndicator progressIndicator;
    private final Tooltip tooltip;
    private String addressString;
    private ConfidenceListener confidenceListener;

    public TransactionsListItem(Transaction transaction, WalletFacade walletFacade)
    {
        this.transaction = transaction;
        this.walletFacade = walletFacade;

        BigInteger valueSentToMe = transaction.getValueSentToMe(walletFacade.getWallet());
        BigInteger valueSentFromMe = transaction.getValueSentFromMe(walletFacade.getWallet());
        Address address = null;
        if (valueSentToMe.compareTo(BigInteger.ZERO) == 0)
        {
            amount.set("-" + BtcFormatter.satoshiToString(valueSentFromMe));

            for (TransactionOutput transactionOutput : transaction.getOutputs())
            {
                if (!transactionOutput.isMine(walletFacade.getWallet()))
                {
                    type.set("Sent to");

                    if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
                    {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletFacade.getWallet().getParams());
                        addressString = address.toString();
                    }
                    else
                    {
                        addressString = "No sent to address script used.";
                    }
                }
            }
        }
        else if (valueSentFromMe.compareTo(BigInteger.ZERO) == 0)
        {
            amount.set(BtcFormatter.satoshiToString(valueSentToMe));
            type.set("Received with");

            for (TransactionOutput transactionOutput : transaction.getOutputs())
            {
                if (transactionOutput.isMine(walletFacade.getWallet()))
                {
                    if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
                    {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletFacade.getWallet().getParams());
                        addressString = address.toString();
                    }
                    else
                    {
                        addressString = "No sent to address script used.";
                    }
                }
            }
        }
        else
        {
            amount.set(BtcFormatter.satoshiToString(valueSentToMe.subtract(valueSentFromMe)));

            boolean outgoing = false;
            for (TransactionOutput transactionOutput : transaction.getOutputs())
            {
                if (!transactionOutput.isMine(walletFacade.getWallet()))
                {
                    outgoing = true;
                    if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isSentToP2SH())
                    {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletFacade.getWallet().getParams());
                        addressString = address.toString();
                    }
                    else
                    {
                        addressString = "No sent to address script used.";
                    }
                }
            }

            if (outgoing)
            {
                type.set("Sent to");
            }
            else
            {
                type.set("Internal (TX Fee)");
                addressString = "Internal swap between addresses.";
            }
        }

        date.set(BitSquareFormatter.formatDateTime(transaction.getUpdateTime()));

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefHeight(30);
        progressIndicator.setPrefWidth(30);
        Tooltip.install(progressIndicator, tooltip);

        if (address != null)
        {
            confidenceListener = walletFacade.addConfidenceListener(new ConfidenceListener(address)
            {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence)
                {
                    updateConfidence(confidence);
                }
            });

            updateConfidence(walletFacade.getConfidenceForAddress(address));
        }
    }


    public void cleanup()
    {
        walletFacade.removeConfidenceListener(confidenceListener);
    }

    private void updateConfidence(TransactionConfidence confidence)
    {
        if (confidence != null)
        {
            //log.debug("Type numBroadcastPeers getDepthInBlocks " + confidence.getConfidenceType() + " / " + confidence.numBroadcastPeers() + " / " + confidence.getDepthInBlocks());
            switch (confidence.getConfidenceType())
            {
                case UNKNOWN:
                    tooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    tooltip.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    tooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    tooltip.setText("Transaction is invalid.");
                    progressIndicator.setProgress(0);
                    break;
            }

            progressIndicator.setPrefSize(24, 24);
        }
    }


    public ConfidenceProgressIndicator getProgressIndicator()
    {
        return progressIndicator;
    }

    public final StringProperty dateProperty()
    {
        return this.date;
    }

    public final StringProperty amountProperty()
    {
        return this.amount;
    }

    public final StringProperty typeProperty()
    {
        return this.type;
    }

    public String getAddressString()
    {
        return addressString;
    }
}

