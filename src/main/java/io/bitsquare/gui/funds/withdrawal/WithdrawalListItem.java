package io.bitsquare.gui.funds.withdrawal;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.TransactionConfidence;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import java.math.BigInteger;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WithdrawalListItem
{
    private final StringProperty addressString = new SimpleStringProperty();
    private final BalanceListener balanceListener;
    @NotNull
    private final Label balanceLabel;
    @NotNull
    private final AddressEntry addressEntry;
    @NotNull
    private final WalletFacade walletFacade;
    private final ConfidenceListener confidenceListener;
    @NotNull
    private final ConfidenceProgressIndicator progressIndicator;
    @NotNull
    private final Tooltip tooltip;
    @Nullable
    private BigInteger balance;

    public WithdrawalListItem(@NotNull AddressEntry addressEntry, @NotNull WalletFacade walletFacade)
    {
        this.addressEntry = addressEntry;
        this.walletFacade = walletFacade;
        this.addressString.set(getAddress().toString());

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefHeight(30);
        progressIndicator.setPrefWidth(30);
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
            public void onBalanceChanged(BigInteger balance)
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

    private void updateBalance(@Nullable BigInteger balance)
    {
        this.balance = balance;
        if (balance != null)
        {
            balanceLabel.setText(BtcFormatter.satoshiToString(balance));
        }
    }

    private void updateConfidence(@Nullable TransactionConfidence confidence)
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

    @Nullable
    public final String getLabel()
    {
        switch (addressEntry.getAddressContext())
        {
            case REGISTRATION_FEE:
                return "Registration fee";
            case TRADE:
                if (addressEntry.getTradeId() != null)
                    return "Trade ID: " + addressEntry.getTradeId();
                else
                    return "Trade (not used yet)";
            case ARBITRATOR_DEPOSIT:
                return "Arbitration deposit";
        }
        return "";
    }

    @NotNull
    public final StringProperty addressStringProperty()
    {
        return this.addressString;
    }

    Address getAddress()
    {
        return addressEntry.getAddress();
    }

    @NotNull
    public AddressEntry getAddressEntry()
    {
        return addressEntry;
    }

    @NotNull
    public ConfidenceProgressIndicator getProgressIndicator()
    {
        return progressIndicator;
    }

    @NotNull
    public Label getBalanceLabel()
    {
        return balanceLabel;
    }

    @Nullable
    public BigInteger getBalance()
    {
        return balance;
    }
}
