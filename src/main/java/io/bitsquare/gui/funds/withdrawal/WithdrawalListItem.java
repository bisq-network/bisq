package io.bitsquare.gui.funds.withdrawal;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.TransactionConfidence;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public class WithdrawalListItem
{
    private final StringProperty addressString = new SimpleStringProperty();
    private final BalanceListener balanceListener;

    private final Label balanceLabel;

    private final AddressEntry addressEntry;

    private final WalletFacade walletFacade;
    private final ConfidenceListener confidenceListener;

    private final ConfidenceProgressIndicator progressIndicator;

    private final Tooltip tooltip;

    private Coin balance;

    public WithdrawalListItem(AddressEntry addressEntry, WalletFacade walletFacade)
    {
        this.addressEntry = addressEntry;
        this.walletFacade = walletFacade;
        this.addressString.set(getAddress().toString());

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefSize(24, 24);
        Tooltip.install(progressIndicator, tooltip);

        confidenceListener = walletFacade.addConfidenceListener(new ConfidenceListener(getAddress())
        {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence)
            {
                updateConfidence(confidence);
            }
        });

        updateConfidence(walletFacade.getConfidenceForAddress(getAddress()));


        // balance
        balanceLabel = new Label();
        balanceListener = walletFacade.addBalanceListener(new BalanceListener(getAddress())
        {
            @Override
            public void onBalanceChanged(Coin balance)
            {
                updateBalance(balance);
            }
        });

        updateBalance(walletFacade.getBalanceForAddress(getAddress()));
    }

    public void cleanup()
    {
        walletFacade.removeConfidenceListener(confidenceListener);
        walletFacade.removeBalanceListener(balanceListener);
    }

    private void updateBalance(Coin balance)
    {
        this.balance = balance;
        if (balance != null)
        {
            //TODO use BitSquareFormatter
            balanceLabel.setText(balance.toFriendlyString());
        }
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
        }
    }


    public final String getLabel()
    {
        switch (addressEntry.getAddressContext())
        {
            case REGISTRATION_FEE:
                return "Registration fee";
            case TRADE:
                if (addressEntry.getTradeId() != null)
                {
                    return "Trade ID: " + addressEntry.getTradeId();
                }
                else
                {
                    return "Trade (not used yet)";
                }
            case ARBITRATOR_DEPOSIT:
                return "Arbitration deposit";
        }
        return "";
    }


    public final StringProperty addressStringProperty()
    {
        return this.addressString;
    }

    Address getAddress()
    {
        return addressEntry.getAddress();
    }


    public AddressEntry getAddressEntry()
    {
        return addressEntry;
    }


    public ConfidenceProgressIndicator getProgressIndicator()
    {
        return progressIndicator;
    }


    public Label getBalanceLabel()
    {
        return balanceLabel;
    }


    public Coin getBalance()
    {
        return balance;
    }
}
