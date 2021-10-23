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

package bisq.desktop.main.offer.bsq_swap.take_offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.BsqSwapOfferDetailsWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.bsqswaps.CompletedBsqSwapsView;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.concurrent.TimeUnit;

import static bisq.core.offer.bsq_swap.BsqSwapOfferModel.BSQ;
import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class BsqSwapTakeOfferView extends BsqSwapOfferView<BsqSwapTakeOfferViewModel> {
    private HBox minAmountHBox;
    private Label offerAvailabilityLabel;
    private TextField paymentMethodTextField, currencyTextField, priceTextField,
            volumeTextField, minAmountTextField;
    private BusyAnimation offerAvailabilityBusyAnimation;
    private Subscription isTradeCompleteSubscription, showWarningInvalidBtcDecimalPlacesSubscription,
            offerWarningSubscription, errorMessageSubscription,
            isOfferAvailableSubscription;
    private boolean offerDetailsWindowDisplayed, missingFundsPopupDisplayed;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqSwapTakeOfferView(BsqSwapTakeOfferViewModel model,
                                Navigation navigation,
                                BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow) {
        super(model, navigation, bsqSwapOfferDetailsWindow);
    }

    @Override
    protected void initialize() {
        super.initialize();

        addOfferAvailabilityLabel();
    }

    @Override
    protected void activate() {
        super.activate();

        addListeners();
        addBindings();
        addSubscriptions();

        if (offerAvailabilityBusyAnimation != null) {
            // temporarily disabled due to high CPU usage (per issue #4649)
            //    offerAvailabilityBusyAnimation.play();
            offerAvailabilityLabel.setVisible(true);
            offerAvailabilityLabel.setManaged(true);
        } else {
            offerAvailabilityLabel.setVisible(false);
            offerAvailabilityLabel.setManaged(false);
        }

        if (!missingFundsPopupDisplayed) {
            String key = "BsqSwapTakerInfo";
            if (DontShowAgainLookup.showAgain(key)) {
                new Popup().information(Res.get("bsqSwapOffer.feeHandling"))
                        .width(1000)
                        .closeButtonText(Res.get("shared.iUnderstand"))
                        .dontShowAgainId(key)
                        .show();
            }
        }
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        removeListeners();
        removeBindings();
        removeSubscriptions();

        if (offerAvailabilityBusyAnimation != null) {
            offerAvailabilityBusyAnimation.stop();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer) {
        model.initWithData(offer);

        if (model.dataModel.isSellOffer()) {
            actionButton.setId("buy-button-big");
            actionButton.updateText(Res.get("takeOffer.takeOfferButton", Res.get("shared.buy")));
            nextButton.setId("buy-button");
            volumeDescriptionLabel.setText(Res.get("createOffer.amountPriceBox.buy.volumeDescription", BSQ));
            amountDescriptionLabel.setText(Res.get("takeOffer.amountPriceBox.sell.amountDescription"));
        } else {
            actionButton.setId("sell-button-big");
            nextButton.setId("sell-button");
            actionButton.updateText(Res.get("takeOffer.takeOfferButton", Res.get("shared.sell")));
            volumeDescriptionLabel.setText(Res.get("createOffer.amountPriceBox.sell.volumeDescription", BSQ));
            amountDescriptionLabel.setText(Res.get("takeOffer.amountPriceBox.buy.amountDescription"));
        }

        paymentMethodTextField.setText(PaymentMethod.BSQ_SWAP.getDisplayString());
        currencyTextField.setText(CurrencyUtil.getNameByCode(BSQ));

        if (model.isRange()) {
            minAmountTextField.setText(model.amountRange);
            minAmountHBox.setVisible(true);
            minAmountHBox.setManaged(true);
        } else {
            amountTextField.setDisable(true);
        }

        priceTextField.setText(model.price);

        if (!model.isRange()) {
            model.dataModel.getMissingFunds().addListener(missingFundsListener);
            checkForMissingFunds(model.dataModel.getMissingFunds().get());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTabSelected(boolean isSelected) {
        model.dataModel.onTabSelected(isSelected);
    }

    @Override
    protected void onCancel1() {
        close();
    }

    @Override
    protected void onShowFeeInfoScreen() {
        super.onShowFeeInfoScreen();

        offerAvailabilityBusyAnimation.stop();
        offerAvailabilityBusyAnimation.setVisible(false);
        offerAvailabilityBusyAnimation.setManaged(false);

        offerAvailabilityLabel.setVisible(false);
        offerAvailabilityLabel.setManaged(false);

        amountTextField.setMouseTransparent(true);
        amountTextField.setDisable(false);
        amountTextField.setFocusTraversable(false);

        minAmountTextField.setMouseTransparent(true);
        minAmountTextField.setDisable(false);
        minAmountTextField.setFocusTraversable(false);

        priceTextField.setMouseTransparent(true);
        priceTextField.setDisable(false);
        priceTextField.setFocusTraversable(false);

        volumeTextField.setMouseTransparent(true);
        volumeTextField.setDisable(false);
        volumeTextField.setFocusTraversable(false);

        actionButtonBar.setManaged(true);
        actionButtonBar.setVisible(true);

        inputAmountTextField.setFundsStructure(model.getInputAmountDetails());
        inputAmountTextField.setContentForInfoPopOver(createInputAmountDetailsPopover());

        payoutAmountTextField.setFundsStructure(model.getPayoutAmountDetails());
        payoutAmountTextField.setContentForInfoPopOver(createPayoutAmountDetailsPopover());

        model.dataModel.onShowFeeInfoScreen();

        if (model.isRange()) {
            model.dataModel.getMissingFunds().addListener(missingFundsListener);
            checkForMissingFunds(model.dataModel.getMissingFunds().get());
        } else if (model.dataModel.hasMissingFunds()) {
            maybeShowMissingFundsPopup();
        }
    }

    @Override
    protected void onAction() {
        if (!model.dataModel.canPlaceOrTakeOffer()) {
            return;
        }

        if (model.dataModel.hasMissingFunds()) {
            maybeShowMissingFundsPopup();
            return;
        }

        if (DevEnv.isDevMode()) {
            model.onTakeOffer(() -> {
                    }, warningMessage -> {
                        log.warn(warningMessage);
                        new Popup().warning(warningMessage).show();
                    },
                    errorMessage -> {
                        log.error(errorMessage);
                        new Popup().warning(errorMessage).show();
                    });
            // JFXComboBox causes a bug with requesting focus. Not clear why that happens but requesting a focus
            // on our view here avoids that the currency List overlay gets displayed.
            requestFocus();
            return;
        }

        bsqSwapOfferDetailsWindow.onTakeOffer(() -> {
            if (model.dataModel.hasMissingFunds()) {
                maybeShowMissingFundsPopup();
                return;
            }

            model.onTakeOffer(() -> {
                        offerDetailsWindowDisplayed = false;
                        model.dataModel.getMissingFunds().removeListener(missingFundsListener);
                    }, warningMessage -> {
                        log.warn(warningMessage);
                        new Popup().warning(warningMessage).show();
                    },
                    errorMessage -> {
                        log.error(errorMessage);
                        new Popup().warning(errorMessage).show();
                    });
            requestFocus();
        }).show(model.offer, model.dataModel.getBtcAmount().get(), model.dataModel.getPrice().get());

        offerDetailsWindowDisplayed = true;
    }

    private void requestFocus() {
        // JFXComboBox causes a bug with requesting focus. Not clear why that happens but requesting a focus
        // on our view here avoids that the currency List overlay gets displayed.
        root.requestFocus();
    }

    @Override
    protected void onCancel2() {
        close();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void createListeners() {
        super.createListeners();

        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        };
    }

    @Override
    protected void addListeners() {
        amountTextField.focusedProperty().addListener(amountFocusedListener);
    }

    @Override
    protected void removeListeners() {
        super.removeListeners();

        amountTextField.focusedProperty().removeListener(amountFocusedListener);
    }

    @Override
    protected void addBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        amountTextField.validationResultProperty().bind(model.amountValidationResult);

        inputAmountTextField.textProperty().bind(model.getInputAmount());
        payoutAmountTextField.textProperty().bind(model.getPayoutAmount());

        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        actionButton.disableProperty().bind(model.isTakeOfferButtonDisabled);
        cancelButton2.disableProperty().bind(model.cancelButtonDisabled);

    }

    @Override
    protected void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        amountTextField.validationResultProperty().unbind();

        inputAmountTextField.textProperty().unbind();
        payoutAmountTextField.textProperty().unbind();

        nextButton.disableProperty().unbind();
        actionButton.disableProperty().unbind();
        cancelButton2.disableProperty().unbind();
    }

    @Override
    protected void addSubscriptions() {
        offerWarningSubscription = EasyBind.subscribe(model.offerWarning, warning -> {
            if (warning != null) {
                if (offerDetailsWindowDisplayed) {
                    bsqSwapOfferDetailsWindow.hide();
                }

                UserThread.runAfter(() -> new Popup().warning(warning)
                        .onClose(() -> {
                            model.resetOfferWarning();
                            close();
                        })
                        .show(), 100, TimeUnit.MILLISECONDS);
            }
        });

        errorMessageSubscription = EasyBind.subscribe(model.errorMessage, newValue -> {
            if (newValue == null) {
                return;
            }
            new Popup().error(Res.get("takeOffer.error.message", model.errorMessage.get()) + "\n\n" +
                    Res.get("popup.error.tryRestart"))
                    .onClose(() -> {
                        model.resetErrorMessage();
                        model.dataModel.removeOffer();
                        close();
                    })
                    .show();
        });

        isOfferAvailableSubscription = EasyBind.subscribe(model.isOfferAvailable, isOfferAvailable -> {
            if (isOfferAvailable) {
                offerAvailabilityBusyAnimation.stop();
                offerAvailabilityBusyAnimation.setVisible(false);
            }

            offerAvailabilityLabel.setVisible(!isOfferAvailable);
            offerAvailabilityLabel.setManaged(!isOfferAvailable);
        });

        showWarningInvalidBtcDecimalPlacesSubscription = EasyBind.subscribe(model.showWarningInvalidBtcDecimalPlaces, newValue -> {
            if (newValue) {
                new Popup().warning(Res.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        isTradeCompleteSubscription = EasyBind.subscribe(model.isTradeComplete, newValue -> {
            if (!newValue) {
                return;
            }

            model.dataModel.removeOffer();

            new Popup().headLine(Res.get("takeOffer.bsqSwap.success.headline"))
                    .feedback(Res.get("takeOffer.bsqSwap.success.info"))
                    .actionButtonTextWithGoTo("navigation.portfolio.bsqSwapTrades")
                    .width(730)
                    .onAction(() -> {
                        UserThread.runAfter(
                                () -> navigation.navigateTo(MainView.class, PortfolioView.class, CompletedBsqSwapsView.class),
                                100, TimeUnit.MILLISECONDS);
                        close();
                    })
                    .onClose(this::close)
                    .show();
        });
    }

    @Override
    protected void removeSubscriptions() {
        offerWarningSubscription.unsubscribe();
        errorMessageSubscription.unsubscribe();
        isOfferAvailableSubscription.unsubscribe();
        showWarningInvalidBtcDecimalPlacesSubscription.unsubscribe();
        isTradeCompleteSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addPaymentAccountGroup() {
        paymentAccountTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("takeOffer.paymentInfo"));
        GridPane.setColumnSpan(paymentAccountTitledGroupBg, 2);

        // We use the addComboBoxTopLabelTextField only for convenience for having the expected layout
        Tuple4<ComboBox<PaymentAccount>, Label, TextField, HBox> paymentAccountTuple = addComboBoxTopLabelTextField(gridPane,
                gridRow, "", Res.get("shared.paymentMethod"), Layout.FIRST_ROW_DISTANCE);
        HBox hBox = paymentAccountTuple.fourth;
        hBox.getChildren().remove(paymentAccountTuple.first);

        paymentMethodTextField = paymentAccountTuple.third;
        paymentMethodTextField.setMinWidth(250);
        paymentMethodTextField.setEditable(false);
        paymentMethodTextField.setMouseTransparent(true);
        paymentMethodTextField.setFocusTraversable(false);

        currencyTextField = new JFXTextField();
        currencyTextField.setMinWidth(250);
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);

        Tuple2<Label, VBox> tradeCurrencyTuple = getTopLabelWithVBox(Res.get("shared.tradeCurrency"), currencyTextField);
        VBox vBox = tradeCurrencyTuple.second;
        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));

        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setPadding(new Insets(10, 0, 18, 0));
        hBox.getChildren().add(vBox);
    }

    @Override
    protected void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("takeOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);

        addFirstRow();
        addSecondRow();
    }

    private void addFirstRow() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("takeOffer.amount.prompt"));
        amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, "");
        amountDescriptionLabel = amountInputBoxTuple.first;
        VBox amountBox = amountInputBoxTuple.second;

        // x
        xLabel = new Label();
        xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().addAll("opaque-icon-character");

        // price
        Tuple3<HBox, TextField, Label> priceValueCurrencyBoxTuple = getNonEditableValueBox();
        priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        priceCurrencyLabel.setText("BTC");
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.priceDescription"));
        priceDescriptionLabel = priceInputBoxTuple.first;
        priceDescriptionLabel.setText(CurrencyUtil.getPriceWithCurrencyCode(BSQ));

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        VBox priceBox = priceInputBoxTuple.second;

        // =
        resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().addAll("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getNonEditableValueBoxWithInfo();
        volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;

        InfoInputTextField volumeInfoTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoTextField.getInputTextField();
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        volumeCurrencyLabel.setText(BSQ);
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, "");
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        VBox volumeBox = volumeInputBoxTuple.second;

        firstRowHBox = new HBox();
        firstRowHBox.setSpacing(5);
        firstRowHBox.setAlignment(Pos.CENTER_LEFT);
        firstRowHBox.getChildren().addAll(amountBox, xLabel, priceBox, resultLabel, volumeBox);
        GridPane.setColumnSpan(firstRowHBox, 2);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        gridPane.getChildren().add(firstRowHBox);
    }

    private void addSecondRow() {
        Tuple3<HBox, TextField, Label> amountValueCurrencyBoxTuple = getNonEditableValueBox();
        minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.amountRangeDescription"));

        VBox minAmountBox = amountInputBoxTuple.second;
        minAmountHBox = new HBox();
        minAmountHBox.setSpacing(5);
        minAmountHBox.setAlignment(Pos.CENTER_LEFT);
        minAmountHBox.getChildren().add(minAmountBox);

        GridPane.setRowIndex(minAmountHBox, ++gridRow);
        GridPane.setMargin(minAmountHBox, new Insets(0, 10, 10, 0));
        gridPane.getChildren().add(minAmountHBox);

        minAmountHBox.setVisible(false);
        minAmountHBox.setManaged(false);
    }

    private void addOfferAvailabilityLabel() {
        offerAvailabilityBusyAnimation = new BusyAnimation(false);
        offerAvailabilityLabel = new AutoTooltipLabel(Res.get("takeOffer.fundsBox.isOfferAvailable"));
        nextButtonBar.getChildren().addAll(offerAvailabilityBusyAnimation, offerAvailabilityLabel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void updateOfferElementsStyle() {
        super.updateOfferElementsStyle();

        GridPane.setColumnSpan(firstRowHBox, 1);
    }

    @Override
    protected void checkForMissingFunds(Coin missing) {
        if (missing.isPositive()) {
            maybeShowMissingFundsPopup();
        }
    }

    private void maybeShowMissingFundsPopup() {
        if (!isMissingFundsPopupOpen) {
            isMissingFundsPopupOpen = true;
            missingFundsPopupDisplayed = true;
            String wallet = model.dataModel.isBuyer() ? "BSQ" : "BTC";
            String warning = Res.get("createOffer.bsqSwap.missingFunds.taker",
                    wallet, model.getMissingFunds(model.dataModel.getMissingFunds().get()));
            new Popup().warning(warning)
                    .onClose(() -> {
                        isMissingFundsPopupOpen = false;
                        close();
                    })
                    .show();
        }
    }
}

