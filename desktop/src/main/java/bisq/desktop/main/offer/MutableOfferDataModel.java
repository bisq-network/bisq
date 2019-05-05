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

import bisq.core.arbitration.Arbitrator;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeRestrictions;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;
import bisq.core.util.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import com.google.common.collect.Lists;

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

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class MutableOfferDataModel extends OfferDataModel implements BsqBalanceListener {
    protected final OpenOfferManager openOfferManager;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    protected final User user;
    private final KeyRing keyRing;
    private final P2PService p2PService;
    protected final PriceFeedService priceFeedService;
    final String shortOfferId;
    private final FilterManager filterManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final FeeService feeService;
    private final TxFeeEstimationService txFeeEstimationService;
    private final ReferralIdService referralIdService;
    private final BSFormatter btcFormatter;
    private final String offerId;
    private final BalanceListener btcBalanceListener;
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    protected OfferPayload.Direction direction;
    protected TradeCurrency tradeCurrency;
    protected final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    protected final BooleanProperty useMarketBasedPrice = new SimpleBooleanProperty();
    //final BooleanProperty isMainNet = new SimpleBooleanProperty();
    //final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();

    // final ObjectProperty<Coin> feeFromFundingTxProperty = new SimpleObjectProperty(Coin.NEGATIVE_SATOSHI);
    protected final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Coin> minAmount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> minVolume = new SimpleObjectProperty<>();

    // Percentage value of buyer security deposit. E.g. 0.01 means 1% of trade amount
    protected final DoubleProperty buyerSecurityDeposit = new SimpleDoubleProperty();
    protected final DoubleProperty sellerSecurityDeposit = new SimpleDoubleProperty();

    protected final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    protected PaymentAccount paymentAccount;
    protected boolean isTabSelected;
    protected double marketPriceMargin = 0;
    private Coin txFeeFromFeeService = Coin.ZERO;
    private boolean marketPriceAvailable;
    private int feeTxSize = TxFeeEstimationService.TYPICAL_TX_WITH_1_INPUT_SIZE;
    protected boolean allowAmountUpdate = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MutableOfferDataModel(OpenOfferManager openOfferManager,
                                 BtcWalletService btcWalletService,
                                 BsqWalletService bsqWalletService,
                                 Preferences preferences,
                                 User user,
                                 KeyRing keyRing,
                                 P2PService p2PService,
                                 PriceFeedService priceFeedService,
                                 FilterManager filterManager,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 FeeService feeService,
                                 TxFeeEstimationService txFeeEstimationService,
                                 ReferralIdService referralIdService,
                                 BSFormatter btcFormatter) {
        super(btcWalletService);

        this.openOfferManager = openOfferManager;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.feeService = feeService;
        this.txFeeEstimationService = txFeeEstimationService;
        this.referralIdService = referralIdService;
        this.btcFormatter = btcFormatter;

        offerId = Utilities.getRandomPrefix(5, 8) + "-" +
                UUID.randomUUID().toString() + "-" +
                Version.VERSION.replace(".", "");
        shortOfferId = Utilities.getShortId(offerId);
        addressEntry = btcWalletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.OFFER_FUNDING);

        useMarketBasedPrice.set(preferences.isUsePercentageBasedPrice());
        buyerSecurityDeposit.set(preferences.getBuyerSecurityDepositAsPercent(null));
        sellerSecurityDeposit.set(Restrictions.getSellerSecurityDepositAsPercent());

        btcBalanceListener = new BalanceListener(getAddressEntry().getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();

               /* if (isMainNet.get()) {
                    SettableFuture<Coin> future = blockchainService.requestFee(tx.getHashAsString());
                    Futures.addCallback(future, new FutureCallback<Coin>() {
                        public void onSuccess(Coin fee) {
                            UserThread.execute(() -> feeFromFundingTxProperty.set(fee));
                        }

                        public void onFailure(@NotNull Throwable throwable) {
                            UserThread.execute(() -> new Popup<>()
                                    .warning("We did not get a response for the request of the mining fee used " +
                                            "in the funding transaction.\n\n" +
                                            "Are you sure you used a sufficiently high fee of at least " +
                                            formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + "?")
                                    .actionButtonText("Yes, I used a sufficiently high fee.")
                                    .onAction(() -> feeFromFundingTxProperty.set(FeePolicy.getMinRequiredFeeForFundingTx()))
                                    .closeButtonText("No. Let's cancel that payment.")
                                    .onClose(() -> feeFromFundingTxProperty.set(Coin.ZERO))
                                    .show());
                        }
                    });
                }*/
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
    public boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
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
        txFeeFromFeeService = feeService.getTxFee(feeTxSize);

        calculateVolume();
        calculateTotalToPay();
        updateBalance();

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

    @SuppressWarnings("ConstantConditions")
    Offer createAndGetOffer() {
        boolean useMarketBasedPriceValue = isUseMarketBasedPriceValue();
        long priceAsLong = price.get() != null && !useMarketBasedPriceValue ? price.get().getValue() : 0L;
        String currencyCode = tradeCurrencyCode.get();
        boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(currencyCode);
        String baseCurrencyCode = isCryptoCurrency ? currencyCode : Res.getBaseCurrencyCode();
        String counterCurrencyCode = isCryptoCurrency ? Res.getBaseCurrencyCode() : currencyCode;

        double marketPriceMarginParam = useMarketBasedPriceValue ? marketPriceMargin : 0;
        long amountAsLong = this.amount.get() != null ? this.amount.get().getValue() : 0L;
        long minAmountAsLong = this.minAmount.get() != null ? this.minAmount.get().getValue() : 0L;

        List<String> acceptedCountryCodes = PaymentAccountUtil.getAcceptedCountryCodes(paymentAccount);
        List<String> acceptedBanks = PaymentAccountUtil.getAcceptedBanks(paymentAccount);
        String bankId = PaymentAccountUtil.getBankId(paymentAccount);
        String countryCode = PaymentAccountUtil.getCountryCode(paymentAccount);

        long maxTradeLimit = getMaxTradeLimit();
        long maxTradePeriod = paymentAccount.getMaxTradePeriod();

        // reserved for future use cases
        // Use null values if not set
        boolean isPrivateOffer = false;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;
        String hashOfChallenge = null;

        Coin makerFeeAsCoin = getMakerFee();

        Map<String, String> extraDataMap = OfferUtil.getExtraDataMap(accountAgeWitnessService,
                referralIdService,
                paymentAccount,
                currencyCode);

        OfferUtil.validateOfferData(filterManager,
                p2PService,
                buyerSecurityDeposit.get(),
                paymentAccount,
                currencyCode,
                makerFeeAsCoin);

        OfferPayload offerPayload = new OfferPayload(offerId,
                new Date().getTime(),
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                OfferPayload.Direction.valueOf(direction.name()),
                priceAsLong,
                marketPriceMarginParam,
                useMarketBasedPriceValue,
                amountAsLong,
                minAmountAsLong,
                baseCurrencyCode,
                counterCurrencyCode,
                Lists.newArrayList(user.getAcceptedArbitratorAddresses()),
                Lists.newArrayList(user.getAcceptedMediatorAddresses()),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                null,
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                Version.VERSION,
                btcWalletService.getLastBlockSeenHeight(),
                txFeeFromFeeService.value,
                makerFeeAsCoin.value,
                isCurrencyForMakerFeeBtc(),
                getBuyerSecurityDepositAsCoin().value,
                getSellerSecurityDepositAsCoin().value,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                upperClosePrice,
                lowerClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION);
        Offer offer = new Offer(offerPayload);
        offer.setPriceFeedService(priceFeedService);
        return offer;
    }

    // This works only if we have already funds in the wallet
    public void estimateTxSize() {
        Coin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        Tuple2<Coin, Integer> estimatedFeeAndTxSize = txFeeEstimationService.getEstimatedFeeAndTxSizeForMaker(reservedFundsForOffer,
                getMakerFee());
        txFeeFromFeeService = estimatedFeeAndTxSize.first;
        feeTxSize = estimatedFeeAndTxSize.second;
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        checkNotNull(getMakerFee(), "makerFee must not be null");

        Coin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        openOfferManager.placeOffer(offer,
                reservedFundsForOffer,
                useSavingsWallet,
                resultHandler,
                log::error);
    }

    void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null && !this.paymentAccount.equals(paymentAccount)) {
            volume.set(null);
            minVolume.set(null);
            price.set(null);
            marketPriceMargin = 0;
            preferences.setSelectedPaymentAccountForCreateOffer(paymentAccount);
            this.paymentAccount = paymentAccount;

            setTradeCurrencyFromPaymentAccount(paymentAccount);

            buyerSecurityDeposit.set(preferences.getBuyerSecurityDepositAsPercent(getPaymentAccount()));

            if (amount.get() != null)
                this.amount.set(Coin.valueOf(Math.min(amount.get().value, getMaxTradeLimit())));
        }
    }

    private void setTradeCurrencyFromPaymentAccount(PaymentAccount paymentAccount) {
        if (!paymentAccount.getTradeCurrencies().contains(tradeCurrency)) {
            if (paymentAccount.getSelectedTradeCurrency() != null)
                tradeCurrency = paymentAccount.getSelectedTradeCurrency();
            else if (paymentAccount.getSingleTradeCurrency() != null)
                tradeCurrency = paymentAccount.getSingleTradeCurrency();
            else if (!paymentAccount.getTradeCurrencies().isEmpty())
                tradeCurrency = paymentAccount.getTradeCurrencies().get(0);
        }

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

            Optional<TradeCurrency> tradeCurrencyOptional = preferences.getTradeCurrenciesAsObservable().stream().filter(e -> e.getCode().equals(code)).findAny();
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
    public void onUpdateBalances(Coin availableConfirmedBalance,
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
            txFeeFromFeeService = feeService.getTxFee(feeTxSize);
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmount.get() != null && amount.get() != null)
            return !minAmount.get().isGreaterThan(amount.get());
        return true;
    }

    OfferPayload.Direction getDirection() {
        return direction;
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

    boolean hasAcceptedArbitrators() {
        final List<Arbitrator> acceptedArbitrators = user.getAcceptedArbitrators();
        return acceptedArbitrators != null && acceptedArbitrators.size() > 0;
    }

    protected void setUseMarketBasedPrice(boolean useMarketBasedPrice) {
        this.useMarketBasedPrice.set(useMarketBasedPrice);
        preferences.setUsePercentageBasedPrice(useMarketBasedPrice);
    }

    /*boolean isFeeFromFundingTxSufficient() {
        return !isMainNet.get() || feeFromFundingTxProperty.get().compareTo(FeePolicy.getMinRequiredFeeForFundingTx()) >= 0;
    }*/

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
        if (paymentAccount != null)
            return AccountAgeRestrictions.getMyTradeLimitAtCreateOffer(accountAgeWitnessService, paymentAccount, tradeCurrencyCode.get(), direction);
        else
            return 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (price.get() != null &&
                amount.get() != null &&
                !amount.get().isZero() &&
                !price.get().isZero()) {
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
        if (price.get() != null &&
                minAmount.get() != null &&
                !minAmount.get().isZero() &&
                !price.get().isZero()) {
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
        if (isHalCashAccount())
            volumeByAmount = OfferUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
            volumeByAmount = OfferUtil.getRoundedFiatVolume(volumeByAmount);
        return volumeByAmount;
    }

    void calculateAmount() {
        if (volume.get() != null &&
                price.get() != null &&
                !volume.get().isZero() &&
                !price.get().isZero() &&
                allowAmountUpdate) {
            try {
                Coin value = btcFormatter.reduceTo4Decimals(price.get().getAmountByVolume(volume.get()));
                if (isHalCashAccount())
                    value = OfferUtil.getAdjustedAmountForHalCash(value, price.get(), getMaxTradeLimit());
                else if (CurrencyUtil.isFiatCurrency(tradeCurrencyCode.get()))
                    value = OfferUtil.getRoundedFiatAmount(value, price.get(), getMaxTradeLimit());

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

    public boolean isBuyOffer() {
        return OfferUtil.isBuyOffer(getDirection());
    }

    public Coin getTxFee() {
        if (isCurrencyForMakerFeeBtc())
            return txFeeFromFeeService;
        else
            return txFeeFromFeeService.subtract(getMakerFee());
    }

    public void swapTradeToSavings() {
        btcWalletService.resetAddressEntriesForOpenOffer(offerId);
    }

    private void fillPaymentAccounts() {
        if (user.getPaymentAccounts() != null)
            paymentAccounts.setAll(new HashSet<>(user.getPaymentAccounts()));
    }

    protected void setAmount(Coin amount) {
        this.amount.set(amount);
    }

    protected void setPrice(Price price) {
        this.price.set(price);
    }

    protected void setVolume(Volume volume) {
        this.volume.set(volume);
    }

    void setBuyerSecurityDeposit(double value) {
        this.buyerSecurityDeposit.set(value);
        preferences.setBuyerSecurityDepositAsPercent(value, getPaymentAccount());
    }

    protected boolean isUseMarketBasedPriceValue() {
        return marketPriceAvailable && useMarketBasedPrice.get() && !isHalCashAccount();
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

    Coin getSellerSecurityDepositAsCoin() {
        Coin amountAsCoin = this.amount.get();
        if (amountAsCoin == null)
            amountAsCoin = Coin.ZERO;

        Coin percentOfAmountAsCoin = CoinUtil.getPercentOfAmountAsCoin(sellerSecurityDeposit.get(), amountAsCoin);
        return getBoundedSellerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }

    private Coin getBoundedBuyerSecurityDepositAsCoin(Coin value) {
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

    public Coin getBsqBalance() {
        return bsqWalletService.getAvailableConfirmedBalance();
    }

    public void setMarketPriceAvailable(boolean marketPriceAvailable) {
        this.marketPriceAvailable = marketPriceAvailable;
    }

    public Coin getMakerFee(boolean isCurrencyForMakerFeeBtc) {
        return OfferUtil.getMakerFee(isCurrencyForMakerFeeBtc, amount.get());
    }

    public Coin getMakerFee() {
        return OfferUtil.getMakerFee(bsqWalletService, preferences, amount.get());
    }

    public Coin getMakerFeeInBtc() {
        return OfferUtil.getMakerFee(true, amount.get());
    }

    public Coin getMakerFeeInBsq() {
        return OfferUtil.getMakerFee(false, amount.get());
    }

    public boolean isCurrencyForMakerFeeBtc() {
        return OfferUtil.isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amount.get());
    }

    public boolean isPreferredFeeCurrencyBtc() {
        return preferences.isPayFeeInBtc();
    }

    public boolean isBsqForFeeAvailable() {
        return OfferUtil.isBsqForMakerFeeAvailable(bsqWalletService, amount.get());
    }

    public boolean isHalCashAccount() {
        return paymentAccount instanceof HalCashAccount;
    }
}
