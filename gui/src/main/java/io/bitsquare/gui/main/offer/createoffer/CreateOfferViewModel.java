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

package io.bitsquare.gui.main.offer.createoffer;

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.offer.Offer;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDataModel<CreateOfferDataModel> implements ViewModel {

    private final BtcValidator btcValidator;
    private final BSFormatter formatter;
    private final FiatValidator fiatValidator;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty amountPriceBoxInfo = new SimpleStringProperty();
    final StringProperty securityDeposit = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty directionLabel = new SimpleStringProperty();
    final StringProperty amountToTradeLabel = new SimpleStringProperty();
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

    private ChangeListener<String> amountListener;
    private ChangeListener<String> minAmountListener;
    private ChangeListener<String> priceListener;
    private ChangeListener<String> volumeListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Fiat> priceAsFiatListener;
    private ChangeListener<Fiat> volumeAsFiatListener;
    private ChangeListener<Boolean> isWalletFundedListener;
    private ChangeListener<Boolean> requestPlaceOfferSuccessListener;
    private ChangeListener<String> requestPlaceOfferErrorMessageListener;
    private InvalidationListener acceptedCountriesListener;
    private InvalidationListener acceptedLanguageCodesListener;
    private InvalidationListener acceptedArbitratorsListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        createListeners();
    }

    @Override
    protected void doActivate() {
        addBindings();
        addListeners();
    }

    @Override
    protected void doDeactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()),
                dataModel.totalToPayAsCoin));
        securityDeposit.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.securityDepositAsCoin.get()),
                dataModel.securityDepositAsCoin));

        tradeAmount.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.amountAsCoin.get()),
                dataModel.amountAsCoin));

        totalToPayAsCoin.bind(dataModel.totalToPayAsCoin);

        offerFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.offerFeeAsCoin.get()),
                dataModel.offerFeeAsCoin));
        networkFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.networkFeeAsCoin.get()),
                dataModel.offerFeeAsCoin));

        bankAccountType.bind(createStringBinding(() -> BSResources.get(dataModel.bankAccountType.get()),
                dataModel.bankAccountType));
        bankAccountCurrency.bind(dataModel.bankAccountCurrency);
        bankAccountCounty.bind(dataModel.bankAccountCounty);

        requestPlaceOfferErrorMessage.bind(dataModel.requestPlaceOfferErrorMessage);
        showTransactionPublishedScreen.bind(dataModel.requestPlaceOfferSuccess);
        transactionId.bind(dataModel.transactionId);

        btcCode.bind(dataModel.btcCode);
        fiatCode.bind(dataModel.fiatCode);
    }

    private void removeBindings() {
        totalToPay.unbind();
        securityDeposit.unbind();
        tradeAmount.unbind();
        totalToPayAsCoin.unbind();
        offerFee.unbind();
        networkFee.unbind();
        bankAccountType.unbind();
        bankAccountCurrency.unbind();
        bankAccountCounty.unbind();
        requestPlaceOfferErrorMessage.unbind();
        showTransactionPublishedScreen.unbind();
        transactionId.unbind();
        btcCode.unbind();
        fiatCode.unbind();
    }

    private void createListeners() {
        amountListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid) {
                setAmountToModel();
                calculateVolume();
                dataModel.calculateTotalToPay();
            }
            updateButtonDisableState();
        };
        minAmountListener = (ov, oldValue, newValue) -> {
            setMinAmountToModel();
            updateButtonDisableState();
        };
        priceListener = (ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setPriceToModel();
                calculateVolume();
                dataModel.calculateTotalToPay();
            }
            updateButtonDisableState();
        };
        volumeListener = (ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setVolumeToModel();
                setPriceToModel();
                dataModel.calculateAmount();
                dataModel.calculateTotalToPay();
            }
            updateButtonDisableState();
        };
        amountAsCoinListener = (ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue));
        minAmountAsCoinListener = (ov, oldValue, newValue) -> minAmount.set(formatter.formatCoin(newValue));
        priceAsFiatListener = (ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue));
        volumeAsFiatListener = (ov, oldValue, newValue) -> volume.set(formatter.formatFiat(newValue));

        isWalletFundedListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };
        requestPlaceOfferSuccessListener = (ov, oldValue, newValue) -> {
            isPlaceOfferButtonVisible.set(!newValue);
            isPlaceOfferSpinnerVisible.set(false);
        };
        requestPlaceOfferErrorMessageListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                isPlaceOfferSpinnerVisible.set(false);
            }
        };

        acceptedCountriesListener = (Observable o) ->
                acceptedCountries.set(formatter.countryLocalesToString(dataModel.acceptedCountries));
        acceptedLanguageCodesListener = (Observable o) -> acceptedLanguages.set(formatter.languageCodesToString(dataModel.acceptedLanguageCodes));
        acceptedArbitratorsListener = (Observable o) -> acceptedArbitrators.set(formatter.arbitratorsToNames(dataModel.acceptedArbitrators));

    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);
        minAmount.addListener(minAmountListener);
        price.addListener(priceListener);
        volume.addListener(volumeListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener(amountAsCoinListener);
        dataModel.minAmountAsCoin.addListener(minAmountAsCoinListener);
        dataModel.priceAsFiat.addListener(priceAsFiatListener);
        dataModel.volumeAsFiat.addListener(volumeAsFiatListener);

        dataModel.isWalletFunded.addListener(isWalletFundedListener);
        dataModel.requestPlaceOfferSuccess.addListener(requestPlaceOfferSuccessListener);
        dataModel.requestPlaceOfferErrorMessage.addListener(requestPlaceOfferErrorMessageListener);

        // ObservableLists
        dataModel.acceptedCountries.addListener(acceptedCountriesListener);
        dataModel.acceptedLanguageCodes.addListener(acceptedLanguageCodesListener);
        dataModel.acceptedArbitrators.addListener(acceptedArbitratorsListener);
    }

    private void removeListeners() {
        amount.removeListener(amountListener);
        minAmount.removeListener(minAmountListener);
        price.removeListener(priceListener);
        volume.removeListener(volumeListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.removeListener(amountAsCoinListener);
        dataModel.minAmountAsCoin.removeListener(minAmountAsCoinListener);
        dataModel.priceAsFiat.removeListener(priceAsFiatListener);
        dataModel.volumeAsFiat.removeListener(volumeAsFiatListener);

        dataModel.isWalletFunded.removeListener(isWalletFundedListener);
        dataModel.requestPlaceOfferSuccess.removeListener(requestPlaceOfferSuccessListener);
        dataModel.requestPlaceOfferErrorMessage.removeListener(requestPlaceOfferErrorMessageListener);

        // ObservableLists
        dataModel.acceptedCountries.removeListener(acceptedCountriesListener);
        dataModel.acceptedLanguageCodes.removeListener(acceptedLanguageCodesListener);
        dataModel.acceptedArbitrators.removeListener(acceptedArbitratorsListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(Offer.Direction direction, Coin amountAsCoin, Fiat priceAsFiat) {
        addListeners();

        dataModel.initWithData(direction);

        if (dataModel.getDirection() == Offer.Direction.BUY) {
            directionLabel.set(BSResources.get("shared.buyBitcoin"));
            amountToTradeLabel.set(BSResources.get("createOffer.amountPriceBox.amountDescription", BSResources.get("shared.buy")));
            volumeDescriptionLabel.set(BSResources.get("createOffer.amountPriceBox.buy.volumeDescription", fiatCode.get()));
            amountPriceBoxInfo.set(BSResources.get("createOffer.amountPriceBox.buy.info"));
        }
        else {
            directionLabel.set(BSResources.get("shared.sellBitcoin"));
            amountToTradeLabel.set(BSResources.get("createOffer.amountPriceBox.amountDescription", BSResources.get("shared.sell")));
            volumeDescriptionLabel.set(BSResources.get("createOffer.amountPriceBox.sell.volumeDescription", fiatCode.get()));
            amountPriceBoxInfo.set(BSResources.get("createOffer.amountPriceBox.sell.info"));
        }


        // apply only if valid
        boolean amountValid = false;
        if (amountAsCoin != null && isBtcInputValid(amountAsCoin.toPlainString())
                .isValid) {
            dataModel.amountAsCoin.set(amountAsCoin);
            dataModel.minAmountAsCoin.set(amountAsCoin);
            amountValid = true;
        }

        // apply only if valid
        boolean priceValid = false;
        if (priceAsFiat != null && isBtcInputValid(priceAsFiat.toPlainString()).isValid) {
            dataModel.priceAsFiat.set(formatter.parseToFiatWith2Decimals(priceAsFiat.toPlainString()));
            priceValid = true;
        }

        if (amountValid && priceValid)
            dataModel.calculateTotalToPay();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer() {
        dataModel.requestPlaceOfferErrorMessage.set(null);
        dataModel.requestPlaceOfferSuccess.set(false);

        isPlaceOfferSpinnerVisible.set(true);

        dataModel.onPlaceOffer();
    }


    void onShowPayFundsScreen() {
        isPlaceOfferButtonVisible.set(true);
    }

    void onSecurityDepositInfoDisplayed() {
        dataModel.onSecurityDepositInfoDisplayed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue, String userInput) {
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

    void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue, String userInput) {
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

    void onFocusOutPriceTextField(boolean oldValue, boolean newValue, String userInput) {
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

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue, String userInput) {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    WalletService getWalletService() {
        return dataModel.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    boolean getDisplaySecurityDepositInfo() {
        return dataModel.getDisplaySecurityDepositInfo();
    }

    boolean isSeller() {
        return dataModel.getDirection() == Offer.Direction.SELL;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

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
