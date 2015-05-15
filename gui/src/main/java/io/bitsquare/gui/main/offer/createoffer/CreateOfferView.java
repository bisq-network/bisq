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

package io.bitsquare.gui.main.offer.createoffer;

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.openoffer.OpenOffersView;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.controlsfx.control.PopOver;
import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import static javafx.beans.binding.Bindings.createStringBinding;

// TODO Implement other positioning method in InoutTextField to display it over the field instead of right side
// priceAmountHBox is too large after redesign as to be used as layoutReference.
@FxmlView
public class CreateOfferView extends ActivatableViewAndModel<AnchorPane, CreateOfferViewModel> {

    private final Navigation navigation;
    private final OverlayManager overlayManager;

    @FXML ScrollPane scrollPane;
    @FXML ImageView imageView;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;
    @FXML ProgressIndicator placeOfferSpinner;
    @FXML InfoDisplay amountPriceBoxInfo, advancedInfoDisplay, fundsBoxInfoDisplay;
    @FXML TitledGroupBg priceAmountPane, payFundsPane, showDetailsPane;
    @FXML Button showPaymentInfoScreenButton, showAdvancedSettingsButton, placeOfferButton;
    @FXML InputTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML TextField acceptedArbitratorsTextField, totalToPayTextField, bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField;
    @FXML Label buyLabel, amountToTradeLabel, addressLabel, balanceLabel, totalToPayLabel, totalToPayInfoIconLabel, bankAccountTypeLabel,
            bankAccountCurrencyLabel, bankAccountCountyLabel, acceptedCountriesLabel, acceptedCountriesLabelIcon,
            acceptedLanguagesLabel, acceptedLanguagesLabelIcon, acceptedArbitratorsLabel,
            acceptedArbitratorsLabelIcon, amountBtcLabel, priceFiatLabel, volumeFiatLabel, minAmountBtcLabel,
            priceDescriptionLabel, volumeDescriptionLabel,
            placeOfferSpinnerInfoLabel;

    private ImageView expand;
    private ImageView collapse;
    private PopOver totalToPayInfoPopover;
    private boolean detailsVisible;
    private boolean advancedScreenInited;

    private OfferView.CloseHandler closeHandler;

    private ChangeListener<Boolean> amountFocusedListener;
    private ChangeListener<Boolean> minAmountFocusedListener;
    private ChangeListener<Boolean> priceFocusedListener;
    private ChangeListener<Boolean> volumeFocusedListener;
    private ChangeListener<Boolean> showWarningInvalidBtcDecimalPlacesListener;
    private ChangeListener<Boolean> showWarningInvalidFiatDecimalPlacesPlacesListener;
    private ChangeListener<Boolean> showWarningAdjustedVolumeListener;
    private ChangeListener<String> requestPlaceOfferErrorMessageListener;
    private ChangeListener<Boolean> isPlaceOfferSpinnerVisibleListener;
    private ChangeListener<Boolean> showTransactionPublishedScreen;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateOfferView(CreateOfferViewModel model, Navigation navigation,
                            OverlayManager overlayManager) {
        super(model);

        this.navigation = navigation;
        this.overlayManager = overlayManager;
    }

    @Override
    protected void initialize() {
        createListeners();

        balanceTextField.setup(model.getWalletService(), model.address.get(), model.getFormatter());
        volumeTextField.setPromptText(BSResources.get("createOffer.volume.prompt", model.fiatCode.get()));
    }

    @Override
    protected void doActivate() {
        addBindings();
        addListeners();
    }

    @Override
    protected void doDeactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        amountBtcLabel.textProperty().bind(model.btcCode);
        priceFiatLabel.textProperty().bind(model.fiatCode);
        volumeFiatLabel.textProperty().bind(model.fiatCode);
        minAmountBtcLabel.textProperty().bind(model.btcCode);

        priceDescriptionLabel.textProperty().bind(createStringBinding(() ->
                BSResources.get("createOffer.amountPriceBox.priceDescription", model.fiatCode.get()), model.fiatCode));

        volumeDescriptionLabel.textProperty().bind(createStringBinding(() -> model.volumeDescriptionLabel.get(), model.fiatCode, model.volumeDescriptionLabel));

        buyLabel.textProperty().bind(model.directionLabel);
        amountToTradeLabel.textProperty().bind(model.amountToTradeLabel);
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        priceTextField.textProperty().bindBidirectional(model.price);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        amountPriceBoxInfo.textProperty().bind(model.amountPriceBoxInfo);

        totalToPayTextField.textProperty().bind(model.totalToPay);

        addressTextField.amountAsCoinProperty().bind(model.totalToPayAsCoin);
        addressTextField.paymentLabelProperty().bind(model.paymentLabel);
        addressTextField.addressProperty().bind(model.addressAsString);

        bankAccountTypeTextField.textProperty().bind(model.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(model.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(model.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(model.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(model.acceptedLanguages);
        acceptedArbitratorsTextField.textProperty().bind(model.acceptedArbitrators);

        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(model.minAmountValidationResult);
        priceTextField.validationResultProperty().bind(model.priceValidationResult);
        volumeTextField.validationResultProperty().bind(model.volumeValidationResult);

        // buttons
        placeOfferButton.visibleProperty().bind(model.isPlaceOfferButtonVisible);
        placeOfferButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);

        placeOfferSpinnerInfoLabel.visibleProperty().bind(model.isPlaceOfferSpinnerVisible);
    }

    private void removeBindings() {
        amountBtcLabel.textProperty().unbind();
        priceFiatLabel.textProperty().unbind();
        volumeFiatLabel.textProperty().unbind();
        minAmountBtcLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();
        buyLabel.textProperty().unbind();
        amountToTradeLabel.textProperty().unbind();
        amountTextField.textProperty().unbindBidirectional(model.amount);
        minAmountTextField.textProperty().unbindBidirectional(model.minAmount);
        priceTextField.textProperty().unbindBidirectional(model.price);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        amountPriceBoxInfo.textProperty().unbind();
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        addressTextField.paymentLabelProperty().unbind();
        addressTextField.addressProperty().unbind();
        bankAccountTypeTextField.textProperty().unbind();
        bankAccountCurrencyTextField.textProperty().unbind();
        bankAccountCountyTextField.textProperty().unbind();
        acceptedCountriesTextField.textProperty().unbind();
        acceptedLanguagesTextField.textProperty().unbind();
        acceptedArbitratorsTextField.textProperty().unbind();
        amountTextField.validationResultProperty().unbind();
        minAmountTextField.validationResultProperty().unbind();
        priceTextField.validationResultProperty().unbind();
        volumeTextField.validationResultProperty().unbind();
        placeOfferButton.visibleProperty().unbind();
        placeOfferButton.disableProperty().unbind();
        placeOfferSpinnerInfoLabel.visibleProperty().unbind();
    }

    private void createListeners() {
        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        };
        minAmountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutMinAmountTextField(oldValue, newValue, minAmountTextField.getText());
            minAmountTextField.setText(model.minAmount.get());
        };
        priceFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutPriceTextField(oldValue, newValue, priceTextField.getText());
            priceTextField.setText(model.price.get());
        };
        volumeFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutVolumeTextField(oldValue, newValue, volumeTextField.getText());
            volumeTextField.setText(model.volume.get());
        };
        showWarningInvalidBtcDecimalPlacesListener = (o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"), BSResources.get("createOffer.amountPriceBox.warning.invalidBtcDecimalPlaces"));
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        };
        showWarningInvalidFiatDecimalPlacesPlacesListener = (o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"), BSResources.get("createOffer.amountPriceBox.warning.invalidFiatDecimalPlaces"));
                model.showWarningInvalidFiatDecimalPlaces.set(false);
            }
        };
        showWarningAdjustedVolumeListener = (o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"), BSResources.get("createOffer.amountPriceBox.warning.adjustedVolume"));
                model.showWarningAdjustedVolume.set(false);
                volumeTextField.setText(model.volume.get());
            }
        };
        requestPlaceOfferErrorMessageListener = (o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"), BSResources.get("createOffer.amountPriceBox.error.message",
                        model.requestPlaceOfferErrorMessage.get()));
                Popups.removeBlurContent();
            }
        };
        isPlaceOfferSpinnerVisibleListener = (ov, oldValue, newValue) -> {
            placeOfferSpinner.setProgress(newValue ? -1 : 0);
            placeOfferSpinner.setVisible(newValue);
        };

        showTransactionPublishedScreen = (o, oldValue, newValue) -> {
            if (BitsquareApp.DEV_MODE) {
                newValue = false;
                close();
                navigation.navigateTo(MainView.class, PortfolioView.class, OpenOffersView.class);
            }

            if (newValue) {
                overlayManager.blurContent();

                // Dialogs are a bit limited. There is no callback for the InformationDialog button click, so we added
                // our own actions.
                List<Action> actions = new ArrayList<>();
              /*  actions.add(new AbstractAction(BSResources.get("shared.copyTxId")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "COPY");
                        Utilities.copyToClipboard(model.transactionId.get());
                    }
                });*/
                actions.add(new AbstractAction(BSResources.get("shared.close")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "CLOSE");
                        try {
                            close();
                            navigation.navigateTo(MainView.class, PortfolioView.class, OpenOffersView.class);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                    }
                });
                Popups.openInfoPopup(BSResources.get("createOffer.success.headline"),
                        BSResources.get("createOffer.success.info"),
                        actions);
            }
        };
    }

    private void addListeners() {
        // focus out
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        minAmountTextField.focusedProperty().addListener(minAmountFocusedListener);
        priceTextField.focusedProperty().addListener(priceFocusedListener);
        volumeTextField.focusedProperty().addListener(volumeFocusedListener);

        // warnings
        model.showWarningInvalidBtcDecimalPlaces.addListener(showWarningInvalidBtcDecimalPlacesListener);
        model.showWarningInvalidFiatDecimalPlaces.addListener(showWarningInvalidFiatDecimalPlacesPlacesListener);
        model.showWarningAdjustedVolume.addListener(showWarningAdjustedVolumeListener);
        model.requestPlaceOfferErrorMessage.addListener(requestPlaceOfferErrorMessageListener);
        model.isPlaceOfferSpinnerVisible.addListener(isPlaceOfferSpinnerVisibleListener);

        model.showTransactionPublishedScreen.addListener(showTransactionPublishedScreen);
    }

    private void removeListeners() {
        // focus out
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        minAmountTextField.focusedProperty().removeListener(minAmountFocusedListener);
        priceTextField.focusedProperty().removeListener(priceFocusedListener);
        volumeTextField.focusedProperty().removeListener(volumeFocusedListener);

        // warnings
        model.showWarningInvalidBtcDecimalPlaces.removeListener(showWarningInvalidBtcDecimalPlacesListener);
        model.showWarningInvalidFiatDecimalPlaces.removeListener(showWarningInvalidFiatDecimalPlacesPlacesListener);
        model.showWarningAdjustedVolume.removeListener(showWarningAdjustedVolumeListener);
        model.requestPlaceOfferErrorMessage.removeListener(requestPlaceOfferErrorMessageListener);
        model.isPlaceOfferSpinnerVisible.removeListener(isPlaceOfferSpinnerVisibleListener);

        model.showTransactionPublishedScreen.removeListener(showTransactionPublishedScreen);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer.Direction direction, Coin amount, Fiat price) {
        model.initWithData(direction, amount, price);

        if (direction == Offer.Direction.BUY)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onPlaceOffer() {
        model.onPlaceOffer();
    }

    @FXML
    void onScroll() {
        InputTextField.hideErrorMessageDisplay();
    }

    @FXML
    void onShowPayFundsScreen() {
        if (!BitsquareApp.DEV_MODE) {
            if (model.getDisplaySecurityDepositInfo()) {
                overlayManager.blurContent();
                List<Action> actions = new ArrayList<>();
                actions.add(new AbstractAction(BSResources.get("shared.close")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "CLOSE");
                        Dialog.Actions.CLOSE.handle(actionEvent);
                    }
                });
                Popups.openInfoPopup("To ensure that both traders behave fair they need to pay a security deposit.",
                        "The deposit will stay in your local trading wallet until the offer gets accepted by " +
                                "another trader. " +
                                "\nIt will be refunded to you after the trade has successfully completed.",
                        actions);

                model.onSecurityDepositInfoDisplayed();
            }
        }


        priceAmountPane.setInactive();

        showPaymentInfoScreenButton.setVisible(false);

        payFundsPane.setVisible(true);
        totalToPayLabel.setVisible(true);
        totalToPayInfoIconLabel.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressLabel.setVisible(true);
        addressTextField.setVisible(true);
        balanceLabel.setVisible(true);
        balanceTextField.setVisible(true);
        fundsBoxInfoDisplay.setVisible(true);

        // for irc demo
        //showAdvancedSettingsButton.setVisible(true);
        showAdvancedSettingsButton.setManaged(false);

        if (expand == null) {
            expand = ImageUtil.getImageViewById(ImageUtil.EXPAND);
            collapse = ImageUtil.getImageViewById(ImageUtil.COLLAPSE);
        }
        showAdvancedSettingsButton.setGraphic(expand);

        setupTotalToPayInfoIconLabel();

        model.onShowPayFundsScreen();
    }

    @FXML
    void onToggleShowAdvancedSettings() {
        detailsVisible = !detailsVisible;
        if (detailsVisible) {
            showAdvancedSettingsButton.setText(BSResources.get("createOffer.fundsBox.hideAdvanced"));
            showAdvancedSettingsButton.setGraphic(collapse);
            showDetailsScreen();
        }
        else {
            showAdvancedSettingsButton.setText(BSResources.get("createOffer.fundsBox.showAdvanced"));
            showAdvancedSettingsButton.setGraphic(expand);
            hideDetailsScreen();
        }
    }

    @FXML
    void onOpenGeneralHelp() {
        Help.openWindow(HelpId.CREATE_OFFER_GENERAL);
    }

    @FXML
    void onOpenFundingHelp() {
        Help.openWindow(HelpId.CREATE_OFFER_FUNDING);
    }

    @FXML
    void onOpenAdvancedSettingsHelp() {
        Help.openWindow(HelpId.CREATE_OFFER_ADVANCED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openAccountSettings() {
        navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, RestrictionsView.class);
    }

    private void close() {
        if (closeHandler != null)
            closeHandler.close();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showDetailsScreen() {
        payFundsPane.setInactive();

        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.layout();

        if (!advancedScreenInited) {
            initEditIcons();
            advancedScreenInited = true;
        }

        toggleDetailsScreen(true);
    }

    private void hideDetailsScreen() {
        payFundsPane.setActive();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.layout();
        toggleDetailsScreen(false);
    }

    private void toggleDetailsScreen(boolean visible) {
        scrollPane.setOnScroll(scrollEvent -> {
            if (!visible)
                scrollEvent.consume();
        });

        // deactivate mouse wheel scrolling if hidden
        scrollPane.setVmax(visible ? scrollPane.getHeight() : 0);
        scrollPane.setVvalue(visible ? scrollPane.getHeight() : 0);

        showDetailsPane.setVisible(visible);

        acceptedCountriesLabel.setVisible(visible);
        acceptedCountriesLabelIcon.setVisible(visible);
        acceptedCountriesTextField.setVisible(visible);
        acceptedLanguagesLabel.setVisible(visible);
        acceptedLanguagesLabelIcon.setVisible(visible);
        acceptedLanguagesTextField.setVisible(visible);
        acceptedArbitratorsLabel.setVisible(visible);
        acceptedArbitratorsLabelIcon.setVisible(visible);
        acceptedArbitratorsTextField.setVisible(visible);

        bankAccountTypeLabel.setVisible(visible);
        bankAccountTypeTextField.setVisible(visible);
        bankAccountCurrencyLabel.setVisible(visible);
        bankAccountCurrencyTextField.setVisible(visible);
        bankAccountCountyLabel.setVisible(visible);
        bankAccountCountyTextField.setVisible(visible);

        advancedInfoDisplay.setVisible(visible);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initEditIcons() {
        acceptedCountriesLabelIcon.setId("clickable-icon");
        AwesomeDude.setIcon(acceptedCountriesLabelIcon, AwesomeIcon.EDIT_SIGN);
        Tooltip.install(acceptedCountriesLabelIcon, new Tooltip(BSResources.get("shared.openSettings")));
        acceptedCountriesLabelIcon.setOnMouseClicked(e -> openAccountSettings());

        acceptedLanguagesLabelIcon.setId("clickable-icon");
        AwesomeDude.setIcon(acceptedLanguagesLabelIcon, AwesomeIcon.EDIT_SIGN);
        Tooltip.install(acceptedLanguagesLabelIcon, new Tooltip(BSResources.get("shared.openSettings")));
        acceptedLanguagesLabelIcon.setOnMouseClicked(e -> openAccountSettings());

        acceptedArbitratorsLabelIcon.setId("clickable-icon");
        AwesomeDude.setIcon(acceptedArbitratorsLabelIcon, AwesomeIcon.EDIT_SIGN);
        Tooltip.install(acceptedArbitratorsLabelIcon, new Tooltip(BSResources.get("shared.openSettings")));
        acceptedArbitratorsLabelIcon.setOnMouseClicked(e -> openAccountSettings());
    }

    private void setupTotalToPayInfoIconLabel() {
        totalToPayInfoIconLabel.setId("clickable-icon");
        AwesomeDude.setIcon(totalToPayInfoIconLabel, AwesomeIcon.QUESTION_SIGN);

        totalToPayInfoIconLabel.setOnMouseEntered(e -> createInfoPopover());
        totalToPayInfoIconLabel.setOnMouseExited(e -> {
            if (totalToPayInfoPopover != null)
                totalToPayInfoPopover.hide();
        });
    }

    // As we don't use binding here we need to recreate it on mouse over to reflect the current state
    private void createInfoPopover() {
        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));

        int i = 0;
        if (model.isSeller()) {
            addPayInfoEntry(infoGridPane, i++,
                    BSResources.get("createOffer.fundsBox.tradeAmount"),
                    model.tradeAmount.get());
        }
        addPayInfoEntry(infoGridPane, i++,
                BSResources.get("createOffer.fundsBox.securityDeposit"),
                model.securityDeposit.get());
        addPayInfoEntry(infoGridPane, i++, BSResources.get("createOffer.fundsBox.offerFee"),
                model.offerFee.get());
        addPayInfoEntry(infoGridPane, i++, BSResources.get("createOffer.fundsBox.networkFee"),
                model.networkFee.get());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i++, BSResources.get("createOffer.fundsBox.total"),
                model.totalToPay.get());
        totalToPayInfoPopover = new PopOver(infoGridPane);
        if (totalToPayInfoIconLabel.getScene() != null) {
            totalToPayInfoPopover.setDetachable(false);
            totalToPayInfoPopover.setArrowIndent(5);
            totalToPayInfoPopover.show(totalToPayInfoIconLabel.getScene().getWindow(),
                    getPopupPosition().getX(),
                    getPopupPosition().getY());
        }
    }

    private void addPayInfoEntry(GridPane infoGridPane, int row, String labelText, String value) {
        Label label = new Label(labelText);
        TextField textField = new TextField(value);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setId("payment-info");
        GridPane.setConstraints(label, 0, row, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(textField, 1, row);
        infoGridPane.getChildren().addAll(label, textField);
    }

    private Point2D getPopupPosition() {
        Window window = totalToPayInfoIconLabel.getScene().getWindow();
        Point2D point = totalToPayInfoIconLabel.localToScene(0, 0);
        double x = point.getX() + window.getX() + totalToPayInfoIconLabel.getWidth() - 3;
        double y = point.getY() + window.getY() + Math.floor(totalToPayInfoIconLabel.getHeight() / 2) - 9;
        return new Point2D(x, y);
    }
}

