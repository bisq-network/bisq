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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createStringBinding;

class TakeOfferPM extends PresentationModel<TakeOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(TakeOfferPM.class);

    private String offerFee;
    private String networkFee;
    private String fiatCode;
    private String amountRange;
    private String price;
    private String directionLabel;
    private String collateralLabel;
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
    private BSFormatter formatter;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty collateral = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty requestTakeOfferErrorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();


    final BooleanProperty isTakeOfferButtonVisible = new SimpleBooleanProperty(false);
    final BooleanProperty isTakeOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();

    // Needed for the addressTextField
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // non private for testing
    @Inject
    TakeOfferPM(TakeOfferModel model, BtcValidator btcValidator, BSFormatter formatter) {
        super(model);

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
        offerFee = formatter.formatCoinWithCode(model.offerFeeAsCoin.get());
        networkFee = formatter.formatCoinWithCode(model.networkFeeAsCoin.get());

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
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    // setOrderBookFilter is a one time call
    void initWithData(Direction direction, Coin amount, Offer offer) {
        model.initWithData(amount, offer);

        directionLabel = direction == Direction.BUY ?
                BSResources.get("shared.buy") : BSResources.get("shared.sell");

        fiatCode = offer.getCurrency().getCurrencyCode();
        if (!model.isMinAmountLessOrEqualAmount()) {
            amountValidationResult.set(new InputValidator.ValidationResult(false,
                    BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));
        }

        updateButtonDisableState();

        //model.volumeAsFiat.set(offer.getVolumeByAmount(model.amountAsCoin.get()));

        amountRange = formatter.formatCoinWithCode(offer.getMinAmount()) + " - " +
                formatter.formatCoinWithCode(offer.getAmount());
        price = formatter.formatFiatWithCode(offer.getPrice());

        paymentLabel = BSResources.get("takeOffer.fundsBox.paymentLabel", offer.getId());
        if (model.getAddressEntry() != null) {
            addressAsString = model.getAddressEntry().getAddress().toString();
            address.set(model.getAddressEntry().getAddress());
        }
        collateralLabel = BSResources.get("takeOffer.fundsBox.collateral",
                formatter.formatCollateralPercent(offer.getCollateral()));

        acceptedCountries = formatter.countryLocalesToString(offer.getAcceptedCountries());
        acceptedLanguages = formatter.languageLocalesToString(offer.getAcceptedLanguageLocales());
        acceptedArbitrators = formatter.arbitratorsToString(offer.getArbitrators());
        bankAccountType = BSResources.get(offer.getBankAccountType().toString());
        bankAccountCurrency = BSResources.get(offer.getCurrency().getDisplayName());
        bankAccountCounty = BSResources.get(offer.getBankAccountCountry().getName());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void takeOffer() {
        model.requestTakeOfferErrorMessage.set(null);
        model.requestTakeOfferSuccess.set(false);

        isTakeOfferButtonDisabled.set(true);

        model.takeOffer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI events
    ///////////////////////////////////////////////////////////////////////////////////////////

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
                amount.set(formatter.formatCoin(model.amountAsCoin.get()));

                calculateVolume();

                if (!model.isMinAmountLessOrEqualAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountSmallerThanMinAmount")));

                if (model.isAmountLargerThanOfferAmount())
                    amountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("takeOffer.validation.amountLargerThanOfferAmount")));
            }
        }
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
        return formatter.formatCoinWithCode(model.amountAsCoin.get());
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

    String getCollateralLabel() {
        return collateralLabel;
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

        model.isWalletFunded.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                updateButtonDisableState();
        });

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        model.amountAsCoin.addListener((ov, oldValue, newValue) -> amount.set(formatter.formatCoin(newValue)));

        model.requestTakeOfferErrorMessage.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                isTakeOfferButtonDisabled.set(false);
        });
        model.requestTakeOfferSuccess.addListener((ov, oldValue, newValue) -> isTakeOfferButtonVisible.set
                (!newValue));
    }

    private void setupBindings() {
        volume.bind(createStringBinding(() -> formatter.formatFiatWithCode(model.volumeAsFiat.get()),
                model.volumeAsFiat));
        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(model.totalToPayAsCoin.get()),
                model.totalToPayAsCoin));
        collateral.bind(createStringBinding(() -> formatter.formatCoinWithCode(model.collateralAsCoin.get()),
                model.collateralAsCoin));

        totalToPayAsCoin.bind(model.totalToPayAsCoin);

        requestTakeOfferErrorMessage.bind(model.requestTakeOfferErrorMessage);
        showTransactionPublishedScreen.bind(model.requestTakeOfferSuccess);
        transactionId.bind(model.transactionId);

        btcCode.bind(model.btcCode);
    }

    private void calculateVolume() {
        setAmountToModel();
        model.calculateVolume();
    }

    private void setAmountToModel() {
        model.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
    }

    private void updateButtonDisableState() {
        isTakeOfferButtonDisabled.set(!(isBtcInputValid(amount.get()).isValid &&
                        model.isMinAmountLessOrEqualAmount() &&
                        !model.isAmountLargerThanOfferAmount() &&
                        model.isWalletFunded.get())
        );
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

}
