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
import io.bitsquare.gui.PresentationModel;
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

class CreateOfferPM extends PresentationModel<CreateOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPM.class);


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
        super(model);

        paymentLabel.setValue("Bitsquare trade (" + model().getOfferId() + ")");

        if (model().addressEntry != null) {
            addressAsString.setValue(model().addressEntry.getAddress().toString());
            address.setValue(model().addressEntry.getAddress());
        }

        setupModelBindings();
        setupUIInputListeners();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialized() {
        super.initialized();
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API methods (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setOrderBookFilter(OrderBookFilter orderBookFilter) {
        model().setDirection(orderBookFilter.getDirection());
        directionLabel.setValue(model().getDirection() == Direction.BUY ? "Buy:" : "Sell:");

        if (orderBookFilter.getAmount() != null && isBtcInputValid(orderBookFilter.getAmount().toPlainString())
                .isValid) {
            model().amountAsCoin.setValue(orderBookFilter.getAmount());
            model().minAmountAsCoin.setValue(orderBookFilter.getAmount());
        }

        // TODO use Fiat in orderBookFilter
        if (orderBookFilter.getPrice() != 0 && isBtcInputValid(String.valueOf(orderBookFilter.getPrice())).isValid)
            model().priceAsFiat.setValue(parseToFiatWith2Decimals(String.valueOf(orderBookFilter.getPrice())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    void placeOffer() {
        model().placeOffer();
        isPlaceOfferButtonDisabled.setValue(true);
        isPlaceOfferButtonVisible.setValue(true);
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
            amountValidationResult.setValue(result);
            if (isValid) {
                showWarningInvalidBtcDecimalPlaces.setValue(!hasBtcValidDecimals(amount.get()));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input to general btc format
                calculateVolume();

                if (!model().isMinAmountLessOrEqualAmount()) {
                    amountValidationResult.setValue(new InputValidator.ValidationResult(false,
                            "Amount cannot be smaller than minimum amount."));
                }
                else {
                    amountValidationResult.setValue(result);
                    if (minAmount.get() != null)
                        minAmountValidationResult.setValue(isBtcInputValid(minAmount.get()));
                }
            }
        }
    }

    void onFocusOutMinAmountTextField(Boolean oldValue, Boolean newValue) {

        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            boolean isValid = result.isValid;
            minAmountValidationResult.setValue(result);
            if (isValid) {
                showWarningInvalidBtcDecimalPlaces.setValue(!hasBtcValidDecimals(minAmount.get()));
                setMinAmountToModel();

                if (!model().isMinAmountLessOrEqualAmount()) {
                    minAmountValidationResult.setValue(new InputValidator.ValidationResult(false,
                            "Minimum amount cannot be larger than amount."));
                }
                else {
                    minAmountValidationResult.setValue(result);
                    if (amount.get() != null)
                        amountValidationResult.setValue(isBtcInputValid(amount.get()));
                }
            }
        }
    }

    void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isFiatInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.setValue(result);
            if (isValid) {
                showWarningInvalidFiatDecimalPlaces.setValue(!hasFiatValidDecimals(price.get()));
                setPriceToModel();

                calculateVolume();
            }
        }
    }

    void onFocusOutVolumeTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(volume.get());
            boolean isValid = result.isValid;
            volumeValidationResult.setValue(result);
            if (isValid) {
                String origVolume = volume.get();
                showWarningInvalidFiatDecimalPlaces.setValue(!hasFiatValidDecimals(volume.get()));
                setVolumeToModel();

                calculateAmount();

                // must be after calculateAmount (btc value has been adjusted in case the calculation leads to 
                // invalid decimal places for the amount value
                showWarningAdjustedVolume.setValue(!formatFiat(parseToFiatWith2Decimals(origVolume)).equals(volume
                        .get()));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    WalletFacade getWalletFacade() {
        return model().getWalletFacade();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupUIInputListeners() {

        // bindBidirectional for amount, price, volume and minAmount
        // We do volume/amount calculation during input
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                model().amountAsCoin.setValue(parseToCoinWith4Decimals(newValue));
                calculateVolume();
                model().calculateTotalToPay();
                model().calculateCollateral();
            }
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                model().priceAsFiat.setValue(parseToFiatWith2Decimals(newValue));
                calculateVolume();
                model().calculateTotalToPay();
                model().calculateCollateral();
            }
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                model().volumeAsFiat.setValue(parseToFiatWith2Decimals(newValue));
                setVolumeToModel();
                setPriceToModel();
                model().calculateAmount();
                model().calculateTotalToPay();
                model().calculateCollateral();
            }
        });

        // Binding with Bindings.createObjectBinding does not work becaue of bi-directional binding in CB
        model().amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatCoin(newValue)));
        model().minAmountAsCoin.addListener((ov, oldValue, newValue) -> minAmount.set(formatCoin(newValue)));
        model().priceAsFiat.addListener((ov, oldValue, newValue) -> price.set(formatFiat(newValue)));
        model().volumeAsFiat.addListener((ov, oldValue, newValue) -> volume.set(formatFiat(newValue)));
    }

    private void setupModelBindings() {
        totalToPay.bind(createStringBinding(() -> formatCoinWithCode(model().totalToPayAsCoin.get()),
                model().totalToPayAsCoin));
        collateral.bind(createStringBinding(() -> formatCoinWithCode(model().collateralAsCoin.get()),
                model().collateralAsCoin));

        collateralLabel.bind(Bindings.createStringBinding(() -> "Collateral (" + BSFormatter.formatCollateralPercent
                (model().collateralAsLong.get()) + "):", model().collateralAsLong));
        totalToPayAsCoin.bind(model().totalToPayAsCoin);

        bankAccountType.bind(Bindings.createStringBinding(() -> Localisation.get(model().bankAccountType.get()),
                model().bankAccountType));
        bankAccountCurrency.bind(model().bankAccountCurrency);
        bankAccountCounty.bind(model().bankAccountCounty);

        // ObservableLists
        model().acceptedCountries.addListener((Observable o) -> acceptedCountries.setValue(BSFormatter
                .countryLocalesToString(model().acceptedCountries)));
        model().acceptedLanguages.addListener((Observable o) -> acceptedLanguages.setValue(BSFormatter
                .languageLocalesToString(model().acceptedLanguages)));

        isCloseButtonVisible.bind(model().requestPlaceOfferSuccess);
        requestPlaceOfferErrorMessage.bind(model().requestPlaceOfferErrorMessage);
        requestPlaceOfferFailed.bind(model().requestPlaceOfferFailed);
        showTransactionPublishedScreen.bind(model().requestPlaceOfferSuccess);

        isPlaceOfferButtonDisabled.bind(Bindings.createBooleanBinding(() -> !model().requestPlaceOfferFailed.get(),
                model().requestPlaceOfferFailed));

        isPlaceOfferButtonVisible.bind(Bindings.createBooleanBinding(() -> !model().requestPlaceOfferSuccess.get(),
                model().requestPlaceOfferSuccess));
    }


    private void calculateVolume() {
        setAmountToModel();
        setPriceToModel();
        model().calculateVolume();
    }

    private void calculateAmount() {
        setVolumeToModel();
        setPriceToModel();
        model().calculateAmount();

        if (!model().isMinAmountLessOrEqualAmount()) {
            amountValidationResult.setValue(new InputValidator.ValidationResult(false,
                    "Amount cannot be smaller than minimum amount."));
        }
        else {
            if (amount.get() != null)
                amountValidationResult.setValue(isBtcInputValid(amount.get()));
            if (minAmount.get() != null)
                minAmountValidationResult.setValue(isBtcInputValid(minAmount.get()));
        }
    }

    private void setAmountToModel() {
        model().amountAsCoin.setValue(parseToCoinWith4Decimals(amount.get()));
    }

    private void setMinAmountToModel() {
        model().minAmountAsCoin.setValue(parseToCoinWith4Decimals(minAmount.get()));
    }

    private void setPriceToModel() {
        model().priceAsFiat.setValue(parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        model().volumeAsFiat.setValue(parseToFiatWith2Decimals(volume.get()));
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
