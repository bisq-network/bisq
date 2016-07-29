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
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.deposit.DepositView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.main.settings.preferences.PreferencesView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import java.util.Calendar;
import java.util.Date;

import static com.google.common.math.LongMath.checkedPow;
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
    final StringProperty priceAsPercentage = new SimpleStringProperty();

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
    final BooleanProperty showWarningAdjustedVolume = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidFiatDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty showWarningInvalidBtcDecimalPlaces = new SimpleBooleanProperty();
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();
    final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new
            SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    final ObjectProperty<Address> address = new SimpleObjectProperty<>();

    private ChangeListener<String> amountListener;
    private ChangeListener<String> minAmountListener;
    private ChangeListener<String> priceListener, marketPriceMarginListener;
    private ChangeListener<String> volumeListener;
    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Fiat> priceAsFiatListener;
    private ChangeListener<Fiat> volumeAsFiatListener;
    private ChangeListener<Boolean> isWalletFundedListener;
    //private ChangeListener<Coin> feeFromFundingTxListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;
    private Timer timeoutTimer;
    private PriceFeedService.Type priceFeedType;
    private boolean inputIsMarketBasedPrice;
    private ChangeListener<Boolean> useMarketBasedPriceListener;
    private ChangeListener<String> currencyCodeListener;


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
            amount.set("0.0001");
            minAmount.set(amount.get());
            price.set("600");
            volume.set("0.12");

            setAmountToModel();
            setMinAmountToModel();
            setPriceToModel();
            calculateVolume();

            dataModel.calculateTotalToPay();
            updateButtonDisableState();
            updateSpinnerInfo();
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

        //TODO remove after AUGUST, 30
        applyCurrencyCode(dataModel.getTradeCurrency().getCode());
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


        tradeAmount.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.amountAsCoin.get()),
                dataModel.amountAsCoin));


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

                if (!inputIsMarketBasedPrice) {
                    MarketPrice marketPrice = priceFeedService.getMarketPrice(dataModel.tradeCurrencyCode.get());
                    if (marketPrice != null) {
                        double marketPriceAsDouble = formatter.roundDouble(marketPrice.getPrice(priceFeedType), 2);
                        try {
                            double priceAsDouble = formatter.parseNumberStringToDouble(price.get());
                            double relation = priceAsDouble / marketPriceAsDouble;
                            relation = formatter.roundDouble(relation, 2);
                            double marketPriceMargin = dataModel.getDirection() == Offer.Direction.BUY ? 1 - relation : relation - 1;
                            priceAsPercentage.set(formatter.formatToPercent(marketPriceMargin, 2));
                        } catch (NumberFormatException t) {
                            priceAsPercentage.set("");
                            new Popup().warning("Your input is not a valid number.")
                                    .show();
                        }
                    } else {
                        log.warn("We don't have a market price. We use the static price instead.");
                    }
                }
            }
            updateButtonDisableState();
        };
        marketPriceMarginListener = (ov, oldValue, newValue) -> {
            if (inputIsMarketBasedPrice) {
                try {
                    if (!newValue.isEmpty() && !newValue.equals("-")) {
                        double marketPriceMargin = formatter.parsePercentStringToDouble(newValue);
                        if (marketPriceMargin >= 1 || marketPriceMargin <= -1) {
                            dataModel.setMarketPriceMargin(0);
                            UserThread.execute(() -> priceAsPercentage.set("0"));
                            new Popup().warning("You cannot set a percentage of 100% or larger. Please enter a percentage number like \"5.4\" for 5.4%")
                                    .show();
                        } else {
                            MarketPrice marketPrice = priceFeedService.getMarketPrice(dataModel.tradeCurrencyCode.get());
                            if (marketPrice != null) {
                                marketPriceMargin = formatter.roundDouble(marketPriceMargin, 4);
                                dataModel.setMarketPriceMargin(marketPriceMargin);
                                Offer.Direction direction = dataModel.getDirection();
                                double marketPriceAsDouble = formatter.roundDouble(marketPrice.getPrice(priceFeedType), 2);
                                double factor = direction == Offer.Direction.BUY ? 1 - marketPriceMargin : 1 + marketPriceMargin;
                                double targetPrice = formatter.roundDouble(marketPriceAsDouble * factor, 2);
                                price.set(formatter.formatToNumberString(targetPrice, 2));
                                setPriceToModel();
                                calculateVolume();
                                dataModel.calculateTotalToPay();
                                updateButtonDisableState();
                            } else {
                                new Popup().warning("There is no price feed available for that currency. You cannot use percent based price.")
                                        .show();
                            }
                        }
                    } else {
                        dataModel.setMarketPriceMargin(0);
                    }
                } catch (Throwable t) {
                    dataModel.setMarketPriceMargin(0);
                    UserThread.execute(() -> priceAsPercentage.set("0"));
                    new Popup().warning("Your input is not a valid number. Please enter a percentage number like \"5.4\" for 5.4%")
                            .show();
                }
            }
        };
        useMarketBasedPriceListener = (observable, oldValue, newValue) -> {
            if (newValue)
                priceValidationResult.set(new InputValidator.ValidationResult(true));
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
       /* feeFromFundingTxListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };*/


        currencyCodeListener = (observable, oldValue, newValue) -> applyCurrencyCode(newValue);
    }

    //TODO remove after AUGUST, 30
    private void applyCurrencyCode(String newValue) {
        String key = "ETH-ETC-Warning";
        if (preferences.showAgain(key) && new Date().before(new Date(2016 - 1900, Calendar.AUGUST, 30))) {
            if (newValue.equals("ETC")) {
                new Popup().information("The EHT/ETC fork situation carries considerable risks.\n" +
                        "Be sure you fully understand the situation and check out the information on the \"Ethereum Classic\" and \"Ethereum\" project web pages.\n\n" +
                        "Please note, that the price is denominated as ETC/BTC not BTC/ETC!")
                        .closeButtonText("I understand")
                        .onAction(() -> GUIUtil.openWebPage("https://ethereumclassic.github.io/"))
                        .actionButtonText("Open Ethereum Classic web page")
                        .dontShowAgainId(key, preferences)
                        .show();
            }
        }
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountListener);
        minAmount.addListener(minAmountListener);
        price.addListener(priceListener);
        priceAsPercentage.addListener(marketPriceMarginListener);
        dataModel.useMarketBasedPrice.addListener(useMarketBasedPriceListener);
        volume.addListener(volumeListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.addListener(amountAsCoinListener);
        dataModel.minAmountAsCoin.addListener(minAmountAsCoinListener);
        dataModel.priceAsFiat.addListener(priceAsFiatListener);
        dataModel.volumeAsFiat.addListener(volumeAsFiatListener);

        // dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.addListener(isWalletFundedListener);

        //TODO remove after AUGUST, 30
        dataModel.tradeCurrencyCode.addListener(currencyCodeListener);
    }

    private void removeListeners() {
        amount.removeListener(amountListener);
        minAmount.removeListener(minAmountListener);
        price.removeListener(priceListener);
        priceAsPercentage.removeListener(marketPriceMarginListener);
        dataModel.useMarketBasedPrice.removeListener(useMarketBasedPriceListener);
        volume.removeListener(volumeListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amountAsCoin.removeListener(amountAsCoinListener);
        dataModel.minAmountAsCoin.removeListener(minAmountAsCoinListener);
        dataModel.priceAsFiat.removeListener(priceAsFiatListener);
        dataModel.volumeAsFiat.removeListener(volumeAsFiatListener);

        //dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.removeListener(isWalletFundedListener);

        if (offer != null && errorMessageListener != null)
            offer.errorMessageProperty().removeListener(errorMessageListener);

        //TODO remove after AUGUST, 30
        dataModel.tradeCurrencyCode.removeListener(currencyCodeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = dataModel.initWithData(direction, tradeCurrency);
        if (dataModel.paymentAccount != null)
            btcValidator.setMaxTradeLimitInBitcoin(dataModel.paymentAccount.getPaymentMethod().getMaxTradeLimit());

        priceFeedType = direction == Offer.Direction.BUY ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;

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
                showWarningInvalidBtcDecimalPlaces.set(!formatter.hasBtcValidDecimals(userInput));
                // only allow max 4 decimal places for btc values
                setAmountToModel();
                // reformat input
                amount.set(formatter.formatCoin(dataModel.amountAsCoin.get()));

                calculateVolume();

                // handle minAmount/amount relationship
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
                showWarningInvalidBtcDecimalPlaces.set(!formatter.hasBtcValidDecimals(userInput));
                setMinAmountToModel();
                minAmount.set(formatter.formatCoin(dataModel.minAmountAsCoin.get()));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    amount.set(minAmount.get());
                   /* minAmountValidationResult.set(new InputValidator.ValidationResult(false,
                            BSResources.get("createOffer.validation.minAmountLargerThanAmount")));*/
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

    void onFocusOutPriceAsPercentageTextField(boolean oldValue, boolean newValue, String userInput) {
        inputIsMarketBasedPrice = !oldValue && newValue;
        if (oldValue && !newValue)
            priceAsPercentage.set(formatter.formatToNumberString(dataModel.getMarketPriceMargin() * 100, 2));
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

    public boolean isPriceInRange() {
        MarketPrice marketPrice = priceFeedService.getMarketPrice(getTradeCurrency().getCode());
        if (marketPrice != null) {
            double marketPriceAsDouble = marketPrice.getPrice(priceFeedType);
            Fiat priceAsFiat = dataModel.priceAsFiat.get();
            long shiftDivisor = checkedPow(10, priceAsFiat.smallestUnitExponent());
            double offerPrice = ((double) priceAsFiat.longValue()) / ((double) shiftDivisor);
            double percentage = Math.abs(1 - (offerPrice / marketPriceAsDouble));
            percentage = formatter.roundDouble(percentage, 2);
            if (marketPriceAsDouble != 0 && percentage > preferences.getMaxPriceDistanceInPercent()) {
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
                isFiatInputValid(price.get()).isValid &&
                dataModel.priceAsFiat.get() != null &&
                dataModel.priceAsFiat.get().getValue() != 0 &&
                isFiatInputValid(volume.get()).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        //isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || notSufficientFees);
        isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || !dataModel.isWalletFunded.get());
    }


    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
