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
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;

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

class CreateOfferPM {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPM.class);

    private CreateOfferModel model;
    private BtcValidator btcValidator = new BtcValidator();
    private FiatValidator fiatValidator = new FiatValidator();

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
    final BooleanProperty showWarningAdjustedVolume = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidFiatDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty requestPlaceOfferFailed = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    // That is needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();

    final ObjectProperty<Address> address = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    CreateOfferPM(CreateOfferModel model) {
        this.model = model;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onViewInitialized() {
        // todo move to contr.

        // static
        paymentLabel.set("Bitsquare trade (" + model.getOfferId() + ")");

        if (model.addressEntry != null) {
            addressAsString.set(model.addressEntry.getAddress().toString());
            address.set(model.addressEntry.getAddress());
        }

        setupModelBindings();
        setupUIInputListeners();

        // TODO transactionId, 
    }

    void activate() {
        //TODO handle in base class
        model.activate();
    }


    void deactivate() {
        //TODO handle in base class
        model.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API methods (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setOrderBookFilter(OrderBookFilter orderBookFilter) {
        model.setDirection(orderBookFilter.getDirection());
        directionLabel.set(model.getDirection() == Direction.BUY ? "Buy:" : "Sell:");

        if (orderBookFilter.getAmount() != null && isBtcInputValid(orderBookFilter.getAmount().toPlainString())
                .isValid) {
            model.amountAsCoin.set(orderBookFilter.getAmount());
            model.minAmountAsCoin.set(orderBookFilter.getAmount());
        }

        // TODO use Fiat in orderBookFilter
        if (orderBookFilter.getPrice() != 0 && isBtcInputValid(String.valueOf(orderBookFilter.getPrice())).isValid)
            model.priceAsFiat.set(parseToFiatWith2Decimals(String.valueOf(orderBookFilter.getPrice())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    void placeOffer() {
        model.placeOffer();
        isPlaceOfferButtonDisabled.set(true);
        isPlaceOfferButtonVisible.set(true);
    }

    void close() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI events (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    // when focus out we do validation and apply the data to the model 

    void onFocusOutAmountTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            boolean isValid = result.isValid;
            amountValidationResult.set(result);
            if (isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!hasBtcValidDecimals(amount.get()));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input to general btc format
                calculateVolume();

                if (!model.isMinAmountLessOrEqualAmount()) {
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            "Amount cannot be smaller than minimum amount."));
                }
                else {
                    amountValidationResult.set(result);
                    if (minAmount.get() != null)
                        minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
                }
            }
        }
    }

    void onFocusOutMinAmountTextField(Boolean oldValue, Boolean newValue) {

        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            boolean isValid = result.isValid;
            minAmountValidationResult.set(result);
            if (isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!hasBtcValidDecimals(minAmount.get()));
                setMinAmountToModel();

                if (!model.isMinAmountLessOrEqualAmount()) {
                    minAmountValidationResult.set(new InputValidator.ValidationResult(false,
                            "Minimum amount cannot be larger than amount."));
                }
                else {
                    minAmountValidationResult.set(result);
                    if (amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(amount.get()));
                }
            }
        }
    }

    void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isFiatInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!hasFiatValidDecimals(price.get()));
                setPriceToModel();

                calculateVolume();
            }
        }
    }

    void onFocusOutVolumeTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(volume.get());
            boolean isValid = result.isValid;
            volumeValidationResult.set(result);
            if (isValid) {
                String origVolume = volume.get();
                showWarningInvalidFiatDecimalPlaces.set(!hasFiatValidDecimals(volume.get()));
                setVolumeToModel();

                calculateAmount();

                // must be after calculateAmount (btc value has been adjusted in case the calculation leads to 
                // invalid decimal places for the amount value
                showWarningAdjustedVolume.set(!formatFiat(parseToFiatWith2Decimals(origVolume)).equals(volume.get()));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    WalletFacade getWalletFacade() {
        return model.getWalletFacade();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupUIInputListeners() {

        // bindBidirectional for amount, price, volume and minAmount
        // We do volume/amount calculation during input
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                model.amountAsCoin.set(parseToCoinWith4Decimals(newValue));
                calculateVolume();
                model.calculateTotalToPay();
                model.calculateCollateral();
            }
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                model.priceAsFiat.set(parseToFiatWith2Decimals(newValue));
                calculateVolume();
                model.calculateTotalToPay();
                model.calculateCollateral();
            }
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                model.volumeAsFiat.set(parseToFiatWith2Decimals(newValue));
                setVolumeToModel();
                setPriceToModel();
                model.calculateAmount();
                model.calculateTotalToPay();
                model.calculateCollateral();
            }
        });
    }

    private void setupModelBindings() {

        amount.bind(Bindings.createObjectBinding(() -> formatCoin(model.amountAsCoin.get()), model.amountAsCoin));
        minAmount.bind(Bindings.createObjectBinding(() -> formatCoin(model.minAmountAsCoin.get()),
                model.minAmountAsCoin));
        price.bind(Bindings.createObjectBinding(() -> formatFiat(model.priceAsFiat.get()), model.priceAsFiat));
        volume.bind(Bindings.createObjectBinding(() -> formatFiat(model.volumeAsFiat.get()), model.volumeAsFiat));

        totalToPay.bind(createStringBinding(() -> formatCoinWithCode(model.totalToPayAsCoin.get()),
                model.totalToPayAsCoin));
        collateral.bind(createStringBinding(() -> formatCoinWithCode(model.collateralAsCoin.get()),
                model.collateralAsCoin));

        collateralLabel.bind(Bindings.createStringBinding(() -> "Collateral (" + BSFormatter.formatCollateralPercent
                (model.collateralAsLong.get()) + "):", model.collateralAsLong));
        totalToPayAsCoin.bind(model.totalToPayAsCoin);

        bankAccountType.bind(Bindings.createStringBinding(() -> Localisation.get(model.bankAccountType.get()),
                model.bankAccountType));
        bankAccountCurrency.bind(model.bankAccountCurrency);
        bankAccountCounty.bind(model.bankAccountCounty);

        // ObservableLists
        model.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(BSFormatter
                .countryLocalesToString(model.acceptedCountries)));
        model.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(BSFormatter
                .languageLocalesToString(model.acceptedLanguages)));

        isCloseButtonVisible.bind(model.requestPlaceOfferSuccess);
        requestPlaceOfferErrorMessage.bind(model.requestPlaceOfferErrorMessage);
        requestPlaceOfferFailed.bind(model.requestPlaceOfferFailed);
        showTransactionPublishedScreen.bind(model.requestPlaceOfferSuccess);


        amount.bind(Bindings.createObjectBinding(() -> formatCoin(model.amountAsCoin.get()), model.amountAsCoin));

        isPlaceOfferButtonDisabled.bind(Bindings.createBooleanBinding(() -> !model.requestPlaceOfferFailed.get(),
                model.requestPlaceOfferFailed));

        isPlaceOfferButtonVisible.bind(Bindings.createBooleanBinding(() -> !model.requestPlaceOfferSuccess.get(),
                model.requestPlaceOfferSuccess));

       /* model.requestPlaceOfferFailed.addListener((o, oldValue, newValue) -> {
            if (newValue) isPlaceOfferButtonDisabled.set(false);
        });

        model.requestPlaceOfferSuccess.addListener((o, oldValue, newValue) -> {
            if (newValue) isPlaceOfferButtonVisible.set(false);
        });*/
    }


    private void calculateVolume() {
        setAmountToModel();
        setPriceToModel();
        model.calculateVolume();
    }

    private void calculateAmount() {
        setVolumeToModel();
        setPriceToModel();
        model.calculateAmount();

        if (!model.isMinAmountLessOrEqualAmount()) {
            amountValidationResult.set(new InputValidator.ValidationResult(false,
                    "Amount cannot be smaller than minimum amount."));
        }
        else {
            if (amount.get() != null)
                amountValidationResult.set(isBtcInputValid(amount.get()));
            if (minAmount.get() != null)
                minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
        }
    }

    private void setAmountToModel() {
        model.amountAsCoin.set(parseToCoinWith4Decimals(amount.get()));
    }

    private void setMinAmountToModel() {
        model.minAmountAsCoin.set(parseToCoinWith4Decimals(minAmount.get()));
    }

    private void setPriceToModel() {
        model.priceAsFiat.set(parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        model.volumeAsFiat.set(parseToFiatWith2Decimals(volume.get()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope for testing
    ///////////////////////////////////////////////////////////////////////////////////////////

    InputValidator.ValidationResult isBtcInputValid(String input) {

        return btcValidator.validate(input);
    }

    InputValidator.ValidationResult isFiatInputValid(String input) {

        return fiatValidator.validate(input);
    }


}
