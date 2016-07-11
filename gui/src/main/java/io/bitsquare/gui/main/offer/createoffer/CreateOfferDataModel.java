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
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.blockchain.BlockchainService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.pricefeed.PriceFeed;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableDataModel;
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
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel extends ActivatableDataModel {
    private final OpenOfferManager openOfferManager;
    final WalletService walletService;
    final TradeWalletService tradeWalletService;
    private final Preferences preferences;
    private final User user;
    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final PriceFeed priceFeed;
    final String shortOfferId;
    private Navigation navigation;
    private final BlockchainService blockchainService;
    private final BSFormatter formatter;
    private final String offerId;
    private final AddressEntry addressEntry;
    private final Coin offerFeeAsCoin;
    private final Coin networkFeeAsCoin;
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
    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmountAsCoin = new SimpleObjectProperty<>();
    // Price is always otherCurrency/BTC, for altcoins we only invert at the display level. 
    // If we would change the price representation in the domain we would not be backward compatible
    final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> missingCoin = new SimpleObjectProperty<>(Coin.ZERO);
    final ObjectProperty<Coin> balance = new SimpleObjectProperty<>();

    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    PaymentAccount paymentAccount;
    boolean isTabSelected;
    private Notification walletFundedNotification;
    boolean useSavingsWallet;
    Coin totalAvailableBalance;
    private double marketPriceMargin = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferDataModel(OpenOfferManager openOfferManager, WalletService walletService, TradeWalletService tradeWalletService,
                         Preferences preferences, User user, KeyRing keyRing, P2PService p2PService, PriceFeed priceFeed,
                         Navigation navigation, BlockchainService blockchainService, BSFormatter formatter) {
        this.openOfferManager = openOfferManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.priceFeed = priceFeed;
        this.navigation = navigation;
        this.blockchainService = blockchainService;
        this.formatter = formatter;

        // isMainNet.set(preferences.getBitcoinNetwork() == BitcoinNetwork.MAINNET);

        offerId = UUID.randomUUID().toString();
        shortOfferId = offerId.substring(0, Math.min(8, offerId.length()));
        addressEntry = walletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.OFFER_FUNDING);
        offerFeeAsCoin = FeePolicy.getCreateOfferFee();
        networkFeeAsCoin = FeePolicy.getFixedTxFeeForTrades();
        securityDepositAsCoin = FeePolicy.getSecurityDeposit();

        useMarketBasedPrice.set(preferences.getUsePercentageBasedPrice());

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

        paymentAccountsChangeListener = change -> paymentAccounts.setAll(user.getPaymentAccounts());
    }

    @Override
    protected void activate() {
        addBindings();
        addListeners();

        paymentAccounts.setAll(user.getPaymentAccounts());

        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeed.setCurrencyCode(tradeCurrencyCode.get());

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

    boolean initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        this.direction = direction;

        PaymentAccount account = user.findFirstPaymentAccountWithCurrency(tradeCurrency);
        if (account != null) {
            paymentAccount = account;
            this.tradeCurrency = tradeCurrency;
        } else {
            Optional<PaymentAccount> paymentAccountOptional = user.getPaymentAccounts().stream().findAny();
            if (paymentAccountOptional.isPresent()) {
                paymentAccount = paymentAccountOptional.get();
                this.tradeCurrency = paymentAccount.getSingleTradeCurrency();
            } else {
                // Should never get called as in offer view you should not be able to open a create offer view
                return false;
            }
        }

        tradeCurrencyCode.set(this.tradeCurrency.getCode());

        if (!preferences.getUseStickyMarketPrice())
            priceFeed.setCurrencyCode(tradeCurrencyCode.get());

        calculateVolume();
        calculateTotalToPay();
        return true;
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeed.setCurrencyCode(tradeCurrencyCode.get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    Offer createAndGetOffer() {
        long fiatPrice = priceAsFiat.get() != null && !useMarketBasedPrice.get() ? priceAsFiat.get().getValue() : 0L;
        double marketPriceMarginParam = useMarketBasedPrice.get() ? marketPriceMargin : 0;
        long amount = amountAsCoin.get() != null ? amountAsCoin.get().getValue() : 0L;
        long minAmount = minAmountAsCoin.get() != null ? minAmountAsCoin.get().getValue() : 0L;

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
        return new Offer(offerId,
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                direction,
                fiatPrice,
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
                priceFeed);
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        openOfferManager.placeOffer(offer, totalToPayAsCoin.get().subtract(offerFeeAsCoin), useSavingsWallet, resultHandler);
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null)
            this.paymentAccount = paymentAccount;
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            this.tradeCurrency = tradeCurrency;
            final String code = tradeCurrency.getCode();
            tradeCurrencyCode.set(code);

            if (paymentAccount != null)
                paymentAccount.setSelectedTradeCurrency(tradeCurrency);

            if (!preferences.getUseStickyMarketPrice())
                priceFeed.setCurrencyCode(code);

            Optional<TradeCurrency> tradeCurrencyOptional = preferences.getTradeCurrenciesAsObservable().stream().filter(e -> e.getCode().equals(code)).findAny();
            if (!tradeCurrencyOptional.isPresent()) {
                if (CurrencyUtil.isCryptoCurrency(code)) {
                    CurrencyUtil.getCryptoCurrency(code).ifPresent(cryptoCurrency -> {
                        preferences.addCryptoCurrency(cryptoCurrency);
                    });
                } else {
                    CurrencyUtil.getFiatCurrency(code).ifPresent(fiatCurrency -> {
                        preferences.addFiatCurrency(fiatCurrency);
                    });
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmountAsCoin.get() != null && amountAsCoin.get() != null)
            return !minAmountAsCoin.get().isGreaterThan(amountAsCoin.get());
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (priceAsFiat.get() != null &&
                amountAsCoin.get() != null &&
                !amountAsCoin.get().isZero() &&
                !priceAsFiat.get().isZero()) {
            volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
        }

        updateBalance();
    }

    void calculateAmount() {
        if (volumeAsFiat.get() != null &&
                priceAsFiat.get() != null &&
                !volumeAsFiat.get().isZero() &&
                !priceAsFiat.get().isZero()) {
            // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
            amountAsCoin.set(formatter.reduceTo4Decimals(new ExchangeRate(priceAsFiat.get()).fiatToCoin(volumeAsFiat.get())));

            calculateTotalToPay();
        }
    }

    void calculateTotalToPay() {
        if (direction != null && amountAsCoin.get() != null) {
            Coin feeAndSecDeposit = offerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin);
            Coin feeAndSecDepositAndAmount = feeAndSecDeposit.add(amountAsCoin.get());
            Coin required = direction == Offer.Direction.BUY ? feeAndSecDeposit : feeAndSecDepositAndAmount;
            totalToPayAsCoin.set(required);
            log.debug("totalToPayAsCoin " + totalToPayAsCoin.get().toFriendlyString());
            updateBalance();
        }
    }

    void updateBalance() {
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

    public Coin getOfferFeeAsCoin() {
        return offerFeeAsCoin;
    }

    public Coin getNetworkFeeAsCoin() {
        return networkFeeAsCoin;
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

    double getMarketPriceMargin() {
        return marketPriceMargin;
    }

    void setMarketPriceMargin(double marketPriceMargin) {
        this.marketPriceMargin = marketPriceMargin;
    }
}
