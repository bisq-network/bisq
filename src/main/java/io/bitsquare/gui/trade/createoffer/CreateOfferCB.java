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
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.ValidatingTextField;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.trade.TradeController;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferCB extends CachedCodeBehind<CreateOfferPM> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferCB.class);

    @FXML private Label buyLabel, confirmationLabel, txTitleLabel, collateralLabel;
    @FXML private ValidatingTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML private Button placeOfferButton, closeButton;
    @FXML private TextField totalToPayTextField, collateralTextField, bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField,
            totalFeesTextField, transactionIdTextField;
    @FXML private ConfidenceProgressIndicator progressIndicator;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferCB(CreateOfferPM presentationModel) {
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
        configTextFieldValidators();
        balanceTextField.setup(presentationModel.getWalletFacade(), presentationModel.address.get());
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        //TODO check that again
        if (parentController != null) ((TradeController) parentController).onCreateOfferViewRemoved();
    }

    @Override
    public void terminate() {
        super.terminate();
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
    public void onPlaceOffer() {
        presentationModel.placeOffer();
    }

    @FXML
    public void onClose() {
        TabPane tabPane = ((TabPane) (root.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {
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

        presentationModel.requestPlaceOfferFailed.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openErrorPopup("Error", "An error occurred when placing the offer.\n" +
                        presentationModel.requestPlaceOfferErrorMessage);
                presentationModel.requestPlaceOfferFailed.set(false);
            }
        });
    }

    private void setupBindings() {
        buyLabel.textProperty().bind(presentationModel.directionLabel);

        amountTextField.textProperty().bindBidirectional(presentationModel.amount);
        minAmountTextField.textProperty().bindBidirectional(presentationModel.minAmount);
        priceTextField.textProperty().bindBidirectional(presentationModel.price);
        volumeTextField.textProperty().bindBidirectional(presentationModel.volume);

        collateralLabel.textProperty().bind(presentationModel.collateralLabel);
        collateralTextField.textProperty().bind(presentationModel.collateral);
        totalToPayTextField.textProperty().bind(presentationModel.totalToPay);
        totalFeesTextField.textProperty().bind(presentationModel.totalFees);

        addressTextField.amountAsCoinProperty().bind(presentationModel.totalToPayAsCoin);
        addressTextField.paymentLabelProperty().bind(presentationModel.paymentLabel);
        addressTextField.addressProperty().bind(presentationModel.addressAsString);

        bankAccountTypeTextField.textProperty().bind(presentationModel.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(presentationModel.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(presentationModel.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(presentationModel.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(presentationModel.acceptedLanguages);
        transactionIdTextField.textProperty().bind(presentationModel.transactionId);

        // Validation
        amountTextField.amountValidationResultProperty().bind(presentationModel.amountValidationResult);
        minAmountTextField.amountValidationResultProperty().bind(presentationModel.minAmountValidationResult);
        priceTextField.amountValidationResultProperty().bind(presentationModel.priceValidationResult);
        volumeTextField.amountValidationResultProperty().bind(presentationModel.volumeValidationResult);

        // buttons
        placeOfferButton.visibleProperty().bind(presentationModel.isPlaceOfferButtonVisible);
        placeOfferButton.disableProperty().bind(presentationModel.isPlaceOfferButtonDisabled);
        closeButton.visibleProperty().bind(presentationModel.isCloseButtonVisible);
    }

    private void configTextFieldValidators() {
        Region referenceNode = (Region) amountTextField.getParent();
        amountTextField.setLayoutReference(referenceNode);
        priceTextField.setLayoutReference(referenceNode);
        volumeTextField.setLayoutReference(referenceNode);
    }
}

