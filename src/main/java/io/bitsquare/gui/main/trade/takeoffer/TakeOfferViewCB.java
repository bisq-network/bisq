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

package io.bitsquare.gui.main.trade.takeoffer;


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
import io.bitsquare.trade.Offer;

import com.google.bitcoin.core.Coin;

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

public class TakeOfferViewCB extends CachedViewCB<TakeOfferPM> {
    private static final Logger log = LoggerFactory.getLogger(TakeOfferViewCB.class);

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
    @FXML Label buyLabel, addressLabel, amountRangeTextField,
            balanceLabel, totalToPayLabel, totalToPayInfoIconLabel,
            bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel,
            acceptedCountriesLabel, acceptedLanguagesLabel,
            acceptedArbitratorsLabel, amountBtcLabel,
            priceDescriptionLabel, volumeDescriptionLabel;
    @FXML Button showPaymentInfoScreenButton, showAdvancedSettingsButton, takeOfferButton;

    @FXML InputTextField amountTextField;
    @FXML TextField priceTextField, volumeTextField, acceptedArbitratorsTextField,
            totalToPayTextField,
            bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakeOfferViewCB(TakeOfferPM presentationModel, Navigation navigation,
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
        if (closeListener != null)
            closeListener.onClosed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods (called form other views/CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Direction direction, Coin amount, Offer offer) {
        presentationModel.initWithData(direction, amount, offer);

        if (direction == Direction.BUY)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");

        priceDescriptionLabel.setText(BSResources.get("takeOffer.amountPriceBox.priceDescription",
                presentationModel.getFiatCode()));
        volumeDescriptionLabel.setText(BSResources.get("takeOffer.amountPriceBox.volumeDescription",
                presentationModel.getFiatCode()));

        balanceTextField.setup(presentationModel.getWalletFacade(), presentationModel.address.get(),
                presentationModel.getFormatter());

        buyLabel.setText(presentationModel.getDirectionLabel());
        amountRangeTextField.setText(presentationModel.getAmountRange());
        priceTextField.setText(presentationModel.getPrice());
        addressTextField.setPaymentLabel(presentationModel.getPaymentLabel());
        addressTextField.setAddress(presentationModel.getAddressAsString());
        bankAccountTypeTextField.setText(presentationModel.getBankAccountType());
        bankAccountTypeTextField.setText(presentationModel.getBankAccountType());
        bankAccountCurrencyTextField.setText(presentationModel.getBankAccountCurrency());
        bankAccountCountyTextField.setText(presentationModel.getBankAccountCounty());
        acceptedCountriesTextField.setText(presentationModel.getAcceptedCountries());
        acceptedLanguagesTextField.setText(presentationModel.getAcceptedLanguages());
        acceptedArbitratorsTextField.setText(presentationModel.getAcceptedArbitrators());
    }

    public void setCloseListener(CloseListener closeListener) {
        this.closeListener = closeListener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onTakeOffer() {
        presentationModel.takeOffer();
    }

    @FXML
    void onShowPayFundsScreen() {
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
            showAdvancedSettingsButton.setText(BSResources.get("takeOffer.fundsBox.hideAdvanced"));
            showAdvancedSettingsButton.setGraphic(collapse);
            showDetailsScreen();
        }
        else {
            showAdvancedSettingsButton.setText(BSResources.get("takeOffer.fundsBox.showAdvanced"));
            showAdvancedSettingsButton.setGraphic(expand);
            hideDetailsScreen();
        }
    }

    @FXML
    void onOpenGeneralHelp() {
        Help.openWindow(HelpId.TAKE_OFFER_GENERAL);
    }

    @FXML
    void onOpenFundingHelp() {
        Help.openWindow(HelpId.TAKE_OFFER_FUNDING);
    }

    @FXML
    void onOpenAdvancedSettingsHelp() {
        Help.openWindow(HelpId.TAKE_OFFER_ADVANCED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        // warnings
        presentationModel.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces"));
                presentationModel.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        presentationModel.requestTakeOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"),
                        BSResources.get("takeOffer.amountPriceBox.error.message",
                                presentationModel.requestTakeOfferErrorMessage.get()));
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
                            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS,
                                    Navigation.Item.PENDING_TRADES);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                Popups.openInfo(BSResources.get("takeOffer.success.info",
                                presentationModel.transactionId.get()),
                        BSResources.get("takeOffer.success.headline"),
                        actions);
            }
        });
    }

    private void setupBindings() {
        amountBtcLabel.textProperty().bind(presentationModel.btcCode);
        amountTextField.textProperty().bindBidirectional(presentationModel.amount);
        volumeTextField.textProperty().bindBidirectional(presentationModel.volume);
        totalToPayTextField.textProperty().bind(presentationModel.totalToPay);
        addressTextField.amountAsCoinProperty().bind(presentationModel.totalToPayAsCoin);

        // Validation
        amountTextField.validationResultProperty().bind(presentationModel.amountValidationResult);

        // buttons
        takeOfferButton.visibleProperty().bind(presentationModel.isTakeOfferButtonVisible);
        takeOfferButton.disableProperty().bind(presentationModel.isTakeOfferButtonDisabled);
    }

    private void showDetailsScreen() {
        payFundsPane.setInactive();

        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.layout();

        advancedScreenInited = !advancedScreenInited;

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
        acceptedCountriesTextField.setVisible(visible);
        acceptedLanguagesLabel.setVisible(visible);
        acceptedLanguagesTextField.setVisible(visible);
        acceptedArbitratorsLabel.setVisible(visible);
        acceptedArbitratorsTextField.setVisible(visible);

        bankAccountTypeLabel.setVisible(visible);
        bankAccountTypeTextField.setVisible(visible);
        bankAccountCurrencyLabel.setVisible(visible);
        bankAccountCurrencyTextField.setVisible(visible);
        bankAccountCountyLabel.setVisible(visible);
        bankAccountCountyTextField.setVisible(visible);

        advancedInfoDisplay.setVisible(visible);
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
                BSResources.get("takeOffer.fundsBox.amount"),
                presentationModel.getAmount());
        addPayInfoEntry(infoGridPane, 1,
                presentationModel.getCollateralLabel(),
                presentationModel.collateral.get());
        addPayInfoEntry(infoGridPane, 2, BSResources.get("takeOffer.fundsBox.offerFee"),
                presentationModel.getOfferFee());
        addPayInfoEntry(infoGridPane, 3, BSResources.get("takeOffer.fundsBox.networkFee"),
                presentationModel.getNetworkFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, 4);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, 5, BSResources.get("takeOffer.fundsBox.total"),
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

