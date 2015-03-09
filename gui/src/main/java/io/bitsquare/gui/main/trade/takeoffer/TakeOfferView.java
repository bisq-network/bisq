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


import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pending.PendingTradesView;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import viewfx.view.FxmlView;
import viewfx.view.support.ActivatableViewAndModel;

import javafx.beans.property.BooleanProperty;
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

@FxmlView
public class TakeOfferView extends ActivatableViewAndModel<AnchorPane, TakeOfferViewModel> {

    @FXML ScrollPane scrollPane;
    @FXML ImageView imageView;
    @FXML InputTextField amountTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;
    @FXML ProgressIndicator takeOfferSpinner;
    @FXML InfoDisplay advancedInfoDisplay, fundsBoxInfoDisplay;
    @FXML TitledGroupBg priceAmountPane, payFundsPane, showDetailsPane;
    @FXML Button showPaymentInfoScreenButton, showAdvancedSettingsButton, takeOfferButton;
    @FXML TextField priceTextField, volumeTextField, acceptedArbitratorsTextField, totalToPayTextField,
            bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField,
            acceptedCountriesTextField, acceptedLanguagesTextField;
    @FXML Label isOfferAvailableLabel, buyLabel, addressLabel, amountRangeTextField, balanceLabel, totalToPayLabel,
            totalToPayInfoIconLabel,
            bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel, acceptedCountriesLabel,
            acceptedLanguagesLabel, acceptedArbitratorsLabel, amountBtcLabel, priceDescriptionLabel,
            volumeDescriptionLabel, takeOfferSpinnerInfoLabel;
    @FXML ProgressIndicator isOfferAvailableProgressIndicator;

    private ImageView expand;
    private ImageView collapse;
    private PopOver totalToPayInfoPopover;

    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private ChangeListener<Offer.State> offerIsAvailableChangeListener;

    @Inject
    private TakeOfferView(TakeOfferViewModel model, Navigation navigation,
                          OverlayManager overlayManager) {
        super(model);

        this.navigation = navigation;
        this.overlayManager = overlayManager;
    }

    @Override
    public void initialize() {
        setupListeners();
        setupBindings();
    }

    @Override
    protected void doDeactivate() {
        if (offerIsAvailableChangeListener != null)
            model.offerIsAvailable.removeListener(offerIsAvailableChangeListener);
    }

    public void initWithData(Direction direction, Coin amount, Offer offer) {
        model.initWithData(direction, amount, offer);

        if (direction == Direction.BUY)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");

        priceDescriptionLabel.setText(BSResources.get("takeOffer.amountPriceBox.priceDescription",
                model.getFiatCode()));
        volumeDescriptionLabel.setText(BSResources.get("takeOffer.amountPriceBox.volumeDescription",
                model.getFiatCode()));

        balanceTextField.setup(model.getWalletService(), model.address.get(),
                model.getFormatter());

        buyLabel.setText(model.getDirectionLabel());
        amountRangeTextField.setText(model.getAmountRange());
        priceTextField.setText(model.getPrice());
        addressTextField.setPaymentLabel(model.getPaymentLabel());
        addressTextField.setAddress(model.getAddressAsString());
        bankAccountTypeTextField.setText(model.getBankAccountType());
        bankAccountTypeTextField.setText(model.getBankAccountType());
        bankAccountCurrencyTextField.setText(model.getBankAccountCurrency());
        bankAccountCountyTextField.setText(model.getBankAccountCounty());
        acceptedCountriesTextField.setText(model.getAcceptedCountries());
        acceptedLanguagesTextField.setText(model.getAcceptedLanguages());
        acceptedArbitratorsTextField.setText(model.getAcceptedArbitrators());

        offerIsAvailableChangeListener = (ov, oldValue, newValue) -> handleOfferIsAvailableState(newValue);
        model.offerIsAvailable.addListener(offerIsAvailableChangeListener);
        handleOfferIsAvailableState(model.offerIsAvailable.get());
    }

    private void handleOfferIsAvailableState(Offer.State state) {
        if (state == Offer.State.OFFER_AVAILABLE) {
            isOfferAvailableLabel.setVisible(false);
            isOfferAvailableLabel.setManaged(false);
            isOfferAvailableProgressIndicator.setProgress(0);
            isOfferAvailableProgressIndicator.setVisible(false);
            isOfferAvailableProgressIndicator.setManaged(false);
            showPaymentInfoScreenButton.setVisible(true);
        }
        else if ((state == Offer.State.OFFER_NOT_AVAILABLE)) {
            Popups.openWarningPopup("You cannot take that offer",
                    "The offerer is either offline or the offer was already taken by another trader.");
            close();
        }
        else if ((state == Offer.State.OFFER_REMOVED)) {
            Popups.openWarningPopup("You cannot take that offer",
                    "The offerer has been removed in the meantime.");
            close();
        }
    }

    public void configCloseHandlers(BooleanProperty tabIsClosable) {
        tabIsClosable.bind(model.tabIsClosable);
    }

    @FXML
    void onTakeOffer() {
        model.takeOffer();
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
        model.detailsVisible = !model.detailsVisible;
        if (model.detailsVisible) {
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

    private void close() {
        TabPane tabPane = ((TabPane) (root.getParent().getParent()));

        // Might fix #315  Offerbook tab gets closed 
        //tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());
        if (tabPane != null && tabPane.getTabs() != null && tabPane.getTabs().size() > 1)
            tabPane.getTabs().remove(1);
    }

    private void setupListeners() {
        scrollPane.setOnScroll(e -> InputTextField.hideErrorMessageDisplay());

        // focus out
        amountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        });

        // warnings
        model.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces"));
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        model.requestTakeOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"),
                        BSResources.get("takeOffer.amountPriceBox.error.message",
                                model.requestTakeOfferErrorMessage.get()));
                Popups.removeBlurContent();
            }
        });

        model.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                overlayManager.blurContent();

                // Dialogs are a bit limited. There is no callback for the InformationDialog button click, so we added
                // our own actions.
                List<Action> actions = new ArrayList<>();
               /* actions.add(new AbstractAction(BSResources.get("shared.copyTxId")) {
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
                            navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                    }
                });

                Popups.openInfoPopup(BSResources.get("takeOffer.success.headline"),
                        BSResources.get("takeOffer.success.info"),
                        actions);
            }
        });
    }

    private void setupBindings() {
        amountBtcLabel.textProperty().bind(model.btcCode);
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.totalToPayAsCoin);

        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);

        // buttons
        takeOfferButton.visibleProperty().bind(model.isTakeOfferButtonVisible);
        takeOfferButton.disableProperty().bind(model.isTakeOfferButtonDisabled);

        takeOfferSpinnerInfoLabel.visibleProperty().bind(model.isTakeOfferSpinnerVisible);

        model.isTakeOfferSpinnerVisible.addListener((ov, oldValue, newValue) -> {
            takeOfferSpinner.setProgress(newValue ? -1 : 0);
            takeOfferSpinner.setVisible(newValue);
        });
    }

    private void showDetailsScreen() {
        payFundsPane.setInactive();

        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.layout();

        model.advancedScreenInited = !model.advancedScreenInited;

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
                model.getAmount());
        addPayInfoEntry(infoGridPane, 1,
                BSResources.get("takeOffer.fundsBox.securityDeposit"),
                model.securityDeposit.get());
        addPayInfoEntry(infoGridPane, 2, BSResources.get("takeOffer.fundsBox.offerFee"),
                model.getOfferFee());
        addPayInfoEntry(infoGridPane, 3, BSResources.get("takeOffer.fundsBox.networkFee"),
                model.getNetworkFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, 4);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, 5, BSResources.get("takeOffer.fundsBox.total"),
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

