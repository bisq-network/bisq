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
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import viewfx.model.ViewModel;
import viewfx.model.support.ActivatableWithDataModel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferViewModel extends ActivatableWithDataModel<TakeOfferDataModel> implements ViewModel {

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
    boolean detailsVisible;
    boolean advancedScreenInited;

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
    final ObjectProperty<Offer.State> offerIsAvailable = new SimpleObjectProperty<>(Offer.State.UNKNOWN);

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    // Needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();


    @Inject
    public TakeOfferViewModel(TakeOfferDataModel dataModel, BtcValidator btcValidator, BSFormatter formatter) {
        super(dataModel);

        this.btcValidator = btcValidator;
        this.formatter = formatter;

        this.offerFee = formatter.formatCoinWithCode(dataModel.offerFeeAsCoin.get());
        this.networkFee = formatter.formatCoinWithCode(dataModel.networkFeeAsCoin.get());

        setupBindings();
        setupListeners();
    }

    // setOfferBookFilter is a one time call
    void initWithData(Direction direction, Coin amount, Offer offer) {
        dataModel.initWithData(amount, offer);

        directionLabel = direction == Direction.BUY ?
                BSResources.get("shared.buy") : BSResources.get("shared.sell");

        fiatCode = offer.getCurrency().getCurrencyCode();
        if (!dataModel.isMinAmountLessOrEqualAmount()) {
            amountValidationResult.set(new InputValidator.ValidationResult(false,
                    BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));
        }

        updateButtonDisableState();

        //model.volumeAsFiat.set(offer.getVolumeByAmount(model.amountAsCoin.get()));

        amountRange = formatter.formatCoinWithCode(offer.getMinAmount()) + " - " +
                formatter.formatCoinWithCode(offer.getAmount());
        price = formatter.formatFiatWithCode(offer.getPrice());

        paymentLabel = BSResources.get("takeOffer.fundsBox.paymentLabel", offer.getId());
        if (dataModel.getAddressEntry() != null) {
            addressAsString = dataModel.getAddressEntry().getAddress().toString();
            address.set(dataModel.getAddressEntry().getAddress());
        }

        acceptedCountries = formatter.countryLocalesToString(offer.getAcceptedCountries());
        acceptedLanguages = formatter.languageLocalesToString(offer.getAcceptedLanguageLocales());
        acceptedArbitrators = formatter.arbitratorsToString(offer.getArbitrators());
        bankAccountType = BSResources.get(offer.getBankAccountType().toString());
        bankAccountCurrency = BSResources.get(offer.getCurrency().getDisplayName());
        bankAccountCounty = BSResources.get(offer.getBankAccountCountry().getName());
    }

    void takeOffer() {
        dataModel.requestTakeOfferErrorMessage.set(null);
        dataModel.requestTakeOfferSuccess.set(false);

        isTakeOfferButtonDisabled.set(true);
        isTakeOfferSpinnerVisible.set(true);

        dataModel.takeOffer();
    }

    void securityDepositInfoDisplayed() {
        dataModel.securityDepositInfoDisplayed();
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
                amount.set(formatter.formatCoin(dataModel.amountAsCoin.get()));

                calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));

                if (dataModel.isAmountLargerThanOfferAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountLargerThanOfferAmount")));
            }
        }
    }


    WalletService getWalletService() {
        return dataModel.getWalletService();
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
        return formatter.formatCoinWithCode(dataModel.amountAsCoin.get());
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
        return dataModel.displaySecurityDepositInfo();
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

        dataModel.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                updateButtonDisableState();
                tabIsClosable.set(false);
            }
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue)));

        dataModel.requestTakeOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                isTakeOfferButtonDisabled.set(false);
                isTakeOfferSpinnerVisible.set(false);
            }
        });
        dataModel.requestTakeOfferSuccess.addListener((ov, oldValue, newValue) -> {
            isTakeOfferButtonVisible.set(!newValue);
            isTakeOfferSpinnerVisible.set(false);
        });
    }

    private void setupBindings() {
        volume.bind(createStringBinding(() -> formatter.formatFiatWithCode(dataModel.volumeAsFiat.get()),
                dataModel.volumeAsFiat));
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()),
                dataModel.totalToPayAsCoin));
        securityDeposit.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.securityDepositAsCoin
                        .get()),
                dataModel.securityDepositAsCoin));

        totalToPayAsCoin.bind(dataModel.totalToPayAsCoin);

        requestTakeOfferErrorMessage.bind(dataModel.requestTakeOfferErrorMessage);
        showTransactionPublishedScreen.bind(dataModel.requestTakeOfferSuccess);
        transactionId.bind(dataModel.transactionId);
        offerIsAvailable.bind(dataModel.offerIsAvailable);

        btcCode.bind(dataModel.btcCode);
    }

    private void calculateVolume() {
        setAmountToModel();
        dataModel.calculateVolume();
    }

    private void setAmountToModel() {
        dataModel.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void updateButtonDisableState() {
        isTakeOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid &&
                        dataModel.isMinAmountLessOrEqualAmount() &&
                        !dataModel.isAmountLargerThanOfferAmount() &&
                        dataModel.isWalletFunded.get())
        );
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

}
