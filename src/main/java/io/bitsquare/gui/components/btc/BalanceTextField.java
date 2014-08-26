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

package io.bitsquare.gui.components.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.TransactionConfidence;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BalanceTextField extends AnchorPane
{
    private static final Logger log = LoggerFactory.getLogger(BalanceTextField.class);

    private final TextField balanceTextField;
    private Address address;
    private final Tooltip progressIndicatorTooltip;
    private final ConfidenceProgressIndicator progressIndicator;
    private WalletFacade walletFacade;
    private ConfidenceListener confidenceListener;
    private BalanceListener balanceListener;
    private Coin balance;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BalanceTextField()
    {
        balanceTextField = new TextField();
        balanceTextField.setFocusTraversable(false);
        balanceTextField.setEditable(false);

        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setFocusTraversable(false);
        progressIndicator.setPrefSize(24, 24);
        progressIndicator.setId("funds-confidence");
        progressIndicator.setLayoutY(1);
        progressIndicator.setProgress(0);
        progressIndicator.setVisible(false);

        progressIndicatorTooltip = new Tooltip("-");
        Tooltip.install(progressIndicator, progressIndicatorTooltip);

        AnchorPane.setRightAnchor(progressIndicator, 0.0);
        AnchorPane.setRightAnchor(balanceTextField, 55.0);
        AnchorPane.setLeftAnchor(balanceTextField, 0.0);

        getChildren().addAll(balanceTextField, progressIndicator);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAddress(Address address)
    {
        this.address = address;
    }

    public void setWalletFacade(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;
        confidenceListener = walletFacade.addConfidenceListener(new ConfidenceListener(address)
        {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence)
            {
                updateConfidence(confidence);
            }
        });
        updateConfidence(walletFacade.getConfidenceForAddress(address));


        balanceListener = walletFacade.addBalanceListener(new BalanceListener(address)
        {
            @Override
            public void onBalanceChanged(Coin balance)
            {
                updateBalance(balance);
            }
        });
        updateBalance(walletFacade.getBalanceForAddress(address));
    }

    // TODO not called yet...
    public void cleanup()
    {
        walletFacade.removeConfidenceListener(confidenceListener);
        walletFacade.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getBalance()
    {
        return balance;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfidence(TransactionConfidence confidence)
    {
        if (confidence != null)
        {
            switch (confidence.getConfidenceType())
            {
                case UNKNOWN:
                    progressIndicatorTooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    progressIndicatorTooltip.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    progressIndicatorTooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    progressIndicatorTooltip.setText("Transaction is invalid.");
                    progressIndicator.setProgress(0);
                    break;
            }

            if (progressIndicator.getProgress() != 0)
            {
                progressIndicator.setVisible(true);
                AnchorPane.setRightAnchor(progressIndicator, 0.0);
                AnchorPane.setRightAnchor(balanceTextField, 35.0);
            }
        }
    }

    private void updateBalance(Coin balance)
    {
        this.balance = balance;
        if (balance != null)
        {
            //TODO use BitSquareFormatter
            balanceTextField.setText(balance.toFriendlyString());
        }
    }

}
