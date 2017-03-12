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

package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.messages.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.messages.arbitration.DisputeResult;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.TradeWalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.messages.trade.payload.Contract;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;

public class DisputeSummaryWindow extends Overlay<DisputeSummaryWindow> {
    private static final Logger log = LoggerFactory.getLogger(DisputeSummaryWindow.class);

    private final BSFormatter formatter;
    private final DisputeManager disputeManager;
    private final BtcWalletService walletService;
    private final TradeWalletService tradeWalletService;
    private Dispute dispute;
    private Optional<Runnable> finalizeDisputeHandlerOptional = Optional.empty();
    private ToggleGroup tradeAmountToggleGroup;
    private DisputeResult disputeResult;
    private RadioButton buyerIsWinnerRadioButton, sellerIsWinnerRadioButton, shareRadioButton, customRadioButton, loserPaysFeeRadioButton, splitFeeRadioButton,
            waiveFeeRadioButton, reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton, reasonProtocolViolationRadioButton, reasonNoReplyRadioButton, reasonWasScamRadioButton, reasonWasOtherRadioButton;
    private Optional<Dispute> peersDisputeOptional;
    private Coin arbitratorPayoutAmount, winnerPayoutAmount, loserPayoutAmount, stalematePayoutAmount;
    private ToggleGroup feeToggleGroup, reasonToggleGroup;
    private String role;
    private TextArea summaryNotesTextArea;

    // keep a reference to not get GCed
    private ObjectBinding<Tuple2<DisputeResult.DisputeFeePolicy, Toggle>> feePaymentPolicyChanged;

    private ChangeListener<Tuple2<DisputeResult.DisputeFeePolicy, Toggle>> feePaymentPolicyListener;
    private ChangeListener<Boolean> shareRadioButtonSelectedListener, customRadioButtonSelectedListener;
    private ChangeListener<Toggle> feeToggleSelectionListener, reasonToggleSelectionListener;
    private InputTextField buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField, arbitratorPayoutAmountInputTextField;
    private ChangeListener<String> buyerPayoutAmountListener, sellerPayoutAmountListener, arbitratorPayoutAmountListener;
    private CheckBox isLoserPublisherCheckBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DisputeSummaryWindow(BSFormatter formatter, DisputeManager disputeManager, BtcWalletService walletService, TradeWalletService tradeWalletService) {
        this.formatter = formatter;
        this.disputeManager = disputeManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;

        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        display();
    }

    public DisputeSummaryWindow onFinalizeDispute(Runnable finalizeDisputeHandler) {
        this.finalizeDisputeHandlerOptional = Optional.of(finalizeDisputeHandler);
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
        if (feePaymentPolicyChanged != null)
            feePaymentPolicyChanged.removeListener(feePaymentPolicyListener);

        if (shareRadioButton != null)
            shareRadioButton.selectedProperty().removeListener(shareRadioButtonSelectedListener);

        if (feeToggleGroup != null)
            feeToggleGroup.selectedToggleProperty().removeListener(feeToggleSelectionListener);

        if (reasonToggleGroup != null)
            reasonToggleGroup.selectedToggleProperty().removeListener(reasonToggleSelectionListener);

        if (customRadioButton != null)
            customRadioButton.selectedProperty().removeListener(customRadioButtonSelectedListener);

        removePayoutAmountListeners();
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.setStyle("-fx-background-color: -bs-content-bg-grey;" +
                        "-fx-background-radius: 5 5 5 5;" +
                        "-fx-effect: dropshadow(gaussian, #999, 10, 0, 0, 0);" +
                        "-fx-background-insets: 10;"
        );
    }

    private void addContent() {
        Contract contract = dispute.getContract();
        if (dispute.disputeResultProperty().get() == null)
            disputeResult = new DisputeResult(dispute.getTradeId(), dispute.getTraderId());
        else
            disputeResult = dispute.disputeResultProperty().get();

        peersDisputeOptional = disputeManager.getDisputesAsObservableList().stream()
                .filter(d -> dispute.getTradeId().equals(d.getTradeId()) && dispute.getTraderId() != d.getTraderId()).findFirst();

        addInfoPane();

        if (!dispute.isSupportTicket())
            addCheckboxes();

        addTradeAmountPayoutControls();
        addFeeControls();
        addPayoutAmountTextFields();
        addReasonControls();

        boolean applyPeersDisputeResult = peersDisputeOptional.isPresent() && peersDisputeOptional.get().isClosed();
        if (applyPeersDisputeResult) {
            // If the other peers dispute has been closed we apply the result to ourselves
            DisputeResult peersDisputeResult = peersDisputeOptional.get().disputeResultProperty().get();
            disputeResult.setBuyerPayoutAmount(peersDisputeResult.getBuyerPayoutAmount());
            disputeResult.setSellerPayoutAmount(peersDisputeResult.getSellerPayoutAmount());
            disputeResult.setArbitratorPayoutAmount(peersDisputeResult.getArbitratorPayoutAmount());
            disputeResult.setDisputeFeePolicy(peersDisputeResult.getDisputeFeePolicy());
            disputeResult.setWinner(peersDisputeResult.getWinner());
            disputeResult.setLoserIsPublisher(peersDisputeResult.isLoserPublisher());
            disputeResult.setReason(peersDisputeResult.getReason());
            disputeResult.setSummaryNotes(peersDisputeResult.summaryNotesProperty().get());

            if (disputeResult.getBuyerPayoutAmount() != null) {
                log.debug("buyerPayoutAmount " + disputeResult.getBuyerPayoutAmount().toFriendlyString());
                log.debug("sellerPayoutAmount " + disputeResult.getSellerPayoutAmount().toFriendlyString());
                log.debug("arbitratorPayoutAmount " + disputeResult.getArbitratorPayoutAmount().toFriendlyString());
            }

            buyerIsWinnerRadioButton.setDisable(true);
            sellerIsWinnerRadioButton.setDisable(true);
            shareRadioButton.setDisable(true);
            customRadioButton.setDisable(true);
            loserPaysFeeRadioButton.setDisable(true);
            splitFeeRadioButton.setDisable(true);
            waiveFeeRadioButton.setDisable(true);

            buyerPayoutAmountInputTextField.setDisable(true);
            sellerPayoutAmountInputTextField.setDisable(true);
            arbitratorPayoutAmountInputTextField.setDisable(true);
            buyerPayoutAmountInputTextField.setEditable(false);
            sellerPayoutAmountInputTextField.setEditable(false);
            arbitratorPayoutAmountInputTextField.setEditable(false);

            reasonWasBugRadioButton.setDisable(true);
            reasonWasUsabilityIssueRadioButton.setDisable(true);
            reasonProtocolViolationRadioButton.setDisable(true);
            reasonNoReplyRadioButton.setDisable(true);
            reasonWasScamRadioButton.setDisable(true);
            reasonWasOtherRadioButton.setDisable(true);

            isLoserPublisherCheckBox.setDisable(true);
            isLoserPublisherCheckBox.setSelected(peersDisputeResult.isLoserPublisher());

            calculatePayoutAmounts(disputeResult.getDisputeFeePolicy());
            applyTradeAmountRadioButtonStates();
        } else {
            applyPayoutAmounts(disputeResult.disputeFeePolicyProperty().get(), tradeAmountToggleGroup.selectedToggleProperty().get());
            feePaymentPolicyChanged = Bindings.createObjectBinding(
                    () -> new Tuple2<>(disputeResult.disputeFeePolicyProperty().get(), tradeAmountToggleGroup.selectedToggleProperty().get()),
                    disputeResult.disputeFeePolicyProperty(),
                    tradeAmountToggleGroup.selectedToggleProperty());
            feePaymentPolicyListener = (observable, oldValue, newValue) -> applyPayoutAmounts(newValue.first, newValue.second);
            feePaymentPolicyChanged.addListener(feePaymentPolicyListener);

            isLoserPublisherCheckBox.setSelected(false);
        }

        setFeeRadioButtonState();
        setReasonRadioButtonState();

        addSummaryNotes();
        addButtons(contract);
    }

    private void addInfoPane() {
        Contract contract = dispute.getContract();
        addTitledGroupBg(gridPane, ++rowIndex, 16, Res.get("disputeSummaryWindow.title"));
        addLabelTextField(gridPane, rowIndex, Res.getWithCol("shared.tradeId"), dispute.getShortTradeId(), Layout.FIRST_ROW_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.openDate"), formatter.formatDateTime(dispute.getOpeningDate()));
        if (dispute.isDisputeOpenerIsOfferer()) {
            if (dispute.isDisputeOpenerIsBuyer())
                role = Res.get("support.buyerOfferer");
            else
                role = Res.get("support.sellerOfferer");
        } else {
            if (dispute.isDisputeOpenerIsBuyer())
                role = Res.get("support.buyerTaker");
            else
                role = Res.get("support.sellerTaker");
        }
        addLabelTextField(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.role"), role);
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradeAmount"), formatter.formatCoinWithCode(contract.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradePrice"), formatter.formatPrice(contract.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradeVolume"), formatter.formatVolumeWithCode(new ExchangeRate(contract.getTradePrice()).coinToFiat(contract.getTradeAmount())));
    }

    private void addCheckboxes() {
        Label evidenceLabel = addLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.evidence"), 10);
        GridPane.setValignment(evidenceLabel, VPos.TOP);
        CheckBox tamperProofCheckBox = new CheckBox(Res.get("disputeSummaryWindow.evidence.tamperProof"));
        CheckBox idVerificationCheckBox = new CheckBox(Res.get("disputeSummaryWindow.evidence.id"));
        CheckBox screenCastCheckBox = new CheckBox(Res.get("disputeSummaryWindow.evidence.video"));

        tamperProofCheckBox.selectedProperty().bindBidirectional(disputeResult.tamperProofEvidenceProperty());
        idVerificationCheckBox.selectedProperty().bindBidirectional(disputeResult.idVerificationProperty());
        screenCastCheckBox.selectedProperty().bindBidirectional(disputeResult.screenCastProperty());

        FlowPane checkBoxPane = new FlowPane();
        checkBoxPane.setHgap(20);
        checkBoxPane.setVgap(5);
        checkBoxPane.getChildren().addAll(tamperProofCheckBox, idVerificationCheckBox, screenCastCheckBox);
        GridPane.setRowIndex(checkBoxPane, rowIndex);
        GridPane.setColumnIndex(checkBoxPane, 1);
        GridPane.setMargin(checkBoxPane, new Insets(10, 0, 0, 0));
        gridPane.getChildren().add(checkBoxPane);
    }

    private void addTradeAmountPayoutControls() {
        Label distributionLabel = addLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.payout"), 10);
        GridPane.setValignment(distributionLabel, VPos.TOP);

        buyerIsWinnerRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.buyerWinner"));
        sellerIsWinnerRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.sellerWinner"));
        shareRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.both"));
        customRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.custom"));
        VBox radioButtonPane = new VBox();
        radioButtonPane.setSpacing(10);
        radioButtonPane.getChildren().addAll(buyerIsWinnerRadioButton, sellerIsWinnerRadioButton, shareRadioButton, customRadioButton);
        GridPane.setRowIndex(radioButtonPane, rowIndex);
        GridPane.setColumnIndex(radioButtonPane, 1);
        GridPane.setMargin(radioButtonPane, new Insets(10, 0, 0, 0));
        gridPane.getChildren().add(radioButtonPane);

        tradeAmountToggleGroup = new ToggleGroup();
        buyerIsWinnerRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerIsWinnerRadioButton.setToggleGroup(tradeAmountToggleGroup);
        shareRadioButton.setToggleGroup(tradeAmountToggleGroup);
        customRadioButton.setToggleGroup(tradeAmountToggleGroup);
        shareRadioButtonSelectedListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                loserPaysFeeRadioButton.setSelected(false);

                if (splitFeeRadioButton != null && !dispute.isSupportTicket())
                    splitFeeRadioButton.setSelected(true);

                if (waiveFeeRadioButton != null && dispute.isSupportTicket())
                    waiveFeeRadioButton.setSelected(true);
            }

            loserPaysFeeRadioButton.setDisable(newValue);
        };
        shareRadioButton.selectedProperty().addListener(shareRadioButtonSelectedListener);

        buyerPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(buyerPayoutAmountInputTextField);
        sellerPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(sellerPayoutAmountInputTextField);
        arbitratorPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(arbitratorPayoutAmountInputTextField);

        customRadioButtonSelectedListener = (observable, oldValue, newValue) -> {
            buyerPayoutAmountInputTextField.setEditable(newValue);
            sellerPayoutAmountInputTextField.setEditable(newValue);
            arbitratorPayoutAmountInputTextField.setEditable(newValue);

            loserPaysFeeRadioButton.setDisable(newValue);
            splitFeeRadioButton.setDisable(newValue);
            waiveFeeRadioButton.setDisable(newValue);

            if (newValue) {
                buyerPayoutAmountInputTextField.textProperty().addListener(buyerPayoutAmountListener);
                sellerPayoutAmountInputTextField.textProperty().addListener(sellerPayoutAmountListener);
                arbitratorPayoutAmountInputTextField.textProperty().addListener(arbitratorPayoutAmountListener);
            } else {
                removePayoutAmountListeners();
            }
        };
        customRadioButton.selectedProperty().addListener(customRadioButtonSelectedListener);
    }

    private void removePayoutAmountListeners() {
        if (buyerPayoutAmountInputTextField != null && buyerPayoutAmountListener != null)
            buyerPayoutAmountInputTextField.textProperty().removeListener(buyerPayoutAmountListener);

        if (sellerPayoutAmountInputTextField != null && sellerPayoutAmountListener != null)
            sellerPayoutAmountInputTextField.textProperty().removeListener(sellerPayoutAmountListener);

        if (arbitratorPayoutAmountInputTextField != null && arbitratorPayoutAmountListener != null)
            arbitratorPayoutAmountInputTextField.textProperty().removeListener(arbitratorPayoutAmountListener);
    }

    private boolean isPayoutAmountValid() {
        Coin buyerAmount = formatter.parseToCoin(buyerPayoutAmountInputTextField.getText());
        Coin sellerAmount = formatter.parseToCoin(sellerPayoutAmountInputTextField.getText());
        Coin arbitratorAmount = formatter.parseToCoin(arbitratorPayoutAmountInputTextField.getText());
        Contract contract = dispute.getContract();
        Coin securityDeposit = contract.offer.getSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();
        Coin available = tradeAmount.add(securityDeposit).add(securityDeposit);
        Coin totalAmount = buyerAmount.add(sellerAmount).add(arbitratorAmount);
        return (totalAmount.compareTo(available) == 0);
    }

    private void applyCustomAmounts(InputTextField inputTextField) {
        Contract contract = dispute.getContract();
        Coin securityDeposit = contract.offer.getSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();

        Coin buyerAmount = formatter.parseToCoin(buyerPayoutAmountInputTextField.getText());
        Coin sellerAmount = formatter.parseToCoin(sellerPayoutAmountInputTextField.getText());
        Coin arbitratorAmount = formatter.parseToCoin(arbitratorPayoutAmountInputTextField.getText());

        Coin available = tradeAmount.add(securityDeposit).add(securityDeposit);
        Coin totalAmount = buyerAmount.add(sellerAmount).add(arbitratorAmount);

        if (totalAmount.compareTo(available) > 0) {
            new Popup<>().warning(Res.get("disputeSummaryWindow.payout.adjustAmount", available.toFriendlyString()))
                    .show();

            if (inputTextField == buyerPayoutAmountInputTextField) {
                buyerAmount = available.subtract(sellerAmount).subtract(arbitratorAmount);
                inputTextField.setText(formatter.formatCoin(buyerAmount));
            } else if (inputTextField == sellerPayoutAmountInputTextField) {
                sellerAmount = available.subtract(buyerAmount).subtract(arbitratorAmount);
                inputTextField.setText(formatter.formatCoin(sellerAmount));
            } else if (inputTextField == arbitratorPayoutAmountInputTextField) {
                arbitratorAmount = available.subtract(sellerAmount).subtract(buyerAmount);
                inputTextField.setText(formatter.formatCoin(arbitratorAmount));
            }
        }

        disputeResult.setBuyerPayoutAmount(buyerAmount);
        disputeResult.setSellerPayoutAmount(sellerAmount);
        disputeResult.setArbitratorPayoutAmount(arbitratorAmount);

        if (buyerAmount.compareTo(sellerAmount) == 0)
            disputeResult.setWinner(DisputeResult.Winner.STALE_MATE);
        else if (buyerAmount.compareTo(sellerAmount) > 0)
            disputeResult.setWinner(DisputeResult.Winner.BUYER);
        else
            disputeResult.setWinner(DisputeResult.Winner.SELLER);
    }

    private void addFeeControls() {
        Label splitFeeLabel = addLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.arbitrationFee"), 10);
        GridPane.setValignment(splitFeeLabel, VPos.TOP);

        loserPaysFeeRadioButton = new RadioButton(Res.get("disputeSummaryWindow.arbitrationFee.looserPay"));
        splitFeeRadioButton = new RadioButton(Res.get("disputeSummaryWindow.split"));
        waiveFeeRadioButton = new RadioButton(Res.get("disputeSummaryWindow.arbitrationFee.waive"));
        HBox feeRadioButtonPane = new HBox();
        feeRadioButtonPane.setSpacing(20);
        feeRadioButtonPane.getChildren().addAll(loserPaysFeeRadioButton, splitFeeRadioButton, waiveFeeRadioButton);
        GridPane.setRowIndex(feeRadioButtonPane, rowIndex);
        GridPane.setColumnIndex(feeRadioButtonPane, 1);
        GridPane.setMargin(feeRadioButtonPane, new Insets(10, 0, 10, 0));
        gridPane.getChildren().add(feeRadioButtonPane);

        feeToggleGroup = new ToggleGroup();
        loserPaysFeeRadioButton.setToggleGroup(feeToggleGroup);
        splitFeeRadioButton.setToggleGroup(feeToggleGroup);
        waiveFeeRadioButton.setToggleGroup(feeToggleGroup);

        feeToggleSelectionListener = (observable, oldValue, newValue) -> {
            if (newValue == loserPaysFeeRadioButton)
                disputeResult.setDisputeFeePolicy(DisputeResult.DisputeFeePolicy.LOSER);
            else if (newValue == splitFeeRadioButton)
                disputeResult.setDisputeFeePolicy(DisputeResult.DisputeFeePolicy.SPLIT);
            else if (newValue == waiveFeeRadioButton)
                disputeResult.setDisputeFeePolicy(DisputeResult.DisputeFeePolicy.WAIVE);
        };
        feeToggleGroup.selectedToggleProperty().addListener(feeToggleSelectionListener);

        if (dispute.isSupportTicket())
            feeToggleGroup.selectToggle(waiveFeeRadioButton);
    }

    private void addPayoutAmountTextFields() {
        buyerPayoutAmountInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.buyer")).second;
        buyerPayoutAmountInputTextField.setEditable(false);

        sellerPayoutAmountInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.seller")).second;
        sellerPayoutAmountInputTextField.setEditable(false);

        arbitratorPayoutAmountInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.arbitrator")).second;
        arbitratorPayoutAmountInputTextField.setEditable(false);

        isLoserPublisherCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.invert")).second;
    }

    private void setFeeRadioButtonState() {
        switch (disputeResult.getDisputeFeePolicy()) {
            case LOSER:
                feeToggleGroup.selectToggle(loserPaysFeeRadioButton);
                break;
            case SPLIT:
                feeToggleGroup.selectToggle(splitFeeRadioButton);
                break;
            case WAIVE:
                feeToggleGroup.selectToggle(waiveFeeRadioButton);
                break;
        }
    }

    private void addReasonControls() {
        Label label = addLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.reason"), 10);
        GridPane.setValignment(label, VPos.TOP);
        reasonWasBugRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.bug"));
        reasonWasUsabilityIssueRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.usability"));
        reasonProtocolViolationRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.protocolViolation"));
        reasonNoReplyRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.noReply"));
        reasonWasScamRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.scam"));
        reasonWasOtherRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.other"));
        //TODO add banks as reason

        HBox feeRadioButtonPane = new HBox();
        feeRadioButtonPane.setSpacing(20);
        feeRadioButtonPane.getChildren().addAll(reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
                reasonProtocolViolationRadioButton, reasonNoReplyRadioButton,
                reasonWasScamRadioButton, reasonWasOtherRadioButton);
        GridPane.setRowIndex(feeRadioButtonPane, rowIndex);
        GridPane.setColumnIndex(feeRadioButtonPane, 1);
        GridPane.setMargin(feeRadioButtonPane, new Insets(10, 0, 10, 0));
        gridPane.getChildren().add(feeRadioButtonPane);

        reasonToggleGroup = new ToggleGroup();
        reasonWasBugRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasUsabilityIssueRadioButton.setToggleGroup(reasonToggleGroup);
        reasonProtocolViolationRadioButton.setToggleGroup(reasonToggleGroup);
        reasonNoReplyRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasScamRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasOtherRadioButton.setToggleGroup(reasonToggleGroup);

        reasonToggleSelectionListener = (observable, oldValue, newValue) -> {
            if (newValue == reasonWasBugRadioButton)
                disputeResult.setReason(DisputeResult.Reason.BUG);
            else if (newValue == reasonWasUsabilityIssueRadioButton)
                disputeResult.setReason(DisputeResult.Reason.USABILITY);
            else if (newValue == reasonProtocolViolationRadioButton)
                disputeResult.setReason(DisputeResult.Reason.PROTOCOL_VIOLATION);
            else if (newValue == reasonNoReplyRadioButton)
                disputeResult.setReason(DisputeResult.Reason.NO_REPLY);
            else if (newValue == reasonWasScamRadioButton)
                disputeResult.setReason(DisputeResult.Reason.SCAM);
            else if (newValue == reasonWasOtherRadioButton)
                disputeResult.setReason(DisputeResult.Reason.OTHER);
        };
        reasonToggleGroup.selectedToggleProperty().addListener(reasonToggleSelectionListener);
    }

    private void setReasonRadioButtonState() {
        if (disputeResult.getReason() != null) {
            switch (disputeResult.getReason()) {
                case BUG:
                    reasonToggleGroup.selectToggle(reasonWasBugRadioButton);
                    break;
                case USABILITY:
                    reasonToggleGroup.selectToggle(reasonWasUsabilityIssueRadioButton);
                    break;
                case PROTOCOL_VIOLATION:
                    reasonToggleGroup.selectToggle(reasonProtocolViolationRadioButton);
                    break;
                case NO_REPLY:
                    reasonToggleGroup.selectToggle(reasonNoReplyRadioButton);
                    break;
                case SCAM:
                    reasonToggleGroup.selectToggle(reasonWasScamRadioButton);
                    break;
                case OTHER:
                    reasonToggleGroup.selectToggle(reasonWasOtherRadioButton);
                    break;
            }
        }
    }

    private void addSummaryNotes() {
        Label label = addLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.summaryNotes"), 0);
        GridPane.setValignment(label, VPos.TOP);

        summaryNotesTextArea = new TextArea();
        summaryNotesTextArea.setPromptText(Res.get("disputeSummaryWindow.addSummaryNotes"));
        summaryNotesTextArea.setWrapText(true);
        summaryNotesTextArea.setPrefHeight(50);
        summaryNotesTextArea.textProperty().bindBidirectional(disputeResult.summaryNotesProperty());
        GridPane.setRowIndex(summaryNotesTextArea, rowIndex);
        GridPane.setColumnIndex(summaryNotesTextArea, 1);
        gridPane.getChildren().add(summaryNotesTextArea);
    }

    private void addButtons(Contract contract) {
        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.close.button"),
                Res.get("shared.cancel"));
        Button closeTicketButton = tuple.first;
        closeTicketButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> tradeAmountToggleGroup.getSelectedToggle() == null
                        || summaryNotesTextArea.getText() == null
                        || summaryNotesTextArea.getText().length() == 0
                        || !isPayoutAmountValid(),
                tradeAmountToggleGroup.selectedToggleProperty(),
                summaryNotesTextArea.textProperty(),
                buyerPayoutAmountInputTextField.textProperty(),
                sellerPayoutAmountInputTextField.textProperty(),
                arbitratorPayoutAmountInputTextField.textProperty()));

        Button cancelButton = tuple.second;

        final Dispute finalPeersDispute = peersDisputeOptional.get();
        closeTicketButton.setOnAction(e -> {
            if (dispute.getDepositTxSerialized() != null) {
                try {
                    AddressEntry arbitratorAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.ARBITRATOR);
                    disputeResult.setArbitratorAddressAsString(arbitratorAddressEntry.getAddressString());
                    disputeResult.setArbitratorPubKey(arbitratorAddressEntry.getPubKey());
                    
                   /* byte[] depositTxSerialized,
                    Coin buyerPayoutAmount,
                    Coin sellerPayoutAmount,
                    Coin arbitratorPayoutAmount,
                    String buyerAddressString,
                    String sellerAddressString,
                    AddressEntry arbitratorAddressEntry,
                    byte[] buyerPubKey,
                    byte[] sellerPubKey,
                    byte[] arbitratorPubKey)
                    */
                    byte[] arbitratorSignature = tradeWalletService.arbitratorSignsDisputedPayoutTx(
                            dispute.getDepositTxSerialized(),
                            disputeResult.getBuyerPayoutAmount(),
                            disputeResult.getSellerPayoutAmount(),
                            disputeResult.getArbitratorPayoutAmount(),
                            contract.getBuyerPayoutAddressString(),
                            contract.getSellerPayoutAddressString(),
                            arbitratorAddressEntry.getAddressString(),
                            arbitratorAddressEntry.getKeyPair(),
                            contract.getBuyerMultiSigPubKey(),
                            contract.getSellerMultiSigPubKey(),
                            arbitratorAddressEntry.getPubKey()
                    );
                    disputeResult.setArbitratorSignature(arbitratorSignature);

                    closeTicketButton.disableProperty().unbind();
                    dispute.setDisputeResult(disputeResult);

                    disputeResult.setLoserIsPublisher(isLoserPublisherCheckBox.isSelected());
                    disputeResult.setCloseDate(new Date());
                    String text = Res.get("disputeSummaryWindow.close.msg",
                            formatter.formatDateTime(disputeResult.getCloseDate()),
                            role,
                            formatter.booleanToYesNo(disputeResult.tamperProofEvidenceProperty().get()),
                            role,
                            formatter.booleanToYesNo(disputeResult.idVerificationProperty().get()),
                            role,
                            formatter.booleanToYesNo(disputeResult.screenCastProperty().get()),
                            formatter.formatCoinWithCode(disputeResult.getBuyerPayoutAmount()),
                            formatter.formatCoinWithCode(disputeResult.getSellerPayoutAmount()),
                            formatter.formatCoinWithCode(disputeResult.getArbitratorPayoutAmount()),
                            disputeResult.summaryNotesProperty().get());

                    dispute.setIsClosed(true);
                    disputeManager.sendDisputeResultMessage(disputeResult, dispute, text);

                    if (!finalPeersDispute.isClosed())
                        UserThread.runAfter(() ->
                                        new Popup().attention(Res.get("disputeSummaryWindow.close.closePeer")).show(),
                                Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS);

                    hide();

                    finalizeDisputeHandlerOptional.ifPresent(Runnable::run);
                } catch (AddressFormatException | TransactionVerificationException e2) {
                    e2.printStackTrace();
                }
            } else {
                log.warn("dispute.getDepositTxSerialized is null");
            }
        });

        cancelButton.setOnAction(e -> {
            dispute.setDisputeResult(disputeResult);
            hide();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Controller
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyPayoutAmounts(DisputeResult.DisputeFeePolicy feePayment, Toggle selectedTradeAmountToggle) {
        if (selectedTradeAmountToggle != customRadioButton) {
            calculatePayoutAmounts(feePayment);
            if (selectedTradeAmountToggle != null) {
                applyPayoutAmountsToDisputeResult(selectedTradeAmountToggle);
                applyTradeAmountRadioButtonStates();
            }
        }
    }

    private void calculatePayoutAmounts(DisputeResult.DisputeFeePolicy feePayment) {
        Contract contract = dispute.getContract();
        Coin refund = contract.offer.getSecurityDeposit();
        Coin winnerRefund;
        Coin loserRefund;
        switch (feePayment) {
            case SPLIT:
                winnerRefund = refund.divide(2L);
                loserRefund = winnerRefund;
                arbitratorPayoutAmount = refund;
                break;
            case WAIVE:
                winnerRefund = refund;
                loserRefund = refund;
                arbitratorPayoutAmount = Coin.ZERO;
                break;
            case LOSER:
            default:
                winnerRefund = refund;
                loserRefund = Coin.ZERO;
                arbitratorPayoutAmount = refund;
                break;
        }

        winnerPayoutAmount = contract.getTradeAmount().add(winnerRefund);
        loserPayoutAmount = loserRefund;
        stalematePayoutAmount = contract.getTradeAmount().divide(2L).add(winnerRefund);
    }

    private void applyPayoutAmountsToDisputeResult(Toggle selectedTradeAmountToggle) {
        if (selectedTradeAmountToggle == buyerIsWinnerRadioButton) {
            disputeResult.setBuyerPayoutAmount(winnerPayoutAmount);
            disputeResult.setSellerPayoutAmount(loserPayoutAmount);
            disputeResult.setWinner(DisputeResult.Winner.BUYER);

            buyerPayoutAmountInputTextField.setText(formatter.formatCoin(winnerPayoutAmount));
            sellerPayoutAmountInputTextField.setText(formatter.formatCoin(loserPayoutAmount));
        } else if (selectedTradeAmountToggle == sellerIsWinnerRadioButton) {
            disputeResult.setBuyerPayoutAmount(loserPayoutAmount);
            disputeResult.setSellerPayoutAmount(winnerPayoutAmount);
            disputeResult.setWinner(DisputeResult.Winner.SELLER);

            buyerPayoutAmountInputTextField.setText(formatter.formatCoin(loserPayoutAmount));
            sellerPayoutAmountInputTextField.setText(formatter.formatCoin(winnerPayoutAmount));
        } else if (selectedTradeAmountToggle == shareRadioButton) {
            disputeResult.setBuyerPayoutAmount(stalematePayoutAmount);
            disputeResult.setSellerPayoutAmount(stalematePayoutAmount);
            disputeResult.setWinner(DisputeResult.Winner.STALE_MATE);

            buyerPayoutAmountInputTextField.setText(formatter.formatCoin(stalematePayoutAmount));
            sellerPayoutAmountInputTextField.setText(formatter.formatCoin(stalematePayoutAmount));
        }
        disputeResult.setArbitratorPayoutAmount(arbitratorPayoutAmount);
        arbitratorPayoutAmountInputTextField.setText(formatter.formatCoin(arbitratorPayoutAmount));
    }

    private void applyTradeAmountRadioButtonStates() {
        Coin buyerPayoutAmount = disputeResult.getBuyerPayoutAmount();
        Coin sellerPayoutAmount = disputeResult.getSellerPayoutAmount();

        buyerPayoutAmountInputTextField.setText(formatter.formatCoin(buyerPayoutAmount));
        sellerPayoutAmountInputTextField.setText(formatter.formatCoin(sellerPayoutAmount));
        arbitratorPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getArbitratorPayoutAmount()));

        if (buyerPayoutAmount.equals(winnerPayoutAmount) && sellerPayoutAmount.equals(loserPayoutAmount)) {
            buyerIsWinnerRadioButton.setSelected(true);
        } else if (sellerPayoutAmount.equals(winnerPayoutAmount) && buyerPayoutAmount.equals(loserPayoutAmount)) {
            sellerIsWinnerRadioButton.setSelected(true);
        } else if (sellerPayoutAmount.equals(buyerPayoutAmount)) {
            shareRadioButton.setSelected(true);
        } else {
            customRadioButton.setSelected(true);
        }
    }
}
