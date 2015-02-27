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

package io.bitsquare.gui.main.trade.createoffer;

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Direction;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import viewfx.model.ViewModel;
import viewfx.model.support.ActivatableWithDelegate;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDelegate<CreateOfferDataModel> implements ViewModel {

    private final BtcValidator btcValidator;
    private final BSFormatter formatter;
    private final FiatValidator fiatValidator;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty securityDeposit = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty directionLabel = new SimpleStringProperty();
    final StringProperty offerFee = new SimpleStringProperty();
    final StringProperty networkFee = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty acceptedCountries = new SimpleStringProperty();
    final StringProperty acceptedLanguages = new SimpleStringProperty();
    final StringProperty acceptedArbitrators = new SimpleStringProperty();
    final StringProperty addressAsString = new SimpleStringProperty();
    final StringProperty paymentLabel = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();

    final BooleanProperty isPlaceOfferButtonVisible = new SimpleBooleanProperty(false);
    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty isPlaceOfferSpinnerVisible = new SimpleBooleanProperty(false);
    final BooleanProperty showWarningAdjustedVolume = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidFiatDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty tabIsClosable = new SimpleBooleanProperty(true);

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new
            SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();


    @Inject
    public CreateOfferViewModel(CreateOfferDataModel delegate, FiatValidator fiatValidator, BtcValidator btcValidator,
                                BSFormatter formatter) {
        super(delegate);

        this.fiatValidator = fiatValidator;
        this.btcValidator = btcValidator;
        this.formatter = formatter;

        paymentLabel.set(BSResources.get("createOffer.fundsBox.paymentLabel", delegate.getOfferId()));

        if (delegate.getAddressEntry() != null) {
            addressAsString.set(delegate.getAddressEntry().getAddress().toString());
            address.set(delegate.getAddressEntry().getAddress());
        }

        setupBindings();
        setupListeners();
    }

    // setOfferBookFilter is a one time call
    void initWithData(Direction direction, Coin amount, Fiat price) {
        delegate.setDirection(direction);
        directionLabel.set(delegate.getDirection() == Direction.BUY ? BSResources.get("shared.buy") : BSResources.get
                ("shared.sell"));

        // apply only if valid
        boolean amountValid = false;
        if (amount != null && isBtcInputValid(amount.toPlainString())
                .isValid) {
            delegate.amountAsCoin.set(amount);
            delegate.minAmountAsCoin.set(amount);
            amountValid = true;
        }

        // apply only if valid
        boolean priceValid = false;
        if (price != null && isBtcInputValid(price.toPlainString()).isValid) {
            delegate.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.toPlainString()));
            priceValid = true;
        }

        if (amountValid && priceValid)
            delegate.calculateTotalToPay();
    }


    void placeOffer() {
        delegate.requestPlaceOfferErrorMessage.set(null);
        delegate.requestPlaceOfferSuccess.set(false);

        isPlaceOfferButtonDisabled.set(true);
        isPlaceOfferSpinnerVisible.set(true);

        delegate.placeOffer();
    }


    void onShowPayFundsScreen() {
        isPlaceOfferButtonVisible.set(true);
    }

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!formatter.hasBtcValidDecimals(userInput));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input
                amount.set(formatter.formatCoin(delegate.amountAsCoin.get()));

                calculateVolume();

                // handle minAmount/amount relationship
                if (!delegate.isMinAmountLessOrEqualAmount()) {
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

    void onFocusOutMinAmountTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!formatter.hasBtcValidDecimals(userInput));
                setMinAmountToModel();
                minAmount.set(formatter.formatCoin(delegate.minAmountAsCoin.get()));

                if (!delegate.isMinAmountLessOrEqualAmount()) {
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

    void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isFiatInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!formatter.hasFiatValidDecimals(userInput));
                setPriceToModel();
                price.set(formatter.formatFiat(delegate.priceAsFiat.get()));

                calculateVolume();
            }
        }
    }

    void onFocusOutVolumeTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!formatter.hasFiatValidDecimals(userInput));
                setVolumeToModel();
                volume.set(formatter.formatFiat(delegate.volumeAsFiat.get()));

                calculateAmount();

                // must be placed after calculateAmount (btc value has been adjusted in case the calculation leads to
                // invalid decimal places for the amount value
                showWarningAdjustedVolume.set(!formatter.formatFiat(formatter.parseToFiatWith2Decimals(userInput))
                        .equals(volume
                                .get()));
            }
        }
    }

    void securityDepositInfoDisplayed() {
        delegate.securityDepositInfoDisplayed();
    }


    WalletService getWalletService() {
        return delegate.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    Boolean displaySecurityDepositInfo() {
        return delegate.displaySecurityDepositInfo();
    }

    private void setupListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                delegate.calculateTotalToPay();
            }
            updateButtonDisableState();
        });

        minAmount.addListener((ov, oldValue, newValue) -> {
            setMinAmountToModel();
            updateButtonDisableState();
        });

        price.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setPriceToModel();
                calculateVolume();
                delegate.calculateTotalToPay();
            }
            updateButtonDisableState();
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setVolumeToModel();
                setPriceToModel();
                delegate.calculateAmount();
                delegate.calculateTotalToPay();
            }
            updateButtonDisableState();
        });
        delegate.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                updateButtonDisableState();
                tabIsClosable.set(false);
            }
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        delegate.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue)));
        delegate.minAmountAsCoin.addListener((ov, oldValue, newValue) -> minAmount.set(formatter.formatCoin(newValue)));
        delegate.priceAsFiat.addListener((ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue)));
        delegate.volumeAsFiat.addListener((ov, oldValue, newValue) -> volume.set(formatter.formatFiat(newValue)));

        delegate.requestPlaceOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                isPlaceOfferButtonDisabled.set(false);
                isPlaceOfferSpinnerVisible.set(false);
            }
        });
        delegate.requestPlaceOfferSuccess.addListener((ov, oldValue, newValue) -> {
            isPlaceOfferButtonVisible.set(!newValue);
            isPlaceOfferSpinnerVisible.set(false);
        });

        // ObservableLists
        delegate.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(formatter
                .countryLocalesToString(delegate.acceptedCountries)));
        delegate.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(formatter
                .languageLocalesToString(delegate.acceptedLanguages)));
        delegate.acceptedArbitrators.addListener((Observable o) -> acceptedArbitrators.set(formatter
                .arbitratorsToString(delegate.acceptedArbitrators)));
    }

    private void setupBindings() {
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(delegate.totalToPayAsCoin.get()),
                delegate.totalToPayAsCoin));
        securityDeposit.bind(createStringBinding(() -> formatter.formatCoinWithCode(delegate.securityDepositAsCoin.get()),
                delegate.securityDepositAsCoin));

        totalToPayAsCoin.bind(delegate.totalToPayAsCoin);

        offerFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(delegate.offerFeeAsCoin.get()),
                delegate.offerFeeAsCoin));
        networkFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(delegate.networkFeeAsCoin.get()),
                delegate.offerFeeAsCoin));

        bankAccountType.bind(Bindings.createStringBinding(() -> BSResources.get(delegate.bankAccountType.get()),
                delegate.bankAccountType));
        bankAccountCurrency.bind(delegate.bankAccountCurrency);
        bankAccountCounty.bind(delegate.bankAccountCounty);

        requestPlaceOfferErrorMessage.bind(delegate.requestPlaceOfferErrorMessage);
        showTransactionPublishedScreen.bind(delegate.requestPlaceOfferSuccess);
        transactionId.bind(delegate.transactionId);

        btcCode.bind(delegate.btcCode);
        fiatCode.bind(delegate.fiatCode);
    }

    private void calculateVolume() {
        setAmountToModel();
        setPriceToModel();
        delegate.calculateVolume();
    }

    private void calculateAmount() {
        setVolumeToModel();
        setPriceToModel();
        delegate.calculateAmount();

        // Amount calculation could lead to amount/minAmount invalidation
        if (!delegate.isMinAmountLessOrEqualAmount()) {
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
        delegate.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void setMinAmountToModel() {
        delegate.minAmountAsCoin.set(formatter.parseToCoinWith4Decimals(minAmount.get()));
    }

    private void setPriceToModel() {
        delegate.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        delegate.volumeAsFiat.set(formatter.parseToFiatWith2Decimals(volume.get()));
    }

    private void updateButtonDisableState() {
        isPlaceOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid &&
                        isBtcInputValid(minAmount.get()).isValid &&
                        isBtcInputValid(price.get()).isValid &&
                        isBtcInputValid(volume.get()).isValid &&
                        delegate.isMinAmountLessOrEqualAmount() &&
                        delegate.isWalletFunded.get())
        );
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {

        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {

        return fiatValidator.validate(input);
    }

}
