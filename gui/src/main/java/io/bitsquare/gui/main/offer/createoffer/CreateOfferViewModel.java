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

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import java.util.List;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDataModel<CreateOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final P2PService p2PService;
    private final BSFormatter formatter;
    private final FiatValidator fiatValidator;

    private String amountDescription;
    private String directionLabel;
    private String addressAsString;
    private final String paymentLabel;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty price = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();

    final BooleanProperty isPlaceOfferButtonVisible = new SimpleBooleanProperty(false);
    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty isPlaceOfferSpinnerVisible = new SimpleBooleanProperty(false);
    final BooleanProperty showWarningAdjustedVolume = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidFiatDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty requestPlaceOfferSuccess = new SimpleBooleanProperty();

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
    private ChangeListener<Coin> feeFromFundingTxListener;
    private ChangeListener<Boolean> requestPlaceOfferSuccessListener;
    private ChangeListener<String> requestPlaceOfferErrorMessageListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferViewModel(CreateOfferDataModel dataModel, FiatValidator fiatValidator, BtcValidator btcValidator,
                                P2PService p2PService,
                                BSFormatter formatter) {
        super(dataModel);

        this.fiatValidator = fiatValidator;
        this.btcValidator = btcValidator;
        this.p2PService = p2PService;
        this.formatter = formatter;

        paymentLabel = BSResources.get("createOffer.fundsBox.paymentLabel", dataModel.getOfferId());

        if (dataModel.getAddressEntry() != null) {
            addressAsString = dataModel.getAddressEntry().getAddress().toString();
            address.set(dataModel.getAddressEntry().getAddress());
        }
        createListeners();
    }

    @Override
    protected void activate() {
        if (BitsquareApp.DEV_MODE) {
            amount.set("0.01");
            minAmount.set(amount.get());
            price.set("400");
            volume.set("0.04");

            setAmountToModel();
            setMinAmountToModel();
            setPriceToModel();
            calculateVolume();
            dataModel.calculateTotalToPay();
            updateButtonDisableState();
        }

        addBindings();
        addListeners();

        updateButtonDisableState();

        if (dataModel.getDirection() == Offer.Direction.BUY) {
            directionLabel = BSResources.get("shared.buyBitcoin");
            amountDescription = BSResources.get("createOffer.amountPriceBox.amountDescription", BSResources.get("shared.buy"));
        } else {
            directionLabel = BSResources.get("shared.sellBitcoin");
            amountDescription = BSResources.get("createOffer.amountPriceBox.amountDescription", BSResources.get("shared.sell"));
        }
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        if (dataModel.getDirection() == Offer.Direction.BUY) {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> BSResources.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.tradeCurrencyCode.get()),
                    dataModel.tradeCurrencyCode));
        } else {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> BSResources.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.tradeCurrencyCode.get()),
                    dataModel.tradeCurrencyCode));
        }
        volumePromptLabel.bind(createStringBinding(
                () -> BSResources.get("createOffer.volume.prompt", dataModel.tradeCurrencyCode.get()),
                dataModel.tradeCurrencyCode));

        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()),
                dataModel.totalToPayAsCoin));


        tradeAmount.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.amountAsCoin.get()),
                dataModel.amountAsCoin));

        totalToPayAsCoin.bind(dataModel.totalToPayAsCoin);

        btcCode.bind(dataModel.btcCode);
        tradeCurrencyCode.bind(dataModel.tradeCurrencyCode);
    }

    private void removeBindings() {
        totalToPay.unbind();
        tradeAmount.unbind();
        totalToPayAsCoin.unbind();
        btcCode.unbind();
        tradeCurrencyCode.unbind();
        volumeDescriptionLabel.unbind();
        volumePromptLabel.unbind();
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

        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();
        feeFromFundingTxListener = (ov, oldValue, newValue) -> updateButtonDisableState();
        requestPlaceOfferSuccessListener = (ov, oldValue, newValue) -> {
            isPlaceOfferButtonVisible.set(!newValue);
            isPlaceOfferSpinnerVisible.set(false);
        };
        requestPlaceOfferErrorMessageListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                isPlaceOfferSpinnerVisible.set(false);
        };
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
        dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.addListener(isWalletFundedListener);
        requestPlaceOfferSuccess.addListener(requestPlaceOfferSuccessListener);
        errorMessage.addListener(requestPlaceOfferErrorMessageListener);

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

        dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.removeListener(isWalletFundedListener);
        requestPlaceOfferSuccess.removeListener(requestPlaceOfferSuccessListener);
        errorMessage.removeListener(requestPlaceOfferErrorMessageListener);

        if (offer != null && errorMessageListener != null)
            offer.errorMessageProperty().removeListener(errorMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        dataModel.initWithData(direction, tradeCurrency);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer(Offer offer) {
        errorMessage.set(null);
        isPlaceOfferSpinnerVisible.set(true);
        requestPlaceOfferSuccess.set(false);

        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (offer.getState() == Offer.State.OFFER_FEE_PAID)
                    this.errorMessage.set(newValue +
                            "\n\nThe offer fee is already paid. In the worst case you have lost that fee. " +
                            "We are sorry about that but keep in mind it is a very small amount.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.");
                else
                    this.errorMessage.set(newValue);
            }
        };
        offer.errorMessageProperty().addListener(errorMessageListener);
        dataModel.onPlaceOffer(offer, (transaction) -> requestPlaceOfferSuccess.set(true));
    }

    void onShowPayFundsScreen() {
        isPlaceOfferButtonVisible.set(true);
    }

    void onSecurityDepositInfoDisplayed() {
        dataModel.onSecurityDepositInfoDisplayed();
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        dataModel.onPaymentAccountSelected(paymentAccount);
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        dataModel.onCurrencySelected(tradeCurrency);
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
                } else {
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
                } else {
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
                showWarningInvalidFiatDecimalPlaces.set(!formatter.hasFiatValidDecimals(userInput, dataModel.tradeCurrencyCode.get()));
                setPriceToModel();
                price.set(formatter.formatFiat(dataModel.priceAsFiat.get()));

                calculateVolume();
            }
        }
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isFiatInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                showWarningInvalidFiatDecimalPlaces.set(!formatter.hasFiatValidDecimals(userInput, dataModel.tradeCurrencyCode.get()));
                setVolumeToModel();
                volume.set(formatter.formatFiat(dataModel.volumeAsFiat.get()));

                calculateAmount();

                // must be placed after calculateAmount (btc value has been adjusted in case the calculation leads to
                // invalid decimal places for the amount value
                showWarningAdjustedVolume.set(!formatter.formatFiat(formatter.parseToFiatWith2Decimals(userInput, dataModel.tradeCurrencyCode.get()))
                        .equals(volume
                                .get()));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    BSFormatter getFormatter() {
        return formatter;
    }

    boolean getDisplaySecurityDepositInfo() {
        return dataModel.getDisplaySecurityDepositInfo();
    }

    boolean isSellOffer() {
        return dataModel.getDirection() == Offer.Direction.SELL;
    }

    public ObservableList<PaymentAccount> getPaymentAccounts() {
        return dataModel.paymentAccounts;
    }

    public TradeCurrency getTradeCurrency() {
        return dataModel.getTradeCurrency();
    }

    public String getOfferFee() {
        return formatter.formatCoinWithCode(dataModel.getOfferFeeAsCoin());
    }

    public String getNetworkFee() {
        return formatter.formatCoinWithCode(dataModel.getNetworkFeeAsCoin());
    }

    public String getSecurityDeposit() {
        return formatter.formatCoinWithCode(dataModel.getSecurityDepositAsCoin());
    }

    public PaymentAccount getPaymentAccount() {
        return dataModel.getPaymentAccount();
    }

    public String getAmountDescription() {
        return amountDescription;
    }

    public String getDirectionLabel() {
        return directionLabel;
    }

    public String getAddressAsString() {
        return addressAsString;
    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    public String formatCoin(Coin coin) {
        return formatter.formatCoin(coin);
    }

    public Offer createAndGetOffer() {
        offer = dataModel.createAndGetOffer();
        return offer;
    }

    boolean hasAcceptedArbitrators() {
        return dataModel.hasAcceptedArbitrators();
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
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
        } else {
            if (amount.get() != null)
                amountValidationResult.set(isBtcInputValid(amount.get()));
            if (minAmount.get() != null)
                minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
        }
    }

    private void setAmountToModel() {
        dataModel.amountAsCoin.set(formatter.parseToCoinWith4Decimals(amount.get()));
        if (dataModel.minAmountAsCoin.get() == null || dataModel.minAmountAsCoin.get().equals(Coin.ZERO)) {
            minAmount.set(amount.get());
            setMinAmountToModel();
        }
    }

    private void setMinAmountToModel() {
        dataModel.minAmountAsCoin.set(formatter.parseToCoinWith4Decimals(minAmount.get()));
    }

    private void setPriceToModel() {
        dataModel.priceAsFiat.set(formatter.parseToFiatWith2Decimals(price.get(), dataModel.tradeCurrencyCode.get()));
    }

    private void setVolumeToModel() {
        dataModel.volumeAsFiat.set(formatter.parseToFiatWith2Decimals(volume.get(), dataModel.tradeCurrencyCode.get()));
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isFiatInputValid(String input) {
        return fiatValidator.validate(input);
    }

    private void updateButtonDisableState() {
        log.debug("updateButtonDisableState");
        isPlaceOfferButtonDisabled.set(
                !(isBtcInputValid(amount.get()).isValid &&
                        isBtcInputValid(minAmount.get()).isValid &&
                        isFiatInputValid(price.get()).isValid &&
                        isFiatInputValid(volume.get()).isValid &&
                        dataModel.isMinAmountLessOrEqualAmount() &&
                        dataModel.isWalletFunded.get() &&
                        dataModel.isFeeFromFundingTxSufficient())
        );
    }

    public List<Arbitrator> getArbitrators() {
        return dataModel.getArbitrators();
    }

    public void setShowPlaceOfferConfirmation(boolean selected) {
        dataModel.setShowPlaceOfferConfirmation(selected);
    }

    public boolean getShowPlaceOfferConfirmation() {
        return dataModel.getShowPlaceOfferConfirmation();
    }

}
