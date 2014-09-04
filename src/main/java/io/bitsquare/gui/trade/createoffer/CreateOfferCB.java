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

package io.bitsquare.gui.trade.createoffer;

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.MainController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.help.Help;
import io.bitsquare.gui.help.HelpId;
import io.bitsquare.gui.trade.TradeController;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

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
import javafx.scene.text.*;
import javafx.stage.Window;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.controlsfx.control.PopOver;
import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Implement other positioning method in InoutTextField to display it over the field instead of right side
// priceAmountHBox is too large after redesign as to be used as layoutReference. 

public class CreateOfferCB extends CachedCodeBehind<CreateOfferPM> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferCB.class);

    private boolean detailsVisible;
    private boolean advancedScreenInited;

    private ImageView expand;
    private ImageView collapse;
    private PopOver totalToPayInfoPopover;

    @FXML private ScrollPane scrollPane;
    @FXML private ImageView payFundsInfoIcon, showDetailsInfoIcon;
    @FXML private TextFlow payFundsInfoTextFlow, showDetailsInfoLabel;
    @FXML private Pane priceAmountPane, payFundsPane, showDetailsPane;
    @FXML private Label buyLabel, priceAmountTitleLabel, addressLabel,
            balanceLabel, payFundsTitleLabel, totalToPayLabel, totalToPayInfoIconLabel,
            showDetailsTitleLabel, bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel,
            acceptedCountriesLabel, acceptedCountriesLabelIcon, acceptedLanguagesLabel, acceptedLanguagesLabelIcon,
            acceptedArbitratorsLabel, acceptedArbitratorsLabelIcon;
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
    private CreateOfferCB(CreateOfferPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        setupBindings();
        setupListeners();
        balanceTextField.setup(presentationModel.getWalletFacade(), presentationModel.address.get());
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

        // Used to reset disable state of createOfferButton in OrderBookController
        if (parentController != null) ((TradeController) parentController).onCreateOfferViewRemoved();
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
        payFundsInfoIcon.setVisible(true);
        payFundsInfoTextFlow.setVisible(true);
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
            showAdvancedSettingsButton.setText("Hide advanced settings");
            showAdvancedSettingsButton.setGraphic(collapse);
            showDetailsScreen();
        }
        else {
            showAdvancedSettingsButton.setText("Show advanced settings");
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

    private void openSettings() {
        MainController.GET_INSTANCE().loadViewAndGetChildController(NavigationItem.SETTINGS);
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
                Popups.openWarningPopup("Warning", "The amount you have entered exceeds the number of allowed decimal" +
                        " places.\nThe amount has been adjusted to 4 decimal places.");
                presentationModel.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        presentationModel.showWarningInvalidFiatDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The amount you have entered exceeds the number of allowed decimal" +
                        " places.\nThe amount has been adjusted to 2 decimal places.");
                presentationModel.showWarningInvalidFiatDecimalPlaces.set(false);
            }
        });

        presentationModel.showWarningAdjustedVolume.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The total volume you have entered leads to invalid fractional " +
                        "Bitcoin amounts.\nThe amount has been adjusted and a new total volume be calculated from it.");
                presentationModel.showWarningAdjustedVolume.set(false);
                volumeTextField.setText(presentationModel.volume.get());
            }
        });

        presentationModel.requestPlaceOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup("Error", "An error occurred when placing the offer.\n" +
                        presentationModel.requestPlaceOfferErrorMessage.get());
            }
        });

        presentationModel.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                // Dialogs are a bit limited. There is no callback for the InformationDialog button click, so we added 
                // our own actions.
                List<Action> actions = new ArrayList<>();
                actions.add(new AbstractAction("Copy transaction ID") {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(presentationModel.transactionId.get());
                        clipboard.setContent(content);
                    }
                });
                actions.add(new AbstractAction("Close") {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        try {
                            close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                    }
                });

                Popups.openInformationPopup("Offer published",
                        "The Bitcoin network transaction ID for the offer payment is: " +
                                presentationModel.transactionId.get(),
                        "Your offer has been successfully published to the distributed orderbook.",
                        actions);
            }
        });
    }

    private void setupBindings() {
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
        amountTextField.amountValidationResultProperty().bind(presentationModel.amountValidationResult);
        minAmountTextField.amountValidationResultProperty().bind(presentationModel.minAmountValidationResult);
        priceTextField.amountValidationResultProperty().bind(presentationModel.priceValidationResult);
        volumeTextField.amountValidationResultProperty().bind(presentationModel.volumeValidationResult);

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

    private void initEditIcons() {
        advancedScreenInited = true;
        acceptedCountriesLabelIcon.setId("clickable-icon");
        AwesomeDude.setIcon(acceptedCountriesLabelIcon, AwesomeIcon.EDIT_SIGN);
        Tooltip.install(acceptedCountriesLabelIcon, new Tooltip("Open settings for editing"));
        acceptedCountriesLabelIcon.setOnMouseClicked(e -> openSettings());

        acceptedLanguagesLabelIcon.setId("clickable-icon");
        AwesomeDude.setIcon(acceptedLanguagesLabelIcon, AwesomeIcon.EDIT_SIGN);
        Tooltip.install(acceptedLanguagesLabelIcon, new Tooltip("Open settings for editing"));
        acceptedLanguagesLabelIcon.setOnMouseClicked(e -> openSettings());

        acceptedArbitratorsLabelIcon.setId("clickable-icon");
        AwesomeDude.setIcon(acceptedArbitratorsLabelIcon, AwesomeIcon.EDIT_SIGN);
        Tooltip.install(acceptedArbitratorsLabelIcon, new Tooltip("Open settings for editing"));
        acceptedArbitratorsLabelIcon.setOnMouseClicked(e -> openSettings());
    }

    private void hideDetailsScreen() {
        payFundsPane.setId("form-group-background-active");
        payFundsTitleLabel.setId("form-group-title-active");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.layout();

        toggleDetailsScreen(false);
    }

    private void toggleDetailsScreen(boolean visible) {
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

        showDetailsInfoIcon.setVisible(visible);
        showDetailsInfoLabel.setVisible(visible);
    }

    private void setupTotalToPayInfoIconLabel() {
        totalToPayInfoIconLabel.setId("clickable-icon");
        AwesomeDude.setIcon(totalToPayInfoIconLabel, AwesomeIcon.QUESTION_SIGN);

        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));

        addPayInfoEntry(infoGridPane, 0, "Collateral (" + presentationModel.collateralLabel.get() + ")",
                presentationModel.collateral.get());
        addPayInfoEntry(infoGridPane, 1, "Offer fee:", presentationModel.offerFee.get());
        addPayInfoEntry(infoGridPane, 2, "Bitcoin network fee:", presentationModel.networkFee.get());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, 3);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, 4, "Total:", presentationModel.totalToPay.get());

        totalToPayInfoIconLabel.setOnMouseEntered(e -> {
            if (totalToPayInfoIconLabel.getScene() != null) {
                totalToPayInfoPopover = new PopOver(infoGridPane);
                totalToPayInfoPopover.setDetachable(false);
                totalToPayInfoPopover.setArrowIndent(5);
                totalToPayInfoPopover.show(totalToPayInfoIconLabel.getScene().getWindow(),
                        getPopupPosition().getX(),
                        getPopupPosition().getY());
            }
        });
        totalToPayInfoIconLabel.setOnMouseExited(e -> {
            if (totalToPayInfoPopover != null)
                totalToPayInfoPopover.hide();
        });
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
        double x = point.getX() + window.getX() + totalToPayInfoIconLabel.getWidth() + 20;
        double y = point.getY() + window.getY() + Math.floor(totalToPayInfoIconLabel.getHeight() / 2);
        return new Point2D(x, y);
    }

}

