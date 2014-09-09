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

package io.bitsquare.gui.view.trade;

import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.OverlayController;
import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.help.Help;
import io.bitsquare.gui.help.HelpId;
import io.bitsquare.gui.pm.trade.CreateOfferPM;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.view.CachedCodeBehind;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
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

public class CreateOfferViewCB extends CachedCodeBehind<CreateOfferPM> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferViewCB.class);

    private NavigationController navigationController;
    private OverlayController overlayController;

    private boolean detailsVisible;
    private boolean advancedScreenInited;
    private Callable<Void> onCloseCallable;

    private ImageView expand;
    private ImageView collapse;
    private PopOver totalToPayInfoPopover;

    @FXML private InfoDisplay advancedInfoDisplay, fundsBoxInfoDisplay;
    @FXML private ScrollPane scrollPane;
    @FXML private Pane priceAmountPane, payFundsPane, showDetailsPane;
    @FXML private Label buyLabel, priceAmountTitleLabel, addressLabel,
            balanceLabel, payFundsTitleLabel, totalToPayLabel, totalToPayInfoIconLabel,
            showDetailsTitleLabel, bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel,
            acceptedCountriesLabel, acceptedCountriesLabelIcon, acceptedLanguagesLabel, acceptedLanguagesLabelIcon,
            acceptedArbitratorsLabel, acceptedArbitratorsLabelIcon, amountBtcLabel,
            priceFiatLabel, volumeFiatLabel, minAmountBtcLabel, priceDescriptionLabel, volumeDescriptionLabel;
    @FXML private Button showPaymentInfoScreenButton, showAdvancedSettingsButton, placeOfferButton;

    @FXML private InputTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML private TextField acceptedArbitratorsTextField, totalToPayTextField, bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateOfferViewCB(CreateOfferPM presentationModel, NavigationController navigationController,
                              OverlayController overlayController) {
        super(presentationModel);
        this.navigationController = navigationController;
        this.overlayController = overlayController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        setupListeners();
        setupBindings();
        balanceTextField.setup(presentationModel.getWalletFacade(), presentationModel.address.get());
        volumeTextField.setPromptText(BSResources.get("createOffer.volume.prompt", presentationModel.fiatCode.get()));
    }

    @SuppressWarnings("EmptyMethod")
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();

        // Inform parent that we gor removed.
        // Needed to reset disable state of createOfferButton in OrderBookController
        if (onCloseCallable != null) {
            try {
                onCloseCallable.call();
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods (called form other views/CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter) {
        presentationModel.setOrderBookFilter(orderBookFilter);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onPlaceOffer() {
        presentationModel.onPlaceOffer();
    }

    @FXML
    private void onShowPayFundsScreen() {
        priceAmountPane.setId("form-group-background");
        priceAmountTitleLabel.setId("form-group-title");

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
        showAdvancedSettingsButton.setVisible(true);

        if (expand == null) {
            expand = ImageUtil.getIconImageView(ImageUtil.EXPAND);
            collapse = ImageUtil.getIconImageView(ImageUtil.COLLAPSE);
        }
        showAdvancedSettingsButton.setGraphic(expand);

        setupTotalToPayInfoIconLabel();

        presentationModel.onShowPayFundsScreen();
    }

    @FXML
    private void onToggleShowAdvancedSettings() {
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
    private void onOpenGeneralHelp() {
        Help.openWindow(HelpId.CREATE_OFFER_GENERAL);
    }

    @FXML
    private void onOpenFundingHelp() {
        Help.openWindow(HelpId.CREATE_OFFER_FUNDING);
    }

    @FXML
    private void onOpenAdvancedSettingsHelp() {
        Help.openWindow(HelpId.CREATE_OFFER_ADVANCED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openAccountSettings() {
        navigationController.navigationTo(NavigationItem.ACCOUNT,
                NavigationItem.ACCOUNT_SETTINGS,
                NavigationItem.RESTRICTIONS);
    }

    private void close() {
        TabPane tabPane = ((TabPane) (root.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {
        scrollPane.setOnScroll(e -> InputTextField.hideErrorMessageDisplay());

        // focus out
        amountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presentationModel.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(presentationModel.amount.get());
        });

        minAmountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presentationModel.onFocusOutMinAmountTextField(oldValue, newValue, minAmountTextField.getText());
            minAmountTextField.setText(presentationModel.minAmount.get());
        });

        priceTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presentationModel.onFocusOutPriceTextField(oldValue, newValue, priceTextField.getText());
            priceTextField.setText(presentationModel.price.get());
        });

        volumeTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presentationModel.onFocusOutVolumeTextField(oldValue, newValue, volumeTextField.getText());
            volumeTextField.setText(presentationModel.volume.get());
        });

        // warnings
        presentationModel.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("createOffer.amountPriceBox.warning.invalidBtcDecimalPlaces"));
                presentationModel.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        presentationModel.showWarningInvalidFiatDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("createOffer.amountPriceBox.warning.invalidFiatDecimalPlaces"));
                presentationModel.showWarningInvalidFiatDecimalPlaces.set(false);
            }
        });

        presentationModel.showWarningAdjustedVolume.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("createOffer.amountPriceBox.warning.adjustedVolume"));
                presentationModel.showWarningAdjustedVolume.set(false);
                volumeTextField.setText(presentationModel.volume.get());
            }
        });

        presentationModel.requestPlaceOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"),
                        BSResources.get("createOffer.amountPriceBox.error.message",
                                presentationModel.requestPlaceOfferErrorMessage.get()));
            }
        });

        presentationModel.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                overlayController.blurContent();

                // Dialogs are a bit limited. There is no callback for the InformationDialog button click, so we added 
                // our own actions.
                List<Action> actions = new ArrayList<>();
                actions.add(new AbstractAction(BSResources.get("shared.copyTxId")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(presentationModel.transactionId.get());
                        clipboard.setContent(content);
                    }
                });
                actions.add(new AbstractAction(BSResources.get("shared.close")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        try {
                            close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                        overlayController.removeBlurContent();
                    }
                });

                Popups.openInfo(BSResources.get("createOffer.success.info",
                                presentationModel.transactionId.get()),
                        BSResources.get("createOffer.success.headline"),
                        actions);
            }
        });
    }

    private void setupBindings() {
        amountBtcLabel.textProperty().bind(presentationModel.btcCode);
        priceFiatLabel.textProperty().bind(presentationModel.fiatCode);
        volumeFiatLabel.textProperty().bind(presentationModel.fiatCode);
        minAmountBtcLabel.textProperty().bind(presentationModel.btcCode);
        priceDescriptionLabel.textProperty().bind(presentationModel.fiatCode);
        volumeDescriptionLabel.textProperty().bind(presentationModel.fiatCode);//Price per Bitcoin in EUR

        priceDescriptionLabel.textProperty().bind(createStringBinding(() ->
                BSResources.get("createOffer.amountPriceBox.priceDescr", presentationModel.fiatCode.get())));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(() ->
                BSResources.get("createOffer.amountPriceBox.volumeDescr", presentationModel.fiatCode.get())));

        buyLabel.textProperty().bind(presentationModel.directionLabel);

        amountTextField.textProperty().bindBidirectional(presentationModel.amount);
        minAmountTextField.textProperty().bindBidirectional(presentationModel.minAmount);
        priceTextField.textProperty().bindBidirectional(presentationModel.price);
        volumeTextField.textProperty().bindBidirectional(presentationModel.volume);

        totalToPayTextField.textProperty().bind(presentationModel.totalToPay);

        addressTextField.amountAsCoinProperty().bind(presentationModel.totalToPayAsCoin);
        addressTextField.paymentLabelProperty().bind(presentationModel.paymentLabel);
        addressTextField.addressProperty().bind(presentationModel.addressAsString);

        bankAccountTypeTextField.textProperty().bind(presentationModel.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(presentationModel.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(presentationModel.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(presentationModel.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(presentationModel.acceptedLanguages);
        acceptedArbitratorsTextField.textProperty().bind(presentationModel.acceptedArbitrators);

        // Validation
        amountTextField.validationResultProperty().bind(presentationModel.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(presentationModel.minAmountValidationResult);
        priceTextField.validationResultProperty().bind(presentationModel.priceValidationResult);
        volumeTextField.validationResultProperty().bind(presentationModel.volumeValidationResult);

        // buttons
        placeOfferButton.visibleProperty().bind(presentationModel.isPlaceOfferButtonVisible);
        placeOfferButton.disableProperty().bind(presentationModel.isPlaceOfferButtonDisabled);
        //  closeButton.visibleProperty().bind(presentationModel.isCloseButtonVisible);
    }

    private void showDetailsScreen() {
        payFundsPane.setId("form-group-background");
        payFundsTitleLabel.setId("form-group-title");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.layout();

        if (!advancedScreenInited) {
            initEditIcons();
            advancedScreenInited = true;
        }

        toggleDetailsScreen(true);
    }

    private void hideDetailsScreen() {
        payFundsPane.setId("form-group-background-active");
        payFundsTitleLabel.setId("form-group-title-active");
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
        showDetailsTitleLabel.setVisible(visible);

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
        advancedScreenInited = true;
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
                presentationModel.collateralLabel.get(),
                presentationModel.collateral.get());
        addPayInfoEntry(infoGridPane, 1, BSResources.get("createOffer.fundsBox.offerFee"),
                presentationModel.offerFee.get());
        addPayInfoEntry(infoGridPane, 2, BSResources.get("createOffer.fundsBox.networkFee"),
                presentationModel.networkFee.get());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, 3);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, 4, BSResources.get("createOffer.fundsBox.total"),
                presentationModel.totalToPay.get());
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

    public void setOnClose(Callable<Void> onCloseCallBack) {
        this.onCloseCallable = onCloseCallBack;
    }
}

