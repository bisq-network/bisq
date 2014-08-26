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

import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.ExchangeRate;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.BSFormatter.*;
import static javafx.beans.binding.Bindings.createStringBinding;

/**
 * Presenter:
 * Knows Model, does not know the View (CodeBehind)
 * <p>
 * - Holds data and state of the View (formatted)
 * - Receive view input from Controller. Validates input, apply business logic, format to Presenter properties and convert input to Model.
 * - Listen to updates from Model, apply business logic and format it to Presenter properties. Model update handling can be done via Binding.
 */
class CreateOfferPresenter
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPresenter.class);

    private CreateOfferModel model;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty collateral = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty directionLabel = new SimpleStringProperty();
    final StringProperty collateralLabel = new SimpleStringProperty();
    final StringProperty totalFeesLabel = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty acceptedCountries = new SimpleStringProperty();
    final StringProperty acceptedLanguages = new SimpleStringProperty();
    final StringProperty address = new SimpleStringProperty();
    final StringProperty paymentLabel = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final BooleanProperty isOfferPlacedScreen = new SimpleBooleanProperty();
    final BooleanProperty placeOfferButtonVisible = new SimpleBooleanProperty(true);
    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty validateInput = new SimpleBooleanProperty();
    final BooleanProperty showVolumeAdjustedWarning = new SimpleBooleanProperty();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    CreateOfferPresenter(CreateOfferModel model)
    {
        this.model = model;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onViewInitialized()
    {
        totalFeesLabel.set(BSFormatter.formatBtc(model.totalFeesAsCoin));
        paymentLabel.set("Bitsquare trade (" + model.getOfferId() + ")");
       // address.set(model.addressEntry.getAddress().toString());

        setupInputListeners();

        collateralLabel.bind(Bindings.createStringBinding(() -> "Collateral (" + BSFormatter.formatCollateralPercent(model.collateralAsLong.get()) + "):", model.collateralAsLong));
        bankAccountType.bind(Bindings.createStringBinding(() -> Localisation.get(model.bankAccountType.get()), model.bankAccountType));
        bankAccountCurrency.bind(model.bankAccountCurrency);
        bankAccountCounty.bind(model.bankAccountCounty);
        totalToPayAsCoin.bind(model.totalToPayAsCoin);
        
        model.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(BSFormatter.countryLocalesToString(model.acceptedCountries)));
        model.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(BSFormatter.languageLocalesToString(model.acceptedLanguages)));

    }

    void deactivate()
    {
    }

    void activate()
    {
        model.activate();


       
        // totalToPay.addListener((ov) -> addressTextField.setAmountToPay(model.totalToPayAsCoin));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    void setOrderBookFilter(OrderBookFilter orderBookFilter)
    {
        // model
        model.setDirection(orderBookFilter.getDirection());
        model.amountAsCoin = orderBookFilter.getAmount();
        model.minAmountAsCoin = orderBookFilter.getAmount();
        //TODO
        model.priceAsFiat = parseToFiat(String.valueOf(orderBookFilter.getPrice()));

        // view props
        directionLabel.set(model.getDirection() == Direction.BUY ? "Buy:" : "Sell:");
        amount.set(formatBtc(model.amountAsCoin));
        minAmount.set(formatBtc(model.minAmountAsCoin));
        price.set(formatFiat(model.priceAsFiat));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // View Events
    ///////////////////////////////////////////////////////////////////////////////////////////

    void placeOffer()
    {
        model.amountAsCoin = parseToCoin(amount.get());
        model.minAmountAsCoin = parseToCoin(minAmount.get());
        model.priceAsFiat = parseToFiat(price.get());
        model.minAmountAsCoin = parseToCoin(minAmount.get());

        validateInput.set(true);

        //balanceTextField.getBalance()

        if (inputValid())
        {
            model.placeOffer();
            isPlaceOfferButtonDisabled.set(true);
            placeOfferButtonVisible.set(true);

        }
        
    /*
    {
                                               isOfferPlacedScreen.set(true);
                                               transactionId.set(transaction.getHashAsString());
                                           }
                                           errorMessage -> {
                                               Popups.openErrorPopup("An error occurred", errorMessage);
                                               isPlaceOfferButtonDisabled.set(false);
                                           }
     */
    }


    void close()
    {

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // 
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean inputValid()
    {
        //TODO
        return true;
    }

    void setupInputListeners()
    {

        // bindBidirectional for amount, price, volume and minAmount
        amount.addListener(ov -> {
            model.amountAsCoin = parseToCoin(amount.get());
            setVolume();
            setTotalToPay();
            setCollateral();
        });

        price.addListener(ov -> {
            model.priceAsFiat = parseToFiat(price.get());
            setVolume();
            setTotalToPay();
            setCollateral();
        });

        volume.addListener(ov -> {
            model.tradeVolumeAsFiat = parseToFiat(volume.get());
            setAmount();
            setTotalToPay();
            setCollateral();
        });
    }


    private void setVolume()
    {
        model.amountAsCoin = parseToCoin(amount.get());
        model.priceAsFiat = parseToFiat(price.get());

        if (model.priceAsFiat != null && model.amountAsCoin != null && !model.amountAsCoin.isZero())
        {
            model.tradeVolumeAsFiat = new ExchangeRate(model.priceAsFiat).coinToFiat(model.amountAsCoin);
            volume.set(formatFiat(model.tradeVolumeAsFiat));
        }
    }

    private void setAmount()
    {
        model.tradeVolumeAsFiat = parseToFiat(volume.get());
        model.priceAsFiat = parseToFiat(price.get());

        if (model.tradeVolumeAsFiat != null && model.priceAsFiat != null && !model.priceAsFiat.isZero())
        {
            model.amountAsCoin = new ExchangeRate(model.priceAsFiat).fiatToCoin(model.tradeVolumeAsFiat);

            // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
            model.amountAsCoin = applyFormatRules(model.amountAsCoin);
            amount.set(formatBtc(model.amountAsCoin));
            setTotalToPay();
            setCollateral();
        }
    }

    private void setTotalToPay()
    {
        setCollateral();

        if (model.collateralAsCoin != null)
        {
            model.totalToPayAsCoin.set(model.collateralAsCoin.add(model.totalFeesAsCoin));
            totalToPay.bind(createStringBinding(() -> formatBtcWithCode(model.totalToPayAsCoin.get()), model.totalToPayAsCoin));
        }
    }

    private void setCollateral()
    {
        if (model.amountAsCoin != null)
        {
            model.collateralAsCoin = model.amountAsCoin.multiply(model.collateralAsLong.get()).divide(1000);
            collateral.set(BSFormatter.formatBtcWithCode(model.collateralAsCoin));
        }
    }

    // We adjust the volume if fractional coins result from volume/price division on focus out
    void checkVolumeOnFocusOut(Boolean oldValue, Boolean newValue, String volumeTextFieldText)
    {
        if (oldValue && !newValue)
        {
            setVolume();
            if (!formatFiat(parseToFiat(volumeTextFieldText)).equals(volume.get()))
                showVolumeAdjustedWarning.set(true);
        }


        //
        // only on focus out and ignore focus loss from window
       /* if (!newValue && volumeTextField.getScene() != null && volumeTextField.getScene().getWindow().isFocused())
            amountTextField.reValidate();*/
    }

    void onFocusOutAmountTextField(Boolean oldValue, Boolean newValue)
    {
        // only on focus out and ignore focus loss from window
       /* if (!newValue && amountTextField.getScene() != null && amountTextField.getScene().getWindow().isFocused())
            volumeTextField.reValidate();*/
    }

    void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue)
    {
        // only on focus out and ignore focus loss from window
      /*  if (!newValue && priceTextField.getScene() != null && priceTextField.getScene().getWindow().isFocused())
            volumeTextField.reValidate();*/
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
