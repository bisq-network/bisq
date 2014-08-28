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

import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.ValidatingTextField;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.trade.TradeController;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.ValidationHelper;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferCB extends CachedViewController {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferCB.class);

    private final CreateOfferPM pm;

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

    //TODO find a better solution, handle at base class?
    @Inject
    public CreateOfferCB(CreateOfferModel model) {
        pm = new CreateOfferPM(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        pm.onViewInitialized();

        balanceTextField.setup(pm.getWalletFacade(), pm.address.get());
    }

    @Override
    public void deactivate() {
        super.deactivate();

        pm.deactivate();

        //TODO check that again
        ((TradeController) parentController).onCreateOfferViewRemoved();
    }

    @Override
    public void activate() {
        super.activate();

        pm.activate();

        setupBindings();
        setupListeners();
        setupTextFieldValidators();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter) {
        pm.setOrderBookFilter(orderBookFilter);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onPlaceOffer() {
        pm.placeOffer();
    }

    @FXML
    public void onClose() {
        pm.close();

        TabPane tabPane = ((TabPane) (root.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {
        volumeTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            pm.onFocusOutVolumeTextField(oldValue, newValue, volumeTextField.getText());
            volumeTextField.setText(pm.volume.get());
        });

        amountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            pm.onFocusOutAmountTextField(oldValue, newValue);
            amountTextField.setText(pm.amount.get());
        });

        priceTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            pm.onFocusOutPriceTextField(oldValue, newValue);
            priceTextField.setText(pm.price.get());
        });

        minAmountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            pm.onFocusOutMinAmountTextField(oldValue, newValue);
            minAmountTextField.setText(pm.minAmount.get());
        });

        pm.needsInputValidation.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                amountTextField.reValidate();
                minAmountTextField.reValidate();
                volumeTextField.reValidate();
                priceTextField.reValidate();
            }
        });

        pm.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The amount you have entered exceeds the number of allowed decimal" +
                        " places.\nThe amount has been adjusted to 4 decimal places.");
                pm.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        pm.showWarningInvalidFiatDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The amount you have entered exceeds the number of allowed decimal" +
                        " places.\nThe amount has been adjusted to 2 decimal places.");
                pm.showWarningInvalidFiatDecimalPlaces.set(false);
            }
        });

        pm.showWarningInvalidBtcFractions.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The total volume you have entered leads to invalid fractional " +
                        "Bitcoin amounts.\nThe amount has been adjusted and a new total volume be calculated from it.");
                pm.showWarningInvalidBtcFractions.set(false);
                volumeTextField.setText(pm.volume.get());
            }
        });

        pm.requestPlaceOfferFailed.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openErrorPopup("Error", "An error occurred when placing the offer.\n" +
                        pm.requestPlaceOfferErrorMessage);
                pm.requestPlaceOfferFailed.set(false);
            }
        });
    }

    private void setupBindings() {
        buyLabel.textProperty().bind(pm.directionLabel);
        amountTextField.textProperty().bindBidirectional(pm.amount);
        priceTextField.textProperty().bindBidirectional(pm.price);
        volumeTextField.textProperty().bindBidirectional(pm.volume);

        minAmountTextField.textProperty().bindBidirectional(pm.minAmount);
        collateralLabel.textProperty().bind(pm.collateralLabel);
        collateralTextField.textProperty().bind(pm.collateral);
        totalToPayTextField.textProperty().bind(pm.totalToPay);

        addressTextField.amountAsCoinProperty().bind(pm.totalToPayAsCoin);
        addressTextField.paymentLabelProperty().bind(pm.paymentLabel);
        addressTextField.addressProperty().bind(pm.addressAsString);

        bankAccountTypeTextField.textProperty().bind(pm.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(pm.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(pm.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(pm.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(pm.acceptedLanguages);
        totalFeesTextField.textProperty().bind(pm.totalFees);
        transactionIdTextField.textProperty().bind(pm.transactionId);

        placeOfferButton.visibleProperty().bind(pm.isPlaceOfferButtonVisible);
        placeOfferButton.disableProperty().bind(pm.isPlaceOfferButtonDisabled);
        closeButton.visibleProperty().bind(pm.isCloseButtonVisible);

        //TODO
       /* progressIndicator.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        confirmationLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        txTitleLabel.visibleProperty().bind(viewModel.isOfferPlacedScreen);
        transactionIdTextField.visibleProperty().bind(viewModel.isOfferPlacedScreen);
       */

        // TODO
       /* placeOfferButton.disableProperty().bind(amountTextField.isValidProperty()
                                                               .and(minAmountTextField.isValidProperty())
                                                               .and(volumeTextField.isValidProperty())
                                                               .and(priceTextField.isValidProperty()).not());*/
    }

    private void setupTextFieldValidators() {
        Region referenceNode = (Region) amountTextField.getParent();

        BtcValidator amountValidator = new BtcValidator();
        amountTextField.setValidator(amountValidator);
        amountTextField.setErrorPopupLayoutReference(referenceNode);

        priceTextField.setValidator(new FiatValidator());
        priceTextField.setErrorPopupLayoutReference(referenceNode);

        volumeTextField.setValidator(new FiatValidator());
        volumeTextField.setErrorPopupLayoutReference(referenceNode);

        BtcValidator minAmountValidator = new BtcValidator();
        minAmountTextField.setValidator(minAmountValidator);

        ValidationHelper.setupMinAmountInRangeOfAmountValidation(amountTextField,
                minAmountTextField,
                pm.amount,
                pm.minAmount,
                amountValidator,
                minAmountValidator);
    }
}

