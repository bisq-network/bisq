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

/**
 * Code behind (FXML Controller is part of View, not a classical MVC controller):
 * <p>
 * Creates Presenter and passes Model from DI to Presenter. Does not hold a reference to Model
 * <p>
 * - Setup binding from Presenter to View elements (also bidirectional - Inputs). Binding are only to presenters
 * properties, not logical bindings or cross-view element bindings.
 * - Listen to UI events (Action) from View and call method in Presenter.
 * - Is entry node for hierarchical view graphs. Passes method calls to Presenter. Calls methods on sub views.
 * - Handle lifecycle and self removal from scene graph.
 * - Non declarative (dynamic) view definitions (if it gets larger, then user a ViewBuilder)
 * - Has no logic and no state, only view elements and a presenter reference!
 * <p>
 * View:
 * - Mostly declared in FXML. Dynamic parts are declared in Controller. If more view elements need to be defined in
 * code then use ViewBuilder.
 * <p>
 * Optional ViewBuilder:
 * - Replacement for FXML view definitions.
 * <p>
 * Note: Don't assign the root node as it is defined in the base class!
 */
public class CreateOfferCodeBehind extends CachedViewController {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferCodeBehind.class);

    private final CreateOfferPresenter presenter;

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
    public CreateOfferCodeBehind(CreateOfferModel model) {
        presenter = new CreateOfferPresenter(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        presenter.onViewInitialized();

        balanceTextField.setup(presenter.getWalletFacade(), presenter.address.get());
    }

    @Override
    public void deactivate() {
        super.deactivate();

        presenter.deactivate();

        ((TradeController) parentController).onCreateOfferViewRemoved();
    }

    @Override
    public void activate() {
        super.activate();

        presenter.activate();

        setupBindings();
        setupListeners();
        setupTextFieldValidators();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter) {
        presenter.setOrderBookFilter(orderBookFilter);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onPlaceOffer() {
        presenter.placeOffer();
    }

    @FXML
    public void onClose() {
        presenter.close();

        TabPane tabPane = ((TabPane) (root.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {
        volumeTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presenter.onFocusOutVolumeTextField(oldValue, newValue, volumeTextField.getText());
            volumeTextField.setText(presenter.volume.get());
        });

        amountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presenter.onFocusOutAmountTextField(oldValue, newValue);
            amountTextField.setText(presenter.amount.get());
        });

        priceTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presenter.onFocusOutPriceTextField(oldValue, newValue);
            priceTextField.setText(presenter.price.get());
        });

        minAmountTextField.focusedProperty().addListener((o, oldValue, newValue) -> {
            presenter.onFocusOutMinAmountTextField(oldValue, newValue);
            minAmountTextField.setText(presenter.minAmount.get());
        });

        presenter.needsInputValidation.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                amountTextField.reValidate();
                minAmountTextField.reValidate();
                volumeTextField.reValidate();
                priceTextField.reValidate();
            }
        });

        presenter.showWarningInvalidBtcDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The amount you have entered exceeds the number of allowed decimal" +
                        " places.\nThe amount has been adjusted to 4 decimal places.");
                presenter.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        presenter.showWarningInvalidFiatDecimalPlaces.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The amount you have entered exceeds the number of allowed decimal" +
                        " places.\nThe amount has been adjusted to 2 decimal places.");
                presenter.showWarningInvalidFiatDecimalPlaces.set(false);
            }
        });

        presenter.showWarningInvalidBtcFractions.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openWarningPopup("Warning", "The total volume you have entered leads to invalid fractional " +
                        "Bitcoin amounts.\nThe amount has been adjusted and a new total volume be calculated from it.");
                presenter.showWarningInvalidBtcFractions.set(false);
                volumeTextField.setText(presenter.volume.get());
            }
        });

        presenter.requestPlaceOfferFailed.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                Popups.openErrorPopup("Error", "An error occurred when placing the offer.\n" +
                        presenter.requestPlaceOfferErrorMessage);
                presenter.requestPlaceOfferFailed.set(false);
            }
        });
    }

    private void setupBindings() {
        buyLabel.textProperty().bind(presenter.directionLabel);
        amountTextField.textProperty().bindBidirectional(presenter.amount);
        priceTextField.textProperty().bindBidirectional(presenter.price);
        volumeTextField.textProperty().bindBidirectional(presenter.volume);

        minAmountTextField.textProperty().bindBidirectional(presenter.minAmount);
        collateralLabel.textProperty().bind(presenter.collateralLabel);
        collateralTextField.textProperty().bind(presenter.collateral);
        totalToPayTextField.textProperty().bind(presenter.totalToPay);

        addressTextField.amountAsCoinProperty().bind(presenter.totalToPayAsCoin);
        addressTextField.paymentLabelProperty().bind(presenter.paymentLabel);
        addressTextField.addressProperty().bind(presenter.addressAsString);

        bankAccountTypeTextField.textProperty().bind(presenter.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(presenter.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(presenter.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(presenter.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(presenter.acceptedLanguages);
        totalFeesTextField.textProperty().bind(presenter.totalFees);
        transactionIdTextField.textProperty().bind(presenter.transactionId);

        placeOfferButton.visibleProperty().bind(presenter.isPlaceOfferButtonVisible);
        placeOfferButton.disableProperty().bind(presenter.isPlaceOfferButtonDisabled);
        closeButton.visibleProperty().bind(presenter.isCloseButtonVisible);

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
                presenter.amount,
                presenter.minAmount,
                amountValidator,
                minAmountValidator);
    }
}

