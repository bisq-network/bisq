/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.offer.createoffer;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.util.MathUtils;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.user.Preferences;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.ActivatableWithDataModel;
import io.bisq.gui.common.model.ViewModel;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.deposit.DepositView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.settings.SettingsView;
import io.bisq.gui.main.settings.preferences.PreferencesView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.validation.*;
import io.bisq.network.p2p.P2PService;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDataModel<CreateOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final BsqValidator bsqValidator;
    private final SecurityDepositValidator securityDepositValidator;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final Navigation navigation;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final FiatVolumeValidator fiatVolumeValidator;
    private final FiatPriceValidator fiatPriceValidator;
    private final AltcoinValidator altcoinValidator;

    private String amountDescription;
    private String directionLabel;
    private String addressAsString;
    private final String paymentLabel;
    private boolean createOfferRequested;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty buyerSecurityDeposit = new SimpleStringProperty();
    final String sellerSecurityDeposit;

    // Price in the viewModel is always dependent on fiat/altcoin: Fiat Fiat/BTC, for altcoins we use inverted price.
    // The domain (dataModel) uses always the same price model (otherCurrencyBTC)
    // If we would change the price representation in the domain we would not be backward compatible
    final StringProperty price = new SimpleStringProperty();
    final StringProperty makerFee = new SimpleStringProperty();
    final StringProperty makerFeeCurrencyCode = new SimpleStringProperty();

    // Positive % value means always a better price form the maker's perspective:
    // Buyer (with fiat): lower price as market
    // Buyer (with altcoin): higher (display) price as market (display price is inverted)
    final StringProperty marketPriceMargin = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty waitingForFundsText = new SimpleStringProperty("");

    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();
    private final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> buyerSecurityDepositValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    private final ObjectProperty<Address> address = new SimpleObjectProperty<>();

    private ChangeListener<String> amountStringListener;
    private ChangeListener<String> minAmountStringListener;
    private ChangeListener<String> priceStringListener, marketPriceMarginStringListener;
    private ChangeListener<String> volumeStringListener;
    private ChangeListener<String> securityDepositStringListener;

    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;
    private ChangeListener<Coin> securityDepositAsCoinListener;

    private ChangeListener<Boolean> isWalletFundedListener;
    //private ChangeListener<Coin> feeFromFundingTxListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;
    private Timer timeoutTimer;
    private boolean inputIsMarketBasedPrice;
    private ChangeListener<Boolean> useMarketBasedPriceListener;
    private boolean ignorePriceStringListener, ignoreVolumeStringListener, ignoreAmountStringListener, ignoreSecurityDepositStringListener;
    private MarketPrice marketPrice;
    final IntegerProperty marketPriceAvailableProperty = new SimpleIntegerProperty(-1);
    private ChangeListener<Number> currenciesUpdateListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferViewModel(CreateOfferDataModel dataModel,
                                FiatVolumeValidator fiatVolumeValidator,
                                FiatPriceValidator fiatPriceValidator,
                                AltcoinValidator altcoinValidator,
                                BtcValidator btcValidator,
                                BsqValidator bsqValidator,
                                SecurityDepositValidator securityDepositValidator,
                                P2PService p2PService,
                                PriceFeedService priceFeedService,
                                Preferences preferences,
                                Navigation navigation,
                                BSFormatter btcFormatter,
                                BsqFormatter bsqFormatter) {
        super(dataModel);

        this.fiatVolumeValidator = fiatVolumeValidator;
        this.fiatPriceValidator = fiatPriceValidator;
        this.altcoinValidator = altcoinValidator;
        this.btcValidator = btcValidator;
        this.bsqValidator = bsqValidator;
        this.securityDepositValidator = securityDepositValidator;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.navigation = navigation;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;

        paymentLabel = Res.get("createOffer.fundsBox.paymentLabel", dataModel.shortOfferId);
        sellerSecurityDeposit = btcFormatter.formatCoin(dataModel.getSellerSecurityDeposit());

        if (dataModel.getAddressEntry() != null) {
            addressAsString = dataModel.getAddressEntry().getAddressString();
            address.set(dataModel.getAddressEntry().getAddress());
        }
        createListeners();
    }

    @Override
    protected void activate() {
        if (DevEnv.DEV_MODE) {
            UserThread.runAfter(() -> {
                switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                    case "BTC":
                        amount.set("0.0001");
                        price.set("7000");
                        break;
                    case "LTC":
                        amount.set("50");
                        price.set("40");
                        break;
                    case "DOGE":
                        amount.set("800000");
                        price.set("0.003");
                        break;
                    case "DASH":
                        amount.set("0.1");
                        price.set("40");
                        break;
                }

                minAmount.set(amount.get());
                onFocusOutPriceAsPercentageTextField(true, false);
                applyMakerFee();
                updateButtonDisableState();
                setAmountToModel();
                setMinAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();
                dataModel.calculateTotalToPay();
                updateButtonDisableState();
                updateSpinnerInfo();
            }, 100, TimeUnit.MILLISECONDS);
        }

        addBindings();
        addListeners();

        updateButtonDisableState();

        updateMarketPriceAvailable();
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
        stopTimeoutTimer();
    }

    private void addBindings() {
        if (dataModel.getDirection() == OfferPayload.Direction.BUY) {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> Res.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.getTradeCurrencyCode().get()),
                    dataModel.getTradeCurrencyCode()));
        } else {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> Res.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.getTradeCurrencyCode().get()),
                    dataModel.getTradeCurrencyCode()));
        }
        volumePromptLabel.bind(createStringBinding(
                () -> Res.get("createOffer.volume.prompt", dataModel.getTradeCurrencyCode().get()),
                dataModel.getTradeCurrencyCode()));

        totalToPay.bind(createStringBinding(() -> btcFormatter.formatCoinWithCode(dataModel.totalToPayAsCoinProperty().get()),
                dataModel.totalToPayAsCoinProperty()));


        tradeAmount.bind(createStringBinding(() -> btcFormatter.formatCoinWithCode(dataModel.getAmount().get()),
                dataModel.getAmount()));


        tradeCurrencyCode.bind(dataModel.getTradeCurrencyCode());
    }

    private void removeBindings() {
        totalToPay.unbind();
        tradeAmount.unbind();
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
            updateMarketPriceAvailable();
            final String currencyCode = dataModel.getTradeCurrencyCode().get();
            if (!ignorePriceStringListener) {
                if (isPriceInputValid(newValue).isValid) {
                    setPriceToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();

                    if (!inputIsMarketBasedPrice) {
                        if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
                            double marketPriceAsDouble = marketPrice.getPrice();
                            try {
                                double priceAsDouble = btcFormatter.parseNumberStringToDouble(price.get());
                                double relation = priceAsDouble / marketPriceAsDouble;
                                final OfferPayload.Direction compareDirection = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                                        OfferPayload.Direction.SELL :
                                        OfferPayload.Direction.BUY;
                                double percentage = dataModel.getDirection() == compareDirection ? 1 - relation : relation - 1;
                                percentage = MathUtils.roundDouble(percentage, 4);
                                dataModel.setMarketPriceMargin(percentage);
                                marketPriceMargin.set(btcFormatter.formatToPercent(percentage));
                                applyMakerFee();
                            } catch (NumberFormatException t) {
                                marketPriceMargin.set("");
                                new Popup<>().warning(Res.get("validation.NaN")).show();
                            }
                        } else {
                            log.debug("We don't have a market price. We use the static price instead.");
                        }
                    }
                }
            }
            updateButtonDisableState();
        };
        marketPriceMarginStringListener = (ov, oldValue, newValue) -> {
            if (inputIsMarketBasedPrice) {
                try {
                    if (!newValue.isEmpty() && !newValue.equals("-")) {
                        double percentage = btcFormatter.parsePercentStringToDouble(newValue);
                        if (percentage >= 1 || percentage <= -1) {
                            new Popup<>().warning(Res.get("popup.warning.tooLargePercentageValue") + "\n" +
                                    Res.get("popup.warning.examplePercentageValue"))
                                    .show();
                        } else {
                            final String currencyCode = dataModel.getTradeCurrencyCode().get();
                            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
                            if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
                                percentage = MathUtils.roundDouble(percentage, 4);
                                double marketPriceAsDouble = marketPrice.getPrice();
                                final boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(currencyCode);
                                final OfferPayload.Direction compareDirection = isCryptoCurrency ?
                                        OfferPayload.Direction.SELL :
                                        OfferPayload.Direction.BUY;
                                double factor = dataModel.getDirection() == compareDirection ?
                                        1 - percentage :
                                        1 + percentage;
                                double targetPrice = marketPriceAsDouble * factor;
                                int precision = isCryptoCurrency ?
                                        Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
                                // protect from triggering unwanted updates
                                ignorePriceStringListener = true;
                                price.set(btcFormatter.formatRoundedDoubleWithPrecision(targetPrice, precision));
                                ignorePriceStringListener = false;
                                setPriceToModel();
                                dataModel.setMarketPriceMargin(percentage);
                                dataModel.calculateVolume();
                                dataModel.calculateTotalToPay();
                                updateButtonDisableState();
                                applyMakerFee();
                            } else {
                                new Popup<>().warning(Res.get("popup.warning.noPriceFeedAvailable")).show();
                                marketPriceMargin.set("");
                            }
                        }
                    }
                } catch (NumberFormatException t) {
                    log.error(t.toString());
                    t.printStackTrace();
                    new Popup<>().warning(Res.get("validation.NaN")).show();
                } catch (Throwable t) {
                    log.error(t.toString());
                    t.printStackTrace();
                    new Popup<>().warning(Res.get("validation.inputError", t.toString())).show();
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
        securityDepositStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreSecurityDepositStringListener) {
                if (securityDepositValidator.validate(newValue).isValid) {
                    setBuyerSecurityDepositToModel();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };


        amountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                amount.set(btcFormatter.formatCoin(newValue));
            else
                amount.set("");

            applyMakerFee();
        };
        minAmountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                minAmount.set(btcFormatter.formatCoin(newValue));
            else
                minAmount.set("");
        };
        priceListener = (ov, oldValue, newValue) -> {
            ignorePriceStringListener = true;
            if (newValue != null)
                price.set(btcFormatter.formatPrice(newValue));
            else
                price.set("");

            ignorePriceStringListener = false;
            applyMakerFee();
        };
        volumeListener = (ov, oldValue, newValue) -> {
            ignoreVolumeStringListener = true;
            if (newValue != null)
                volume.set(btcFormatter.formatVolume(newValue));
            else
                volume.set("");

            ignoreVolumeStringListener = false;
            applyMakerFee();
        };

        securityDepositAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                buyerSecurityDeposit.set(btcFormatter.formatCoin(newValue));
            else
                buyerSecurityDeposit.set("");
        };


        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();
       /* feeFromFundingTxListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };*/

        currenciesUpdateListener = (observable, oldValue, newValue) -> {
            updateMarketPriceAvailable();
            updateButtonDisableState();
        };
    }

    private void applyMakerFee() {
        makerFee.set(getFormatterForMakerFee().formatCoin(dataModel.getMakerFee()));
        makerFeeCurrencyCode.set(dataModel.isCurrencyForMakerFeeBtc() ? Res.getBaseCurrencyCode() : "BSQ");
    }

    private void updateMarketPriceAvailable() {
        marketPrice = priceFeedService.getMarketPrice(dataModel.getTradeCurrencyCode().get());
        marketPriceAvailableProperty.set(marketPrice == null || !marketPrice.isExternallyProvidedPrice() ? 0 : 1);
        dataModel.setMarketPriceAvailable(marketPrice != null && marketPrice.isExternallyProvidedPrice());
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountStringListener);
        minAmount.addListener(minAmountStringListener);
        price.addListener(priceStringListener);
        marketPriceMargin.addListener(marketPriceMarginStringListener);
        dataModel.getUseMarketBasedPrice().addListener(useMarketBasedPriceListener);
        volume.addListener(volumeStringListener);
        buyerSecurityDeposit.addListener(securityDepositStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().addListener(amountAsCoinListener);
        dataModel.getMinAmount().addListener(minAmountAsCoinListener);
        dataModel.getPrice().addListener(priceListener);
        dataModel.getVolume().addListener(volumeListener);
        dataModel.getBuyerSecurityDeposit().addListener(securityDepositAsCoinListener);

        // dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.getIsBtcWalletFunded().addListener(isWalletFundedListener);

        priceFeedService.updateCounterProperty().addListener(currenciesUpdateListener);
    }

    private void removeListeners() {
        amount.removeListener(amountStringListener);
        minAmount.removeListener(minAmountStringListener);
        price.removeListener(priceStringListener);
        marketPriceMargin.removeListener(marketPriceMarginStringListener);
        dataModel.getUseMarketBasedPrice().removeListener(useMarketBasedPriceListener);
        volume.removeListener(volumeStringListener);
        buyerSecurityDeposit.removeListener(securityDepositStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().removeListener(amountAsCoinListener);
        dataModel.getMinAmount().removeListener(minAmountAsCoinListener);
        dataModel.getPrice().removeListener(priceListener);
        dataModel.getVolume().removeListener(volumeListener);
        dataModel.getBuyerSecurityDeposit().removeListener(securityDepositAsCoinListener);

        //dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);
        dataModel.getIsBtcWalletFunded().removeListener(isWalletFundedListener);

        if (offer != null && errorMessageListener != null)
            offer.getErrorMessageProperty().removeListener(errorMessageListener);

        priceFeedService.updateCounterProperty().removeListener(currenciesUpdateListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = dataModel.initWithData(direction, tradeCurrency);
        if (dataModel.paymentAccount != null)
            btcValidator.setMaxValue(dataModel.paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(dataModel.getTradeCurrencyCode().get()));
        btcValidator.setMaxTradeLimit(Coin.valueOf(dataModel.getMaxTradeLimit()));
        btcValidator.setMinValue(Restrictions.getMinTradeAmount());

        final boolean isBuy = dataModel.getDirection() == OfferPayload.Direction.BUY;
        directionLabel = isBuy ? Res.get("shared.buyBitcoin") : Res.get("shared.sellBitcoin");
        amountDescription = Res.get("createOffer.amountPriceBox.amountDescription",
                isBuy ? Res.get("shared.buy") : Res.get("shared.sell"));

        buyerSecurityDeposit.set(btcFormatter.formatCoin(dataModel.getBuyerSecurityDeposit().get()));

        applyMakerFee();
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
                errorMessage.set(Res.get("createOffer.timeoutAtPublishing"));

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }, 60);
        }
        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                stopTimeoutTimer();
                createOfferRequested = false;
                if (offer.getState() == Offer.State.OFFER_FEE_PAID)
                    errorMessage.set(newValue + Res.get("createOffer.errorInfo"));
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
        dataModel.onPaymentAccountSelected(paymentAccount);
        if (amount.get() != null)
            amountValidationResult.set(isBtcInputValid(amount.get()));

        btcValidator.setMaxValue(dataModel.paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(dataModel.getTradeCurrencyCode().get()));
        btcValidator.setMaxTradeLimit(Coin.valueOf(dataModel.getMaxTradeLimit()));
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        dataModel.onCurrencySelected(tradeCurrency);

        marketPrice = priceFeedService.getMarketPrice(dataModel.getTradeCurrencyCode().get());
        marketPriceAvailableProperty.set(marketPrice == null || !marketPrice.isExternallyProvidedPrice() ? 0 : 1);
        updateButtonDisableState();
    }

    void onShowPayFundsScreen() {
        dataModel.estimateTxSize();
        dataModel.requestTxFee();
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

    boolean fundFromSavingsWallet() {
        dataModel.fundFromSavingsWallet();
        if (dataModel.getIsBtcWalletFunded().get()) {
            updateButtonDisableState();
            return true;
        } else {
            //noinspection unchecked
            new Popup<>().warning(Res.get("shared.notEnoughFunds",
                    btcFormatter.formatCoinWithCode(dataModel.totalToPayAsCoinProperty().get()),
                    btcFormatter.formatCoinWithCode(dataModel.totalAvailableBalance)))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
            return false;
        }

    }

    void setCurrencyForMakerFeeBtc(boolean currencyForMakerFeeBtc) {
        dataModel.setPreferredCurrencyForMakerFeeBtc(currencyForMakerFeeBtc);
        applyMakerFee();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                setAmountToModel();
                ignoreAmountStringListener = true;
                amount.set(btcFormatter.formatCoin(dataModel.getAmount().get()));
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

    void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                setMinAmountToModel();
                minAmount.set(btcFormatter.formatCoin(dataModel.getMinAmount().get()));

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

    void onFocusOutPriceTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isPriceInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                setPriceToModel();
                ignorePriceStringListener = true;
                if (dataModel.getPrice().get() != null)
                    price.set(btcFormatter.formatPrice(dataModel.getPrice().get()));
                ignorePriceStringListener = false;
                dataModel.calculateVolume();
                dataModel.calculateAmount();
                applyMakerFee();
            }
        }
    }

    void onFocusOutPriceAsPercentageTextField(boolean oldValue, boolean newValue) {
        inputIsMarketBasedPrice = !oldValue && newValue;
        if (oldValue && !newValue)
            marketPriceMargin.set(btcFormatter.formatRoundedDoubleWithPrecision(dataModel.getMarketPriceMargin() * 100, 2));
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isVolumeInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                setVolumeToModel();
                ignoreVolumeStringListener = true;
                if (dataModel.getVolume().get() != null)
                    volume.set(btcFormatter.formatVolume(dataModel.getVolume().get()));
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

    void onFocusOutBuyerSecurityDepositTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = securityDepositValidator.validate(buyerSecurityDeposit.get());
            buyerSecurityDepositValidationResult.set(result);
            if (result.isValid) {
                Coin defaultSecurityDeposit = Restrictions.getDefaultBuyerSecurityDeposit();
                String key = "buyerSecurityDepositLowerAsDefault";
                if (preferences.showAgain(key) &&
                        btcFormatter.parseToCoin(buyerSecurityDeposit.get()).compareTo(defaultSecurityDeposit) < 0) {
                    final String postfix = dataModel.isBuyOffer() ?
                            Res.get("createOffer.tooLowSecDeposit.makerIsBuyer") :
                            Res.get("createOffer.tooLowSecDeposit.makerIsSeller");
                    new Popup<>()
                            .warning(Res.get("createOffer.tooLowSecDeposit.warning",
                                    btcFormatter.formatCoinWithCode(defaultSecurityDeposit)) + "\n\n" + postfix)
                            .width(800)
                            .actionButtonText(Res.get("createOffer.resetToDefault"))
                            .onAction(() -> {
                                dataModel.setBuyerSecurityDeposit(defaultSecurityDeposit);
                                ignoreSecurityDepositStringListener = true;
                                buyerSecurityDeposit.set(btcFormatter.formatCoin(dataModel.getBuyerSecurityDeposit().get()));
                                ignoreSecurityDepositStringListener = false;
                            })
                            .closeButtonText(Res.get("createOffer.useLowerValue"))
                            .onClose(this::applyBuyerSecurityDepositOnFocusOut)
                            .dontShowAgainId(key)
                            .show();
                } else {
                    applyBuyerSecurityDepositOnFocusOut();
                }
            }
        }
    }

    private void applyBuyerSecurityDepositOnFocusOut() {
        setBuyerSecurityDepositToModel();
        ignoreSecurityDepositStringListener = true;
        buyerSecurityDeposit.set(btcFormatter.formatCoin(dataModel.getBuyerSecurityDeposit().get()));
        ignoreSecurityDepositStringListener = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isPriceInRange() {
        if (marketPriceMargin.get() != null && !marketPriceMargin.get().isEmpty()) {
            if (Math.abs(btcFormatter.parsePercentStringToDouble(marketPriceMargin.get())) > preferences.getMaxPriceDistanceInPercent()) {
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
        Popup popup = new Popup<>();
        //noinspection unchecked
        popup.warning(Res.get("createOffer.priceOutSideOfDeviation",
                btcFormatter.formatToPercentWithSymbol(preferences.getMaxPriceDistanceInPercent())))
                .actionButtonText(Res.get("createOffer.changePrice"))
                .onAction(popup::hide)
                .closeButtonTextWithGoTo("navigation.settings.preferences")
                .onClose(() -> navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class))
                .show();
    }

    BSFormatter getBtcFormatter() {
        return btcFormatter;
    }

    boolean isSellOffer() {
        return dataModel.getDirection() == OfferPayload.Direction.SELL;
    }

    public TradeCurrency getTradeCurrency() {
        return dataModel.getTradeCurrency();
    }

    public String getTradeAmount() {
        return btcFormatter.formatCoinWithCode(dataModel.getAmount().get());
    }

    public String getSecurityDepositInfo() {
        return btcFormatter.formatCoinWithCode(dataModel.getSecurityDeposit()) +
                GUIUtil.getPercentageOfTradeAmount(dataModel.getSecurityDeposit(), dataModel.getAmount().get(), btcFormatter);
    }

    public String getMakerFee() {
        //TODO use last bisq market price to estimate BSQ val
        final Coin makerFeeAsCoin = dataModel.getMakerFee();
        final String makerFee = getFormatterForMakerFee().formatCoinWithCode(makerFeeAsCoin);
        if (dataModel.isCurrencyForMakerFeeBtc())
            return makerFee + GUIUtil.getPercentageOfTradeAmount(makerFeeAsCoin, dataModel.getAmount().get(), btcFormatter);
        else
            return makerFee + " (" + Res.get("shared.tradingFeeInBsqInfo", btcFormatter.formatCoinWithCode(makerFeeAsCoin)) + ")";
    }

    public String getTotalToPayInfo() {
        final String totalToPay = this.totalToPay.get();
        if (dataModel.isCurrencyForMakerFeeBtc())
            return totalToPay;
        else
            return totalToPay + " + " + bsqFormatter.formatCoinWithCode(dataModel.getMakerFee());
    }

    public String getTxFee() {
        Coin txFeeAsCoin = dataModel.getTxFee();
        return btcFormatter.formatCoinWithCode(txFeeAsCoin) +
                GUIUtil.getPercentageOfTradeAmount(txFeeAsCoin, dataModel.getAmount().get(), btcFormatter);

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
        return btcFormatter.formatCoin(coin);
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
            dataModel.setAmount(btcFormatter.parseToCoinWith4Decimals(amount.get()));
            if (dataModel.getMinAmount().get() == null || dataModel.getMinAmount().get().equals(Coin.ZERO)) {
                minAmount.set(amount.get());
                setMinAmountToModel();
            }
        } else {
            dataModel.setAmount(null);
        }
    }

    private void setMinAmountToModel() {
        if (minAmount.get() != null && !minAmount.get().isEmpty())
            dataModel.setMinAmount(btcFormatter.parseToCoinWith4Decimals(minAmount.get()));
        else
            dataModel.setMinAmount(null);
    }

    private void setPriceToModel() {
        if (price.get() != null && !price.get().isEmpty()) {
            try {
                dataModel.setPrice(Price.parse(dataModel.getTradeCurrencyCode().get(), this.price.get()));
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.setPrice(null);
        }
    }

    private void setVolumeToModel() {
        if (volume.get() != null && !volume.get().isEmpty()) {
            try {
                dataModel.setVolume(Volume.parse(volume.get(), dataModel.getTradeCurrencyCode().get()));
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.setVolume(null);
        }
    }

    private void setBuyerSecurityDepositToModel() {
        if (buyerSecurityDeposit.get() != null && !buyerSecurityDeposit.get().isEmpty()) {
            dataModel.setBuyerSecurityDeposit(btcFormatter.parseToCoinWith4Decimals(buyerSecurityDeposit.get()));
        } else {
            dataModel.setBuyerSecurityDeposit(null);
        }
    }


    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isPriceInputValid(String input) {
        return getFiatPriceValidator().validate(input);
    }

    private InputValidator.ValidationResult isVolumeInputValid(String input) {
        return getVolumeValidator().validate(input);
    }

    private MonetaryValidator getFiatPriceValidator() {
        return CurrencyUtil.isCryptoCurrency(getTradeCurrency().getCode()) ? altcoinValidator : fiatPriceValidator;
    }

    private MonetaryValidator getVolumeValidator() {
        final String code = getTradeCurrency().getCode();
        if (CurrencyUtil.isCryptoCurrency(code)) {
            return code.equals("BSQ") ? bsqValidator : altcoinValidator;
        } else {
            return fiatVolumeValidator;
        }
    }

    private void updateSpinnerInfo() {
        if (!showPayFundsScreenDisplayed.get() ||
                errorMessage.get() != null ||
                showTransactionPublishedScreen.get()) {
            waitingForFundsText.set("");
        } else if (dataModel.getIsBtcWalletFunded().get()) {
            waitingForFundsText.set("");
           /* if (dataModel.isFeeFromFundingTxSufficient.get()) {
                spinnerInfoText.set("");
            } else {
                spinnerInfoText.set("Check if funding tx miner fee is sufficient...");
            }*/
        } else {
            waitingForFundsText.set(Res.get("shared.waitingForFunds"));
        }

        isWaitingForFunds.set(!waitingForFundsText.get().isEmpty());
    }

    private void updateButtonDisableState() {
        log.debug("updateButtonDisableState");
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid &&
                isBtcInputValid(minAmount.get()).isValid &&
                isPriceInputValid(price.get()).isValid &&
                securityDepositValidator.validate(buyerSecurityDeposit.get()).isValid &&
                dataModel.getPrice().get() != null &&
                dataModel.getPrice().get().getValue() != 0 &&
                isVolumeInputValid(volume.get()).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        //isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || notSufficientFees);
        isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || !dataModel.getIsBtcWalletFunded().get());
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    private BSFormatter getFormatterForMakerFee() {
        return dataModel.isCurrencyForMakerFeeBtc() ? btcFormatter : bsqFormatter;
    }
}
