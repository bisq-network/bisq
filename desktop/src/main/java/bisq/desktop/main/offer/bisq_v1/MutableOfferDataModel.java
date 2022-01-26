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

package bisq.desktop.main.offer.bisq_v1;

import bisq.desktop.Navigation;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.CreateOfferService;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.bisq_v1.TransactionResultHandler;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Named;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.core.payment.payload.PaymentMethod.HAL_CASH_ID;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;

public abstract class MutableOfferDataModel extends OfferDataModel implements BsqBalanceListener {
    private final CreateOfferService createOfferService;
    protected final OpenOfferManager openOfferManager;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    protected final User user;
    private final P2PService p2PService;
    protected final PriceFeedService priceFeedService;
    final String shortOfferId;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final FeeService feeService;
    private final CoinFormatter btcFormatter;
    private final Navigation navigation;
    private final String offerId;
    private final BalanceListener btcBalanceListener;
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    protected OfferDirection direction;
    protected TradeCurrency tradeCurrency;
    protected final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    protected final BooleanProperty useMarketBasedPrice = new SimpleBooleanProperty();
    protected final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Coin> minAmount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> minVolume = new SimpleObjectProperty<>();

    // Percentage value of buyer security deposit. E.g. 0.01 means 1% of trade amount
    protected final DoubleProperty buyerSecurityDeposit = new SimpleDoubleProperty();

    protected final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    protected PaymentAccount paymentAccount;
    boolean isTabSelected;
    protected double marketPriceMargin = 0;
    private Coin txFeeFromFeeService = Coin.ZERO;
    @Getter
    private boolean marketPriceAvailable;
    private int feeTxVsize = TxFeeEstimationService.TYPICAL_TX_WITH_1_INPUT_VSIZE;
    protected boolean allowAmountUpdate = true;
    private final TradeStatisticsManager tradeStatisticsManager;

    private final Predicate<ObjectProperty<Coin>> isNonZeroAmount = (c) -> c.get() != null && !c.get().isZero();
    private final Predicate<ObjectProperty<Price>> isNonZeroPrice = (p) -> p.get() != null && !p.get().isZero();
    private final Predicate<ObjectProperty<Volume>> isNonZeroVolume = (v) -> v.get() != null && !v.get().isZero();
    @Getter
    protected long triggerPrice;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MutableOfferDataModel(CreateOfferService createOfferService,
                                 OpenOfferManager openOfferManager,
                                 OfferUtil offerUtil,
                                 BtcWalletService btcWalletService,
                                 BsqWalletService bsqWalletService,
                                 Preferences preferences,
                                 User user,
                                 P2PService p2PService,
                                 PriceFeedService priceFeedService,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 FeeService feeService,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                 TradeStatisticsManager tradeStatisticsManager,
                                 Navigation navigation) {
        super(btcWalletService, offerUtil);

        this.createOfferService = createOfferService;
        this.openOfferManager = openOfferManager;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.user = user;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.feeService = feeService;
        this.btcFormatter = btcFormatter;
        this.navigation = navigation;
        this.tradeStatisticsManager = tradeStatisticsManager;

        offerId = OfferUtil.getRandomOfferId();
        shortOfferId = Utilities.getShortId(offerId);
        addressEntry = btcWalletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.OFFER_FUNDING);

        useMarketBasedPrice.set(preferences.isUsePercentageBasedPrice());
        buyerSecurityDeposit.set(Restrictions.getMinBuyerSecurityDepositAsPercent());

        btcBalanceListener = new BalanceListener(getAddressEntry().getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        };

        paymentAccountsChangeListener = change -> fillPaymentAccounts();
    }

    @Override
    public void activate() {
        addListeners();

        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        updateBalance();
    }

    @Override
    protected void deactivate() {
        removeListeners();
    }

    private void addListeners() {
        btcWalletService.addBalanceListener(btcBalanceListener);
        bsqWalletService.addBsqBalanceListener(this);
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }

    private void removeListeners() {
        btcWalletService.removeBalanceListener(btcBalanceListener);
        bsqWalletService.removeBsqBalanceListener(this);
        user.getPaymentAccountsAsObservable().removeListener(paymentAccountsChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate()
    public boolean initWithData(OfferDirection direction, TradeCurrency tradeCurrency) {
        this.direction = direction;
        this.tradeCurrency = tradeCurrency;

        fillPaymentAccounts();

        PaymentAccount account;

        PaymentAccount lastSelectedPaymentAccount = getPreselectedPaymentAccount();
        if (lastSelectedPaymentAccount != null &&
                lastSelectedPaymentAccount.getTradeCurrencies().contains(tradeCurrency) &&
                user.getPaymentAccounts() != null &&
                user.getPaymentAccounts().stream().anyMatch(paymentAccount -> paymentAccount.getId().equals(lastSelectedPaymentAccount.getId()))) {
            account = lastSelectedPaymentAccount;
        } else {
            account = user.findFirstPaymentAccountWithCurrency(tradeCurrency);
        }

        if (account != null) {
            this.paymentAccount = account;
        } else {
            Optional<PaymentAccount> paymentAccountOptional = paymentAccounts.stream().findAny();
            if (paymentAccountOptional.isPresent()) {
                this.paymentAccount = paymentAccountOptional.get();

            } else {
                log.warn("PaymentAccount not available. Should never get called as in offer view you should not be able to open a create offer view");
                return false;
            }
        }

        setTradeCurrencyFromPaymentAccount(paymentAccount);
        tradeCurrencyCode.set(this.tradeCurrency.getCode());

        priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        // We request to get the actual estimated fee
        requestTxFee(null);

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But offer creation happens usually after that so we should have already the value from the estimation service.
        txFeeFromFeeService = feeService.getTxFee(feeTxVsize);

        calculateVolume();
        calculateTotalToPay();
        updateBalance();
        setSuggestedSecurityDeposit(getPaymentAccount());

        return true;
    }

    protected PaymentAccount getPreselectedPaymentAccount() {
        return preferences.getSelectedPaymentAccountForCreateOffer();
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Offer createAndGetOffer() {
        return createOfferService.createAndGetOffer(offerId,
                direction,
                tradeCurrencyCode.get(),
                amount.get(),
                minAmount.get(),
                price.get(),
                txFeeFromFeeService,
                useMarketBasedPrice.get(),
                marketPriceMargin,
                buyerSecurityDeposit.get(),
                paymentAccount);
    }

    // This works only if we have already funds in the wallet
    public void updateEstimatedFeeAndTxVsize() {
        Tuple2<Coin, Integer> estimatedFeeAndTxVsize = createOfferService.getEstimatedFeeAndTxVsize(amount.get(),
                direction,
                buyerSecurityDeposit.get(),
                createOfferService.getSellerSecurityDepositAsDouble(buyerSecurityDeposit.get()));
        txFeeFromFeeService = estimatedFeeAndTxVsize.first;
        feeTxVsize = estimatedFeeAndTxVsize.second;
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        openOfferManager.placeOffer(offer,
                buyerSecurityDeposit.get(),
                useSavingsWallet,
                triggerPrice,
                resultHandler,
                log::error);
    }

    void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null && !this.paymentAccount.equals(paymentAccount)) {
            preferences.setSelectedPaymentAccountForCreateOffer(paymentAccount);
            this.paymentAccount = paymentAccount;

            setTradeCurrencyFromPaymentAccount(paymentAccount);
            setSuggestedSecurityDeposit(getPaymentAccount());

            if (amount.get() != null && this.allowAmountUpdate)
                this.amount.set(Coin.valueOf(Math.min(amount.get().value, getMaxTradeLimit())));
        }
    }

    private void setSuggestedSecurityDeposit(PaymentAccount paymentAccount) {
        var minSecurityDeposit = Restrictions.getMinBuyerSecurityDepositAsPercent();
        try {
            if (getTradeCurrency() == null) {
                setBuyerSecurityDeposit(minSecurityDeposit);
                return;
            }
            // Get average historic prices over for the prior trade period equaling the lock time
            var blocksRange = Restrictions.getLockTime(paymentAccount.getPaymentMethod().isBlockchain());
            var startDate = new Date(System.currentTimeMillis() - blocksRange * 10L * 60000);
            var sortedRangeData = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                    .filter(e -> e.getCurrency().equals(getTradeCurrency().getCode()))
                    .filter(e -> e.getDate().compareTo(startDate) >= 0)
                    .sorted(Comparator.comparing(TradeStatistics3::getDate))
                    .collect(Collectors.toList());
            var movingAverage = new MathUtils.MovingAverage(10, 0.2);
            double[] extremes = {Double.MAX_VALUE, Double.MIN_VALUE};
            sortedRangeData.forEach(e -> {
                var price = e.getTradePrice().getValue();
                movingAverage.next(price).ifPresent(val -> {
                    if (val < extremes[0]) extremes[0] = val;
                    if (val > extremes[1]) extremes[1] = val;
                });
            });
            var min = extremes[0];
            var max = extremes[1];
            if (min == 0d || max == 0d) {
                setBuyerSecurityDeposit(minSecurityDeposit);
                return;
            }
            // Suggested deposit is double the trade range over the previous lock time period, bounded by min/max deposit
            var suggestedSecurityDeposit =
                    Math.min(2 * (max - min) / max, Restrictions.getMaxBuyerSecurityDepositAsPercent());
            buyerSecurityDeposit.set(Math.max(suggestedSecurityDeposit, minSecurityDeposit));
        } catch (Throwable t) {
            log.error(t.toString());
            buyerSecurityDeposit.set(minSecurityDeposit);
        }
    }

    private void setTradeCurrencyFromPaymentAccount(PaymentAccount paymentAccount) {
        if (!paymentAccount.getTradeCurrencies().contains(tradeCurrency))
            tradeCurrency = paymentAccount.getTradeCurrency().orElse(tradeCurrency);

        checkNotNull(tradeCurrency, "tradeCurrency must not be null");
        tradeCurrencyCode.set(tradeCurrency.getCode());
    }

    void onCurrencySelected(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            if (!this.tradeCurrency.equals(tradeCurrency)) {
                volume.set(null);
                minVolume.set(null);
                price.set(null);
                marketPriceMargin = 0;
            }

            this.tradeCurrency = tradeCurrency;
            final String code = this.tradeCurrency.getCode();
            tradeCurrencyCode.set(code);

            if (paymentAccount != null)
                paymentAccount.setSelectedTradeCurrency(tradeCurrency);

            priceFeedService.setCurrencyCode(code);

            Optional<TradeCurrency> tradeCurrencyOptional = preferences.getTradeCurrenciesAsObservable()
                    .stream().filter(e -> e.getCode().equals(code)).findAny();
            if (!tradeCurrencyOptional.isPresent()) {
                if (CurrencyUtil.isCryptoCurrency(code)) {
                    CurrencyUtil.getCryptoCurrency(code).ifPresent(preferences::addCryptoCurrency);
                } else {
                    CurrencyUtil.getFiatCurrency(code).ifPresent(preferences::addFiatCurrency);
                }
            }
        }
    }

    @Override
    public void onUpdateBalances(Coin availableBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance,
                                 Coin unlockingBondsBalance) {
        updateBalance();
    }

    void fundFromSavingsWallet() {
        this.useSavingsWallet = true;
        updateBalance();
        if (!isBtcWalletFunded.get()) {
            this.useSavingsWallet = false;
            updateBalance();
        }
    }

    protected void setMarketPriceMargin(double marketPriceMargin) {
        this.marketPriceMargin = marketPriceMargin;
    }

    void requestTxFee(@Nullable Runnable actionHandler) {
        feeService.requestFees(() -> {
            txFeeFromFeeService = feeService.getTxFee(feeTxVsize);
            calculateTotalToPay();
            if (actionHandler != null)
                actionHandler.run();
        });
    }

    void setPreferredCurrencyForMakerFeeBtc(boolean preferredCurrencyForMakerFeeBtc) {
        preferences.setPayFeeInBtc(preferredCurrencyForMakerFeeBtc);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmount.get() != null && amount.get() != null)
            return !minAmount.get().isGreaterThan(amount.get());
        return true;
    }

    OfferDirection getDirection() {
        return direction;
    }

    boolean isSellOffer() {
        return direction == OfferDirection.SELL;
    }

    boolean isBuyOffer() {
        return direction == OfferDirection.BUY;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }

    protected TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    protected PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    protected void setUseMarketBasedPrice(boolean useMarketBasedPrice) {
        this.useMarketBasedPrice.set(useMarketBasedPrice);
        preferences.setUsePercentageBasedPrice(useMarketBasedPrice);
    }

    public ObservableList<PaymentAccount> getPaymentAccounts() {
        return paymentAccounts;
    }

    public double getMarketPriceMargin() {
        return marketPriceMargin;
    }

    boolean isMakerFeeValid() {
        return preferences.getPayFeeInBtc() || isBsqForFeeAvailable();
    }

    long getMaxTradeLimit() {
        if (paymentAccount != null) {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrencyCode.get(), direction);
        } else {
            return 0;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    double calculateMarketPriceManual(double marketPrice, double volumeAsDouble, double amountAsDouble) {
        double manualPriceAsDouble = offerUtil.calculateManualPrice(volumeAsDouble, amountAsDouble);
        double percentage = offerUtil.calculateMarketPriceMargin(manualPriceAsDouble, marketPrice);

        setMarketPriceMargin(percentage);

        return manualPriceAsDouble;
    }

    void calculateVolume() {
        if (isNonZeroPrice.test(price) && isNonZeroAmount.test(amount)) {
            try {
                Volume volumeByAmount = calculateVolumeForAmount(amount);

                volume.set(volumeByAmount);

                calculateMinVolume();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }

        updateBalance();
    }

    void calculateMinVolume() {
        if (isNonZeroPrice.test(price) && isNonZeroAmount.test(minAmount)) {
            try {
                Volume volumeByAmount = calculateVolumeForAmount(minAmount);

                minVolume.set(volumeByAmount);

            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    private Volume calculateVolumeForAmount(ObjectProperty<Coin> minAmount) {
        Volume volumeByAmount = price.get().getVolumeByAmount(minAmount.get());

        // For HalCash we want multiple of 10 EUR
        if (isUsingHalCashAccount())
            volumeByAmount = VolumeUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
            volumeByAmount = VolumeUtil.getRoundedFiatVolume(volumeByAmount);
        return volumeByAmount;
    }

    void calculateAmount() {
        if (isNonZeroPrice.test(price) && isNonZeroVolume.test(volume) && allowAmountUpdate) {
            try {
                Coin value = DisplayUtils.reduceTo4Decimals(price.get().getAmountByVolume(volume.get()), btcFormatter);
                if (isUsingHalCashAccount())
                    value = CoinUtil.getAdjustedAmountForHalCash(value, price.get(), getMaxTradeLimit());
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    value = CoinUtil.getRoundedFiatAmount(value, price.get(), getMaxTradeLimit());

                calculateVolume();

                amount.set(value);
                calculateTotalToPay();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    void calculateTotalToPay() {
        // Maker does not pay the mining fee for the trade txs because the mining fee might be different when maker
        // created the offer and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the createOfferFee tx is deducted from the createOfferFee and not visible to the trader
        final Coin makerFee = getMakerFee();
        if (direction != null && amount.get() != null && makerFee != null) {
            Coin feeAndSecDeposit = getTxFee().add(getSecurityDeposit());
            if (isCurrencyForMakerFeeBtc())
                feeAndSecDeposit = feeAndSecDeposit.add(makerFee);
            Coin total = isBuyOffer() ? feeAndSecDeposit : feeAndSecDeposit.add(amount.get());
            totalToPayAsCoin.set(total);
            updateBalance();
        }
    }

    Coin getSecurityDeposit() {
        return isBuyOffer() ? getBuyerSecurityDepositAsCoin() : getSellerSecurityDepositAsCoin();
    }

    public Coin getTxFee() {
        if (isCurrencyForMakerFeeBtc()) {
            return txFeeFromFeeService;
        } else {
            // when BSQ is burnt to pay the Bisq maker fee, it has the benefit of those sats also going to the miners.
            // so that reduces the explicit mining fee for the maker transaction
            Coin makerFee = getMakerFee() != null ? getMakerFee() : Coin.ZERO;
            return txFeeFromFeeService.isGreaterThan(makerFee) ? txFeeFromFeeService.subtract(makerFee) : Coin.ZERO;
        }
    }

    void swapTradeToSavings() {
        btcWalletService.resetAddressEntriesForOpenOffer(offerId);
    }

    private void fillPaymentAccounts() {
        paymentAccounts.setAll(getUserPaymentAccounts());
        paymentAccounts.sort(comparing(PaymentAccount::getAccountName));
    }

    protected abstract Set<PaymentAccount> getUserPaymentAccounts();

    protected void setAmount(Coin amount) {
        this.amount.set(amount);
    }

    protected void setPrice(Price price) {
        this.price.set(price);
    }

    protected void setVolume(Volume volume) {
        this.volume.set(volume);
    }

    protected void setBuyerSecurityDeposit(double value) {
        this.buyerSecurityDeposit.set(value);
    }

    void resetAddressEntry() {
        btcWalletService.resetAddressEntriesForOpenOffer(offerId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ReadOnlyObjectProperty<Coin> getAmount() {
        return amount;
    }

    protected ReadOnlyObjectProperty<Coin> getMinAmount() {
        return minAmount;
    }

    public ReadOnlyObjectProperty<Price> getPrice() {
        return price;
    }

    ReadOnlyObjectProperty<Volume> getVolume() {
        return volume;
    }

    ReadOnlyObjectProperty<Volume> getMinVolume() {
        return minVolume;
    }

    protected void setMinAmount(Coin minAmount) {
        this.minAmount.set(minAmount);
    }

    public ReadOnlyStringProperty getTradeCurrencyCode() {
        return tradeCurrencyCode;
    }

    public String getCurrencyCode() {
        return tradeCurrencyCode.get();
    }

    boolean isCryptoCurrency() {
        return CurrencyUtil.isCryptoCurrency(tradeCurrencyCode.get());
    }

    boolean isFiatCurrency() {
        return CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get());
    }

    ReadOnlyBooleanProperty getUseMarketBasedPrice() {
        return useMarketBasedPrice;
    }

    ReadOnlyDoubleProperty getBuyerSecurityDeposit() {
        return buyerSecurityDeposit;
    }

    protected Coin getBuyerSecurityDepositAsCoin() {
        Coin percentOfAmountAsCoin = CoinUtil.getPercentOfAmountAsCoin(buyerSecurityDeposit.get(), amount.get());
        return getBoundedBuyerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }

    private Coin getSellerSecurityDepositAsCoin() {
        Coin amountAsCoin = this.amount.get();
        if (amountAsCoin == null)
            amountAsCoin = Coin.ZERO;

        Coin percentOfAmountAsCoin = CoinUtil.getPercentOfAmountAsCoin(
                createOfferService.getSellerSecurityDepositAsDouble(buyerSecurityDeposit.get()), amountAsCoin);
        return getBoundedSellerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }

    protected Coin getBoundedBuyerSecurityDepositAsCoin(Coin value) {
        // We need to ensure that for small amount values we don't get a too low BTC amount. We limit it with using the
        // MinBuyerSecurityDepositAsCoin from Restrictions.
        return Coin.valueOf(Math.max(Restrictions.getMinBuyerSecurityDepositAsCoin().value, value.value));
    }

    private Coin getBoundedSellerSecurityDepositAsCoin(Coin value) {
        // We need to ensure that for small amount values we don't get a too low BTC amount. We limit it with using the
        // MinSellerSecurityDepositAsCoin from Restrictions.
        return Coin.valueOf(Math.max(Restrictions.getMinSellerSecurityDepositAsCoin().value, value.value));
    }

    ReadOnlyObjectProperty<Coin> totalToPayAsCoinProperty() {
        return totalToPayAsCoin;
    }

    Coin getUsableBsqBalance() {
        return offerUtil.getUsableBsqBalance();
    }

    public void setMarketPriceAvailable(boolean marketPriceAvailable) {
        this.marketPriceAvailable = marketPriceAvailable;
    }

    public Coin getMakerFee(boolean isCurrencyForMakerFeeBtc) {
        return CoinUtil.getMakerFee(isCurrencyForMakerFeeBtc, amount.get());
    }

    public Coin getMakerFee() {
        return offerUtil.getMakerFee(amount.get());
    }

    public Coin getMakerFeeInBtc() {
        return CoinUtil.getMakerFee(true, amount.get());
    }

    public Coin getMakerFeeInBsq() {
        return CoinUtil.getMakerFee(false, amount.get());
    }

    public boolean isCurrencyForMakerFeeBtc() {
        return offerUtil.isCurrencyForMakerFeeBtc(amount.get());
    }

    boolean isPreferredFeeCurrencyBtc() {
        return preferences.isPayFeeInBtc();
    }

    boolean isBsqForFeeAvailable() {
        return offerUtil.isBsqForMakerFeeAvailable(amount.get());
    }

    boolean isAttemptToBuyBsq() {
        // When you buy an asset you actually sell BTC.
        // This is why an offer to buy BSQ is actually an offer to sell BTC for BSQ.
        return !isBuyOffer() && getTradeCurrency().getCode().equals("BSQ");
    }

    boolean canPlaceOffer() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService) &&
                GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation, tradeCurrency);
    }

    public boolean isMinBuyerSecurityDeposit() {
        return !getBuyerSecurityDepositAsCoin().isGreaterThan(Restrictions.getMinBuyerSecurityDepositAsCoin());
    }

    public void setTriggerPrice(long triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public boolean isUsingHalCashAccount() {
        return paymentAccount.hasPaymentMethodWithId(HAL_CASH_ID);
    }
}
