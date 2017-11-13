/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.core.arbitration.Dispute;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.arbitration.DisputeResult;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Contract;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.Transitions;
import javafx.beans.binding.Bindings;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;

public class DisputeSummaryWindow extends Overlay<DisputeSummaryWindow> {
    private static final Logger log = LoggerFactory.getLogger(DisputeSummaryWindow.class);

    private final BSFormatter formatter;
    private final DisputeManager disputeManager;
    private final BtcWalletService walletService;
    private final TradeWalletService tradeWalletService;
    private Dispute dispute;
    private Optional<Runnable> finalizeDisputeHandlerOptional = Optional.<Runnable>empty();
    private ToggleGroup tradeAmountToggleGroup, reasonToggleGroup;
    private DisputeResult disputeResult;
    private RadioButton buyerGetsTradeAmountRadioButton, sellerGetsTradeAmountRadioButton,
            buyerGetsAllRadioButton, sellerGetsAllRadioButton, customRadioButton;
    private RadioButton reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
            reasonProtocolViolationRadioButton, reasonNoReplyRadioButton, reasonWasScamRadioButton,
            reasonWasOtherRadioButton, reasonWasBankRadioButton;
    private Optional<Dispute> peersDisputeOptional;
    private String role;
    private TextArea summaryNotesTextArea;

    private ChangeListener<Boolean> customRadioButtonSelectedListener;
    private ChangeListener<Toggle> reasonToggleSelectionListener;
    private InputTextField buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField;
    private ChangeListener<String> buyerPayoutAmountListener, sellerPayoutAmountListener;
    private CheckBox isLoserPublisherCheckBox;
    private ChangeListener<Toggle> tradeAmountToggleGroupListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DisputeSummaryWindow(BSFormatter formatter, DisputeManager disputeManager, BtcWalletService walletService,
                                TradeWalletService tradeWalletService) {

        this.formatter = formatter;
        this.disputeManager = disputeManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;

        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 1050;
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
        if (reasonToggleGroup != null)
            reasonToggleGroup.selectedToggleProperty().removeListener(reasonToggleSelectionListener);

        if (customRadioButton != null)
            customRadioButton.selectedProperty().removeListener(customRadioButtonSelectedListener);

        if (tradeAmountToggleGroup != null)
            tradeAmountToggleGroup.selectedToggleProperty().removeListener(tradeAmountToggleGroupListener);

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
        if (dispute.getDisputeResultProperty().get() == null)
            disputeResult = new DisputeResult(dispute.getTradeId(), dispute.getTraderId());
        else
            disputeResult = dispute.getDisputeResultProperty().get();

        peersDisputeOptional = disputeManager.getDisputesAsObservableList().stream()
                .filter(d -> dispute.getTradeId().equals(d.getTradeId()) && dispute.getTraderId() != d.getTraderId()).findFirst();

        addInfoPane();

        if (!dispute.isSupportTicket())
            addCheckboxes();

        addTradeAmountPayoutControls();
        addPayoutAmountTextFields();
        addReasonControls();

        boolean applyPeersDisputeResult = peersDisputeOptional.isPresent() && peersDisputeOptional.get().isClosed();
        if (applyPeersDisputeResult) {
            // If the other peers dispute has been closed we apply the result to ourselves
            DisputeResult peersDisputeResult = peersDisputeOptional.get().getDisputeResultProperty().get();
            disputeResult.setBuyerPayoutAmount(peersDisputeResult.getBuyerPayoutAmount());
            disputeResult.setSellerPayoutAmount(peersDisputeResult.getSellerPayoutAmount());
            disputeResult.setWinner(peersDisputeResult.getWinner());
            disputeResult.setLoserPublisher(peersDisputeResult.isLoserPublisher());
            disputeResult.setReason(peersDisputeResult.getReason());
            disputeResult.setSummaryNotes(peersDisputeResult.summaryNotesProperty().get());

           /* if (disputeResult.getBuyerPayoutAmount() != null) {
                log.debug("buyerPayoutAmount " + disputeResult.getBuyerPayoutAmount().toFriendlyString());
                log.debug("sellerPayoutAmount " + disputeResult.getSellerPayoutAmount().toFriendlyString());
            }*/

            buyerGetsTradeAmountRadioButton.setDisable(true);
            buyerGetsAllRadioButton.setDisable(true);
            sellerGetsTradeAmountRadioButton.setDisable(true);
            sellerGetsAllRadioButton.setDisable(true);
            customRadioButton.setDisable(true);

            buyerPayoutAmountInputTextField.setDisable(true);
            sellerPayoutAmountInputTextField.setDisable(true);
            buyerPayoutAmountInputTextField.setEditable(false);
            sellerPayoutAmountInputTextField.setEditable(false);

            reasonWasBugRadioButton.setDisable(true);
            reasonWasUsabilityIssueRadioButton.setDisable(true);
            reasonProtocolViolationRadioButton.setDisable(true);
            reasonNoReplyRadioButton.setDisable(true);
            reasonWasScamRadioButton.setDisable(true);
            reasonWasOtherRadioButton.setDisable(true);
            reasonWasBankRadioButton.setDisable(true);

            isLoserPublisherCheckBox.setDisable(true);
            isLoserPublisherCheckBox.setSelected(peersDisputeResult.isLoserPublisher());

            applyPayoutAmounts(tradeAmountToggleGroup.selectedToggleProperty().get());
            applyTradeAmountRadioButtonStates();
        } else {
            isLoserPublisherCheckBox.setSelected(false);
        }

        setReasonRadioButtonState();

        addSummaryNotes();
        addButtons(contract);
    }

    private void addInfoPane() {
        Contract contract = dispute.getContract();
        addTitledGroupBg(gridPane, ++rowIndex, 16, Res.get("disputeSummaryWindow.title"));
        addLabelTextField(gridPane, rowIndex, Res.getWithCol("shared.tradeId"), dispute.getShortTradeId(), Layout.FIRST_ROW_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.openDate"), formatter.formatDateTime(dispute.getOpeningDate()));
        if (dispute.isDisputeOpenerIsMaker()) {
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
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradePrice"),
                formatter.formatPrice(contract.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradeVolume"),
                formatter.formatVolumeWithCode(contract.getTradePrice().getVolumeByAmount(contract.getTradeAmount())));
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

        buyerGetsTradeAmountRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount",
                Res.get("shared.buyer")));
        buyerGetsAllRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.getsAll",
                Res.get("shared.buyer")));
        sellerGetsTradeAmountRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount",
                Res.get("shared.seller")));
        sellerGetsAllRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.getsAll",
                Res.get("shared.seller")));

        customRadioButton = new RadioButton(Res.get("disputeSummaryWindow.payout.custom"));
        VBox radioButtonPane = new VBox();
        radioButtonPane.setSpacing(10);
        radioButtonPane.getChildren().addAll(buyerGetsTradeAmountRadioButton, buyerGetsAllRadioButton,
                sellerGetsTradeAmountRadioButton, sellerGetsAllRadioButton,
                customRadioButton);
        GridPane.setRowIndex(radioButtonPane, rowIndex);
        GridPane.setColumnIndex(radioButtonPane, 1);
        GridPane.setMargin(radioButtonPane, new Insets(10, 0, 0, 0));
        gridPane.getChildren().add(radioButtonPane);

        tradeAmountToggleGroup = new ToggleGroup();
        buyerGetsTradeAmountRadioButton.setToggleGroup(tradeAmountToggleGroup);
        buyerGetsAllRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsTradeAmountRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsAllRadioButton.setToggleGroup(tradeAmountToggleGroup);
        customRadioButton.setToggleGroup(tradeAmountToggleGroup);

        tradeAmountToggleGroupListener = (observable, oldValue, newValue) -> applyPayoutAmounts(newValue);
        tradeAmountToggleGroup.selectedToggleProperty().addListener(tradeAmountToggleGroupListener);

        buyerPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(buyerPayoutAmountInputTextField);
        sellerPayoutAmountListener = (observable1, oldValue1, newValue1) -> applyCustomAmounts(sellerPayoutAmountInputTextField);

        customRadioButtonSelectedListener = (observable, oldValue, newValue) -> {
            buyerPayoutAmountInputTextField.setEditable(newValue);
            sellerPayoutAmountInputTextField.setEditable(newValue);

            if (newValue) {
                buyerPayoutAmountInputTextField.textProperty().addListener(buyerPayoutAmountListener);
                sellerPayoutAmountInputTextField.textProperty().addListener(sellerPayoutAmountListener);
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

    }

    private boolean isPayoutAmountValid() {
        Coin buyerAmount = formatter.parseToCoin(buyerPayoutAmountInputTextField.getText());
        Coin sellerAmount = formatter.parseToCoin(sellerPayoutAmountInputTextField.getText());
        Contract contract = dispute.getContract();
        Coin tradeAmount = contract.getTradeAmount();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin available = tradeAmount
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin totalAmount = buyerAmount.add(sellerAmount);
        return (totalAmount.compareTo(available) == 0);
    }

    private void applyCustomAmounts(InputTextField inputTextField) {
        Contract contract = dispute.getContract();
        Coin buyerAmount = formatter.parseToCoin(buyerPayoutAmountInputTextField.getText());
        Coin sellerAmount = formatter.parseToCoin(sellerPayoutAmountInputTextField.getText());
        Offer offer = new Offer(contract.getOfferPayload());
        Coin available = contract.getTradeAmount().
                add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin totalAmount = buyerAmount.add(sellerAmount);

        if (totalAmount.compareTo(available) > 0) {
            new Popup<>().warning(Res.get("disputeSummaryWindow.payout.adjustAmount", available.toFriendlyString()))
                    .show();

            if (inputTextField == buyerPayoutAmountInputTextField) {
                buyerAmount = available.subtract(sellerAmount);
                inputTextField.setText(formatter.formatCoin(buyerAmount));
            } else if (inputTextField == sellerPayoutAmountInputTextField) {
                sellerAmount = available.subtract(buyerAmount);
                inputTextField.setText(formatter.formatCoin(sellerAmount));
            }
        }

        disputeResult.setBuyerPayoutAmount(buyerAmount);
        disputeResult.setSellerPayoutAmount(sellerAmount);

        if (buyerAmount.compareTo(sellerAmount) > 0)
            disputeResult.setWinner(DisputeResult.Winner.BUYER);
        else
            disputeResult.setWinner(DisputeResult.Winner.SELLER);
    }

    private void addPayoutAmountTextFields() {
        buyerPayoutAmountInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.buyer")).second;
        buyerPayoutAmountInputTextField.setEditable(false);

        sellerPayoutAmountInputTextField = addLabelInputTextField(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.seller")).second;
        sellerPayoutAmountInputTextField.setEditable(false);

        isLoserPublisherCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.payoutAmount.invert")).second;
    }

    private void addReasonControls() {
        Label label = addLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.reason"), 10);
        GridPane.setValignment(label, VPos.TOP);
        reasonWasBugRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.bug"));
        reasonWasUsabilityIssueRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.usability"));
        reasonProtocolViolationRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.protocolViolation"));
        reasonNoReplyRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.noReply"));
        reasonWasScamRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.scam"));
        reasonWasBankRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.bank"));
        reasonWasOtherRadioButton = new RadioButton(Res.get("disputeSummaryWindow.reason.other"));

        HBox feeRadioButtonPane = new HBox();
        feeRadioButtonPane.setSpacing(20);
        feeRadioButtonPane.getChildren().addAll(reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
                reasonProtocolViolationRadioButton, reasonNoReplyRadioButton,
                reasonWasBankRadioButton, reasonWasScamRadioButton, reasonWasOtherRadioButton);
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
        reasonWasBankRadioButton.setToggleGroup(reasonToggleGroup);

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
            else if (newValue == reasonWasBankRadioButton)
                disputeResult.setReason(DisputeResult.Reason.BANK_PROBLEMS);
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
                case BANK_PROBLEMS:
                    reasonToggleGroup.selectToggle(reasonWasBankRadioButton);
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
                sellerPayoutAmountInputTextField.textProperty()));

        Button cancelButton = tuple.second;

        final Dispute finalPeersDispute = peersDisputeOptional.get();
        closeTicketButton.setOnAction(e -> {
            if (dispute.getDepositTxSerialized() != null) {
                try {
                    AddressEntry arbitratorAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.ARBITRATOR);
                    disputeResult.setArbitratorPubKey(walletService.getOrCreateAddressEntry(AddressEntry.Context.ARBITRATOR).getPubKey());

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
                            contract.getBuyerPayoutAddressString(),
                            contract.getSellerPayoutAddressString(),
                            arbitratorAddressEntry.getKeyPair(),
                            contract.getBuyerMultiSigPubKey(),
                            contract.getSellerMultiSigPubKey(),
                            arbitratorAddressEntry.getPubKey()
                    );
                    disputeResult.setArbitratorSignature(arbitratorSignature);

                    closeTicketButton.disableProperty().unbind();
                    dispute.setDisputeResult(disputeResult);

                    disputeResult.setLoserPublisher(isLoserPublisherCheckBox.isSelected());
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
                            disputeResult.summaryNotesProperty().get());

                    dispute.setIsClosed(true);
                    disputeManager.sendDisputeResultMessage(disputeResult, dispute, text);

                    if (!finalPeersDispute.isClosed())
                        UserThread.runAfter(() ->
                                        new Popup<>().attention(Res.get("disputeSummaryWindow.close.closePeer")).show(),
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

    private void applyPayoutAmounts(Toggle selectedTradeAmountToggle) {
        if (selectedTradeAmountToggle != customRadioButton && selectedTradeAmountToggle != null) {
            applyPayoutAmountsToDisputeResult(selectedTradeAmountToggle);
            applyTradeAmountRadioButtonStates();
        }
    }

    private void applyPayoutAmountsToDisputeResult(Toggle selectedTradeAmountToggle) {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin buyerSecurityDeposit = offer.getBuyerSecurityDeposit();
        Coin sellerSecurityDeposit = offer.getSellerSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();
        if (selectedTradeAmountToggle == buyerGetsTradeAmountRadioButton) {
            disputeResult.setBuyerPayoutAmount(tradeAmount.add(buyerSecurityDeposit));
            disputeResult.setSellerPayoutAmount(sellerSecurityDeposit);
            disputeResult.setWinner(DisputeResult.Winner.BUYER);
        } else if (selectedTradeAmountToggle == buyerGetsAllRadioButton) {
            disputeResult.setBuyerPayoutAmount(tradeAmount
                    .add(buyerSecurityDeposit)
                    .add(sellerSecurityDeposit));
            disputeResult.setSellerPayoutAmount(Coin.ZERO);
            disputeResult.setWinner(DisputeResult.Winner.BUYER);
        } else if (selectedTradeAmountToggle == sellerGetsTradeAmountRadioButton) {
            disputeResult.setBuyerPayoutAmount(buyerSecurityDeposit);
            disputeResult.setSellerPayoutAmount(tradeAmount.add(sellerSecurityDeposit));
            disputeResult.setWinner(DisputeResult.Winner.SELLER);
        } else if (selectedTradeAmountToggle == sellerGetsAllRadioButton) {
            disputeResult.setBuyerPayoutAmount(Coin.ZERO);
            disputeResult.setSellerPayoutAmount(tradeAmount
                    .add(sellerSecurityDeposit)
                    .add(buyerSecurityDeposit));
            disputeResult.setWinner(DisputeResult.Winner.SELLER);
        }

        buyerPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getBuyerPayoutAmount()));
        sellerPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getSellerPayoutAmount()));
    }

    private void applyTradeAmountRadioButtonStates() {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin buyerSecurityDeposit = offer.getBuyerSecurityDeposit();
        Coin sellerSecurityDeposit = offer.getSellerSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();

        Coin buyerPayoutAmount = disputeResult.getBuyerPayoutAmount();
        Coin sellerPayoutAmount = disputeResult.getSellerPayoutAmount();

        buyerPayoutAmountInputTextField.setText(formatter.formatCoin(buyerPayoutAmount));
        sellerPayoutAmountInputTextField.setText(formatter.formatCoin(sellerPayoutAmount));

        if (buyerPayoutAmount.equals(tradeAmount.add(buyerSecurityDeposit)) &&
                sellerPayoutAmount.equals(sellerSecurityDeposit)) {
            buyerGetsTradeAmountRadioButton.setSelected(true);
        } else if (buyerPayoutAmount.equals(tradeAmount.add(buyerSecurityDeposit).add(sellerSecurityDeposit)) &&
                sellerPayoutAmount.equals(Coin.ZERO)) {
            buyerGetsAllRadioButton.setSelected(true);
        } else if (sellerPayoutAmount.equals(tradeAmount.add(sellerSecurityDeposit))
                && buyerPayoutAmount.equals(buyerSecurityDeposit)) {
            sellerGetsTradeAmountRadioButton.setSelected(true);
        } else if (sellerPayoutAmount.equals(tradeAmount.add(buyerSecurityDeposit).add(sellerSecurityDeposit))
                && buyerPayoutAmount.equals(Coin.ZERO)) {
            sellerGetsAllRadioButton.setSelected(true);
        } else {
            customRadioButton.setSelected(true);
        }
    }
}
