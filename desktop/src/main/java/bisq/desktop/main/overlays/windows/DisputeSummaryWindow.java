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
import bisq.desktop.main.support.dispute.DisputeSummaryVerification;
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
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

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

import java.time.Instant;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DisputeSummaryWindow extends Overlay<DisputeSummaryWindow> {
    private final CoinFormatter formatter;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final TradeWalletService tradeWalletService;
    private final BtcWalletService btcWalletService;
    private final TxFeeEstimationService txFeeEstimationService;
    private final MempoolService mempoolService;
    private final DaoFacade daoFacade;
    private Dispute dispute;
    private Optional<Runnable> finalizeDisputeHandlerOptional = Optional.empty();
    private ToggleGroup tradeAmountToggleGroup, reasonToggleGroup;
    private DisputeResult disputeResult;
    private RadioButton buyerGetsTradeAmountRadioButton, sellerGetsTradeAmountRadioButton,
            buyerGetsCompensationRadioButton, sellerGetsCompensationRadioButton,
            buyerGetsTradeAmountMinusPenaltyRadioButton, sellerGetsTradeAmountMinusPenaltyRadioButton, customRadioButton;
    private RadioButton reasonWasBugRadioButton, reasonWasUsabilityIssueRadioButton,
            reasonProtocolViolationRadioButton, reasonNoReplyRadioButton, reasonWasScamRadioButton,
            reasonWasOtherRadioButton, reasonWasBankRadioButton, reasonWasOptionTradeRadioButton,
            reasonWasSellerNotRespondingRadioButton, reasonWasWrongSenderAccountRadioButton,
            reasonWasPeerWasLateRadioButton, reasonWasTradeAlreadySettledRadioButton;

    // Dispute object of other trade peer. The dispute field is the one from which we opened the close dispute window.
    private Optional<Dispute> peersDisputeOptional;
    private String role;
    private Label delayedPayoutTxStatus;
    private TextArea summaryNotesTextArea;

    private ChangeListener<Boolean> customRadioButtonSelectedListener, buyerGetsTradeAmountSelectedListener, sellerGetsTradeAmountSelectedListener;
    private ChangeListener<Toggle> reasonToggleSelectionListener;
    private InputTextField buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField, compensationOrPenalty;
    private ChangeListener<Boolean> buyerPayoutAmountListener, sellerPayoutAmountListener;
    private ChangeListener<Toggle> tradeAmountToggleGroupListener;
    private ChangeListener<String> compensationOrPenaltyListener;
    private boolean updatingUi = false;

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
                                MempoolService mempoolService,
                                DaoFacade daoFacade) {

        this.formatter = formatter;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.tradeWalletService = tradeWalletService;
        this.btcWalletService = btcWalletService;
        this.txFeeEstimationService = txFeeEstimationService;
        this.mempoolService = mempoolService;
        this.daoFacade = daoFacade;

        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 1150;
        createGridPane();
        addContent();
        checkDelayedPayoutTransaction();
        display();

        if (DevEnv.isDevMode()) {
            UserThread.execute(() -> {
                summaryNotesTextArea.setText("dummy result....");
            });
        }
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

        if (buyerGetsTradeAmountRadioButton != null)
            buyerGetsTradeAmountRadioButton.selectedProperty().removeListener(buyerGetsTradeAmountSelectedListener);

        if (sellerGetsTradeAmountRadioButton != null)
            sellerGetsTradeAmountRadioButton.selectedProperty().removeListener(sellerGetsTradeAmountSelectedListener);

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
        gridPane.setPadding(new Insets(35, 40, 0, 40));
        gridPane.getStyleClass().add("grid-pane");
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);
        gridPane.setPrefWidth(width);
    }

    private void addContent() {
        Contract contract = dispute.getContract();
        if (dispute.getDisputeResultProperty().get() == null)
            disputeResult = new DisputeResult(dispute.getTradeId(), dispute.getTraderId());
        else
            disputeResult = dispute.getDisputeResultProperty().get();

        peersDisputeOptional = checkNotNull(getDisputeManager(dispute)).getDisputesAsObservableList().stream()
                .filter(d -> dispute.getTradeId().equals(d.getTradeId()) && dispute.getTraderId() != d.getTraderId())
                .findFirst();

        addInfoPane();

        addTradeAmountPayoutControls();
        addPayoutAmountTextFields();
        addReasonControls();
        applyDisputeResultToUiControls();

        boolean applyPeersDisputeResult = peersDisputeOptional.isPresent() && peersDisputeOptional.get().isClosed();
        if (applyPeersDisputeResult) {
            // If the other peers dispute has been closed we apply the result to ourselves
            DisputeResult peersDisputeResult = peersDisputeOptional.get().getDisputeResultProperty().get();
            disputeResult.setBuyerPayoutAmount(peersDisputeResult.getBuyerPayoutAmount());
            disputeResult.setSellerPayoutAmount(peersDisputeResult.getSellerPayoutAmount());
            disputeResult.setPayoutAdjustmentPercent(peersDisputeResult.getPayoutAdjustmentPercent());
            disputeResult.setPayoutSuggestion(peersDisputeResult.getPayoutSuggestion());
            disputeResult.setWinner(peersDisputeResult.getWinner());
            disputeResult.setReason(peersDisputeResult.getReason());
            disputeResult.setSummaryNotes(peersDisputeResult.summaryNotesProperty().get());

            buyerGetsTradeAmountRadioButton.setDisable(true);
            buyerGetsCompensationRadioButton.setDisable(true);
            buyerGetsTradeAmountMinusPenaltyRadioButton.setDisable(true);
            sellerGetsTradeAmountRadioButton.setDisable(true);
            sellerGetsCompensationRadioButton.setDisable(true);
            sellerGetsTradeAmountMinusPenaltyRadioButton.setDisable(true);
            customRadioButton.setDisable(true);

            buyerPayoutAmountInputTextField.setDisable(true);
            sellerPayoutAmountInputTextField.setDisable(true);
            compensationOrPenalty.setDisable(true);
            buyerPayoutAmountInputTextField.setEditable(false);
            sellerPayoutAmountInputTextField.setEditable(false);
            compensationOrPenalty.setEditable(false);

            reasonWasBugRadioButton.setDisable(true);
            reasonWasUsabilityIssueRadioButton.setDisable(true);
            reasonProtocolViolationRadioButton.setDisable(true);
            reasonNoReplyRadioButton.setDisable(true);
            reasonWasScamRadioButton.setDisable(true);
            reasonWasOtherRadioButton.setDisable(true);
            reasonWasBankRadioButton.setDisable(true);
            reasonWasOptionTradeRadioButton.setDisable(true);
            reasonWasSellerNotRespondingRadioButton.setDisable(true);
            reasonWasWrongSenderAccountRadioButton.setDisable(true);
            reasonWasPeerWasLateRadioButton.setDisable(true);
            reasonWasTradeAlreadySettledRadioButton.setDisable(true);
            applyDisputeResultToUiControls();
        }

        setReasonRadioButtonState();

        addSummaryNotes();
        addButtons(contract);
    }

    private void addInfoPane() {
        Contract contract = dispute.getContract();
        addTitledGroupBg(gridPane, ++rowIndex, 17, Res.get("disputeSummaryWindow.title")).getStyleClass().add("last");
        addConfirmationLabelTextField(gridPane, rowIndex, Res.get("shared.tradeId"), dispute.getShortTradeId(),
                Layout.TWICE_FIRST_ROW_DISTANCE);
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.openDate"), DisplayUtils.formatDateTime(dispute.getOpeningDate()));
        role = dispute.getRoleString();
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.role"), role);
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                FormattingUtils.formatPrice(contract.getTradePrice()));
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradeVolume"),
                VolumeUtil.formatVolumeWithCode(contract.getTradeVolume()));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(contract.getOfferPayload().getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(contract.getOfferPayload().getSellerSecurityDeposit());
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.securityDeposit"), securityDeposit);

        boolean isMediationDispute = getDisputeManager(dispute) instanceof MediationManager;
        if (isMediationDispute) {
            if (dispute.getTradePeriodEnd().getTime() > 0) {
                String status = DisplayUtils.formatDateTime(dispute.getTradePeriodEnd());
                Label tradePeriodEnd = addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.tradePeriodEnd"), status).second;
                if (dispute.getTradePeriodEnd().toInstant().isAfter(Instant.now())) {
                    tradePeriodEnd.getStyleClass().add("alert"); // highlight field when the trade period is still active
                }
            }
            if (dispute.getExtraDataMap() != null && dispute.getExtraDataMap().size() > 0) {
                String extraDataSummary = "";
                for (Map.Entry<String, String> entry : dispute.getExtraDataMap().entrySet()) {
                    extraDataSummary += "[" + entry.getKey() + ":" + entry.getValue() + "] ";
                }
                addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.extraInfo"), extraDataSummary);
            }
        } else {
            delayedPayoutTxStatus = addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.delayedPayoutStatus"), "Checking...").second;
        }
    }

    private void addTradeAmountPayoutControls() {
        buyerGetsTradeAmountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount", Res.get("shared.buyer")));
        buyerGetsCompensationRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsCompensation", Res.get("shared.buyer")));
        buyerGetsTradeAmountMinusPenaltyRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsPenalty", Res.get("shared.buyer")));
        sellerGetsTradeAmountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsTradeAmount", Res.get("shared.seller")));
        sellerGetsCompensationRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsCompensation", Res.get("shared.seller")));
        sellerGetsTradeAmountMinusPenaltyRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.getsPenalty", Res.get("shared.seller")));
        customRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.payout.custom"));

        VBox radioButtonPane = new VBox();
        radioButtonPane.setSpacing(10);
        radioButtonPane.getChildren().addAll(buyerGetsTradeAmountRadioButton, buyerGetsCompensationRadioButton,
                buyerGetsTradeAmountMinusPenaltyRadioButton, sellerGetsTradeAmountRadioButton, sellerGetsCompensationRadioButton, sellerGetsTradeAmountMinusPenaltyRadioButton,
                customRadioButton);

        addTopLabelWithVBox(gridPane, ++rowIndex, Res.get("disputeSummaryWindow.payout"), radioButtonPane, 0);

        tradeAmountToggleGroup = new ToggleGroup();
        buyerGetsTradeAmountRadioButton.setToggleGroup(tradeAmountToggleGroup);
        buyerGetsCompensationRadioButton.setToggleGroup(tradeAmountToggleGroup);
        buyerGetsTradeAmountMinusPenaltyRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsTradeAmountRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsCompensationRadioButton.setToggleGroup(tradeAmountToggleGroup);
        sellerGetsTradeAmountMinusPenaltyRadioButton.setToggleGroup(tradeAmountToggleGroup);
        customRadioButton.setToggleGroup(tradeAmountToggleGroup);

        tradeAmountToggleGroupListener = (observable, oldValue, newValue) -> applyUpdateFromUi(newValue);
        tradeAmountToggleGroup.selectedToggleProperty().addListener(tradeAmountToggleGroupListener);

        buyerPayoutAmountListener = (observable, oldValue, newValue) -> applyCustomAmounts(buyerPayoutAmountInputTextField, oldValue, newValue);
        sellerPayoutAmountListener = (observable, oldValue, newValue) -> applyCustomAmounts(sellerPayoutAmountInputTextField, oldValue, newValue);

        buyerGetsTradeAmountSelectedListener = (observable, oldValue, newValue) -> {
            compensationOrPenalty.setEditable(!newValue);
        };
        buyerGetsTradeAmountRadioButton.selectedProperty().addListener(buyerGetsTradeAmountSelectedListener);

        sellerGetsTradeAmountSelectedListener = (observable, oldValue, newValue) -> {
            compensationOrPenalty.setEditable(!newValue);
        };
        sellerGetsTradeAmountRadioButton.selectedProperty().addListener(sellerGetsTradeAmountSelectedListener);

        customRadioButtonSelectedListener = (observable, oldValue, newValue) -> {
            buyerPayoutAmountInputTextField.setEditable(newValue);
            sellerPayoutAmountInputTextField.setEditable(newValue);
            compensationOrPenalty.setEditable(!newValue);
            if (newValue) {
                buyerPayoutAmountInputTextField.focusedProperty().addListener(buyerPayoutAmountListener);
                sellerPayoutAmountInputTextField.focusedProperty().addListener(sellerPayoutAmountListener);
            } else {
                removePayoutAmountListeners();
            }
        };
        customRadioButton.selectedProperty().addListener(customRadioButtonSelectedListener);
    }

    private void removePayoutAmountListeners() {
        if (buyerPayoutAmountInputTextField != null && buyerPayoutAmountListener != null)
            buyerPayoutAmountInputTextField.focusedProperty().removeListener(buyerPayoutAmountListener);

        if (sellerPayoutAmountInputTextField != null && sellerPayoutAmountListener != null)
            sellerPayoutAmountInputTextField.focusedProperty().removeListener(sellerPayoutAmountListener);
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

        boolean isRefundAgent = getDisputeManager(dispute) instanceof RefundManager;
        if (isRefundAgent) {
            // We allow to spend less in case of RefundAgent or even zero to both, so in that case no payout tx will
            // be made
            return totalAmount.compareTo(available) <= 0;
        } else {
            if (!totalAmount.isPositive()) {
                return false;
            }
            return totalAmount.compareTo(available) == 0;
        }
    }

    private void applyCustomAmounts(InputTextField inputTextField, boolean oldFocusValue, boolean newFocusValue) {
        // We only apply adjustments at focus out, otherwise we cannot enter certain values if we update at each
        // keystroke.
        if (!oldFocusValue || newFocusValue) {
            return;
        }

        Contract contract = dispute.getContract();
        boolean isMediationDispute = getDisputeManager(dispute) instanceof MediationManager;
        // At mediation we require a min. payout to the losing party to keep incentive for the trader to accept the
        // mediated payout. For Refund agent cases we do not have that restriction.
        Coin minRefundAtDispute = isMediationDispute ? Restrictions.getMinRefundAtMediatedDispute() : Coin.ZERO;

        Offer offer = new Offer(contract.getOfferPayload());
        Coin totalAvailable = contract.getTradeAmount()
                .add(offer.getBuyerSecurityDeposit())
                .add(offer.getSellerSecurityDeposit());
        Coin availableForPayout = totalAvailable.subtract(minRefundAtDispute);

        Coin enteredAmount = ParsingUtils.parseToCoin(inputTextField.getText(), formatter);
        if (enteredAmount.compareTo(minRefundAtDispute) < 0) {
            enteredAmount = minRefundAtDispute;
            inputTextField.setText(formatter.formatCoin(enteredAmount));
        }
        if (enteredAmount.isPositive() && !Restrictions.isAboveDust(enteredAmount)) {
            enteredAmount = Restrictions.getMinNonDustOutput();
            inputTextField.setText(formatter.formatCoin(enteredAmount));
        }
        if (enteredAmount.compareTo(availableForPayout) > 0) {
            enteredAmount = availableForPayout;
            inputTextField.setText(formatter.formatCoin(enteredAmount));
        }
        Coin counterPartAsCoin = totalAvailable.subtract(enteredAmount);
        String formattedCounterPartAmount = formatter.formatCoin(counterPartAsCoin);
        Coin buyerAmount;
        Coin sellerAmount;
        if (inputTextField == buyerPayoutAmountInputTextField) {
            buyerAmount = enteredAmount;
            sellerAmount = counterPartAsCoin;
            Coin sellerAmountFromField = ParsingUtils.parseToCoin(sellerPayoutAmountInputTextField.getText(), formatter);
            Coin totalAmountFromFields = enteredAmount.add(sellerAmountFromField);
            // RefundAgent can enter less then available
            if (isMediationDispute ||
                    totalAmountFromFields.compareTo(totalAvailable) > 0) {
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
            if (isMediationDispute ||
                    totalAmountFromFields.compareTo(totalAvailable) > 0) {
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

        compensationOrPenalty = new InputTextField();
        compensationOrPenalty.setPromptText("Comp|Penalty percent");
        compensationOrPenalty.setLabelFloat(true);
        HBox hBoxPenalty = new HBox(compensationOrPenalty);
        HBox hBoxPayouts = new HBox(buyerPayoutAmountInputTextField, sellerPayoutAmountInputTextField);
        hBoxPayouts.setSpacing(15);

        VBox vBox = new VBox();
        vBox.setSpacing(25);
        vBox.getChildren().addAll(hBoxPenalty, hBoxPayouts);
        GridPane.setMargin(vBox, new Insets(80, 50, 50, 50));
        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setColumnIndex(vBox, 1);
        gridPane.getChildren().add(vBox);

        compensationOrPenaltyListener = (observable, oldValue, newValue) -> {
            applyUpdateFromUi(tradeAmountToggleGroup.selectedToggleProperty().get());
        };

        compensationOrPenalty.textProperty().addListener(compensationOrPenaltyListener);
    }

    private void addReasonControls() {
        reasonWasBugRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.BUG.name()));
        reasonWasUsabilityIssueRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.USABILITY.name()));
        reasonProtocolViolationRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.PROTOCOL_VIOLATION.name()));
        reasonNoReplyRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.NO_REPLY.name()));
        reasonWasScamRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.SCAM.name()));
        reasonWasBankRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.BANK_PROBLEMS.name()));
        reasonWasOtherRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.OTHER.name()));
        reasonWasOptionTradeRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.OPTION_TRADE.name()));
        reasonWasSellerNotRespondingRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.SELLER_NOT_RESPONDING.name()));
        reasonWasWrongSenderAccountRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.WRONG_SENDER_ACCOUNT.name()));
        reasonWasPeerWasLateRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.PEER_WAS_LATE.name()));
        reasonWasTradeAlreadySettledRadioButton = new AutoTooltipRadioButton(Res.get("disputeSummaryWindow.reason." + DisputeResult.Reason.TRADE_ALREADY_SETTLED.name()));

        HBox feeRadioButtonPane = new HBox();
        feeRadioButtonPane.setSpacing(20);
        // We don't show no reply and protocol violation as those should be covered by more specific ones. We still leave
        // the code to enable it if it turns out it is still requested by mediators.
        feeRadioButtonPane.getChildren().addAll(
                reasonWasTradeAlreadySettledRadioButton,
                reasonWasPeerWasLateRadioButton,
                reasonWasOptionTradeRadioButton,
                reasonWasSellerNotRespondingRadioButton,
                reasonWasWrongSenderAccountRadioButton,
                reasonWasBugRadioButton,
                reasonWasUsabilityIssueRadioButton,
                reasonWasBankRadioButton,
                reasonWasOtherRadioButton
        );

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
        reasonWasOptionTradeRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasSellerNotRespondingRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasWrongSenderAccountRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasPeerWasLateRadioButton.setToggleGroup(reasonToggleGroup);
        reasonWasTradeAlreadySettledRadioButton.setToggleGroup(reasonToggleGroup);

        reasonToggleSelectionListener = (observable, oldValue, newValue) -> {
            if (newValue == reasonWasBugRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.BUG);
            } else if (newValue == reasonWasUsabilityIssueRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.USABILITY);
            } else if (newValue == reasonProtocolViolationRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.PROTOCOL_VIOLATION);
            } else if (newValue == reasonNoReplyRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.NO_REPLY);
            } else if (newValue == reasonWasScamRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.SCAM);
            } else if (newValue == reasonWasBankRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.BANK_PROBLEMS);
            } else if (newValue == reasonWasOtherRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.OTHER);
            } else if (newValue == reasonWasOptionTradeRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.OPTION_TRADE);
            } else if (newValue == reasonWasSellerNotRespondingRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.SELLER_NOT_RESPONDING);
            } else if (newValue == reasonWasWrongSenderAccountRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.WRONG_SENDER_ACCOUNT);
            } else if (newValue == reasonWasTradeAlreadySettledRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.TRADE_ALREADY_SETTLED);
            } else if (newValue == reasonWasPeerWasLateRadioButton) {
                disputeResult.setReason(DisputeResult.Reason.PEER_WAS_LATE);
            }
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
                case OPTION_TRADE:
                    reasonToggleGroup.selectToggle(reasonWasOptionTradeRadioButton);
                    break;
                case SELLER_NOT_RESPONDING:
                    reasonToggleGroup.selectToggle(reasonWasSellerNotRespondingRadioButton);
                    break;
                case WRONG_SENDER_ACCOUNT:
                    reasonToggleGroup.selectToggle(reasonWasWrongSenderAccountRadioButton);
                    break;
                case PEER_WAS_LATE:
                    reasonToggleGroup.selectToggle(reasonWasPeerWasLateRadioButton);
                    break;
                case TRADE_ALREADY_SETTLED:
                    reasonToggleGroup.selectToggle(reasonWasTradeAlreadySettledRadioButton);
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

        summaryNotesTextArea.setPrefHeight(160);
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
                showPayoutTxConfirmation(contract,
                        disputeResult,
                        () -> doCloseIfValid(closeTicketButton));
            } else {
                doCloseIfValid(closeTicketButton);
            }
        });

        cancelButton.setOnAction(e -> {
            dispute.setDisputeResult(disputeResult);
            checkNotNull(getDisputeManager(dispute)).requestPersistence();
            hide();
        });
    }

    private void showPayoutTxConfirmation(Contract contract, DisputeResult disputeResult, ResultHandler resultHandler) {
        if (dispute.isPayoutDone()) {
            new Popup().headLine(Res.get("disputeSummaryWindow.close.alreadyPaid.headline"))
                    .confirmation(Res.get("disputeSummaryWindow.close.alreadyPaid.text"))
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
        }

        Coin buyerPayoutAmount = disputeResult.getBuyerPayoutAmount();
        String buyerPayoutAddressString = contract.getBuyerPayoutAddressString();
        Coin sellerPayoutAmount = disputeResult.getSellerPayoutAmount();
        String sellerPayoutAddressString = contract.getSellerPayoutAddressString();
        Coin outputAmount = buyerPayoutAmount.add(sellerPayoutAmount);
        Tuple2<Coin, Integer> feeTuple = txFeeEstimationService.getEstimatedFeeAndTxVsize(outputAmount, btcWalletService);
        Coin fee = feeTuple.first;
        Integer txVsize = feeTuple.second;
        double feePerVbyte = CoinUtil.getFeePerVbyte(fee, txVsize);
        double vkb = txVsize / 1000d;
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
        if (outputAmount.isPositive()) {
            new Popup().width(900)
                    .headLine(Res.get("disputeSummaryWindow.close.txDetails.headline"))
                    .confirmation(Res.get("disputeSummaryWindow.close.txDetails",
                            formatter.formatCoinWithCode(inputAmount),
                            buyerDetails,
                            sellerDetails,
                            formatter.formatCoinWithCode(fee),
                            feePerVbyte,
                            vkb))
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
                    .show();
        } else {
            // No payout will be made
            new Popup().headLine(Res.get("disputeSummaryWindow.close.noPayout.headline"))
                    .confirmation(Res.get("disputeSummaryWindow.close.noPayout.text"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(resultHandler::handleResult)
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
        }
    }

    private void doPayout(Coin buyerPayoutAmount,
                          Coin sellerPayoutAmount,
                          Coin fee,
                          String buyerPayoutAddressString,
                          String sellerPayoutAddressString,
                          ResultHandler resultHandler) {
        if (dispute.isPayoutDone()) {
            log.error("Payout already processed, returning to avoid double payout for dispute of trade {}",
                    dispute.getTradeId());
            return;
        }
        dispute.setPayoutDone(true);
        try {
            Transaction tx = btcWalletService.createRefundPayoutTx(buyerPayoutAmount,
                    sellerPayoutAmount,
                    fee,
                    buyerPayoutAddressString,
                    sellerPayoutAddressString);
            tradeWalletService.broadcastTx(tx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.handleResult();
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    log.error("TxBroadcastException at doPayout", exception);
                    new Popup().error(exception.toString()).show();
                }
            });
        } catch (InsufficientMoneyException | WalletException | TransactionVerificationException e) {
            log.error("Exception at doPayout", e);
            new Popup().error(e.toString()).show();
        }
    }

    private void doCloseIfValid(Button closeTicketButton) {
        var disputeManager = checkNotNull(getDisputeManager(dispute));
        try {
            TradeDataValidation.validateDonationAddress(dispute.getDonationAddressOfDelayedPayoutTx(), daoFacade);
            TradeDataValidation.testIfDisputeTriesReplay(dispute, disputeManager.getDisputesAsObservableList());
            doClose(closeTicketButton);
        } catch (TradeDataValidation.AddressException exception) {
            String addressAsString = dispute.getDonationAddressOfDelayedPayoutTx();
            String tradeId = dispute.getTradeId();

            // For mediators we do not enforce that the case cannot be closed to stay flexible,
            // but for refund agents we do.
            if (disputeManager instanceof MediationManager) {
                new Popup().width(900)
                        .warning(Res.get("support.warning.disputesWithInvalidDonationAddress",
                                addressAsString,
                                daoFacade.getAllDonationAddresses(),
                                tradeId,
                                Res.get("support.warning.disputesWithInvalidDonationAddress.mediator")))
                        .onAction(() -> {
                            doClose(closeTicketButton);
                        })
                        .actionButtonText(Res.get("shared.yes"))
                        .closeButtonText(Res.get("shared.no"))
                        .show();
            } else {
                new Popup().width(900)
                        .warning(Res.get("support.warning.disputesWithInvalidDonationAddress",
                                addressAsString,
                                daoFacade.getAllDonationAddresses(),
                                tradeId,
                                Res.get("support.warning.disputesWithInvalidDonationAddress.refundAgent")))
                        .show();
            }
        } catch (TradeDataValidation.DisputeReplayException exception) {
            if (disputeManager instanceof MediationManager) {
                log.error("Closing of ticket failed as mediator", exception);
                new Popup().width(900)
                        .warning(exception.getMessage())
                        .onAction(() -> {
                            doClose(closeTicketButton);
                        })
                        .actionButtonText(Res.get("shared.yes"))
                        .closeButtonText(Res.get("shared.no"))
                        .show();
            } else {
                log.error("Closing of ticket failed", exception);
                new Popup().width(900)
                        .warning(exception.getMessage())
                        .show();
            }
        }
    }

    private void doClose(Button closeTicketButton) {
        DisputeManager<? extends DisputeList<Dispute>> disputeManager = getDisputeManager(dispute);
        if (disputeManager == null) {
            return;
        }

        boolean isRefundAgent = disputeManager instanceof RefundManager;
        disputeResult.setLoserPublisher(false); // field no longer used per pazza / leo816
        disputeResult.setCloseDate(new Date());
        dispute.setDisputeResult(disputeResult);
        dispute.setIsClosed();
        DisputeResult.Reason reason = disputeResult.getReason();

        summaryNotesTextArea.textProperty().unbindBidirectional(disputeResult.summaryNotesProperty());
        String role = isRefundAgent ? Res.get("shared.refundAgent") : Res.get("shared.mediator");
        String agentNodeAddress = checkNotNull(disputeManager.getAgentNodeAddress(dispute)).getFullAddress();
        Contract contract = dispute.getContract();
        String currencyCode = contract.getOfferPayload().getCurrencyCode();
        String amount = formatter.formatCoinWithCode(contract.getTradeAmount());
        String textToSign = Res.get("disputeSummaryWindow.close.msg",
                DisplayUtils.formatDateTime(disputeResult.getCloseDate()),
                role,
                agentNodeAddress,
                dispute.getShortTradeId(),
                currencyCode,
                amount,
                formatter.formatCoinWithCode(disputeResult.getBuyerPayoutAmount()),
                formatter.formatCoinWithCode(disputeResult.getSellerPayoutAmount()),
                Res.get("disputeSummaryWindow.reason." + reason.name()),
                disputeResult.summaryNotesProperty().get()
        );

        if (reason == DisputeResult.Reason.OPTION_TRADE &&
                dispute.getChatMessages().size() > 1 &&
                dispute.getChatMessages().get(1).isSystemMessage()) {
            textToSign += "\n" + dispute.getChatMessages().get(1).getMessage() + "\n";
        }

        String summaryText = DisputeSummaryVerification.signAndApply(disputeManager, disputeResult, textToSign);

        if (isRefundAgent) {
            summaryText += Res.get("disputeSummaryWindow.close.nextStepsForRefundAgentArbitration");
        } else {
            summaryText += Res.get("disputeSummaryWindow.close.nextStepsForMediation");
        }

        disputeManager.sendDisputeResultMessage(disputeResult, dispute, summaryText);

        if (peersDisputeOptional.isPresent() && !peersDisputeOptional.get().isClosed() && !DevEnv.isDevMode()) {
            UserThread.runAfter(() -> new Popup()
                            .attention(Res.get("disputeSummaryWindow.close.closePeer"))
                            .show(),
                    200, TimeUnit.MILLISECONDS);
        }

        finalizeDisputeHandlerOptional.ifPresent(Runnable::run);

        disputeManager.requestPersistence();

        closeTicketButton.disableProperty().unbind();

        hide();
    }

    private DisputeManager<? extends DisputeList<Dispute>> getDisputeManager(Dispute dispute) {
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

    private boolean isMediationDispute() {
        return getDisputeManager(dispute) instanceof MediationManager;
    }

    // called when a radio button or amount box ui control is changed
    private void applyUpdateFromUi(Toggle selectedTradeAmountToggle) {
        if (updatingUi || selectedTradeAmountToggle == null) {
            return;
        }
        applyUiControlsToDisputeResult(selectedTradeAmountToggle);
        applyDisputeResultToUiControls();
    }

    private void applyUiControlsToDisputeResult(Toggle selectedTradeAmountToggle) {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());
        Coin buyerSecurityDeposit = offer.getBuyerSecurityDeposit();
        Coin sellerSecurityDeposit = offer.getSellerSecurityDeposit();
        Coin tradeAmount = contract.getTradeAmount();
        Coin totalPot = tradeAmount.add(buyerSecurityDeposit).add(sellerSecurityDeposit);
        // At mediation we require a min. payout to the losing party to keep incentive for the trader to accept the
        // mediated payout. For Refund agent cases we do not have that restriction.
        Coin minRefundAtDispute = isMediationDispute() ? Restrictions.getMinRefundAtMediatedDispute() : Coin.ZERO;

        Coin penalizedPortionOfTradeAmount = Coin.ZERO;
        try {
            disputeResult.setPayoutAdjustmentPercent(compensationOrPenalty.getText().replaceAll("[^0-9,.]", ""));
            double percentPenalty = ParsingUtils.parsePercentStringToDouble(disputeResult.getPayoutAdjustmentPercent());
            penalizedPortionOfTradeAmount = Coin.valueOf((long) (contract.getTradeAmount().getValue() * percentPenalty));
        } catch (NumberFormatException | NullPointerException e) {
            log.warn(e.toString());
        }

        if (selectedTradeAmountToggle == buyerGetsTradeAmountRadioButton) {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.BUYER_GETS_TRADE_AMOUNT);
            disputeResult.setBuyerPayoutAmount(tradeAmount.add(buyerSecurityDeposit));
            disputeResult.setSellerPayoutAmount(sellerSecurityDeposit);
            disputeResult.setPayoutAdjustmentPercent("");
        } else if (selectedTradeAmountToggle == sellerGetsTradeAmountRadioButton) {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.SELLER_GETS_TRADE_AMOUNT);
            disputeResult.setBuyerPayoutAmount(buyerSecurityDeposit);
            disputeResult.setSellerPayoutAmount(tradeAmount.add(sellerSecurityDeposit));
            disputeResult.setPayoutAdjustmentPercent("");
        } else if (selectedTradeAmountToggle == buyerGetsTradeAmountMinusPenaltyRadioButton) {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.BUYER_GETS_TRADE_AMOUNT_MINUS_PENALTY);
            Coin buyerPayout = tradeAmount.add(offer.getBuyerSecurityDeposit()).subtract(penalizedPortionOfTradeAmount);
            disputeResult.setBuyerPayoutAmount(buyerPayout);
            disputeResult.setSellerPayoutAmount(totalPot.subtract(buyerPayout));
        } else if (selectedTradeAmountToggle == sellerGetsTradeAmountMinusPenaltyRadioButton) {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.SELLER_GETS_TRADE_AMOUNT_MINUS_PENALTY);
            Coin sellerPayout = tradeAmount.add(offer.getBuyerSecurityDeposit()).subtract(penalizedPortionOfTradeAmount);
            disputeResult.setSellerPayoutAmount(sellerPayout);
            disputeResult.setBuyerPayoutAmount(totalPot.subtract(sellerPayout));
        } else if (selectedTradeAmountToggle == buyerGetsCompensationRadioButton) {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.BUYER_GETS_TRADE_AMOUNT_PLUS_COMPENSATION);
            Coin buyerPayout = tradeAmount.add(offer.getBuyerSecurityDeposit()).add(penalizedPortionOfTradeAmount);
            disputeResult.setBuyerPayoutAmount(buyerPayout);
            disputeResult.setSellerPayoutAmount(totalPot.subtract(buyerPayout));
        } else if (selectedTradeAmountToggle == sellerGetsCompensationRadioButton) {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.SELLER_GETS_TRADE_AMOUNT_PLUS_COMPENSATION);
            Coin sellerPayout = tradeAmount.add(offer.getSellerSecurityDeposit()).add(penalizedPortionOfTradeAmount);
            disputeResult.setSellerPayoutAmount(sellerPayout);
            disputeResult.setBuyerPayoutAmount(totalPot.subtract(sellerPayout));
        } else {
            disputeResult.setPayoutSuggestion(DisputeResult.PayoutSuggestion.CUSTOM_PAYOUT);
            disputeResult.setPayoutAdjustmentPercent("");
        }

        // enforce rule that we cannot pay out less than minRefundAtDispute
        if (disputeResult.getBuyerPayoutAmount().isLessThan(minRefundAtDispute)) {
            disputeResult.setBuyerPayoutAmount(minRefundAtDispute);
            disputeResult.setSellerPayoutAmount(totalPot.subtract(minRefundAtDispute));
        } else if (disputeResult.getSellerPayoutAmount().isLessThan(minRefundAtDispute)) {
            disputeResult.setSellerPayoutAmount(minRefundAtDispute);
            disputeResult.setBuyerPayoutAmount(totalPot.subtract(minRefundAtDispute));
        }

        // winner is the one who receives most from the multisig, or if equal, the buyer.
        // (winner is used to decide who publishes the tx)
        disputeResult.setWinner(disputeResult.getSellerPayoutAmount().isLessThan(disputeResult.getBuyerPayoutAmount()) ?
                DisputeResult.Winner.BUYER : DisputeResult.Winner.BUYER);
    }

    private void applyDisputeResultToUiControls() {
        updatingUi = true;
        buyerPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getBuyerPayoutAmount()));
        sellerPayoutAmountInputTextField.setText(formatter.formatCoin(disputeResult.getSellerPayoutAmount()));
        compensationOrPenalty.setText(disputeResult.getPayoutAdjustmentPercent());
        if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.BUYER_GETS_TRADE_AMOUNT) {
            buyerGetsTradeAmountRadioButton.setSelected(true);
        } else if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.SELLER_GETS_TRADE_AMOUNT) {
            sellerGetsTradeAmountRadioButton.setSelected(true);
        } else if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.BUYER_GETS_TRADE_AMOUNT_PLUS_COMPENSATION) {
            buyerGetsCompensationRadioButton.setSelected(true);
        } else if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.SELLER_GETS_TRADE_AMOUNT_PLUS_COMPENSATION) {
            sellerGetsCompensationRadioButton.setSelected(true);
        } else if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.BUYER_GETS_TRADE_AMOUNT_MINUS_PENALTY) {
            buyerGetsTradeAmountMinusPenaltyRadioButton.setSelected(true);
        } else if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.SELLER_GETS_TRADE_AMOUNT_MINUS_PENALTY) {
            sellerGetsTradeAmountMinusPenaltyRadioButton.setSelected(true);
        } else if (disputeResult.getPayoutSuggestion() == DisputeResult.PayoutSuggestion.CUSTOM_PAYOUT) {
            customRadioButton.setSelected(true);
        } else {
            // the option was not set, this will apply to older records before PayoutSuggestion was persisted
            // what it used to do was infer the option based on the payout amounts
            Contract contract = dispute.getContract();
            Offer offer = new Offer(contract.getOfferPayload());
            Coin buyerSecurityDeposit = offer.getBuyerSecurityDeposit();
            Coin sellerSecurityDeposit = offer.getSellerSecurityDeposit();
            Coin tradeAmount = contract.getTradeAmount();
            Coin totalPot = tradeAmount.add(buyerSecurityDeposit).add(sellerSecurityDeposit);
            Coin minRefundAtDispute = isMediationDispute() ? Restrictions.getMinRefundAtMediatedDispute() : Coin.ZERO;
            Coin maxPayoutAmount = totalPot.subtract(minRefundAtDispute);
            if (disputeResult.getBuyerPayoutAmount().equals(tradeAmount.add(buyerSecurityDeposit)) &&
                    disputeResult.getSellerPayoutAmount().equals(sellerSecurityDeposit)) {
                buyerGetsTradeAmountRadioButton.setSelected(true);
            } else if (disputeResult.getBuyerPayoutAmount().equals(maxPayoutAmount) &&
                    disputeResult.getSellerPayoutAmount().equals(minRefundAtDispute)) {
                buyerGetsCompensationRadioButton.setSelected(true);
            } else if (disputeResult.getSellerPayoutAmount().equals(tradeAmount.add(sellerSecurityDeposit))
                    && disputeResult.getBuyerPayoutAmount().equals(buyerSecurityDeposit)) {
                sellerGetsTradeAmountRadioButton.setSelected(true);
            } else if (disputeResult.getSellerPayoutAmount().equals(maxPayoutAmount)
                    && disputeResult.getBuyerPayoutAmount().equals(minRefundAtDispute)) {
                sellerGetsCompensationRadioButton.setSelected(true);
            } else {
                customRadioButton.setSelected(true);
            }
        }
        updatingUi = false;
    }

    private void checkDelayedPayoutTransaction() {
        if (dispute.getDelayedPayoutTxId() == null)
            return;
        mempoolService.checkTxIsConfirmed(dispute.getDelayedPayoutTxId(), (validator -> {
            long confirms = validator.parseJsonValidateTx();
            log.info("Mempool check confirmation status of DelayedPayoutTxId returned: [{}]", confirms);
            displayPayoutStatus(confirms);
        }));
    }

    private void displayPayoutStatus(long nConfirmStatus) {
        if (delayedPayoutTxStatus != null) {
            String status = Res.get("confidence.unknown");
            if (nConfirmStatus == 0)
                status = Res.get("confidence.seen", 1);
            else if (nConfirmStatus > 0)
                status = Res.get("confidence.confirmed", nConfirmStatus);
            delayedPayoutTxStatus.setText(status);
        }
    }
}

