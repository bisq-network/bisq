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
import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;

public abstract class AtomicOfferDataModel extends ActivatableDataModel implements BsqBalanceListener {
    private final OfferUtil offerUtil;
    private final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    @Getter
    private final BooleanProperty isOfferFunded = new SimpleBooleanProperty();

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
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    protected OfferPayload.Direction direction;
    protected TradeCurrency tradeCurrency;
    protected final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    protected final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Coin> minAmount = new SimpleObjectProperty<>();
    protected final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    protected final ObjectProperty<Volume> minVolume = new SimpleObjectProperty<>();

    protected final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    protected PaymentAccount paymentAccount;
    boolean isTabSelected;
    protected double marketPriceMargin = 0;
    private Coin txFeeFromFeeService = Coin.ZERO;
    private int feeTxVsize = TxFeeEstimationService.TYPICAL_TX_WITH_1_INPUT_VSIZE;
    protected boolean allowAmountUpdate = true;

    private final Predicate<ObjectProperty<Coin>> isNonZeroAmount = (c) -> c.get() != null && !c.get().isZero();
    private final Predicate<ObjectProperty<Price>> isNonZeroPrice = (p) -> p.get() != null && !p.get().isZero();
    private final Predicate<ObjectProperty<Volume>> isNonZeroVolume = (v) -> v.get() != null && !v.get().isZero();
    @Getter
    protected long triggerPrice;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AtomicOfferDataModel(CreateOfferService createOfferService,
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
        this.offerUtil = offerUtil;
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

        offerId = createOfferService.getRandomOfferId();
        shortOfferId = Utilities.getShortId(offerId);

        paymentAccountsChangeListener = change -> fillPaymentAccounts();

        // TODO(sq): Add listener to BTC and BSQ wallets to decide if wallets are funded. Needs to be checked
        // continuously to keep offers up
        isOfferFunded.set(true);
        totalToPayAsCoin.set(Coin.ZERO);
    }

    @Override
    public void activate() {
        addListeners();

        if (isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
    }

    @Override
    protected void deactivate() {
        removeListeners();
    }

    private void addListeners() {
        bsqWalletService.addBsqBalanceListener(this);
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }

    private void removeListeners() {
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
        txFeeFromFeeService = feeService.getTxFee(feeTxVsize);

        calculateVolume();
        calculateTotalToPay();

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
        return createOfferService.createAndGetAtomicOffer(offerId,
                direction,
                amount.get(),
                minAmount.get(),
                price.get(),
                paymentAccount);
    }

    // This works only if we have already funds in the wallet
    public void updateEstimatedFeeAndTxVsize() {
        Tuple2<Coin, Integer> estimatedFeeAndTxVsize = createOfferService.getEstimatedFeeAndTxVsize(amount.get(),
                direction, 0, 0);
        txFeeFromFeeService = estimatedFeeAndTxVsize.first;
        feeTxVsize = estimatedFeeAndTxVsize.second;
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        openOfferManager.placeAtomicOffer(offer,
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

            if (amount.get() != null && this.allowAmountUpdate)
                this.amount.set(Coin.valueOf(Math.min(amount.get().value, getMaxTradeLimit())));
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
    public void onUpdateBalances(Coin availableConfirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance,
                                 Coin unlockingBondsBalance) {
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

    OfferPayload.Direction getDirection() {
        return direction;
    }

    boolean isSellOffer() {
        return direction == OfferPayload.Direction.SELL;
    }

    boolean isBuyOffer() {
        return direction == OfferPayload.Direction.BUY;
    }

    protected TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    protected PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    public ObservableList<PaymentAccount> getPaymentAccounts() {
        return paymentAccounts;
    }

    public double getMarketPriceMargin() {
        return marketPriceMargin;
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
        return price.get().getVolumeByAmount(minAmount.get());
    }

    void calculateAmount() {
        if (isNonZeroPrice.test(price) && isNonZeroVolume.test(volume) && allowAmountUpdate) {
            try {
                Coin value = DisplayUtils.reduceTo4Decimals(price.get().getAmountByVolume(volume.get()), btcFormatter);
                calculateVolume();

                amount.set(value);
                calculateTotalToPay();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    void calculateTotalToPay() {
        // A maker should pay the maker fee and the trade amount
        // Taker pays taker fee, trade amount and mining fee
        final Coin makerFee = getMakerFee();
        if (direction != null && amount.get() != null && makerFee != null) {
            var total = Coin.ZERO;
            if (isCurrencyForMakerFeeBtc())
                total = total.add(makerFee);
            if (isBuyOffer())
                total = total.add(amount.get());
            totalToPayAsCoin.set(total);
        }
    }

    public Coin getTxFee() {
        if (isCurrencyForMakerFeeBtc())
            return txFeeFromFeeService;
        else
            return txFeeFromFeeService.subtract(getMakerFee());
    }

    private void fillPaymentAccounts() {
        if (user.getPaymentAccounts() != null)
            paymentAccounts.setAll(new HashSet<>(user.getPaymentAccounts()));
        paymentAccounts.sort(comparing(PaymentAccount::getAccountName));
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

    ReadOnlyObjectProperty<Coin> totalToPayAsCoinProperty() {
        return totalToPayAsCoin;
    }

    Coin getUsableBsqBalance() {
        return offerUtil.getUsableBsqBalance();
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

    boolean canPlaceOffer() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService) &&
                GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation, tradeCurrency);
    }

    public void setTriggerPrice(long triggerPrice) {
        this.triggerPrice = triggerPrice;
    }
}
