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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps;

import io.bitsquare.arbitration.Dispute;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.trade.Trade;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.*;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public abstract class TradeStepDetailsView extends AnchorPane {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final PendingTradesViewModel model;
    protected final GridPane gridPane;
    protected int gridRow = 0;
    private final BlockChainListener blockChainListener;

    private long checkPaymentTimeInBlocks;
    protected long openDisputeTimeInBlocks;
    protected Label infoLabel;
    protected TitledGroupBg infoTitledGroupBg;
    protected Button openDisputeButton;
    private Button openSupportTicketButton;

    private Trade trade;
    private Subscription errorMessageSubscription;
    private Subscription disputeStateSubscription;
    private Subscription tradePeriodStateSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeStepDetailsView(PendingTradesViewModel model) {
        this.model = model;

        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, -10d);
        AnchorPane.setBottomAnchor(this, 0d);

        gridPane = addGridPane(this);

        buildGridEntries();

        blockChainListener = new BlockChainListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                setDateFromBlocks(block.getHeight());
            }

            @Override
            public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
                setDateFromBlocks(model.getBestChainHeight());
            }

            @Override
            public boolean isTransactionRelevant(Transaction tx) throws ScriptException {
                return false;
            }

            @Override
            public void receiveFromBlock(Transaction tx, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws
                    VerificationException {
            }

            @Override
            public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset)
                    throws VerificationException {
                return false;
            }
        };
    }

    // That is called at every state change!
    public void doActivate() {
        trade = model.getTrade();

        errorMessageSubscription = EasyBind.subscribe(trade.errorMessageProperty(), newValue -> {
            if (newValue != null) {
                addErrorLabel();
            }
        });

        disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), newValue -> {
            if (newValue != null) {
                updateDisputeState(newValue);
            }
        });

        tradePeriodStateSubscription = EasyBind.subscribe(trade.getTradePeriodStateProperty(), newValue -> {
            if (newValue != null) {
                updateTradePeriodState(newValue);
            }
        });


        // first call updateTradePeriodState as there is the dispute button created in case we are in period over time
        updateTradePeriodState(trade.getTradePeriodState());
        updateDisputeState(trade.getDisputeState());

        model.addBlockChainListener(blockChainListener);
        setDateFromBlocks(model.getBestChainHeight());
    }

    public void doDeactivate() {
        model.removeBlockChainListener(blockChainListener);

        if (errorMessageSubscription != null)
            errorMessageSubscription.unsubscribe();
        if (disputeStateSubscription != null)
            disputeStateSubscription.unsubscribe();
        if (tradePeriodStateSubscription != null)
            tradePeriodStateSubscription.unsubscribe();

        if (openDisputeButton != null)
            openDisputeButton.setOnAction(null);
        if (openSupportTicketButton != null)
            openSupportTicketButton.setOnAction(null);
    }

    protected void disputeInProgress() {
        if (openDisputeButton != null)
            openDisputeButton.setDisable(true);

        addDisputeInfoLabel();
    }

    protected void addDisputeInfoLabel() {
        if (infoLabel == null) {
            infoTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
            infoLabel = addMultilineLabel(gridPane, gridRow, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        }
        infoLabel.setStyle(" -fx-text-fill: -bs-error-red;");
    }

    protected void addOpenDisputeButton() {
        if (openDisputeButton == null) {
            openDisputeButton = addButtonAfterGroup(gridPane, ++gridRow, "Open a dispute with arbitrator");
            GridPane.setColumnIndex(openDisputeButton, 0);
            GridPane.setHalignment(openDisputeButton, HPos.LEFT);
            openDisputeButton.setOnAction(e -> {
                openDisputeButton.setDisable(true);
                disputeInProgress();
                model.dataModel.onOpenDispute();
            });
        }
    }

    private void addErrorLabel() {
        if (infoLabel == null) {
            infoTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, "Error", Layout.GROUP_DISTANCE);
            infoLabel = addMultilineLabel(gridPane, gridRow, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        }
        infoTitledGroupBg.setText("Error message");
        infoLabel.setText(trade.errorMessageProperty().getValue()
                + "\n\nPlease report the problem to your arbitrator. He will forward it to the developers to investigate the problem.\n" +
                "After the problem has be analysed you will get back all the funds you paid in.\n" +
                "There will be no arbitration fee charged if it was a technical error.");
        infoLabel.setStyle(" -fx-text-fill: -bs-error-red;");

        if (openSupportTicketButton == null) {
            openSupportTicketButton = addButtonAfterGroup(gridPane, ++gridRow, "Request support");
            GridPane.setColumnIndex(openSupportTicketButton, 0);
            GridPane.setHalignment(openSupportTicketButton, HPos.LEFT);
            openSupportTicketButton.setOnAction(e -> model.dataModel.onOpenSupportTicket());
        }
    }


    private void updateDisputeState(Trade.DisputeState disputeState) {
        Optional<Dispute> ownDispute = model.dataModel.getDisputeManager().findOwnDispute(trade.getId());

        switch (disputeState) {
            case NONE:
                break;
            case DISPUTE_REQUESTED:
                disputeInProgress();
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket())
                        msg = "You opened already a support ticket.\n" +
                                "Please communicate in the support section with the arbitrator.";
                    else
                        msg = "You opened already a dispute.\n" +
                                "Please communicate in the support section with the arbitrator.";

                    infoLabel.setText(msg);
                });

                break;
            case DISPUTE_STARTED_BY_PEER:
                disputeInProgress();
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket())
                        msg = "Your trading peer opened a support ticket due technical problems.\n" +
                                "Please communicate in the support section with the arbitrator.";
                    else
                        msg = "Your trading peer opened a dispute.\n" +
                                "Please communicate in the support section with the arbitrator.";

                    infoLabel.setText(msg);
                });
                break;
            case DISPUTE_CLOSED:
                break;
        }
    }


    private void updateTradePeriodState(Trade.TradePeriodState tradePeriodState) {
        switch (tradePeriodState) {
            case NORMAL:
                break;
            case HALF_REACHED:
                displayRequestCheckPayment();
                break;
            case TRADE_PERIOD_OVER:
                displayOpenForDisputeForm();
                break;
        }
    }

    private void setDateFromBlocks(long bestBlocKHeight) {
        checkPaymentTimeInBlocks = model.getCheckPaymentTimeAsBlockHeight() - bestBlocKHeight;
        openDisputeTimeInBlocks = model.getOpenDisputeTimeAsBlockHeight() - bestBlocKHeight;
        updateDateFromBlocks(bestBlocKHeight);
    }

    protected void updateDateFromBlocks(long bestBlocKHeight) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onOpenHelp() {
        Help.openWindow(model.isOfferer() ? HelpId.PENDING_TRADE_OFFERER : HelpId.PENDING_TRADE_TAKER);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract void buildGridEntries();

    protected void displayRequestCheckPayment() {
    }

    protected void displayOpenForDisputeForm() {
    }


}
