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

package io.bitsquare.gui.main.trade.createoffer;

import io.bitsquare.gui.ActivatableView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Direction;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createStringBinding;

// TODO Implement other positioning method in InoutTextField to display it over the field instead of right side
// priceAmountHBox is too large after redesign as to be used as layoutReference.

public class CreateOfferView extends ActivatableView<CreateOfferViewModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferView.class);


    private final Navigation navigation;
    private final OverlayManager overlayManager;

    private BooleanProperty tabIsClosable;

    private boolean detailsVisible;
    private boolean advancedScreenInited;

    private ImageView expand;
    private ImageView collapse;
    private PopOver totalToPayInfoPopover;

    @FXML InfoDisplay advancedInfoDisplay, fundsBoxInfoDisplay;
    @FXML ScrollPane scrollPane;
    @FXML ImageView imageView;
    @FXML TitledGroupBg priceAmountPane, payFundsPane, showDetailsPane;
    @FXML Label buyLabel, addressLabel,
            balanceLabel, totalToPayLabel, totalToPayInfoIconLabel,
            bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel,
            acceptedCountriesLabel, acceptedCountriesLabelIcon, acceptedLanguagesLabel, acceptedLanguagesLabelIcon,
            acceptedArbitratorsLabel, acceptedArbitratorsLabelIcon, amountBtcLabel,
            priceFiatLabel, volumeFiatLabel, minAmountBtcLabel, priceDescriptionLabel, volumeDescriptionLabel,
            placeOfferSpinnerInfoLabel;
    @FXML Button showPaymentInfoScreenButton, showAdvancedSettingsButton, placeOfferButton;

    @FXML InputTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML TextField acceptedArbitratorsTextField, totalToPayTextField, bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;
    @FXML ProgressIndicator placeOfferSpinner;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateOfferView(CreateOfferViewModel model, Navigation navigation,
                            OverlayManager overlayManager) {
        super(model);
        this.navigation = navigation;
        this.overlayManager = overlayManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        setupListeners();
        setupBindings();
        balanceTextField.setup(model.getWalletService(), model.address.get(),
                model.getFormatter());
        volumeTextField.setPromptText(BSResources.get("createOffer.volume.prompt", model.fiatCode.get()));
    }

    @Override
    public void doDeactivate() {
        tabIsClosable.unbind();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods (called form other views/CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Direction direction, Coin amount, Fiat price) {
        model.initWithData(direction, amount, price);

        if (direction == Direction.BUY)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");
    }

    public void configCloseHandlers(BooleanProperty tabIsClosable) {
        this.tabIsClosable = tabIsClosable;
        tabIsClosable.bind(model.tabIsClosable);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onPlaceOffer() {
        model.placeOffer();
    }

    @FXML
    void onShowPayFundsScreen() {
        if (model.displaySecurityDepositInfo()) {
            overlayManager.blurContent();
            List<Action> actions = new ArrayList<>();
            actions.add(new AbstractAction(BSResources.get("shared.close")) {
                @Override
                public void handle(ActionEvent actionEvent) {
                    getProperties().put("type", "CLOSE");
                    Dialog.Actions.CLOSE.handle(actionEvent);
                    overlayManager.removeBlurContent();
                }
            });
            Popups.openInfoPopup("To ensure that both traders behave fair they need to pay a security deposit.",
                    "The deposit will stay in your local trading wallet until the offer gets accepted by " +
                            "another trader. " +
                            "\nIt will be refunded to you after the trade has successfully completed.",
                    actions);

            model.securityDepositInfoDisplayed();
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
        navigation.navigationTo(Navigation.Item.MAIN,
                Navigation.Item.ACCOUNT,
                Navigation.Item.ACCOUNT_SETTINGS,
                Navigation.Item.RESTRICTIONS);
    }

    private void close() {
        TabPane tabPane = ((TabPane) (root.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());

        navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.OFFERS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {
        scrollPane.setOnScroll(e -> InputTextField.hideErrorMessageDisplay());

        // focus out
        amountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        });

        minAmountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            model.onFocusOutMinAmountTextField(oldValue, newValue, minAmountTextField.getText());
            minAmountTextField.setText(model.minAmount.get());
        });

        priceTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            model.onFocusOutPriceTextField(oldValue, newValue, priceTextField.getText());
            priceTextField.setText(model.price.get());
        });

        volumeTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            model.onFocusOutVolumeTextField(oldValue, newValue, volumeTextField.getText());
            volumeTextField.setText(model.volume.get());
        });

        // warnings
        model.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("createOffer.amountPriceBox.warning.invalidBtcDecimalPlaces"));
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        model.showWarningInvalidFiatDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("createOffer.amountPriceBox.warning.invalidFiatDecimalPlaces"));
                model.showWarningInvalidFiatDecimalPlaces.set(false);
            }
        });

        model.showWarningAdjustedVolume.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("createOffer.amountPriceBox.warning.adjustedVolume"));
                model.showWarningAdjustedVolume.set(false);
                volumeTextField.setText(model.volume.get());
            }
        });

        model.requestPlaceOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"),
                        BSResources.get("createOffer.amountPriceBox.error.message",
                                model.requestPlaceOfferErrorMessage.get()));
            }
        });

        model.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
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
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                Popups.openInfoPopup(BSResources.get("createOffer.success.headline"),
                        BSResources.get("createOffer.success.info"),
                        actions);
            }
        });
    }

    private void setupBindings() {
        amountBtcLabel.textProperty().bind(model.btcCode);
        priceFiatLabel.textProperty().bind(model.fiatCode);
        volumeFiatLabel.textProperty().bind(model.fiatCode);
        minAmountBtcLabel.textProperty().bind(model.btcCode);

        priceDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("createOffer.amountPriceBox.priceDescription",
                                model.fiatCode.get()),
                model.fiatCode));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("createOffer.amountPriceBox.volumeDescription",
                                model.fiatCode.get()),
                model.fiatCode));

        buyLabel.textProperty().bind(model.directionLabel);

        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        priceTextField.textProperty().bindBidirectional(model.price);
        volumeTextField.textProperty().bindBidirectional(model.volume);

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

        model.isPlaceOfferSpinnerVisible.addListener((ov, oldValue, newValue) -> {
            placeOfferSpinner.setProgress(newValue ? -1 : 0);
            placeOfferSpinner.setVisible(newValue);
        });
    }

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

        addPayInfoEntry(infoGridPane, 0,
                BSResources.get("createOffer.fundsBox.securityDeposit"),
                model.securityDeposit.get());
        addPayInfoEntry(infoGridPane, 1, BSResources.get("createOffer.fundsBox.offerFee"),
                model.offerFee.get());
        addPayInfoEntry(infoGridPane, 2, BSResources.get("createOffer.fundsBox.networkFee"),
                model.networkFee.get());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, 3);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, 4, BSResources.get("createOffer.fundsBox.total"),
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

