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

import io.bitsquare.app.DevFlags;
import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.MathUtils;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.deposit.DepositView;
import io.bitsquare.gui.main.offer.createoffer.monetary.Altcoin;
import io.bitsquare.gui.main.offer.createoffer.monetary.Price;
import io.bitsquare.gui.main.offer.createoffer.monetary.Volume;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.main.settings.preferences.PreferencesView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDataModel<CreateOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final P2PService p2PService;
    private PriceFeedService priceFeedService;
    private Preferences preferences;
    private Navigation navigation;
    final BSFormatter formatter;
    private final FiatValidator fiatValidator;

    private String amountDescription;
    private String directionLabel;
    private String addressAsString;
    private final String paymentLabel;
    private boolean createOfferRequested;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();

    // Price in the viewModel is always dependent on fiat/altcoin: Fiat Fiat/BTC, for altcoins we use inverted price.
    // The domain (dataModel) uses always the same price model (otherCurrencyBTC)
    // If we would change the price representation in the domain we would not be backward compatible
    final StringProperty price = new SimpleStringProperty();

    // Positive % value means always a better price form the offerers perspective: 
    // Buyer (with fiat): lower price as market
    // Buyer (with altcoin): higher (display) price as market (display price is inverted)
    final StringProperty marketPriceMargin = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty waitingForFundsText = new SimpleStringProperty("");

    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();

    private ChangeListener<String> amountStringListener;
    private ChangeListener<String> minAmountStringListener;
    private ChangeListener<String> priceStringListener, marketPriceMarginStringListener;
    private ChangeListener<String> volumeStringListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;
    private ChangeListener<Boolean> isWalletFundedListener;
    //private ChangeListener<Coin> feeFromFundingTxListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;
    private Timer timeoutTimer;
    private boolean inputIsMarketBasedPrice;
    private ChangeListener<Boolean> useMarketBasedPriceListener;
    private boolean ignorePriceStringListener, ignoreVolumeStringListener, ignoreAmountStringListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferViewModel(CreateOfferDataModel dataModel, FiatValidator fiatValidator, BtcValidator btcValidator,
                                P2PService p2PService, PriceFeedService priceFeedService, Preferences preferences, Navigation navigation,
                                BSFormatter formatter) {
        super(dataModel);

        this.fiatValidator = fiatValidator;
        this.btcValidator = btcValidator;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.navigation = navigation;
        this.formatter = formatter;

        paymentLabel = BSResources.get("createOffer.fundsBox.paymentLabel", dataModel.shortOfferId);

        if (dataModel.getAddressEntry() != null) {
            addressAsString = dataModel.getAddressEntry().getAddressString();
            address.set(dataModel.getAddressEntry().getAddress());
        }
        createListeners();
    }

    @Override
    protected void activate() {
        if (DevFlags.DEV_MODE) {
            UserThread.runAfter(() -> {
                amount.set("1");
                minAmount.set(amount.get());
                price.set("500");

                setAmountToModel();
                setMinAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();

                dataModel.calculateTotalToPay();
                updateButtonDisableState();
                updateSpinnerInfo();
            }, 10, TimeUnit.MILLISECONDS);
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
        stopTimeoutTimer();
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


        tradeAmount.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.amount.get()),
                dataModel.amount));


        btcCode.bind(dataModel.btcCode);
        tradeCurrencyCode.bind(dataModel.tradeCurrencyCode);
    }

    private void removeBindings() {
        totalToPay.unbind();
        tradeAmount.unbind();
        btcCode.unbind();
        tradeCurrencyCode.unbind();
        volumeDescriptionLabel.unbind();
        volumePromptLabel.unbind();
    }

    private void createListeners() {
        amountStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreAmountStringListener) {
                if (isBtcInputValid(newValue).isValid) {
                    setAmountToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };
        minAmountStringListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid)
                setMinAmountToModel();
            updateButtonDisableState();
        };
        priceStringListener = (ov, oldValue, newValue) -> {
            if (!ignorePriceStringListener) {
                if (isPriceInputValid(newValue).isValid) {
                    setPriceToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();

                    if (!inputIsMarketBasedPrice) {
                        final String currencyCode = dataModel.tradeCurrencyCode.get();
                        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
                        if (marketPrice != null) {
                            double marketPriceAsDouble = marketPrice.getPrice(getPriceFeedType());
                            try {
                                double priceAsDouble = formatter.parseNumberStringToDouble(price.get());
                                double relation = priceAsDouble / marketPriceAsDouble;
                                double percentage;
                                if (CurrencyUtil.isCryptoCurrency(currencyCode))
                                    percentage = dataModel.getDirection() == Offer.Direction.SELL ? 1 - relation : relation - 1;
                                else
                                    percentage = dataModel.getDirection() == Offer.Direction.BUY ? 1 - relation : relation - 1;
                                percentage = MathUtils.roundDouble(percentage, 4);
                                marketPriceMargin.set(formatter.formatToPercent(percentage));
                            } catch (NumberFormatException t) {
                                marketPriceMargin.set("");
                                new Popup().warning("Input is not a valid number.").show();
                            }
                        } else {
                            log.debug("We don't have a market price. We use the static price instead.");
                        }
                    }
                }
                updateButtonDisableState();
            }
        };
        marketPriceMarginStringListener = (ov, oldValue, newValue) -> {
            if (inputIsMarketBasedPrice) {
                try {
                    if (!newValue.isEmpty() && !newValue.equals("-")) {
                        double percentage = formatter.parsePercentStringToDouble(newValue);
                        if (percentage >= 1 || percentage <= -1) {
                            new Popup().warning("You cannot set a percentage of 100% or larger. Please enter a percentage number like \"5.4\" for 5.4%")
                                    .show();
                        } else {
                            final String currencyCode = dataModel.tradeCurrencyCode.get();
                            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
                            if (marketPrice != null) {
                                percentage = MathUtils.roundDouble(percentage, 4);
                                dataModel.setMarketPriceMargin(percentage);

                                double marketPriceAsDouble = marketPrice.getPrice(getPriceFeedType());
                                double factor;
                                if (CurrencyUtil.isCryptoCurrency(currencyCode))
                                    factor = dataModel.getDirection() == Offer.Direction.SELL ? 1 - percentage : 1 + percentage;
                                else
                                    factor = dataModel.getDirection() == Offer.Direction.BUY ? 1 - percentage : 1 + percentage;
                                double targetPrice = marketPriceAsDouble * factor;
                                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : 2;
                                ignorePriceStringListener = true;
                                price.set(formatter.formatRoundedDoubleWithPrecision(targetPrice, precision));
                                ignorePriceStringListener = false;
                                setPriceToModel();
                                dataModel.calculateVolume();
                                dataModel.calculateTotalToPay();
                                updateButtonDisableState();
                            } else {
                                new Popup().warning("There is no price feed available for that currency. You cannot use a percent based price.\n" +
                                        "Please select the fixed price.")
                                        .show();
                                marketPriceMargin.set("");
                            }
                        }
                    }
                } catch (Throwable t) {
                    new Popup().warning("Your input is not a valid number. Please enter a percentage number like \"5.4\" for 5.4%")
                            .show();
                }
            }
        };
        useMarketBasedPriceListener = (observable, oldValue, newValue) -> {
            if (newValue)
                priceValidationResult.set(new InputValidator.ValidationResult(true));
        };

        volumeStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreVolumeStringListener) {
                if (isVolumeInputValid(newValue).isValid) {
                    setVolumeToModel();
                    setPriceToModel();
                    dataModel.calculateAmount();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };
        amountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                amount.set(formatter.formatCoin(newValue));
            else
                amount.set("");
        };
        minAmountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                minAmount.set(formatter.formatCoin(newValue));
            else
                minAmount.set("");
        };
        priceListener = (ov, oldValue, newValue) -> {
            ignorePriceStringListener = true;
            if (newValue != null)
                price.set(newValue.toString());
            else
                price.set("");

            ignorePriceStringListener = false;
        };
        volumeListener = (ov, oldValue, newValue) -> {
            ignoreVolumeStringListener = true;
            if (newValue != null)
                volume.set(newValue.toString());
            else
                volume.set("");

            ignoreVolumeStringListener = false;
        };

        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();
       /* feeFromFundingTxListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };*/
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountStringListener);
        minAmount.addListener(minAmountStringListener);
        price.addListener(priceStringListener);
        marketPriceMargin.addListener(marketPriceMarginStringListener);
        dataModel.useMarketBasedPrice.addListener(useMarketBasedPriceListener);
        volume.addListener(volumeStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amount.addListener(amountAsCoinListener);
        dataModel.minAmount.addListener(minAmountAsCoinListener);
        dataModel.price.addListener(priceListener);
        dataModel.volume.addListener(volumeListener);

        // dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.addListener(isWalletFundedListener);
    }

    private void removeListeners() {
        amount.removeListener(amountStringListener);
        minAmount.removeListener(minAmountStringListener);
        price.removeListener(priceStringListener);
        marketPriceMargin.removeListener(marketPriceMarginStringListener);
        dataModel.useMarketBasedPrice.removeListener(useMarketBasedPriceListener);
        volume.removeListener(volumeStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amount.removeListener(amountAsCoinListener);
        dataModel.minAmount.removeListener(minAmountAsCoinListener);
        dataModel.price.removeListener(priceListener);
        dataModel.volume.removeListener(volumeListener);

        //dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.removeListener(isWalletFundedListener);

        if (offer != null && errorMessageListener != null)
            offer.errorMessageProperty().removeListener(errorMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = dataModel.initWithData(direction, tradeCurrency);
        if (dataModel.paymentAccount != null)
            btcValidator.setMaxTradeLimitInBitcoin(dataModel.paymentAccount.getPaymentMethod().getMaxTradeLimit());

        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer(Offer offer, Runnable resultHandler) {
        errorMessage.set(null);
        createOfferRequested = true;

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                stopTimeoutTimer();
                createOfferRequested = false;
                errorMessage.set("A timeout occurred at publishing the offer.");

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }, 30);
        }
        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                stopTimeoutTimer();
                createOfferRequested = false;
                if (offer.getState() == Offer.State.OFFER_FEE_PAID)
                    errorMessage.set(newValue +
                            "\n\nThe offer fee is already paid. In the worst case you have lost that fee. " +
                            "We are sorry about that but keep in mind it is a very small amount.\n" +
                            "Please try to restart you application and check your network connection to see if you can resolve the issue.");
                else
                    errorMessage.set(newValue);

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }
        };

        offer.errorMessageProperty().addListener(errorMessageListener);

        dataModel.onPlaceOffer(offer, transaction -> {
            stopTimeoutTimer();
            resultHandler.run();
            placeOfferCompleted.set(true);
            errorMessage.set(null);
        });

        updateButtonDisableState();
        updateSpinnerInfo();
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        btcValidator.setMaxTradeLimitInBitcoin(paymentAccount.getPaymentMethod().getMaxTradeLimit());
        dataModel.onPaymentAccountSelected(paymentAccount);
        if (amount.get() != null)
            amountValidationResult.set(isBtcInputValid(amount.get()));
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        dataModel.onCurrencySelected(tradeCurrency);
    }

    void onShowPayFundsScreen() {
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

    boolean fundFromSavingsWallet() {
        dataModel.fundFromSavingsWallet();
        if (dataModel.isWalletFunded.get()) {
            updateButtonDisableState();
            return true;
        } else {
            new Popup().warning("You don't have enough funds in your Bitsquare wallet.\n" +
                    "You need " + formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()) + " but you have only " +
                    formatter.formatCoinWithCode(dataModel.totalAvailableBalance) + " in your Bitsquare wallet.\n\n" +
                    "Please fund that trade from an external Bitcoin wallet or fund your Bitsquare " +
                    "wallet at \"Funds/Depost funds\".")
                    .actionButtonText("Go to \"Funds/Depost funds\"")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
            return false;
        }

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
                setAmountToModel();
                ignoreAmountStringListener = true;
                amount.set(formatter.formatCoin(dataModel.amount.get()));
                ignoreAmountStringListener = false;
                dataModel.calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    minAmount.set(amount.get());
                else
                    amountValidationResult.set(result);

                if (minAmount.get() != null)
                    minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
            }
        }
    }

    void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                setMinAmountToModel();
                minAmount.set(formatter.formatCoin(dataModel.minAmount.get()));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    amount.set(minAmount.get());
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
            InputValidator.ValidationResult result = isPriceInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                setPriceToModel();
                ignorePriceStringListener = true;
                if (dataModel.price.get() != null)
                    price.set(dataModel.price.get().toString());
                ignorePriceStringListener = false;
                dataModel.calculateVolume();
                dataModel.calculateAmount();
            }
        }
    }

    void onFocusOutPriceAsPercentageTextField(boolean oldValue, boolean newValue, String userInput) {
        inputIsMarketBasedPrice = !oldValue && newValue;
        if (oldValue && !newValue)
            marketPriceMargin.set(formatter.formatRoundedDoubleWithPrecision(dataModel.getMarketPriceMargin() * 100, 2));
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue, String userInput) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isVolumeInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                setVolumeToModel();
                ignoreVolumeStringListener = true;
                if (dataModel.volume.get() != null)
                    volume.set(dataModel.volume.get().toString());
                ignoreVolumeStringListener = false;

                dataModel.calculateAmount();

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    minAmount.set(amount.getValue());
                } else {
                    if (amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(amount.get()));

                    // We only check minAmountValidationResult if amountValidationResult is valid, otherwise we would get 
                    // triggered a close of the popup when the minAmountValidationResult is applied
                    if (amountValidationResult.getValue() != null && amountValidationResult.getValue().isValid && minAmount.get() != null)
                        minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isPriceInRange() {
        if (marketPriceMargin.get() != null && !marketPriceMargin.get().isEmpty()) {
            if (formatter.parsePercentStringToDouble(marketPriceMargin.get()) > preferences.getMaxPriceDistanceInPercent()) {
                displayPriceOutOfRangePopup();
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private void displayPriceOutOfRangePopup() {
        Popup popup = new Popup();
        popup.warning("The price you have entered is outside the max. allowed deviation from the market price.\n" +
                "The max. allowed deviation is " +
                formatter.formatToPercentWithSymbol(preferences.getMaxPriceDistanceInPercent()) +
                " and can be adjusted in the preferences.")
                .actionButtonText("Change price")
                .onAction(() -> popup.hide())
                .closeButtonText("Go to \"Preferences\"")
                .onClose(() -> navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class))
                .show();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    boolean isSellOffer() {
        return dataModel.getDirection() == Offer.Direction.SELL;
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

    private void setAmountToModel() {
        if (amount.get() != null && !amount.get().isEmpty()) {
            dataModel.amount.set(formatter.parseToCoinWith4Decimals(amount.get()));
            if (dataModel.minAmount.get() == null || dataModel.minAmount.get().equals(Coin.ZERO)) {
                minAmount.set(amount.get());
                setMinAmountToModel();
            }
        } else {
            dataModel.amount.set(null);
        }
    }

    private void setMinAmountToModel() {
        if (minAmount.get() != null && !minAmount.get().isEmpty())
            dataModel.minAmount.set(formatter.parseToCoinWith4Decimals(minAmount.get()));
        else
            dataModel.minAmount.set(null);
    }

    private void setPriceToModel() {
        if (price.get() != null && !price.get().isEmpty()) {
            try {
                final Price price = Price.parse(this.price.get(), dataModel.tradeCurrencyCode.get());
                dataModel.price.set(price);
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.price.set(null);
        }
    }

    private void setVolumeToModel() {
        if (volume.get() != null && !volume.get().isEmpty()) {
            try {
                final Volume value = Volume.parse(volume.get(), dataModel.tradeCurrencyCode.get());
                dataModel.volume.set(value);
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.volume.set(null);
        }
    }

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isPriceInputValid(String input) {
        if (CurrencyUtil.isCryptoCurrency(getTradeCurrency().getCode()))
            fiatValidator.setMinValue(0.00000001);
        else
            fiatValidator.setMinValue(FiatValidator.MIN_FIAT_VALUE);

        return fiatValidator.validate(input);
    }

    private InputValidator.ValidationResult isVolumeInputValid(String input) {
        if (CurrencyUtil.isCryptoCurrency(getTradeCurrency().getCode()))
            fiatValidator.setMinValue(0.01);
        else
            fiatValidator.setMinValue(FiatValidator.MIN_FIAT_VALUE);

        return fiatValidator.validate(input);
    }

    private void updateSpinnerInfo() {
        if (!showPayFundsScreenDisplayed.get() ||
                errorMessage.get() != null ||
                showTransactionPublishedScreen.get()) {
            waitingForFundsText.set("");
        } else if (dataModel.isWalletFunded.get()) {
            waitingForFundsText.set("");
           /* if (dataModel.isFeeFromFundingTxSufficient.get()) {
                spinnerInfoText.set("");
            } else {
                spinnerInfoText.set("Check if funding tx miner fee is sufficient...");
            }*/
        } else {
            waitingForFundsText.set("Waiting for funds...");
        }

        isWaitingForFunds.set(!waitingForFundsText.get().isEmpty());
    }

    private void updateButtonDisableState() {
        log.debug("updateButtonDisableState");
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid &&
                isBtcInputValid(minAmount.get()).isValid &&
                isPriceInputValid(price.get()).isValid &&
                dataModel.price.get() != null &&
                dataModel.price.get().getValue() != 0 &&
                isVolumeInputValid(volume.get()).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        //isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || notSufficientFees);
        isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || !dataModel.isWalletFunded.get());
    }

    private PriceFeedService.Type getPriceFeedType() {
        if (CurrencyUtil.isCryptoCurrency(tradeCurrencyCode.get()))
            return dataModel.getDirection() == Offer.Direction.BUY ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
        else
            return dataModel.getDirection() == Offer.Direction.SELL ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
