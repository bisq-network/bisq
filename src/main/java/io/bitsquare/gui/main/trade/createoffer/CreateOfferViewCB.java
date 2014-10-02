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

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.CloseListener;
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
import io.bitsquare.trade.Direction;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

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

public class CreateOfferViewCB extends CachedViewCB<CreateOfferPM> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferViewCB.class);


    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private CloseListener closeListener;

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
            priceFiatLabel, volumeFiatLabel, minAmountBtcLabel, priceDescriptionLabel, volumeDescriptionLabel;
    @FXML Button showPaymentInfoScreenButton, showAdvancedSettingsButton, placeOfferButton;

    @FXML InputTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML TextField acceptedArbitratorsTextField, totalToPayTextField, bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateOfferViewCB(CreateOfferPM presentationModel, Navigation navigation,
                              OverlayManager overlayManager) {
        super(presentationModel);
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
        balanceTextField.setup(presentationModel.getWalletFacade(), presentationModel.address.get(),
                presentationModel.getFormatter());
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

        // Inform parent that we got removed.
        // Needed to reset disable state of createOfferButton in OrderBookController
        if (closeListener != null)
            closeListener.onClosed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods (called form other views/CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Direction direction, Coin amount, Fiat price) {
        presentationModel.initWithData(direction, amount, price);

        if (direction == Direction.BUY)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");
    }

    public void setCloseListener(CloseListener closeListener) {
        this.closeListener = closeListener;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onPlaceOffer() {
        presentationModel.placeOffer();
    }

    @FXML
    void onShowPayFundsScreen() {
        if (presentationModel.displaySecurityDepositInfo()) {
            overlayManager.blurContent();
            List<Action> actions = new ArrayList<>();
            actions.add(new AbstractAction(BSResources.get("shared.close")) {
                @Override
                public void handle(ActionEvent actionEvent) {
                    Dialog.Actions.CLOSE.handle(actionEvent);
                    overlayManager.removeBlurContent();
                }
            });
            Popups.openInfo("To ensure that both traders behave fair they need to pay a security deposit.",
                    "The deposit will stay in your local trading wallet until the offer gets accepted by " +
                            "another trader. " +
                            "\nIt will be refunded to you after the trade has successfully completed.",
                    actions);
        
        /*
          Popups.openInfo("To ensure that both traders are behaving fair you need to put in a security deposit to an " +
                "offer. That will be refunded to you after the trade has successful completed. In case of a " +
                "dispute and the arbitrator will take the security deposit from the dishonest trader as his payment " +
                "for the dispute resolution. The security deposit will be included in the deposit transaction at the " +
                "moment when a trader accept your offer. As long as your offer is not taken by another trader, " +
                "the security deposit will not leave your trading wallet, and will be refunded when you cancel your " +
                "offer.");
         */
        }

        presentationModel.securityDepositInfoDisplayed();

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
        showAdvancedSettingsButton.setVisible(true);

        if (expand == null) {
            expand = ImageUtil.getImageViewById(ImageUtil.EXPAND);
            collapse = ImageUtil.getImageViewById(ImageUtil.COLLAPSE);
        }
        showAdvancedSettingsButton.setGraphic(expand);

        setupTotalToPayInfoIconLabel();

        presentationModel.onShowPayFundsScreen();
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
                overlayManager.blurContent();

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
                        overlayManager.removeBlurContent();
                    }
                });

                Popups.openInfo(BSResources.get("createOffer.success.headline"),
                        BSResources.get("createOffer.success.info", presentationModel.transactionId.get()),
                        actions);
            }
        });
    }

    private void setupBindings() {
        amountBtcLabel.textProperty().bind(presentationModel.btcCode);
        priceFiatLabel.textProperty().bind(presentationModel.fiatCode);
        volumeFiatLabel.textProperty().bind(presentationModel.fiatCode);
        minAmountBtcLabel.textProperty().bind(presentationModel.btcCode);

        priceDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("createOffer.amountPriceBox.priceDescription",
                                presentationModel.fiatCode.get()),
                presentationModel.fiatCode));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("createOffer.amountPriceBox.volumeDescription",
                                presentationModel.fiatCode.get()),
                presentationModel.fiatCode));

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
}

