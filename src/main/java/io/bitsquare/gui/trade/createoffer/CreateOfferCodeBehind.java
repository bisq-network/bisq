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
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO check DI

/**
 * Code behind (FXML Controller is part of View, not a classical controller from MVC):
 * <p>
 * Creates Presenter and passes Model from DI to Presenter. Does not hold a reference to Model
 * <p>
 * - Setup binding from Presenter to View elements (also bidirectional - Inputs). Binding are only to presenters properties, not logical bindings or cross-view element bindings.
 * - Listen to UI events (Action) from View and call method in Presenter.
 * - Is entry node for hierarchical view graphs. Passes method calls to Presenter. Calls methods on sub views.
 * - Handle lifecycle and self removal from scene graph.
 * - Non declarative (dynamic) view definitions (if it gets larger, then user a ViewBuilder)
 * <p>
 * View:
 * - Mostly declared in FXML. Dynamic parts are declared in Controller. If more view elements need to be defined in code then use ViewBuilder.
 * <p>
 * Optional ViewBuilder:
 * - Replacement for FXML view definitions.
 */
public class CreateOfferCodeBehind extends CachedViewController
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferCodeBehind.class);

    private final CreateOfferPresenter presenter;

    @FXML private AnchorPane rootContainer;
    @FXML private Label buyLabel, confirmationLabel, txTitleLabel, collateralLabel;

    @FXML private ValidatingTextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML private Button placeOfferButton, closeButton;
    @FXML private TextField totalToPayTextField, collateralTextField, bankAccountTypeTextField, bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField, acceptedLanguagesTextField,
            feeLabel, transactionIdTextField;
    @FXML private ConfidenceProgressIndicator progressIndicator;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferCodeBehind(CreateOfferModel model)
    {
        presenter = new CreateOfferPresenter(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);
        presenter.onViewInitialized();
    }

    @Override
    public void deactivate()
    {
        super.deactivate();
        presenter.deactivate();
        ((TradeController) parentController).onCreateOfferViewRemoved();
    }

    @Override
    public void activate()
    {
        super.activate();
        presenter.activate();

        setupBindings();
        setupListeners();
        setupTextFieldValidators();


        //addressTextField.setAddress(addressEntry.getAddress().toString());
        //addressTextField.setPaymentLabel("Bitsquare trade (" + offerId + ")");

        // balanceTextField.setAddress(addressEntry.getAddress());
        //TODO  balanceTextField.setWalletFacade(walletFacade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        presenter.setOrderBookFilter(orderBookFilter);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onPlaceOffer()
    {
        presenter.placeOffer();
    }

    @FXML
    public void onClose()
    {
        presenter.close();

        TabPane tabPane = ((TabPane) (rootContainer.getParent().getParent()));
        tabPane.getTabs().remove(tabPane.getSelectionModel().getSelectedItem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners()
    {
        volumeTextField.focusedProperty().addListener((observableValue, oldValue, newValue) -> presenter.checkVolumeOnFocusOut(oldValue, newValue, volumeTextField.getText()));
        amountTextField.focusedProperty().addListener((observableValue, oldValue, newValue) -> presenter.onFocusOutAmountTextField(oldValue, newValue));
        priceTextField.focusedProperty().addListener((observableValue, oldValue, newValue) -> presenter.onFocusOutPriceTextField(oldValue, newValue));

        presenter.validateInput.addListener((o, oldValue, newValue) -> {
            if (newValue)
            {
                amountTextField.reValidate();
                minAmountTextField.reValidate();
                volumeTextField.reValidate();
                priceTextField.reValidate();
            }
        });

        presenter.showVolumeAdjustedWarning.addListener((o, oldValue, newValue) -> {
            if (newValue)
            {
                Popups.openWarningPopup("Warning", "The total volume you have entered leads to invalid fractional Bitcoin amounts.\nThe amount has been adjusted and a new total volume be calculated from it.");
                volumeTextField.setText(presenter.volume.get());
            }
        });
    }

    private void setupBindings()
    {
        buyLabel.textProperty().bind(presenter.directionLabel);
        amountTextField.textProperty().bindBidirectional(presenter.amount);
        priceTextField.textProperty().bindBidirectional(presenter.price);
        volumeTextField.textProperty().bindBidirectional(presenter.volume);

        minAmountTextField.textProperty().bindBidirectional(presenter.minAmount);
        collateralLabel.textProperty().bind(presenter.collateralLabel);
        collateralTextField.textProperty().bind(presenter.collateral);
        totalToPayTextField.textProperty().bind(presenter.totalToPay);

        bankAccountTypeTextField.textProperty().bind(presenter.bankAccountType);
        bankAccountCurrencyTextField.textProperty().bind(presenter.bankAccountCurrency);
        bankAccountCountyTextField.textProperty().bind(presenter.bankAccountCounty);

        acceptedCountriesTextField.textProperty().bind(presenter.acceptedCountries);
        acceptedLanguagesTextField.textProperty().bind(presenter.acceptedLanguages);
        feeLabel.textProperty().bind(presenter.totalFeesLabel);
        transactionIdTextField.textProperty().bind(presenter.transactionId);

        placeOfferButton.visibleProperty().bind(presenter.placeOfferButtonVisible);
        closeButton.visibleProperty().bind(presenter.isOfferPlacedScreen);

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

    private void setupTextFieldValidators()
    {
       /* BtcValidator amountValidator = new BtcValidator();
        amountTextField.setNumberValidator(amountValidator);
        amountTextField.setErrorPopupLayoutReference((Region) amountTextField.getParent());

        priceTextField.setNumberValidator(new FiatValidator());
        priceTextField.setErrorPopupLayoutReference((Region) amountTextField.getParent());

        BtcValidator volumeValidator = new BtcValidator();
        volumeTextField.setNumberValidator(volumeValidator);
        volumeTextField.setErrorPopupLayoutReference((Region) volumeTextField.getParent());

        BtcValidator minAmountValidator = new BtcValidator();
        minAmountTextField.setNumberValidator(minAmountValidator);

        ValidationHelper.setupMinAmountInRangeOfAmountValidation(amountTextField,
                                                                 minAmountTextField,
                                                                 presenter.amount,
                                                                 presenter.minAmount,
                                                                 amountValidator,
                                                                 minAmountValidator);*/
    }
  /*
    private void setVolume()
    {
        amountAsCoin = parseToCoin(presenter.amount.get());
        priceAsFiat = parseToFiat(presenter.price.get());

        if (priceAsFiat != null && amountAsCoin != null)
        {
            tradeVolumeAsFiat = new ExchangeRate(priceAsFiat).coinToFiat(amountAsCoin);
            presenter.volume.set(formatFiat(tradeVolumeAsFiat));
        }
    }

  private void setAmount()
    {
        tradeVolumeAsFiat = parseToFiat(presenter.volume.get());
        priceAsFiat = parseToFiat(presenter.price.get());

        if (tradeVolumeAsFiat != null && priceAsFiat != null && !priceAsFiat.isZero())
        {
            amountAsCoin = new ExchangeRate(priceAsFiat).fiatToCoin(tradeVolumeAsFiat);

            // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
            amountAsCoin = parseToCoin(formatBtc(amountAsCoin));

            presenter.amount.set(formatBtc(amountAsCoin));
            setTotalToPay();
            setCollateral();
        }
    }

    private void setTotalToPay()
    {
        setCollateral();

        totalFeesAsCoin = FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE);

        if (collateralAsCoin != null)
        {
            totalToPayAsCoin = collateralAsCoin.add(totalFeesAsCoin);
            presenter.totalToPay.set(formatBtcWithCode(totalToPayAsCoin));
        }
    }

    private void setCollateral()
    {
        if (amountAsCoin != null)
        {
            collateralAsCoin = amountAsCoin.multiply(collateralAsLong).divide(1000);
            presenter.collateral.set(BSFormatter.formatBtcWithCode(collateralAsCoin));
        }
    }*/
}

   