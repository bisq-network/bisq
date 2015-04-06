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

package io.bitsquare.gui.main.offer.takeoffer;


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
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.application.Platform;
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

@FxmlView
public class TakeOfferView extends ActivatableViewAndModel<AnchorPane, TakeOfferViewModel> {

    @FXML ScrollPane scrollPane;
    @FXML ImageView imageView;
    @FXML InputTextField amountTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;
    @FXML ProgressIndicator takeOfferSpinner, isOfferAvailableProgressIndicator;
    @FXML InfoDisplay amountPriceBoxInfoDisplay, advancedInfoDisplay, fundsBoxInfoDisplay;
    @FXML TitledGroupBg priceAmountPane, payFundsPane, showDetailsPane;
    @FXML Button showPaymentInfoScreenButton, showAdvancedSettingsButton, takeOfferButton;
    @FXML TextField priceTextField, volumeTextField, acceptedArbitratorsTextField, totalToPayTextField,
            bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField,
            acceptedCountriesTextField, acceptedLanguagesTextField;
    @FXML Label isOfferAvailableLabel, buyLabel, addressLabel, amountDescriptionLabel, amountRangeTextField, balanceLabel, totalToPayLabel,
            totalToPayInfoIconLabel,
            bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel, acceptedCountriesLabel,
            acceptedLanguagesLabel, acceptedArbitratorsLabel, amountBtcLabel, priceDescriptionLabel,
            volumeDescriptionLabel, takeOfferSpinnerInfoLabel;

    private ImageView expand;
    private ImageView collapse;
    private PopOver totalToPayInfoPopover;

    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private OfferView.CloseHandler closeHandler;

    private ChangeListener<String> errorMessageChangeListener;

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
        model.errorMessage.removeListener(errorMessageChangeListener);
    }

    public void initWithData(Coin amount, Offer offer) {
        model.initWithData(amount, offer);

        if (offer.getDirection() == Offer.Direction.SELL)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");

        priceDescriptionLabel.setText(BSResources.get("takeOffer.amountPriceBox.priceDescription", model.getFiatCode()));
        volumeDescriptionLabel.setText(BSResources.get("takeOffer.amountPriceBox.volumeDescription", model.getFiatCode()));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(() -> model.volumeDescriptionLabel.get(), model.fiatCode, model.volumeDescriptionLabel));

        balanceTextField.setup(model.getWalletService(), model.address.get(), model.getFormatter());

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
        acceptedArbitratorsTextField.setText(model.getAcceptedArbitratorIds());

        amountPriceBoxInfoDisplay.textProperty().bind(model.amountPriceBoxInfo);
        fundsBoxInfoDisplay.textProperty().bind(model.fundsBoxInfoDisplay);
        showCheckAvailabilityScreen();
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    @FXML
    void onTakeOffer() {
        model.takeOffer();
    }

    @FXML
    void onShowPaymentScreen() {
        model.onShowPaymentScreen();
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
        if (closeHandler != null)
            closeHandler.close();
    }

    private void setupListeners() {
        scrollPane.setOnScroll(e -> InputTextField.hideErrorMessageDisplay());

        // focus out
        amountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        });

        model.state.addListener((ov, oldValue, newValue) -> {
            switch (newValue) {
                case CHECK_AVAILABILITY:
                    showCheckAvailabilityScreen();
                    break;
                case AMOUNT_SCREEN:
                    showAmountScreen();
                    break;
                case PAYMENT_SCREEN:
                    setupPaymentScreen();
                    break;
                case DETAILS_SCREEN:
                    showDetailsScreen();
                    break;
            }
        });

        // warnings
        model.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup(BSResources.get("shared.warning"),
                        BSResources.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces"));
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        errorMessageChangeListener = (o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"), BSResources.get("takeOffer.error.message", model.errorMessage.get()));
                Popups.removeBlurContent();
                Platform.runLater(this::close);
            }
        };
        model.errorMessage.addListener(errorMessageChangeListener);

        model.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
            // TODO temp just for testing 
            newValue = false;
            close();
            navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);

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
        amountDescriptionLabel.textProperty().bind(model.amountDescription);


        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);

        // buttons
        takeOfferButton.disableProperty().bind(model.takeOfferButtonDisabled);

        takeOfferSpinnerInfoLabel.visibleProperty().bind(model.isTakeOfferSpinnerVisible);

        model.isTakeOfferSpinnerVisible.addListener((ov, oldValue, newValue) -> {
            takeOfferSpinner.setProgress(newValue ? -1 : 0);
            takeOfferSpinner.setVisible(newValue);
        });
    }

    private void showCheckAvailabilityScreen() {

    }

    private void showAmountScreen() {
        isOfferAvailableLabel.setVisible(false);
        isOfferAvailableLabel.setManaged(false);
        isOfferAvailableProgressIndicator.setProgress(0);
        isOfferAvailableProgressIndicator.setVisible(false);
        isOfferAvailableProgressIndicator.setManaged(false);

        showPaymentInfoScreenButton.setVisible(true);
    }

    private void setupPaymentScreen() {
        // TODO deactivate for testing the moment
       /* if (model.getDisplaySecurityDepositInfo()) {
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
                    "The deposit will stay in your local trading wallet until the offer gets accepted by another trader. " +
                            "\nIt will be refunded to you after the trade has successfully completed.",
                    actions);

            model.securityDepositInfoDisplayed();
        }*/

        priceAmountPane.setInactive();
        takeOfferButton.setVisible(true);
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

        int i = 0;
        if (model.isSeller()) {
            addPayInfoEntry(infoGridPane, i++,
                    BSResources.get("takeOffer.fundsBox.amount"),
                    model.getAmount());
        }


        addPayInfoEntry(infoGridPane, i++,
                BSResources.get("takeOffer.fundsBox.securityDeposit"),
                model.securityDeposit.get());
        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.offerFee"),
                model.getOfferFee());
        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.networkFee"),
                model.getNetworkFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.total"),
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

