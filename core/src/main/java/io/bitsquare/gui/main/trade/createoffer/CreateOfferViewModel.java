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
import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDataModel<CreateOfferDataModel> implements ViewModel {

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

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new
            SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();


    @Inject
    public CreateOfferViewModel(CreateOfferDataModel dataModel, FiatValidator fiatValidator, BtcValidator btcValidator,
                                BSFormatter formatter) {
        super(dataModel);

        this.fiatValidator = fiatValidator;
        this.btcValidator = btcValidator;
        this.formatter = formatter;

        paymentLabel.set(BSResources.get("createOffer.fundsBox.paymentLabel", dataModel.getOfferId()));

        if (dataModel.getAddressEntry() != null) {
            addressAsString.set(dataModel.getAddressEntry().getAddress().toString());
            address.set(dataModel.getAddressEntry().getAddress());
        }

        setupBindings();
        setupListeners();
    }

    // setOfferBookFilter is a one time call
    void initWithData(Offer.Direction direction, Coin amount, Fiat price) {
        dataModel.setDirection(direction);
        directionLabel.set(dataModel.getDirection() == Offer.Direction.BUY ? BSResources.get("shared.buy") : BSResources.get
                ("shared.sell"));

        // apply only if valid
        boolean amountValid = false;
        if (amount != null && isBtcInputValid(amount.toPlainString())
                .isValid) {
            dataModel.amountAsCoin.set(amount);
            dataModel.minAmountAsCoin.set(amount);
            amountValid = true;
        }

        // apply only if valid
        boolean priceValid = false;
        if (price != null && isBtcInputValid(price.toPlainString()).isValid) {
            dataModel.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.toPlainString()));
            priceValid = true;
        }

        if (amountValid && priceValid)
            dataModel.calculateTotalToPay();
    }


    void placeOffer() {
        dataModel.requestPlaceOfferErrorMessage.set(null);
        dataModel.requestPlaceOfferSuccess.set(false);

        isPlaceOfferSpinnerVisible.set(true);

        dataModel.placeOffer();
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
                amount.set(formatter.formatCoin(dataModel.amountAsCoin.get()));

                calculateVolume();

                // handle minAmount/amount relationship
                if (!dataModel.isMinAmountLessOrEqualAmount()) {
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
                minAmount.set(formatter.formatCoin(dataModel.minAmountAsCoin.get()));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
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
                price.set(formatter.formatFiat(dataModel.priceAsFiat.get()));

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
                volume.set(formatter.formatFiat(dataModel.volumeAsFiat.get()));

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
        dataModel.securityDepositInfoDisplayed();
    }


    WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    Boolean getDisplaySecurityDepositInfo() {
        return dataModel.getDisplaySecurityDepositInfo();
    }

    private void setupListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener((ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                dataModel.calculateTotalToPay();
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
                dataModel.calculateTotalToPay();
            }
            updateButtonDisableState();
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setVolumeToModel();
                setPriceToModel();
                dataModel.calculateAmount();
                dataModel.calculateTotalToPay();
            }
            updateButtonDisableState();
        });
        dataModel.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            updateButtonDisableState();
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue)));
        dataModel.minAmountAsCoin.addListener((ov, oldValue, newValue) -> minAmount.set(formatter.formatCoin(newValue)));
        dataModel.priceAsFiat.addListener((ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue)));
        dataModel.volumeAsFiat.addListener((ov, oldValue, newValue) -> volume.set(formatter.formatFiat(newValue)));

        dataModel.requestPlaceOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                isPlaceOfferSpinnerVisible.set(false);
            }
        });
        dataModel.requestPlaceOfferSuccess.addListener((ov, oldValue, newValue) -> {
            isPlaceOfferButtonVisible.set(!newValue);
            isPlaceOfferSpinnerVisible.set(false);
        });

        // ObservableLists
        dataModel.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(formatter
                .countryLocalesToString(dataModel.acceptedCountries)));
        dataModel.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(formatter
                .languageLocalesToString(dataModel.acceptedLanguages)));
        dataModel.acceptedArbitrators.addListener((Observable o) -> acceptedArbitrators.set(formatter
                .arbitratorsToString(dataModel.acceptedArbitrators)));
    }

    private void setupBindings() {
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()),
                dataModel.totalToPayAsCoin));
        securityDeposit.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.securityDepositAsCoin.get()),
                dataModel.securityDepositAsCoin));

        totalToPayAsCoin.bind(dataModel.totalToPayAsCoin);

        offerFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.offerFeeAsCoin.get()),
                dataModel.offerFeeAsCoin));
        networkFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.networkFeeAsCoin.get()),
                dataModel.offerFeeAsCoin));

        bankAccountType.bind(Bindings.createStringBinding(() -> BSResources.get(dataModel.bankAccountType.get()),
                dataModel.bankAccountType));
        bankAccountCurrency.bind(dataModel.bankAccountCurrency);
        bankAccountCounty.bind(dataModel.bankAccountCounty);

        requestPlaceOfferErrorMessage.bind(dataModel.requestPlaceOfferErrorMessage);
        showTransactionPublishedScreen.bind(dataModel.requestPlaceOfferSuccess);
        transactionId.bind(dataModel.transactionId);

        btcCode.bind(dataModel.btcCode);
        fiatCode.bind(dataModel.fiatCode);
    }

    private void calculateVolume() {
        setAmountToModel();
        setPriceToModel();
        dataModel.calculateVolume();
    }

    private void calculateAmount() {
        setVolumeToModel();
        setPriceToModel();
        dataModel.calculateAmount();

        // Amount calculation could lead to amount/minAmount invalidation
        if (!dataModel.isMinAmountLessOrEqualAmount()) {
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
        dataModel.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void setMinAmountToModel() {
        dataModel.minAmountAsCoin.set(formatter.parseToCoinWith4Decimals(minAmount.get()));
    }

    private void setPriceToModel() {
        dataModel.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        dataModel.volumeAsFiat.set(formatter.parseToFiatWith2Decimals(volume.get()));
    }

    private void updateButtonDisableState() {
        isPlaceOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid &&
                        isBtcInputValid(minAmount.get()).isValid &&
                        isBtcInputValid(price.get()).isValid &&
                        isBtcInputValid(volume.get()).isValid &&
                        dataModel.isMinAmountLessOrEqualAmount() &&
                        dataModel.isWalletFunded.get())
        );
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {

        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {

        return fiatValidator.validate(input);
    }

}
