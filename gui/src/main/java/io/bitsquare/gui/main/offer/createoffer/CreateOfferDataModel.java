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

import com.google.inject.Inject;
import io.bitsquare.app.DevFlags;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.provider.price.PriceFeedService;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.TradeWalletService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.main.offer.createoffer.monetary.Price;
import io.bitsquare.gui.main.offer.createoffer.monetary.Volume;
import io.bitsquare.gui.main.overlays.notifications.Notification;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.*;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel extends ActivatableDataModel {
    private final OpenOfferManager openOfferManager;
    private final BtcWalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final Preferences preferences;
    private final User user;
    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    final String shortOfferId;
    private final FeeService feeService;
    private final Navigation navigation;
    private final BSFormatter formatter;
    private final String offerId;
    private final AddressEntry addressEntry;
    private Coin createOfferFeeAsCoin;
    private Coin txFeeAsCoin;
    private final Coin securityDepositAsCoin;
    private final BalanceListener balanceListener;
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    private Offer.Direction direction;

    private TradeCurrency tradeCurrency;

    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMarketBasedPrice = new SimpleBooleanProperty();
    //final BooleanProperty isMainNet = new SimpleBooleanProperty();
    //final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();

    // final ObjectProperty<Coin> feeFromFundingTxProperty = new SimpleObjectProperty(Coin.NEGATIVE_SATOSHI);
    final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmount = new SimpleObjectProperty<>();
    // Price is always otherCurrency/BTC, for altcoins we only invert at the display level. 
    // If we would change the price representation in the domain we would not be backward compatible
    final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> missingCoin = new SimpleObjectProperty<>(Coin.ZERO);
    final ObjectProperty<Coin> balance = new SimpleObjectProperty<>();

    private final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    PaymentAccount paymentAccount;
    boolean isTabSelected;
    private Notification walletFundedNotification;
    private boolean useSavingsWallet;
    Coin totalAvailableBalance;
    private double marketPriceMargin = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferDataModel(OpenOfferManager openOfferManager, BtcWalletService walletService, TradeWalletService tradeWalletService,
                         Preferences preferences, User user, KeyRing keyRing, P2PService p2PService, PriceFeedService priceFeedService,
                         FeeService feeService, Navigation navigation, BSFormatter formatter) {
        this.openOfferManager = openOfferManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.feeService = feeService;
        this.navigation = navigation;
        this.formatter = formatter;

        offerId = Utilities.getRandomPrefix(5, 8) + "-" +
                UUID.randomUUID().toString() + "-" +
                Version.VERSION.replace(".", "");
        shortOfferId = Utilities.getShortId(offerId);
        addressEntry = walletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.OFFER_FUNDING);

        useMarketBasedPrice.set(preferences.getUsePercentageBasedPrice());

        // TODO add ui for editing, use preferences
        securityDepositAsCoin = FeePolicy.getDefaultSecurityDeposit();

        balanceListener = new BalanceListener(getAddressEntry().getAddress()) {
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
                            UserThread.execute(() -> new Popup()
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
    protected void activate() {
        addBindings();
        addListeners();

        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        updateBalance();
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        btcCode.bind(preferences.btcDenominationProperty());
    }

    private void removeBindings() {
        btcCode.unbind();
    }

    private void addListeners() {
        walletService.addBalanceListener(balanceListener);
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }


    private void removeListeners() {
        walletService.removeBalanceListener(balanceListener);
        user.getPaymentAccountsAsObservable().removeListener(paymentAccountsChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate()
    boolean initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        this.direction = direction;

        fillPaymentAccounts();

        PaymentAccount account = user.findFirstPaymentAccountWithCurrency(tradeCurrency);
        if (account != null && !isUSBankAccount(account)) {
            paymentAccount = account;
            this.tradeCurrency = tradeCurrency;
        } else {
            Optional<PaymentAccount> paymentAccountOptional = paymentAccounts.stream().findAny();
            if (paymentAccountOptional.isPresent()) {
                paymentAccount = paymentAccountOptional.get();
                this.tradeCurrency = paymentAccount.getSingleTradeCurrency();
            } else {
                log.warn("PaymentAccount not available. Should never get called as in offer view you should not be able to open a create offer view");
                return false;
            }
        }

        tradeCurrencyCode.set(this.tradeCurrency.getCode());

        if (!preferences.getUseStickyMarketPrice())
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());

        // The offerer only pays the mining fee for the trade fee tx (not the mining fee for other trade txs). 
        // A typical trade fee tx has about 226 bytes (if one input). We use 400 as a safe value.
        // We cannot use tx size calculation as we do not know initially how the input is funded. And we require the
        // fee for getting the funds needed.
        // So we use an estimated average size and risk that in some cases we might get a bit of delay if the actual required 
        // fee would be larger. 
        // As we use the best fee estimation (for 1 confirmation) that risk should not be too critical as long there are
        // not too many inputs.

        // trade fee tx: 226 bytes (1 input) - 374 bytes (2 inputs)         

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But offer creation happens usually after that so we should have already the value from the estimation service.
        txFeeAsCoin = feeService.getTxFee(400);

        // We request to get the actual estimated fee
        requestTxFee();

        calculateVolume();
        calculateTotalToPay();
        return true;
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeedService.setCurrencyCode(tradeCurrencyCode.get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    Offer createAndGetOffer() {
        long priceAsLong = price.get() != null && !useMarketBasedPrice.get() ? price.get().getValue() : 0L;
        // We use precision 8 in AltcoinPrice but in Offer we use Fiat with precision 4. Will be refactored once in a bigger update....
        // TODO use same precision for both in next release
        if (CurrencyUtil.isCryptoCurrency(tradeCurrencyCode.get()))
            priceAsLong = priceAsLong / 10000;

        double marketPriceMarginParam = useMarketBasedPrice.get() ? marketPriceMargin : 0;
        long amount = this.amount.get() != null ? this.amount.get().getValue() : 0L;
        long minAmount = this.minAmount.get() != null ? this.minAmount.get().getValue() : 0L;

        ArrayList<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.addAll(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
        } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
            acceptedCountryCodes = new ArrayList<>();
            acceptedCountryCodes.add(((CountryBasedPaymentAccount) paymentAccount).getCountry().code);
        }

        ArrayList<String> acceptedBanks = null;
        if (paymentAccount instanceof SpecificBanksAccount) {
            acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
        } else if (paymentAccount instanceof SameBankAccount) {
            acceptedBanks = new ArrayList<>();
            acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
        }

        String bankId = paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;

        // That is optional and set to null if not supported (AltCoins, OKPay,...)
        String countryCode = paymentAccount instanceof CountryBasedPaymentAccount ? ((CountryBasedPaymentAccount) paymentAccount).getCountry().code : null;

        checkNotNull(p2PService.getAddress(), "Address must not be null");
        checkNotNull(createOfferFeeAsCoin, "createOfferFeeAsCoin must not be null");

        long maxTradeLimit = paymentAccount.getPaymentMethod().getMaxTradeLimit().value;
        long maxTradePeriod = paymentAccount.getPaymentMethod().getMaxTradePeriod();

        // reserved for future use cases
        boolean isPrivateOffer = false;
        String hashOfChallenge = null;
        HashMap<String, String> extraDataMap = null;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        long lowerClosePrice = 0;
        long upperClosePrice = 0;

        return new Offer(offerId,
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                direction,
                priceAsLong,
                marketPriceMarginParam,
                useMarketBasedPrice.get(),
                amount,
                minAmount,
                tradeCurrencyCode.get(),
                new ArrayList<>(user.getAcceptedArbitratorAddresses()),
                paymentAccount.getPaymentMethod().getId(),
                paymentAccount.getId(),
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                priceFeedService,

                Version.VERSION,
                walletService.getLastBlockSeenHeight(),
                txFeeAsCoin.value,
                createOfferFeeAsCoin.value,
                securityDepositAsCoin.value,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                upperClosePrice,
                lowerClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap);
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        checkNotNull(createOfferFeeAsCoin, "createOfferFeeAsCoin must not be null");
        openOfferManager.placeOffer(offer, totalToPayAsCoin.get().subtract(txFeeAsCoin).subtract(createOfferFeeAsCoin), useSavingsWallet, resultHandler);
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null) {

            if (!this.paymentAccount.equals(paymentAccount)) {
                volume.set(null);
                price.set(null);
                marketPriceMargin = 0;
            }
            this.paymentAccount = paymentAccount;
        }
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            if (!this.tradeCurrency.equals(tradeCurrency)) {
                volume.set(null);
                price.set(null);
                marketPriceMargin = 0;
            }

            this.tradeCurrency = tradeCurrency;
            final String code = tradeCurrency.getCode();
            tradeCurrencyCode.set(code);

            if (paymentAccount != null)
                paymentAccount.setSelectedTradeCurrency(tradeCurrency);

            if (!preferences.getUseStickyMarketPrice())
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

    void fundFromSavingsWallet() {
        this.useSavingsWallet = true;
        updateBalance();
        if (!isWalletFunded.get()) {
            this.useSavingsWallet = false;
            updateBalance();
        }
    }

    void setMarketPriceMargin(double marketPriceMargin) {
        this.marketPriceMargin = marketPriceMargin;
    }

    void requestTxFee() {
        feeService.requestFees(() -> {
            txFeeAsCoin = feeService.getTxFee(400);
            calculateTotalToPay();
        }, null);
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

    Offer.Direction getDirection() {
        return direction;
    }

    String getOfferId() {
        return offerId;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    public PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    boolean hasAcceptedArbitrators() {
        return user.getAcceptedArbitrators().size() > 0;
    }

    public void setUseMarketBasedPrice(boolean useMarketBasedPrice) {
        this.useMarketBasedPrice.set(useMarketBasedPrice);
        preferences.setUsePercentageBasedPrice(useMarketBasedPrice);
    }

    /*boolean isFeeFromFundingTxSufficient() {
        return !isMainNet.get() || feeFromFundingTxProperty.get().compareTo(FeePolicy.getMinRequiredFeeForFundingTx()) >= 0;
    }*/

    public ObservableList<PaymentAccount> getPaymentAccounts() {
        return paymentAccounts;
    }

    double getMarketPriceMargin() {
        return marketPriceMargin;
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
                volume.set(new Volume(price.get().getVolumeByAmount(amount.get())));
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }

        updateBalance();
    }

    void calculateAmount() {
        if (volume.get() != null &&
                price.get() != null &&
                !volume.get().isZero() &&
                !price.get().isZero()) {
            try {
                amount.set(formatter.reduceTo4Decimals(price.get().getAmountByVolume(volume.get().getMonetary())));
                calculateTotalToPay();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    void calculateTotalToPay() {
        // Offerer does not pay the tx fee for the trade txs because the mining fee might be different when offerer 
        // created the offer and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the createOfferFee tx is deducted from the createOfferFee and not visible to the trader
        if (direction != null && amount.get() != null && createOfferFeeAsCoin != null) {
            Coin feeAndSecDeposit = createOfferFeeAsCoin.add(txFeeAsCoin).add(securityDepositAsCoin);
            Coin required = direction == Offer.Direction.BUY ? feeAndSecDeposit : feeAndSecDeposit.add(amount.get());
            totalToPayAsCoin.set(required);
            log.debug("totalToPayAsCoin " + totalToPayAsCoin.get().toFriendlyString());
            updateBalance();
        }
    }

    private void updateBalance() {
        Coin tradeWalletBalance = walletService.getBalanceForAddress(addressEntry.getAddress());
        if (useSavingsWallet) {
            Coin savingWalletBalance = walletService.getSavingWalletBalance();
            totalAvailableBalance = savingWalletBalance.add(tradeWalletBalance);
            if (totalToPayAsCoin.get() != null) {
                if (totalAvailableBalance.compareTo(totalToPayAsCoin.get()) > 0)
                    balance.set(totalToPayAsCoin.get());
                else
                    balance.set(totalAvailableBalance);
            }
        } else {
            balance.set(tradeWalletBalance);
        }

        if (totalToPayAsCoin.get() != null) {
            missingCoin.set(totalToPayAsCoin.get().subtract(balance.get()));
            if (missingCoin.get().isNegative())
                missingCoin.set(Coin.ZERO);
        }

        log.debug("missingCoin " + missingCoin.get().toFriendlyString());

        isWalletFunded.set(isBalanceSufficient(balance.get()));
        if (totalToPayAsCoin.get() != null && isWalletFunded.get() && walletFundedNotification == null && !DevFlags.DEV_MODE) {
            walletFundedNotification = new Notification()
                    .headLine("Trading wallet update")
                    .notification("Your trading wallet is sufficiently funded.\n" +
                            "Amount: " + formatter.formatCoinWithCode(totalToPayAsCoin.get()))
                    .autoClose();

            walletFundedNotification.show();
        }
    }

    private boolean isBalanceSufficient(Coin balance) {
        return totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0;
    }

    public Coin getCreateOfferFeeAsCoin() {
        checkNotNull(createOfferFeeAsCoin, "createOfferFeeAsCoin must not be null");
        return createOfferFeeAsCoin;
    }

    public Coin getTxFeeAsCoin() {
        return txFeeAsCoin;
    }

    public Coin getSecurityDepositAsCoin() {
        return securityDepositAsCoin;
    }

    public List<Arbitrator> getArbitrators() {
        return user.getAcceptedArbitrators();
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public void swapTradeToSavings() {
        walletService.swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.OFFER_FUNDING);
        walletService.swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.RESERVED_FOR_TRADE);
    }

    private void fillPaymentAccounts() {
        paymentAccounts.setAll(user.getPaymentAccounts().stream()
                .filter(e -> !isUSBankAccount(e))
                .collect(Collectors.toSet()));
    }

    private boolean isUSBankAccount(PaymentAccount paymentAccount) {
        if (paymentAccount instanceof SameCountryRestrictedBankAccount && paymentAccount.getContractData() instanceof BankAccountContractData)
            return ((SameCountryRestrictedBankAccount) paymentAccount).getCountryCode().equals("US");
        else
            return false;
    }

    void setAmount(Coin amount) {
        this.amount.set(amount);
    }

    void updateTradeFee() {
        createOfferFeeAsCoin = Utilities.getFeePerBtc(feeService.getCreateOfferFeeInBtcPerBtc(), amount.get());
        // We don't want too fractional btc values so we use only a divide by 10 instead of 100
        createOfferFeeAsCoin = createOfferFeeAsCoin.divide(10).multiply(Math.round(marketPriceMargin * 1_000));
        createOfferFeeAsCoin = Utilities.maxCoin(createOfferFeeAsCoin, feeService.getMinCreateOfferFeeInBtc());
    }
}
