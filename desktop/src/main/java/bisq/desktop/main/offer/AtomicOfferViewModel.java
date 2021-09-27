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

package bisq.desktop.main.offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.MonetaryValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;
import bisq.core.util.validation.InputValidator;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.util.Callback;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static javafx.beans.binding.Bindings.createStringBinding;

@Slf4j
public abstract class AtomicOfferViewModel<M extends AtomicOfferDataModel> extends ActivatableWithDataModel<M> {
    private final BtcValidator btcValidator;
    private final BsqValidator bsqValidator;
    protected final SecurityDepositValidator securityDepositValidator;
//    private final PriceFeedService priceFeedService;
    private final AccountAgeWitnessService accountAgeWitnessService;
//    private final Navigation navigation;
//    private final Preferences preferences;
    protected final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final FiatVolumeValidator fiatVolumeValidator;
    private final FiatPriceValidator fiatPriceValidator;
    private final AltcoinValidator altcoinValidator;
    protected final OfferUtil offerUtil;

    private String amountDescription;
//    private String addressAsString;
    private final String paymentLabel;
    private boolean createOfferRequested;

    public final StringProperty amount = new SimpleStringProperty();
    public final StringProperty minAmount = new SimpleStringProperty();

    // Price in the viewModel is always dependent on fiat/altcoin: Fiat Fiat/BTC, for altcoins we use inverted price.
    // The domain (dataModel) uses always the same price model (otherCurrencyBTC)
    // If we would change the price representation in the domain we would not be backward compatible
    public final StringProperty price = new SimpleStringProperty();
    final StringProperty tradeFee = new SimpleStringProperty();
    final StringProperty tradeFeeInBtcWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeInBsqWithFiat = new SimpleStringProperty();
    final StringProperty tradeFeeCurrencyCode = new SimpleStringProperty();
    final StringProperty tradeFeeDescription = new SimpleStringProperty();
    final BooleanProperty isTradeFeeVisible = new SimpleBooleanProperty(false);

    public final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty waitingForFundsText = new SimpleStringProperty("");

    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
//    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();
    private final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();

    private ChangeListener<String> amountStringListener;
    private ChangeListener<String> minAmountStringListener;
    private ChangeListener<String> priceStringListener;
    private ChangeListener<String> volumeStringListener;

    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;

//    private ChangeListener<Boolean> isWalletFundedListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;
    private Timer timeoutTimer;
    private boolean ignorePriceStringListener, ignoreVolumeStringListener, ignoreAmountStringListener;
//    private ChangeListener<Number> currenciesUpdateListener;
    protected boolean syncMinAmountWithAmount = true;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AtomicOfferViewModel(M dataModel,
                                FiatVolumeValidator fiatVolumeValidator,
                                FiatPriceValidator fiatPriceValidator,
                                AltcoinValidator altcoinValidator,
                                BtcValidator btcValidator,
                                BsqValidator bsqValidator,
                                SecurityDepositValidator securityDepositValidator,
//                                PriceFeedService priceFeedService,
                                AccountAgeWitnessService accountAgeWitnessService,
                                Navigation navigation,
                                Preferences preferences,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                BsqFormatter bsqFormatter,
                                OfferUtil offerUtil) {
        super(dataModel);

        this.fiatVolumeValidator = fiatVolumeValidator;
        this.fiatPriceValidator = fiatPriceValidator;
        this.altcoinValidator = altcoinValidator;
        this.btcValidator = btcValidator;
        this.bsqValidator = bsqValidator;
        this.securityDepositValidator = securityDepositValidator;
//        this.priceFeedService = priceFeedService;
        this.accountAgeWitnessService = accountAgeWitnessService;
//        this.navigation = navigation;
//        this.preferences = preferences;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        this.offerUtil = offerUtil;

        paymentLabel = Res.get("createOffer.fundsBox.paymentLabel", dataModel.shortOfferId);

//        if (dataModel.getAddressEntry() != null) {
//            addressAsString = dataModel.getAddressEntry().getAddressString();
//        }
        createListeners();
    }

    @Override
    public void activate() {
        if (DevEnv.isDevMode()) {
            UserThread.runAfter(() -> {
                amount.set("0.001");
                price.set("70000");
                minAmount.set(amount.get());
                applyMakerFee();
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
            final String currencyCode = dataModel.getTradeCurrencyCode().get();
            if (!ignorePriceStringListener) {
                if (isPriceInputValid(newValue).isValid) {
                    setPriceToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();
                }
            }
            updateButtonDisableState();
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
            if (newValue != null) {
                amount.set(btcFormatter.formatCoin(newValue));
            } else {
                amount.set("");
            }

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
                price.set(FormattingUtils.formatPrice(newValue));
            else
                price.set("");

            ignorePriceStringListener = false;
            applyMakerFee();
        };
        volumeListener = (ov, oldValue, newValue) -> {
            ignoreVolumeStringListener = true;
            if (newValue != null)
                volume.set(DisplayUtils.formatVolume(newValue));
            else
                volume.set("");

            ignoreVolumeStringListener = false;
            applyMakerFee();
        };

//        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();

//        currenciesUpdateListener = (observable, oldValue, newValue) -> {
//            updateButtonDisableState();
//        };
    }

    private void applyMakerFee() {
        tradeFeeCurrencyCode.set(dataModel.isCurrencyForMakerFeeBtc() ? Res.getBaseCurrencyCode() : "BSQ");
        tradeFeeDescription.set(DevEnv.isDaoActivated() ? Res.get("createOffer.tradeFee.descriptionBSQEnabled") :
                Res.get("createOffer.tradeFee.descriptionBTCOnly"));

        Coin makerFeeAsCoin = dataModel.getMakerFee();
        if (makerFeeAsCoin == null) {
            return;
        }

        isTradeFeeVisible.setValue(true);
        tradeFee.set(getFormatterForMakerFee().formatCoin(makerFeeAsCoin));
        tradeFeeInBtcWithFiat.set(FeeUtil.getTradeFeeWithFiatEquivalent(offerUtil,
                dataModel.getMakerFeeInBtc(),
                true,
                btcFormatter));
        tradeFeeInBsqWithFiat.set(FeeUtil.getTradeFeeWithFiatEquivalent(offerUtil,
                dataModel.getMakerFeeInBsq(),
                false,
                bsqFormatter));
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountStringListener);
        minAmount.addListener(minAmountStringListener);
        price.addListener(priceStringListener);
        volume.addListener(volumeStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().addListener(amountAsCoinListener);
        dataModel.getMinAmount().addListener(minAmountAsCoinListener);
        dataModel.getPrice().addListener(priceListener);
        dataModel.getVolume().addListener(volumeListener);

//        priceFeedService.updateCounterProperty().addListener(currenciesUpdateListener);
    }

    private void removeListeners() {
        amount.removeListener(amountStringListener);
        minAmount.removeListener(minAmountStringListener);
        price.removeListener(priceStringListener);
        volume.removeListener(volumeStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.getAmount().removeListener(amountAsCoinListener);
        dataModel.getMinAmount().removeListener(minAmountAsCoinListener);
        dataModel.getPrice().removeListener(priceListener);
        dataModel.getVolume().removeListener(volumeListener);

        if (offer != null && errorMessageListener != null)
            offer.getErrorMessageProperty().removeListener(errorMessageListener);

//        priceFeedService.updateCounterProperty().removeListener(currenciesUpdateListener);
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
        amountDescription = Res.get("createOffer.amountPriceBox.amountDescription",
                isBuy ? Res.get("shared.buy") : Res.get("shared.sell"));

        securityDepositValidator.setPaymentAccount(dataModel.paymentAccount);

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

        securityDepositValidator.setPaymentAccount(paymentAccount);
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        dataModel.onCurrencySelected(tradeCurrency);
        updateButtonDisableState();
    }

    void onShowPayFundsScreen(Runnable actionHandler) {
        dataModel.updateEstimatedFeeAndTxVsize();
        dataModel.requestTxFee(actionHandler);
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

//    boolean fundFromSavingsWallet() {
//        dataModel.fundFromSavingsWallet();
//        if (dataModel.getIsBtcWalletFunded().get()) {
//            updateButtonDisableState();
//            return true;
//        } else {
//            new Popup().warning(Res.get("shared.notEnoughFunds",
//                    btcFormatter.formatCoinWithCode(dataModel.totalToPayAsCoinProperty().get()),
//                    btcFormatter.formatCoinWithCode(dataModel.getTotalAvailableBalance())))
//                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
//                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
//                    .show();
//            return false;
//        }
//
//    }

    public void setIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
        dataModel.setPreferredCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc);
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
            } else if (amount.get() != null && btcValidator.getMaxTradeLimit() != null && btcValidator.getMaxTradeLimit().value == OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value) {
                amount.set(btcFormatter.formatCoin(btcValidator.getMaxTradeLimit()));
                new Popup().information(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.buyer",
                        btcFormatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT),
                        Res.get("offerbook.warning.newVersionAnnouncement")))
                        .width(900)
                        .show();
            }
            // We want to trigger a recalculation of the volume
            UserThread.execute(() -> {
                onFocusOutVolumeTextField(true, false);
                onFocusOutMinAmountTextField(true, false);
            });
        }
    }

    public void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                Coin minAmountAsCoin = dataModel.getMinAmount().get();
                syncMinAmountWithAmount = minAmountAsCoin != null && minAmountAsCoin.equals(dataModel.getAmount().get());
                setMinAmountToModel();

                dataModel.calculateMinVolume();

                if (dataModel.getMinVolume().get() != null) {
                    InputValidator.ValidationResult minVolumeResult = isVolumeInputValid(
                            DisplayUtils.formatVolume(dataModel.getMinVolume().get()));

                    volumeValidationResult.set(minVolumeResult);

                    updateButtonDisableState();
                }

                this.minAmount.set(btcFormatter.formatCoin(minAmountAsCoin));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    this.amount.set(this.minAmount.get());
                } else {
                    minAmountValidationResult.set(result);
                    if (this.amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(this.amount.get()));
                }
            } else {
                syncMinAmountWithAmount = true;
            }
        }
    }

    void onFocusOutPriceTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isPriceInputValid(price.get());
            priceValidationResult.set(result);
            if (result.isValid) {
                setPriceToModel();
                ignorePriceStringListener = true;
                if (dataModel.getPrice().get() != null)
                    price.set(FormattingUtils.formatPrice(dataModel.getPrice().get()));
                ignorePriceStringListener = false;
                dataModel.calculateVolume();
                dataModel.calculateAmount();
                applyMakerFee();
            }

            // We want to trigger a recalculation of the volume and minAmount
            UserThread.execute(() -> {
                onFocusOutVolumeTextField(true, false);
                triggerFocusOutOnAmountFields();
            });
        }
    }

    public void triggerFocusOutOnAmountFields() {
        onFocusOutAmountTextField(true, false);
        onFocusOutMinAmountTextField(true, false);
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isVolumeInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                setVolumeToModel();
                ignoreVolumeStringListener = true;

                Volume volume = dataModel.getVolume().get();
                if (volume != null) {
                    // For HalCash we want multiple of 10 EUR
                    if (dataModel.paymentAccount.isHalCashAccount())
                        volume = VolumeUtil.getAdjustedVolumeForHalCash(volume);
                    else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                        volume = VolumeUtil.getRoundedFiatVolume(volume);

                    this.volume.set(DisplayUtils.formatVolume(volume));
                }

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

    public boolean isSellOffer() {
        return dataModel.getDirection() == OfferPayload.Direction.SELL;
    }

    public TradeCurrency getTradeCurrency() {
        return dataModel.getTradeCurrency();
    }

    public String getTradeAmount() {
        return btcFormatter.formatCoinWithCode(dataModel.getAmount().get());
    }

    public String getTradeFee() {
        if (dataModel.isCurrencyForMakerFeeBtc()) {
            return FeeUtil.getTradeFeeWithFiatEquivalentAndPercentage(offerUtil,
                    dataModel.getMakerFeeInBtc(),
                    dataModel.getAmount().get(),
                    true,
                    btcFormatter,
                    FeeService.getMinMakerFee(dataModel.isCurrencyForMakerFeeBtc()));
        } else {
            // For BSQ we use the fiat equivalent only. Calculating the % value would require to
            // calculate the BTC value of the BSQ fee and use that...
            return FeeUtil.getTradeFeeWithFiatEquivalent(offerUtil,
                    dataModel.getMakerFeeInBsq(),
                    false,
                    bsqFormatter);
        }
    }

    public String getMakerFeePercentage() {
        final Coin makerFeeAsCoin = dataModel.getMakerFee();
        if (dataModel.isCurrencyForMakerFeeBtc())
            return GUIUtil.getPercentage(makerFeeAsCoin, dataModel.getAmount().get());
        else
            return Res.get("dao.paidWithBsq");
    }

    public String getTotalToPayInfo() {
        final String totalToPay = this.totalToPay.get();
        if (dataModel.isCurrencyForMakerFeeBtc())
            return totalToPay;
        else
            return totalToPay + " + " + bsqFormatter.formatCoinWithCode(dataModel.getMakerFee());
    }

    public String getTxFee() {
        return FeeUtil.getTradeFeeWithFiatEquivalentAndPercentage(offerUtil,
                dataModel.getTxFee(),
                dataModel.getAmount().get(),
                true,
                btcFormatter,
                Coin.ZERO
        );
    }

    public String getTxFeePercentage() {
        Coin txFeeAsCoin = dataModel.getTxFee();
        return GUIUtil.getPercentage(txFeeAsCoin, dataModel.getAmount().get());
    }

    public PaymentAccount getPaymentAccount() {
        return dataModel.getPaymentAccount();
    }

    public String getAmountDescription() {
        return amountDescription;
    }

//    public String getAddressAsString() {
//        return addressAsString;
//    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    public Offer createAndGetOffer() {
        offer = dataModel.createAndGetOffer();
        return offer;
    }

    public Callback<ListView<PaymentAccount>, ListCell<PaymentAccount>> getPaymentAccountListCellFactory(
            ComboBox<PaymentAccount> paymentAccountsComboBox) {
        return GUIUtil.getPaymentAccountListCellFactory(paymentAccountsComboBox, accountAgeWitnessService);
    }

    public M getDataModel() {
        return dataModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setAmountToModel() {
        if (amount.get() != null && !amount.get().isEmpty()) {
            Coin amount = DisplayUtils.parseToCoinWith4Decimals(this.amount.get(), btcFormatter);

            long maxTradeLimit = dataModel.getMaxTradeLimit();
            Price price = dataModel.getPrice().get();
            if (price != null) {
                if (dataModel.paymentAccount.isHalCashAccount())
                    amount = CoinUtil.getAdjustedAmountForHalCash(amount, price, maxTradeLimit);
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    amount = CoinUtil.getRoundedFiatAmount(amount, price, maxTradeLimit);
            }
            dataModel.setAmount(amount);
            if (syncMinAmountWithAmount ||
                    dataModel.getMinAmount().get() == null ||
                    dataModel.getMinAmount().get().equals(Coin.ZERO)) {
                minAmount.set(this.amount.get());
                setMinAmountToModel();
            }
        } else {
            dataModel.setAmount(null);
        }
    }

    private void setMinAmountToModel() {
        if (minAmount.get() != null && !minAmount.get().isEmpty()) {
            Coin minAmount = DisplayUtils.parseToCoinWith4Decimals(this.minAmount.get(), btcFormatter);

            Price price = dataModel.getPrice().get();
            long maxTradeLimit = dataModel.getMaxTradeLimit();
            if (price != null) {
                if (dataModel.paymentAccount.isHalCashAccount())
                    minAmount = CoinUtil.getAdjustedAmountForHalCash(minAmount, price, maxTradeLimit);
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    minAmount = CoinUtil.getRoundedFiatAmount(minAmount, price, maxTradeLimit);
            }

            dataModel.setMinAmount(minAmount);
        } else {
            dataModel.setMinAmount(null);
        }
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

    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isPriceInputValid(String input) {
        return getPriceValidator().validate(input);
    }

    private InputValidator.ValidationResult isVolumeInputValid(String input) {
        return getVolumeValidator().validate(input);
    }

    private MonetaryValidator getPriceValidator() {
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
//        } else if (dataModel.getIsBtcWalletFunded().get()) {
//            waitingForFundsText.set("");
        } else {
            waitingForFundsText.set(Res.get("shared.waitingForFunds"));
        }

        isWaitingForFunds.set(!waitingForFundsText.get().isEmpty());
    }

    void updateButtonDisableState() {
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid &&
                isBtcInputValid(minAmount.get()).isValid &&
                isPriceInputValid(price.get()).isValid &&
                dataModel.getPrice().get() != null &&
                dataModel.getPrice().get().getValue() != 0 &&
                isVolumeInputValid(volume.get()).isValid &&
                isVolumeInputValid(DisplayUtils.formatVolume(dataModel.getMinVolume().get())).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isPlaceOfferButtonDisabled.set(!inputDataValid || !dataModel.getIsOfferFunded().get());
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    private CoinFormatter getFormatterForMakerFee() {
        return dataModel.isCurrencyForMakerFeeBtc() ? btcFormatter : bsqFormatter;
    }
}
