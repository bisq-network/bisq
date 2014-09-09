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

package io.bitsquare.gui.pm.trade;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.model.trade.CreateOfferModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;

import javax.inject.Inject;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.BSFormatter.*;
import static javafx.beans.binding.Bindings.createStringBinding;

public class CreateOfferPM extends PresentationModel<CreateOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPM.class);

    private final BtcValidator btcValidator = new BtcValidator();
    private final FiatValidator fiatValidator = new FiatValidator();

    public final StringProperty amount = new SimpleStringProperty();
    public final StringProperty minAmount = new SimpleStringProperty();
    public final StringProperty price = new SimpleStringProperty();
    public final StringProperty volume = new SimpleStringProperty();
    public final StringProperty collateral = new SimpleStringProperty();
    public final StringProperty totalToPay = new SimpleStringProperty();
    public final StringProperty directionLabel = new SimpleStringProperty();
    public final StringProperty collateralLabel = new SimpleStringProperty();
    public final StringProperty offerFee = new SimpleStringProperty();
    public final StringProperty networkFee = new SimpleStringProperty();
    public final StringProperty bankAccountType = new SimpleStringProperty();
    public final StringProperty bankAccountCurrency = new SimpleStringProperty();
    public final StringProperty bankAccountCounty = new SimpleStringProperty();
    public final StringProperty acceptedCountries = new SimpleStringProperty();
    public final StringProperty acceptedLanguages = new SimpleStringProperty();
    public final StringProperty acceptedArbitrators = new SimpleStringProperty();
    public final StringProperty addressAsString = new SimpleStringProperty();
    public final StringProperty paymentLabel = new SimpleStringProperty();
    public final StringProperty transactionId = new SimpleStringProperty();
    public final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    public final StringProperty btcCode = new SimpleStringProperty();
    public final StringProperty fiatCode = new SimpleStringProperty();

    public final BooleanProperty isPlaceOfferButtonVisible = new SimpleBooleanProperty(false);
    public final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    public final BooleanProperty showWarningAdjustedVolume = new SimpleBooleanProperty();
    public final BooleanProperty showWarningInvalidFiatDecimalPlaces = new SimpleBooleanProperty();
    public final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    public final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();

    public final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    public final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new 
            SimpleObjectProperty<>();
    public final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    public final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    public final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    public final ObjectProperty<Address> address = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // non private for testing
    @Inject
    CreateOfferPM(CreateOfferModel model) {
        super(model);

        // Note: Don't do setup in constructor to make object creation faster
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialized() {
        super.initialized();

        // static
        paymentLabel.set(BSResources.get("createOffer.fundsBox.paymentLabel", model.getOfferId()));

        if (model.addressEntry != null) {
            addressAsString.set(model.addressEntry.getAddress().toString());
            address.set(model.addressEntry.getAddress());
        }

        setupBindings();
        setupListeners();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API methods (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    // setOrderBookFilter is a one time call
    public void setOrderBookFilter(@NotNull OrderBookFilter orderBookFilter) {
        model.setDirection(orderBookFilter.getDirection());
        directionLabel.set(model.getDirection() == Direction.BUY ? BSResources.get("shared.buy") : BSResources.get
                ("shared.sell"));

        // apply only if valid
        if (orderBookFilter.getAmount() != null && isBtcInputValid(orderBookFilter.getAmount().toPlainString())
                .isValid) {
            model.amountAsCoin.set(orderBookFilter.getAmount());
            model.minAmountAsCoin.set(orderBookFilter.getAmount());
        }

        // apply only if valid
        if (orderBookFilter.getPrice() != null && isBtcInputValid(orderBookFilter.getPrice().toPlainString()).isValid)
            model.priceAsFiat.set(parseToFiatWith2Decimals(orderBookFilter.getPrice().toPlainString()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPlaceOffer() {
        model.requestPlaceOfferErrorMessage.set(null);
        model.requestPlaceOfferSuccess.set(false);

        isPlaceOfferButtonDisabled.set(true);

        model.placeOffer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI events (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onShowPayFundsScreen() {
        isPlaceOfferButtonVisible.set(true);
    }

    // On focus out we do validation and apply the data to the model 
    public void onFocusOutAmountTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!hasBtcValidDecimals(userInput));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input
                amount.set(formatCoin(model.amountAsCoin.get()));

                calculateVolume();

                // handle minAmount/amount relationship
                if (!model.isMinAmountLessOrEqualAmount()) {
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("createOffer.validation.amountSmallerThanMinAmount")));
                }
                else {
                    amountValidationResult.set(result);
                    if (minAmount.get() != null)
                        minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
                }
            }
        }
    }

    public void onFocusOutMinAmountTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!hasBtcValidDecimals(userInput));
                setMinAmountToModel();
                minAmount.set(formatCoin(model.minAmountAsCoin.get()));

                if (!model.isMinAmountLessOrEqualAmount()) {
                    minAmountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("createOffer.validation.minAmountLargerThanAmount")));
                }
                else {
                    minAmountValidationResult.set(result);
                    if (amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(amount.get()));
                }
            }
        }
    }

    public void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isFiatInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!hasFiatValidDecimals(userInput));
                setPriceToModel();
                price.set(formatFiat(model.priceAsFiat.get()));

                calculateVolume();
            }
        }
    }

    public void onFocusOutVolumeTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!hasFiatValidDecimals(userInput));
                setVolumeToModel();
                volume.set(formatFiat(model.volumeAsFiat.get()));

                calculateAmount();

                // must be placed after calculateAmount (btc value has been adjusted in case the calculation leads to 
                // invalid decimal places for the amount value
                showWarningAdjustedVolume.set(!formatFiat(parseToFiatWith2Decimals(userInput)).equals(volume
                        .get()));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters (called by CB)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WalletFacade getWalletFacade() {
        return model.getWalletFacade();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {

        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                model.calculateTotalToPay();
                model.calculateCollateral();
            }
            validateInput();
        });

        minAmount.addListener((ov, oldValue, newValue) -> {
            setMinAmountToModel();
            validateInput();
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setPriceToModel();
                calculateVolume();
                model.calculateTotalToPay();
                model.calculateCollateral();
            }
            validateInput();
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setVolumeToModel();
                setPriceToModel();
                model.calculateAmount();
                model.calculateTotalToPay();
                model.calculateCollateral();
            }
            validateInput();
        });
        model.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                validateInput();
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        model.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatCoin(newValue)));
        model.minAmountAsCoin.addListener((ov, oldValue, newValue) -> minAmount.set(formatCoin(newValue)));
        model.priceAsFiat.addListener((ov, oldValue, newValue) -> price.set(formatFiat(newValue)));
        model.volumeAsFiat.addListener((ov, oldValue, newValue) -> volume.set(formatFiat(newValue)));

        model.requestPlaceOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                isPlaceOfferButtonDisabled.set(false);
        });
        model.requestPlaceOfferSuccess.addListener((ov, oldValue, newValue) -> isPlaceOfferButtonVisible.set
                (!newValue));

        // ObservableLists
        model.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(BSFormatter
                .countryLocalesToString(model.acceptedCountries)));
        model.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(BSFormatter
                .languageLocalesToString(model.acceptedLanguages)));
        model.acceptedArbitrators.addListener((Observable o) -> acceptedArbitrators.set(BSFormatter
                .arbitratorsToString(model.acceptedArbitrators)));
    }

    private void setupBindings() {
        totalToPay.bind(createStringBinding(() -> formatCoinWithCode(model.totalToPayAsCoin.get()),
                model.totalToPayAsCoin));
        collateral.bind(createStringBinding(() -> formatCoinWithCode(model.collateralAsCoin.get()),
                model.collateralAsCoin));

        collateralLabel.bind(Bindings.createStringBinding(() ->
                        BSResources.get("createOffer.fundsBox.collateral",
                                BSFormatter.formatCollateralPercent(model.collateralAsLong.get())),
                model.collateralAsLong));
        totalToPayAsCoin.bind(model.totalToPayAsCoin);

        offerFee.bind(createStringBinding(() -> formatCoinWithCode(model.offerFeeAsCoin.get()),
                model.offerFeeAsCoin));
        networkFee.bind(createStringBinding(() -> formatCoinWithCode(model.networkFeeAsCoin.get()),
                model.offerFeeAsCoin));

        bankAccountType.bind(Bindings.createStringBinding(() -> BSResources.get(model.bankAccountType.get()),
                model.bankAccountType));
        bankAccountCurrency.bind(model.bankAccountCurrency);
        bankAccountCounty.bind(model.bankAccountCounty);

        requestPlaceOfferErrorMessage.bind(model.requestPlaceOfferErrorMessage);
        showTransactionPublishedScreen.bind(model.requestPlaceOfferSuccess);
        transactionId.bind(model.transactionId);

        btcCode.bind(model.btcCode);
        fiatCode.bind(model.fiatCode);
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

        // Amount calculation could lead to amount/minAmount invalidation
        if (!model.isMinAmountLessOrEqualAmount()) {
            amountValidationResult.set(new InputValidator.ValidationResult(false,
                    BSResources.get("createOffer.validation.amountSmallerThanMinAmount")));
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

    private void validateInput() {
        isPlaceOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid &&
                        isBtcInputValid(minAmount.get()).isValid &&
                        isBtcInputValid(price.get()).isValid &&
                        isBtcInputValid(volume.get()).isValid &&
                        model.isMinAmountLessOrEqualAmount() &&
                        model.isWalletFunded.get())
        );
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {

        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {

        return fiatValidator.validate(input);
    }


}
