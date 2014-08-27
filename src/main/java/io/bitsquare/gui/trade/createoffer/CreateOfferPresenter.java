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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import com.google.bitcoin.core.Address;
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
 * - Holds data and state of the View (formatting,...)
 * - Receive user input via method calls from CodeBehind.
 * - Validates input, applies business logic and converts input to Model.
 * - Format model data to properties used for binding from the view.
 * - Listen to updates from Model via Bindings.
 * - Is testable
 */
class CreateOfferPresenter {
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
    final StringProperty totalFees = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty acceptedCountries = new SimpleStringProperty();
    final StringProperty acceptedLanguages = new SimpleStringProperty();
    final StringProperty addressAsString = new SimpleStringProperty();
    final StringProperty paymentLabel = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();

    final BooleanProperty isCloseButtonVisible = new SimpleBooleanProperty();
    final BooleanProperty isPlaceOfferButtonVisible = new SimpleBooleanProperty(true);
    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty needsInputValidation = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcFractions = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidFiatDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty requestPlaceOfferFailed = new SimpleBooleanProperty();

    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    CreateOfferPresenter(CreateOfferModel model) {
        this.model = model;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onViewInitialized() {
        totalFees.set(BSFormatter.formatBtc(model.totalFeesAsCoin));
        paymentLabel.set("Bitsquare trade (" + model.getOfferId() + ")");

        if (model.addressEntry != null) {
            addressAsString.set(model.addressEntry.getAddress().toString());
            address.set(model.addressEntry.getAddress());
        }

        setupInputListeners();

        collateralLabel.bind(Bindings.createStringBinding(() -> "Collateral (" + BSFormatter.formatCollateralPercent
                (model.collateralAsLong.get()) + "):", model.collateralAsLong));
        bankAccountType.bind(Bindings.createStringBinding(() -> Localisation.get(model.bankAccountType.get()),
                model.bankAccountType));
        bankAccountCurrency.bind(model.bankAccountCurrency);
        bankAccountCounty.bind(model.bankAccountCounty);
        totalToPayAsCoin.bind(model.totalToPayAsCoin);

        model.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(BSFormatter
                .countryLocalesToString(model.acceptedCountries)));
        model.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(BSFormatter
                .languageLocalesToString(model.acceptedLanguages)));

        isCloseButtonVisible.bind(model.requestPlaceOfferSuccess);
        requestPlaceOfferErrorMessage.bind(model.requestPlaceOfferErrorMessage);
        requestPlaceOfferFailed.bind(model.requestPlaceOfferFailed);
        showTransactionPublishedScreen.bind(model.requestPlaceOfferSuccess);

        model.requestPlaceOfferFailed.addListener((o, oldValue, newValue) -> {
            if (newValue) isPlaceOfferButtonDisabled.set(false);
        });

        model.requestPlaceOfferSuccess.addListener((o, oldValue, newValue) -> {
            if (newValue) isPlaceOfferButtonVisible.set(false);
        });

        // TODO transactionId, 
    }

    void activate() {
        model.activate();
    }

    void deactivate() {
        model.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setOrderBookFilter(OrderBookFilter orderBookFilter) {
        model.setDirection(orderBookFilter.getDirection());
        model.amountAsCoin = orderBookFilter.getAmount();
        model.minAmountAsCoin = orderBookFilter.getAmount();

        // TODO use Fiat in orderBookFilter
        model.priceAsFiat = parseToFiatWith2Decimals(String.valueOf(orderBookFilter.getPrice()));

        directionLabel.set(model.getDirection() == Direction.BUY ? "Buy:" : "Sell:");
        amount.set(formatBtc(model.amountAsCoin));
        minAmount.set(formatBtc(model.minAmountAsCoin));
        price.set(formatFiat(model.priceAsFiat));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // View Events
    ///////////////////////////////////////////////////////////////////////////////////////////

    void placeOffer() {
        model.amountAsCoin = parseToBtcWith4Decimals(amount.get());
        model.minAmountAsCoin = parseToBtcWith4Decimals(minAmount.get());
        model.priceAsFiat = parseToFiatWith2Decimals(price.get());
        model.minAmountAsCoin = parseToBtcWith4Decimals(minAmount.get());

        needsInputValidation.set(true);

        if (inputValid()) {
            model.placeOffer();
            isPlaceOfferButtonDisabled.set(true);
            isPlaceOfferButtonVisible.set(true);
        }
    }

    void close() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setupInputListeners() {

        // bindBidirectional for amount, price, volume and minAmount
        amount.addListener(ov -> {
            model.amountAsCoin = parseToBtcWith4Decimals(amount.get());
            calculateVolume();
            calculateTotalToPay();
            calculateCollateral();
        });

        price.addListener(ov -> {
            model.priceAsFiat = parseToFiatWith2Decimals(price.get());
            calculateVolume();
            calculateTotalToPay();
            calculateCollateral();
        });

        volume.addListener(ov -> {
            model.volumeAsFiat = parseToFiatWith2Decimals(volume.get());
            calculateAmount();
            calculateTotalToPay();
            calculateCollateral();
        });
    }

    void onFocusOutAmountTextField(Boolean oldValue, Boolean newValue) {

        if (oldValue && !newValue) {
            showWarningInvalidBtcDecimalPlaces.set(!hasBtcValidDecimals(amount.get()));
            model.amountAsCoin = parseToBtcWith4Decimals(amount.get());
            amount.set(formatBtc(model.amountAsCoin));
            calculateVolume();
        }
    }

    void onFocusOutMinAmountTextField(Boolean oldValue, Boolean newValue) {

        if (oldValue && !newValue) {
            showWarningInvalidBtcDecimalPlaces.set(!hasBtcValidDecimals(minAmount.get()));
            model.minAmountAsCoin = parseToBtcWith4Decimals(minAmount.get());
            minAmount.set(formatBtc(model.minAmountAsCoin));
        }
    }

     void onFocusOutVolumeTextField(Boolean oldValue, Boolean newValue, String volumeTextFieldText) {
        if (oldValue && !newValue) {
            showWarningInvalidFiatDecimalPlaces.set(!hasFiatValidDecimals(volume.get()));
            model.volumeAsFiat = parseToFiatWith2Decimals(volume.get());
            volume.set(formatFiat(model.volumeAsFiat));
            calculateAmount();

            showWarningInvalidBtcFractions.set(!formatFiat(parseToFiatWith2Decimals(volumeTextFieldText)).equals
                    (volume.get()));
        }
    }

    void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            showWarningInvalidFiatDecimalPlaces.set(!hasFiatValidDecimals(price.get()));
            model.priceAsFiat = parseToFiatWith2Decimals(price.get());
            price.set(formatFiat(model.priceAsFiat));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    WalletFacade getWalletFacade() {
        return model.getWalletFacade();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean inputValid() {
        //TODO
        return true;
    }

    private void calculateVolume() {
        model.amountAsCoin = parseToBtcWith4Decimals(amount.get());
        model.priceAsFiat = parseToFiatWith2Decimals(price.get());

        if (model.priceAsFiat != null && model.amountAsCoin != null && !model.amountAsCoin.isZero()) {
            model.volumeAsFiat = new ExchangeRate(model.priceAsFiat).coinToFiat(model.amountAsCoin);
            volume.set(formatFiat(model.volumeAsFiat));
        }
    }

    private void calculateAmount() {
        model.volumeAsFiat = parseToFiatWith2Decimals(volume.get());
        model.priceAsFiat = parseToFiatWith2Decimals(price.get());

        if (model.volumeAsFiat != null && model.priceAsFiat != null && !model.priceAsFiat.isZero()) {
            model.amountAsCoin = new ExchangeRate(model.priceAsFiat).fiatToCoin(model.volumeAsFiat);

            // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
            model.amountAsCoin = reduceto4Dezimals(model.amountAsCoin);
            amount.set(formatBtc(model.amountAsCoin));
            calculateTotalToPay();
            calculateCollateral();
        }
    }

    private void calculateTotalToPay() {
        calculateCollateral();

        if (model.collateralAsCoin != null) {
            model.totalToPayAsCoin.set(model.collateralAsCoin.add(model.totalFeesAsCoin));
            totalToPay.bind(createStringBinding(() -> formatBtcWithCode(model.totalToPayAsCoin.get()),
                    model.totalToPayAsCoin));
        }
    }

    private void calculateCollateral() {
        if (model.amountAsCoin != null) {
            model.collateralAsCoin = model.amountAsCoin.multiply(model.collateralAsLong.get()).divide(1000);
            collateral.set(BSFormatter.formatBtcWithCode(model.collateralAsCoin));
        }
    }
}
