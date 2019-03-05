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

package bisq.desktop.main.portfolio.pendingtrades.steps.buyer;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.notifications.Notification;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TradeFeedbackWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.closedtrades.ClosedTradesView;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.AddressEntryException;
import bisq.core.btc.exceptions.InsufficientFundsException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.BSFormatter;
import bisq.core.util.CoinUtil;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

public class BuyerStep4View extends TradeStepView {
    // private final ChangeListener<Boolean> focusedPropertyListener;

    private InputTextField withdrawAddressTextField;
    private Button withdrawToExternalWalletButton, useSavingsWalletButton;
    private TitledGroupBg withdrawTitledGroupBg;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep4View(PendingTradesViewModel model) {
        super(model);

       /* focusedPropertyListener = (ov, oldValue, newValue) -> {
            if (oldValue && !newValue)
                model.withdrawAddressFocusOut(withdrawAddressTextField.getText());
        };*/
    }

    @Override
    public void activate() {
        super.activate();

        // TODO valid. handler need improvement
        //withdrawAddressTextField.focusedProperty().addListener(focusedPropertyListener);
        //withdrawAddressTextField.setValidator(model.getBtcAddressValidator());
        // withdrawButton.disableProperty().bind(model.getWithdrawalButtonDisable());

        // We need to handle both cases: Address not set and address already set (when returning from other view)
        // We get address validation after focus out, so first make sure we loose focus and then set it again as hint for user to put address in
        //TODO app wide focus
       /* UserThread.execute(() -> {
            withdrawAddressTextField.requestFocus();
           UserThread.execute(() -> {
                this.requestFocus();
                UserThread.execute(() -> withdrawAddressTextField.requestFocus());
            });
        });*/

        hideNotificationGroup();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        //withdrawAddressTextField.focusedProperty().removeListener(focusedPropertyListener);
        // withdrawButton.disableProperty().unbind();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    protected void addContent() {
        gridPane.getColumnConstraints().get(1).setHgrow(Priority.SOMETIMES);

        addTitledGroupBg(gridPane, gridRow, 5, Res.get("portfolio.pending.step5_buyer.groupTitle"), 0);
        addCompactTopLabelTextField(gridPane, gridRow, getBtcTradeAmountLabel(), model.getTradeVolume(), Layout.TWICE_FIRST_ROW_DISTANCE);

        addCompactTopLabelTextField(gridPane, ++gridRow, getFiatTradeAmountLabel(), model.getFiatVolume());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.refunded"), model.getSecurityDeposit());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.tradeFee"), model.getTradeFee());
        final String miningFee = model.dataModel.isMaker() ?
                Res.get("portfolio.pending.step5_buyer.makersMiningFee") :
                Res.get("portfolio.pending.step5_buyer.takersMiningFee");
        addCompactTopLabelTextField(gridPane, ++gridRow, miningFee, model.getTxFee());
        withdrawTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, Res.get("portfolio.pending.step5_buyer.withdrawBTC"), Layout.COMPACT_GROUP_DISTANCE);
        withdrawTitledGroupBg.getStyleClass().add("last");
        addCompactTopLabelTextField(gridPane, gridRow, Res.get("portfolio.pending.step5_buyer.amount"), model.getPayoutAmount(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        withdrawAddressTextField = addInputTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.withdrawToAddress"));
        withdrawAddressTextField.setManaged(false);
        withdrawAddressTextField.setVisible(false);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        useSavingsWalletButton = new AutoTooltipButton(Res.get("portfolio.pending.step5_buyer.moveToBisqWallet"));
        useSavingsWalletButton.setDefaultButton(true);
        useSavingsWalletButton.getStyleClass().add("action-button");
        Label label = new AutoTooltipLabel(Res.get("shared.OR"));
        label.setPadding(new Insets(5, 0, 0, 0));
        withdrawToExternalWalletButton = new AutoTooltipButton(Res.get("portfolio.pending.step5_buyer.withdrawExternal"));
        withdrawToExternalWalletButton.setDefaultButton(false);
        hBox.getChildren().addAll(useSavingsWalletButton, label, withdrawToExternalWalletButton);
        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setMargin(hBox, new Insets(5, 10, 0, 0));
        gridPane.getChildren().add(hBox);

        useSavingsWalletButton.setOnAction(e -> {
            handleTradeCompleted();
            model.dataModel.tradeManager.addTradeToClosedTrades(trade);
        });
        withdrawToExternalWalletButton.setOnAction(e -> onWithdrawal());

        String key = "tradeCompleted" + trade.getId();
        //noinspection ConstantConditions
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
            DontShowAgainLookup.dontShowAgain(key, true);
            new Notification().headLine(Res.get("notification.tradeCompleted.headline"))
                    .notification(Res.get("notification.tradeCompleted.msg"))
                    .autoClose()
                    .show();
        }
    }

    private void onWithdrawal() {
        withdrawAddressTextField.setManaged(true);
        withdrawAddressTextField.setVisible(true);
        GridPane.setRowSpan(withdrawTitledGroupBg, 2);
        withdrawToExternalWalletButton.setDefaultButton(true);
        useSavingsWalletButton.setDefaultButton(false);
        withdrawToExternalWalletButton.getStyleClass().add("action-button");
        useSavingsWalletButton.getStyleClass().remove("action-button");

        withdrawToExternalWalletButton.setOnAction(e -> {
            if (model.dataModel.isReadyForTxBroadcast())
                reviewWithdrawal();
            else
                model.dataModel.showNotReadyForTxBroadcastPopups();
        });

    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void reviewWithdrawal() {
        Coin amount = trade.getPayoutAmount();
        BtcWalletService walletService = model.dataModel.btcWalletService;

        AddressEntry fromAddressesEntry = walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
        String fromAddresses = fromAddressesEntry.getAddressString();
        String toAddresses = withdrawAddressTextField.getText();
        if (new BtcAddressValidator().validate(toAddresses).isValid) {
            Coin balance = walletService.getBalanceForAddress(fromAddressesEntry.getAddress());
            try {
                Transaction feeEstimationTransaction = walletService.getFeeEstimationTransaction(fromAddresses, toAddresses, amount, AddressEntry.Context.TRADE_PAYOUT);
                Coin fee = feeEstimationTransaction.getFee();
                //noinspection UnusedAssignment
                Coin receiverAmount = amount.subtract(fee);
                if (balance.isZero()) {
                    new Popup<>().warning(Res.get("portfolio.pending.step5_buyer.alreadyWithdrawn")).show();
                    model.dataModel.tradeManager.addTradeToClosedTrades(trade);
                } else {
                    if (toAddresses.isEmpty()) {
                        validateWithdrawAddress();
                    } else if (Restrictions.isAboveDust(receiverAmount)) {
                        BSFormatter formatter = model.btcFormatter;
                        int txSize = feeEstimationTransaction.bitcoinSerialize().length;
                        double feePerByte = CoinUtil.getFeePerByte(fee, txSize);
                        double kb = txSize / 1000d;
                        String recAmount = formatter.formatCoinWithCode(receiverAmount);
                        new Popup<>().headLine(Res.get("portfolio.pending.step5_buyer.confirmWithdrawal"))
                                .confirmation(Res.get("shared.sendFundsDetailsWithFee",
                                        formatter.formatCoinWithCode(amount),
                                        fromAddresses,
                                        toAddresses,
                                        formatter.formatCoinWithCode(fee),
                                        feePerByte,
                                        kb,
                                        recAmount))
                                .actionButtonText(Res.get("shared.yes"))
                                .onAction(() -> doWithdrawal(amount, fee))
                                .closeButtonText(Res.get("shared.cancel"))
                                .onClose(() -> {
                                    useSavingsWalletButton.setDisable(false);
                                    withdrawToExternalWalletButton.setDisable(false);
                                })
                                .show();
                    } else {
                        new Popup<>().warning(Res.get("portfolio.pending.step5_buyer.amountTooLow")).show();
                    }
                }
            } catch (AddressFormatException e) {
                validateWithdrawAddress();
            } catch (AddressEntryException e) {
                log.error(e.getMessage());
            } catch (InsufficientFundsException e) {
                log.error(e.getMessage());
                e.printStackTrace();
                new Popup<>().warning(e.getMessage()).show();
            }
        } else {
            new Popup<>().warning(Res.get("validation.btc.invalidAddress")).show();
        }
    }

    private void doWithdrawal(Coin amount, Coin fee) {
        String toAddress = withdrawAddressTextField.getText();
        ResultHandler resultHandler = this::handleTradeCompleted;
        FaultHandler faultHandler = (errorMessage, throwable) -> {
            useSavingsWalletButton.setDisable(false);
            withdrawToExternalWalletButton.setDisable(false);
            if (throwable != null && throwable.getMessage() != null)
                new Popup<>().error(errorMessage + "\n\n" + throwable.getMessage()).show();
            else
                new Popup<>().error(errorMessage).show();
        };
        if (model.dataModel.btcWalletService.isEncrypted()) {
            UserThread.runAfter(() -> model.dataModel.walletPasswordWindow.onAesKey(aesKey ->
                    doWithdrawRequest(toAddress, amount, fee, aesKey, resultHandler, faultHandler))
                    .show(), 300, TimeUnit.MILLISECONDS);
        } else
            doWithdrawRequest(toAddress, amount, fee, null, resultHandler, faultHandler);
    }

    private void doWithdrawRequest(String toAddress, Coin amount, Coin fee, KeyParameter aesKey, ResultHandler resultHandler, FaultHandler faultHandler) {
        useSavingsWalletButton.setDisable(true);
        withdrawToExternalWalletButton.setDisable(true);
        model.dataModel.onWithdrawRequest(toAddress,
                amount,
                fee,
                aesKey,
                resultHandler,
                faultHandler);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void handleTradeCompleted() {
        useSavingsWalletButton.setDisable(true);
        withdrawToExternalWalletButton.setDisable(true);
        model.dataModel.btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);

        openTradeFeedbackWindow();
    }

    private void openTradeFeedbackWindow() {
        String key = "feedbackPopupAfterTrade";
        if (!DevEnv.isDevMode() && preferences.showAgain(key)) {
            UserThread.runAfter(() -> {
                new TradeFeedbackWindow()
                        .dontShowAgainId(key)
                        .onAction(this::showNavigateToClosedTradesViewPopup)
                        .show();
            }, 500, TimeUnit.MILLISECONDS);
        } else {
            showNavigateToClosedTradesViewPopup();
        }
    }

    private void showNavigateToClosedTradesViewPopup() {
        if (!DevEnv.isDevMode()) {
            UserThread.runAfter(() -> {
                new Popup<>().headLine(Res.get("portfolio.pending.step5_buyer.withdrawalCompleted.headline"))
                        .feedback(Res.get("portfolio.pending.step5_buyer.withdrawalCompleted.msg"))
                        .actionButtonTextWithGoTo("navigation.portfolio.closedTrades")
                        .onAction(() -> model.dataModel.navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class))
                        .dontShowAgainId("tradeCompleteWithdrawCompletedInfo")
                        .show();
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    private void validateWithdrawAddress() {
        withdrawAddressTextField.setValidator(model.btcAddressValidator);
        withdrawAddressTextField.requestFocus();
        useSavingsWalletButton.requestFocus();
    }

    protected String getBtcTradeAmountLabel() {
        return Res.get("portfolio.pending.step5_buyer.bought");
    }

    protected String getFiatTradeAmountLabel() {
        return Res.get("portfolio.pending.step5_buyer.paid");
    }
}
