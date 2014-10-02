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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.Direction;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

import javax.inject.Inject;

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

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferPM extends PresentationModel<CreateOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferPM.class);

    private final BtcValidator btcValidator;
    private BSFormatter formatter;
    private final FiatValidator fiatValidator;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty collateral = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty directionLabel = new SimpleStringProperty();
    final StringProperty collateralLabel = new SimpleStringProperty();
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // non private for testing
    @Inject
    CreateOfferPM(CreateOfferModel model, FiatValidator fiatValidator, BtcValidator btcValidator,
                  BSFormatter formatter) {
        super(model);

        this.fiatValidator = fiatValidator;
        this.btcValidator = btcValidator;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        super.initialize();

        // static
        paymentLabel.set(BSResources.get("createOffer.fundsBox.paymentLabel", model.getOfferId()));

        if (model.getAddressEntry() != null) {
            addressAsString.set(model.getAddressEntry().getAddress().toString());
            address.set(model.getAddressEntry().getAddress());
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
    // Public API methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // setOrderBookFilter is a one time call
    void initWithData(Direction direction, Coin amount, Fiat price) {
        model.setDirection(direction);
        directionLabel.set(model.getDirection() == Direction.BUY ? BSResources.get("shared.buy") : BSResources.get
                ("shared.sell"));

        // apply only if valid 
        boolean amountValid = false;
        if (amount != null && isBtcInputValid(amount.toPlainString())
                .isValid) {
            model.amountAsCoin.set(amount);
            model.minAmountAsCoin.set(amount);
            amountValid = true;
        }

        // apply only if valid
        boolean priceValid = false;
        if (price != null && isBtcInputValid(price.toPlainString()).isValid) {
            model.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.toPlainString()));
            priceValid = true;
        }

        if (amountValid && priceValid)
            model.calculateTotalToPay();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void placeOffer() {
        model.requestPlaceOfferErrorMessage.set(null);
        model.requestPlaceOfferSuccess.set(false);

        isPlaceOfferButtonDisabled.set(true);

        model.placeOffer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI events
    ///////////////////////////////////////////////////////////////////////////////////////////

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
                amount.set(formatter.formatCoin(model.amountAsCoin.get()));

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

    void onFocusOutMinAmountTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidBtcDecimalPlaces.set(!formatter.hasBtcValidDecimals(userInput));
                setMinAmountToModel();
                minAmount.set(formatter.formatCoin(model.minAmountAsCoin.get()));

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

    void onFocusOutPriceTextField(Boolean oldValue, Boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isFiatInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!formatter.hasFiatValidDecimals(userInput));
                setPriceToModel();
                price.set(formatter.formatFiat(model.priceAsFiat.get()));

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
                volume.set(formatter.formatFiat(model.volumeAsFiat.get()));

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
        model.securityDepositInfoDisplayed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    WalletFacade getWalletFacade() {
        return model.getWalletFacade();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    Boolean displaySecurityDepositInfo() {
        return model.displaySecurityDepositInfo();
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
                model.calculateCollateral();
                model.calculateTotalToPay();
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
                model.calculateCollateral();
                model.calculateTotalToPay();
            }
            updateButtonDisableState();
        });

        volume.addListener((ov, oldValue, newValue) -> {
            if (isFiatInputValid(newValue).isValid) {
                setVolumeToModel();
                setPriceToModel();
                model.calculateAmount();
                model.calculateCollateral();
                model.calculateTotalToPay();
            }
            updateButtonDisableState();
        });
        model.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                updateButtonDisableState();
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        model.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue)));
        model.minAmountAsCoin.addListener((ov, oldValue, newValue) -> minAmount.set(formatter.formatCoin(newValue)));
        model.priceAsFiat.addListener((ov, oldValue, newValue) -> price.set(formatter.formatFiat(newValue)));
        model.volumeAsFiat.addListener((ov, oldValue, newValue) -> volume.set(formatter.formatFiat(newValue)));

        model.requestPlaceOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                isPlaceOfferButtonDisabled.set(false);
        });
        model.requestPlaceOfferSuccess.addListener((ov, oldValue, newValue) -> isPlaceOfferButtonVisible.set
                (!newValue));

        // ObservableLists
        model.acceptedCountries.addListener((Observable o) -> acceptedCountries.set(formatter
                .countryLocalesToString(model.acceptedCountries)));
        model.acceptedLanguages.addListener((Observable o) -> acceptedLanguages.set(formatter
                .languageLocalesToString(model.acceptedLanguages)));
        model.acceptedArbitrators.addListener((Observable o) -> acceptedArbitrators.set(formatter
                .arbitratorsToString(model.acceptedArbitrators)));
    }

    private void setupBindings() {
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(model.totalToPayAsCoin.get()),
                model.totalToPayAsCoin));
        collateral.bind(createStringBinding(() -> formatter.formatCoinWithCode(model.collateralAsCoin.get()),
                model.collateralAsCoin));

        collateralLabel.bind(Bindings.createStringBinding(() ->
                        BSResources.get("createOffer.fundsBox.collateral",
                                formatter.formatCollateralPercent(model.collateralAsLong.get())),
                model.collateralAsLong));
        totalToPayAsCoin.bind(model.totalToPayAsCoin);

        offerFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(model.offerFeeAsCoin.get()),
                model.offerFeeAsCoin));
        networkFee.bind(createStringBinding(() -> formatter.formatCoinWithCode(model.networkFeeAsCoin.get()),
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
        model.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void setMinAmountToModel() {
        model.minAmountAsCoin.set(formatter.parseToCoinWith4Decimals(minAmount.get()));
    }

    private void setPriceToModel() {
        model.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.get()));
    }

    private void setVolumeToModel() {
        model.volumeAsFiat.set(formatter.parseToFiatWith2Decimals(volume.get()));
    }

    private void updateButtonDisableState() {
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
