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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.components.AutoTooltipRadioButton;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.Layout;

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.Contract;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.add2ButtonsWithBox;
import static bisq.desktop.util.FormBuilder.addConfirmationLabelLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelWithVBox;
import static com.google.common.base.Preconditions.checkNotNull;

public class DisputeSummaryWindow extends Overlay<DisputeSummaryWindow> {
    private static final Logger log = LoggerFactory.getLogger(DisputeSummaryWindow.class);

    private final CoinFormatter formatter;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final TradeWalletService tradeWalletService;
    private final BtcWalletService btcWalletService;
    private final TxFeeEstimationService txFeeEstimationService;
    private final FeeService feeService;
    private Dispute dispute;
    private Optional<Runnable> finalizeDisputeHandlerOptional = Optional.<Runnable>empty();
    private ToggleGroup tradeAmountToggleGroup, reasonToggleGroup;
    private DisputeResult disputeResult;
    private RadioButton buyerGetsTradeAmountRadioButton, sellerGetsTradeAmountRadioButton,
            buyerGetsAllRadioButton, sellerGetsAllRadioButton, customRadioButton;
    private RadioButton reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
            reasonProtocolViolationRadioButton, reasonNoReplyRadioButton, reasonWasScamRadioButton,
            reasonWasOtherRadioButton, reasonWasBankRadioButton;
    // Dispute object of other trade peer. The dispute field is the one from which we opened the close dispute window.
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
    public DisputeSummaryWindow(@Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                                MediationManager mediationManager,
                                RefundManager refundManager,
                                TradeWalletService tradeWalletService,
                                BtcWalletService btcWalletService,
                                TxFeeEstimationService txFeeEstimationService,
                                FeeService feeService) {

        this.formatter = formatter;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.tradeWalletService = tradeWalletService;
        this.btcWalletService = btcWalletService;
        this.txFeeEstimationService = txFeeEstimationService;
        this.feeService = feeService;

        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 700;
        createGridPane();
        addContent();
        display();

        if (DevEnv.isDevMode()) {
            UserThread.execute(() -> {
                summaryNotesTextArea.setText("dummy result....");
            });
        }
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
        gridPane.getStyleClass().add("grid-pane");
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);
        gridPane.setPrefWidth(900);
    }

    private void addContent() {
        Contract contract = dispute.getContract();
        if (dispute.getDisputeResultProperty().get() == null)
            disputeResult = new DisputeResult(dispute.getTradeId(), dispute.getTraderId());
        else
            disputeResult = dispute.getDisputeResultProperty().get();

        peersDisputeOptional = getDisputeManager(dispute).getDisputesAsObservableList().stream()
                .filter(d -> dispute.getTradeId().equals(d.getTradeId()) && dispute.getTraderId() != d.getTraderId())
                .findFirst();

        addInfoPane();

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
        addTitledGroupBg(gridPane, ++rowIndex, 17, Res.get("disputeSummaryWindow.title")).getStyleClass().add("last");
        addConfirmationLabelLabel(gridPane, rowIndex, Res.get("shared.tradeId"), dispute.getShortTradeId(),
                Layout.TWICE_FIRST_ROW_DISTANCE);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.openDate"), DisplayUtils.formatDateTime(dispute.getOpeningDate()));
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
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.role"), role);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                FormattingUtils.formatPrice(contract.getTradePrice()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradeVolume"),
                DisplayUtils.formatVolumeWithCode(contract.getTradeVolume()));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(contract.getOfferPayload().getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(contract.getOfferPayload().getSellerSecurityDeposit());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.securityDeposit"), securityDeposit);
    }

    private void addTradeAmountPayoutControls() {
        buyerGetsTradeAmountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount",
                Res.get("shared.buyer")));
        buyerGetsAllRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsAll",
                Res.get("shared.buyer")));
        sellerGetsTradeAmountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount",
                Res.get("shared.seller")));
        sellerGetsAllRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsAll",
                Res.get("shared.seller")));
        customRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.custom"));

        VBox radioButtonPane = new VBox();
        radioButtonPane.setSpacing(10);
        radioButtonPane.getChildren().addAll(buyerGetsTradeAmountRadioButton, buyerGetsAllRadioButton,
                sellerGetsTradeAmountRadioButton, sellerGetsAllRadioButton,
                customRadioButton);

        addTopLabelWithVBox(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.payout"), radioButtonPane, 0);

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
        Coin buyerAmount = ParsingUtils.parseToCoin(buyerPayoutAmountInputTextField.getText(), formatter);
        Coin sellerAmount = ParsingUtils.parseToCoin(sellerPayoutAmountInputTextField.getText(), formatter);
        Contract contract = dispute.getContract();
        Coin tradeAmount = contract.getTradeAmount();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin available = tradeAmount
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin totalAmount = buyerAmount.add(sellerAmount);
        if (getDisputeManager(dispute) instanceof RefundManager) {
            // We allow to spend less in case of RefundAgent
            return totalAmount.compareTo(available) <= 0;
        } else {
            return totalAmount.compareTo(available) == 0;
        }
    }

    private void applyCustomAmounts(InputTextField inputTextField) {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin available = contract.getTradeAmount()
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin enteredAmount = ParsingUtils.parseToCoin(inputTextField.getText(), formatter);
        if (enteredAmount.isNegative()) {
            enteredAmount = Coin.ZERO;
            inputTextField.setText(formatter.formatCoin(enteredAmount));
        }
        if (enteredAmount.isPositive() && !Restrictions.isAboveDust(enteredAmount)) {
            enteredAmount = Restrictions.getMinNonDustOutput();
            inputTextField.setText(formatter.formatCoin(enteredAmount));
        }
        if (enteredAmount.compareTo(available) > 0) {
            enteredAmount = available;
            inputTextField.setText(formatter.formatCoin(enteredAmount));
        }
        Coin counterPartAsCoin = available.subtract(enteredAmount);
        String formattedCounterPartAmount = formatter.formatCoin(counterPartAsCoin);
        Coin buyerAmount;
        Coin sellerAmount;
        if (inputTextField == buyerPayoutAmountInputTextField) {
            buyerAmount = enteredAmount;
            sellerAmount = counterPartAsCoin;
            Coin sellerAmountFromField = ParsingUtils.parseToCoin(sellerPayoutAmountInputTextField.getText(), formatter);
            Coin totalAmountFromFields = enteredAmount.add(sellerAmountFromField);
            // RefundAgent can enter less then available
            if (getDisputeManager(dispute) instanceof MediationManager ||
                    totalAmountFromFields.compareTo(available) > 0) {
                sellerPayoutAmountInputTextField.setText(formattedCounterPartAmount);
            } else {
                sellerAmount = sellerAmountFromField;
            }
        } else {
            sellerAmount = enteredAmount;
            buyerAmount = counterPartAsCoin;
            Coin buyerAmountFromField = ParsingUtils.parseToCoin(buyerPayoutAmountInputTextField.getText(), formatter);
            Coin totalAmountFromFields = enteredAmount.add(buyerAmountFromField);
            // RefundAgent can enter less then available
            if (getDisputeManager(dispute) instanceof MediationManager ||
                    totalAmountFromFields.compareTo(available) > 0) {
                buyerPayoutAmountInputTextField.setText(formattedCounterPartAmount);
            } else {
                buyerAmount = buyerAmountFromField;
            }
        }

        disputeResult.setBuyerPayoutAmount(buyerAmount);
        disputeResult.setSellerPayoutAmount(sellerAmount);
        disputeResult.setWinner(buyerAmount.compareTo(sellerAmount) > 0 ?
                DisputeResult.Winner.BUYER :
                DisputeResult.Winner.SELLER);
    }

    private void addPayoutAmountTextFields() {
        buyerPayoutAmountInputTextField = new InputTextField();
        buyerPayoutAmountInputTextField.setLabelFloat(true);
        buyerPayoutAmountInputTextField.setEditable(false);
        buyerPayoutAmountInputTextField.setPromptText(Res.get("disputeSummaryWindow.payoutAmount.buyer"));

        sellerPayoutAmountInputTextField = new InputTextField();
        sellerPayoutAmountInputTextField.setLabelFloat(true);
        sellerPayoutAmountInputTextField.setPromptText(Res.get("disputeSummaryWindow.payoutAmount.seller"));
        sellerPayoutAmountInputTextField.setEditable(false);

        isLoserPublisherCheckBox = new AutoTooltipCheckBox(Res.get("disputeSummaryWindow.payoutAmount.invert"));

        VBox vBox = new VBox();
        vBox.setSpacing(15);
        vBox.getChildren().addAll(buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField, isLoserPublisherCheckBox);
        GridPane.setMargin(vBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setColumnIndex(vBox, 1);
        gridPane.getChildren().add(vBox);
    }

    private void addReasonControls() {
        reasonWasBugRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.bug"));
        reasonWasUsabilityIssueRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.usability"));
        reasonProtocolViolationRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.protocolViolation"));
        reasonNoReplyRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.noReply"));
        reasonWasScamRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.scam"));
        reasonWasBankRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.bank"));
        reasonWasOtherRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason.other"));

        HBox feeRadioButtonPane = new HBox();
        feeRadioButtonPane.setSpacing(20);
        feeRadioButtonPane.getChildren().addAll(reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
                reasonProtocolViolationRadioButton, reasonNoReplyRadioButton,
                reasonWasBankRadioButton, reasonWasScamRadioButton, reasonWasOtherRadioButton);

        VBox vBox = addTopLabelWithVBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.reason"),
                feeRadioButtonPane, 10).second;
        GridPane.setColumnSpan(vBox, 2);

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
        summaryNotesTextArea = new BisqTextArea();
        summaryNotesTextArea.setPromptText(Res.get("disputeSummaryWindow.addSummaryNotes"));
        summaryNotesTextArea.setWrapText(true);

        Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.summaryNotes"), summaryNotesTextArea, 0);
        GridPane.setColumnSpan(topLabelWithVBox.second, 2);

        summaryNotesTextArea.setPrefHeight(50);
        summaryNotesTextArea.textProperty().bindBidirectional(disputeResult.summaryNotesProperty());
    }

    private void addButtons(Contract contract) {
        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++rowIndex,
                Res.get("disputeSummaryWindow.close.button"),
                Res.get("shared.cancel"), 15, true);
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

        closeTicketButton.setOnAction(e -> {
            if (dispute.getDepositTxSerialized() == null) {
                log.warn("dispute.getDepositTxSerialized is null");
                return;
            }
            if (dispute.getSupportType() == SupportType.REFUND &&
                    peersDisputeOptional.isPresent() &&
                    !peersDisputeOptional.get().isClosed()) {
                showPayoutTxConfirmation(contract, disputeResult,
                        () -> {
                            doClose(closeTicketButton);
                        });
            } else {
                doClose(closeTicketButton);
            }
        });

        cancelButton.setOnAction(e -> {
            dispute.setDisputeResult(disputeResult);
            hide();
        });
    }

    private void showPayoutTxConfirmation(Contract contract, DisputeResult disputeResult, ResultHandler resultHandler) {
        Coin buyerPayoutAmount = disputeResult.getBuyerPayoutAmount();
        String buyerPayoutAddressString = contract.getBuyerPayoutAddressString();
        Coin sellerPayoutAmount = disputeResult.getSellerPayoutAmount();
        String sellerPayoutAddressString = contract.getSellerPayoutAddressString();
        Coin outputAmount = buyerPayoutAmount.add(sellerPayoutAmount);
        Tuple2<Coin, Integer> feeTuple = txFeeEstimationService.getEstimatedFeeAndTxSize(outputAmount, feeService, btcWalletService);
        Coin fee = feeTuple.first;
        Integer txSize = feeTuple.second;
        double feePerByte = CoinUtil.getFeePerByte(fee, txSize);
        double kb = txSize / 1000d;
        Coin inputAmount = outputAmount.add(fee);
        String buyerDetails = "";
        if (buyerPayoutAmount.isPositive()) {
            buyerDetails = Res.get("disputeSummaryWindow.close.txDetails.buyer",
                    formatter.formatCoinWithCode(buyerPayoutAmount),
                    buyerPayoutAddressString);
        }
        String sellerDetails = "";
        if (sellerPayoutAmount.isPositive()) {
            sellerDetails = Res.get("disputeSummaryWindow.close.txDetails.seller",
                    formatter.formatCoinWithCode(sellerPayoutAmount),
                    sellerPayoutAddressString);
        }
        new Popup().width(900)
                .headLine(Res.get("disputeSummaryWindow.close.txDetails.headline"))
                .confirmation(Res.get("disputeSummaryWindow.close.txDetails",
                        formatter.formatCoinWithCode(inputAmount),
                        buyerDetails,
                        sellerDetails,
                        formatter.formatCoinWithCode(fee),
                        feePerByte,
                        kb))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    doPayout(buyerPayoutAmount,
                            sellerPayoutAmount,
                            fee,
                            buyerPayoutAddressString,
                            sellerPayoutAddressString,
                            resultHandler);
                })
                .closeButtonText(Res.get("shared.cancel"))
                .onClose(() -> {
                })
                .show();
    }

    private void doPayout(Coin buyerPayoutAmount,
                          Coin sellerPayoutAmount,
                          Coin fee,
                          String buyerPayoutAddressString,
                          String sellerPayoutAddressString,
                          ResultHandler resultHandler) {
        try {
            Transaction tx = btcWalletService.createRefundPayoutTx(buyerPayoutAmount,
                    sellerPayoutAmount,
                    fee,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString);
            log.error("transaction " + tx);
            tradeWalletService.broadcastTx(tx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.handleResult();
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    log.error("TxBroadcastException at doPayout", exception);
                    new Popup().error(exception.toString()).show();
                    ;
                }
            });
        } catch (InsufficientMoneyException | WalletException | TransactionVerificationException e) {
            log.error("Exception at doPayout", e);
            new Popup().error(e.toString()).show();
        }
    }

    private void doClose(Button closeTicketButton) {
        disputeResult.setLoserPublisher(isLoserPublisherCheckBox.isSelected());
        disputeResult.setCloseDate(new Date());
        dispute.setDisputeResult(disputeResult);
        dispute.setIsClosed(true);
        String text = Res.get("disputeSummaryWindow.close.msg",
                DisplayUtils.formatDateTime(disputeResult.getCloseDate()),
                formatter.formatCoinWithCode(disputeResult.getBuyerPayoutAmount()),
                formatter.formatCoinWithCode(disputeResult.getSellerPayoutAmount()),
                disputeResult.summaryNotesProperty().get());

        if (dispute.getSupportType() == SupportType.MEDIATION) {
            text += Res.get("disputeSummaryWindow.close.nextStepsForMediation");
        } else if (dispute.getSupportType() == SupportType.REFUND) {
            text += Res.get("disputeSummaryWindow.close.nextStepsForRefundAgentArbitration");
        }

        checkNotNull(getDisputeManager(dispute)).sendDisputeResultMessage(disputeResult, dispute, text);

        if (peersDisputeOptional.isPresent() && !peersDisputeOptional.get().isClosed() && !DevEnv.isDevMode()) {
            UserThread.runAfter(() -> new Popup()
                            .attention(Res.get("disputeSummaryWindow.close.closePeer"))
                            .show(),
                    200, TimeUnit.MILLISECONDS);
        }

        finalizeDisputeHandlerOptional.ifPresent(Runnable::run);

        closeTicketButton.disableProperty().unbind();

        hide();
    }

    private DisputeManager<? extends DisputeList<? extends DisputeList>> getDisputeManager(Dispute dispute) {
        if (dispute.getSupportType() != null) {
            switch (dispute.getSupportType()) {
                case ARBITRATION:
                    return null;
                case MEDIATION:
                    return mediationManager;
                case TRADE:
                    break;
                case REFUND:
                    return refundManager;
            }
        }
        return null;
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
