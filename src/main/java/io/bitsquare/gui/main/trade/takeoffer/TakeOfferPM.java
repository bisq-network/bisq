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

package io.bitsquare.gui.main.trade.takeoffer;

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.ActivatableWithDelegate;
import io.bitsquare.gui.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferPM extends ActivatableWithDelegate<TakeOfferModel> implements ViewModel {

    private String fiatCode;
    private String amountRange;
    private String price;
    private String directionLabel;
    private String bankAccountType;
    private String bankAccountCurrency;
    private String bankAccountCounty;
    private String acceptedCountries;
    private String acceptedLanguages;
    private String acceptedArbitrators;
    private String addressAsString;
    private String paymentLabel;

    // Needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();

    private final BtcValidator btcValidator;
    private final BSFormatter formatter;
    private final String offerFee;
    private final String networkFee;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty securityDeposit = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty requestTakeOfferErrorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();


    final BooleanProperty isTakeOfferButtonVisible = new SimpleBooleanProperty(false);
    final BooleanProperty isTakeOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty isTakeOfferSpinnerVisible = new SimpleBooleanProperty(false);
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty tabIsClosable = new SimpleBooleanProperty(true);

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    // Needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();


    @Inject
    public TakeOfferPM(TakeOfferModel model, BtcValidator btcValidator, BSFormatter formatter) {
        super(model);

        this.btcValidator = btcValidator;
        this.formatter = formatter;

        this.offerFee = formatter.formatCoinWithCode(model.offerFeeAsCoin.get());
        this.networkFee = formatter.formatCoinWithCode(model.networkFeeAsCoin.get());

        setupBindings();
        setupListeners();
    }

    // setOfferBookFilter is a one time call
    void initWithData(Direction direction, Coin amount, Offer offer) {
        delegate.initWithData(amount, offer);

        directionLabel = direction == Direction.BUY ?
                BSResources.get("shared.buy") : BSResources.get("shared.sell");

        fiatCode = offer.getCurrency().getCurrencyCode();
        if (!delegate.isMinAmountLessOrEqualAmount()) {
            amountValidationResult.set(new InputValidator.ValidationResult(false,
                    BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));
        }

        updateButtonDisableState();

        //model.volumeAsFiat.set(offer.getVolumeByAmount(model.amountAsCoin.get()));

        amountRange = formatter.formatCoinWithCode(offer.getMinAmount()) + " - " +
                formatter.formatCoinWithCode(offer.getAmount());
        price = formatter.formatFiatWithCode(offer.getPrice());

        paymentLabel = BSResources.get("takeOffer.fundsBox.paymentLabel", offer.getId());
        if (delegate.getAddressEntry() != null) {
            addressAsString = delegate.getAddressEntry().getAddress().toString();
            address.set(delegate.getAddressEntry().getAddress());
        }

        acceptedCountries = formatter.countryLocalesToString(offer.getAcceptedCountries());
        acceptedLanguages = formatter.languageLocalesToString(offer.getAcceptedLanguageLocales());
        acceptedArbitrators = formatter.arbitratorsToString(offer.getArbitrators());
        bankAccountType = BSResources.get(offer.getBankAccountType().toString());
        bankAccountCurrency = BSResources.get(offer.getCurrency().getDisplayName());
        bankAccountCounty = BSResources.get(offer.getBankAccountCountry().getName());
    }


    void takeOffer() {
        delegate.requestTakeOfferErrorMessage.set(null);
        delegate.requestTakeOfferSuccess.set(false);

        isTakeOfferButtonDisabled.set(true);
        isTakeOfferSpinnerVisible.set(true);

        delegate.takeOffer();
    }

    void securityDepositInfoDisplayed() {
        delegate.securityDepositInfoDisplayed();
    }


    void onShowPayFundsScreen() {
        isTakeOfferButtonVisible.set(true);
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

                if (!delegate.isMinAmountLessOrEqualAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));

                if (delegate.isAmountLargerThanOfferAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountLargerThanOfferAmount")));
            }
        }
    }


    WalletService getWalletService() {
        return delegate.getWalletService();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    String getOfferFee() {
        return offerFee;
    }

    String getNetworkFee() {
        return networkFee;
    }

    String getFiatCode() {
        return fiatCode;
    }

    String getAmount() {
        return formatter.formatCoinWithCode(delegate.amountAsCoin.get());
    }

    String getAmountRange() {
        return amountRange;
    }

    String getPrice() {
        return price;
    }

    String getDirectionLabel() {
        return directionLabel;
    }

    String getBankAccountType() {
        return bankAccountType;
    }

    String getBankAccountCurrency() {
        return bankAccountCurrency;
    }

    String getBankAccountCounty() {
        return bankAccountCounty;
    }

    String getAcceptedCountries() {
        return acceptedCountries;
    }

    String getAcceptedLanguages() {
        return acceptedLanguages;
    }

    String getAcceptedArbitrators() {
        return acceptedArbitrators;
    }

    String getAddressAsString() {
        return addressAsString;
    }

    String getPaymentLabel() {
        return paymentLabel;
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

        delegate.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                updateButtonDisableState();
                tabIsClosable.set(false);
            }
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        delegate.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue)));

        delegate.requestTakeOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                isTakeOfferButtonDisabled.set(false);
                isTakeOfferSpinnerVisible.set(false);
            }
        });
        delegate.requestTakeOfferSuccess.addListener((ov, oldValue, newValue) -> {
            isTakeOfferButtonVisible.set(!newValue);
            isTakeOfferSpinnerVisible.set(false);
        });
    }

    private void setupBindings() {
        volume.bind(createStringBinding(() -> formatter.formatFiatWithCode(delegate.volumeAsFiat.get()),
                delegate.volumeAsFiat));
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(delegate.totalToPayAsCoin.get()),
                delegate.totalToPayAsCoin));
        securityDeposit.bind(createStringBinding(() -> formatter.formatCoinWithCode(delegate.securityDepositAsCoin.get()),
                delegate.securityDepositAsCoin));

        totalToPayAsCoin.bind(delegate.totalToPayAsCoin);

        requestTakeOfferErrorMessage.bind(delegate.requestTakeOfferErrorMessage);
        showTransactionPublishedScreen.bind(delegate.requestTakeOfferSuccess);
        transactionId.bind(delegate.transactionId);

        btcCode.bind(delegate.btcCode);
    }

    private void calculateVolume() {
        setAmountToModel();
        delegate.calculateVolume();
    }

    private void setAmountToModel() {
        delegate.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void updateButtonDisableState() {
        isTakeOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid &&
                        delegate.isMinAmountLessOrEqualAmount() &&
                        !delegate.isAmountLargerThanOfferAmount() &&
                        delegate.isWalletFunded.get())
        );
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

}
