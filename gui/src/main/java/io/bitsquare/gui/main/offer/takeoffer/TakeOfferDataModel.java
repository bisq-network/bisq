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

package io.bitsquare.gui.main.offer.takeoffer;

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
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.main.overlays.notifications.Notification;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.handlers.TradeResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class TakeOfferDataModel extends ActivatableDataModel {
    private final TradeManager tradeManager;
    final TradeWalletService tradeWalletService;
    final WalletService walletService;
    private final User user;
    private final Preferences preferences;
    private final PriceFeed priceFeed;
    private final BlockchainService blockchainService;
    private final BSFormatter formatter;

    private final Coin takerFeeAsCoin;
    private final Coin networkFeeAsCoin;
    private final Coin securityDepositAsCoin;
    // Coin feeFromFundingTx = Coin.NEGATIVE_SATOSHI;

    private Offer offer;

    private AddressEntry addressEntry;
    final StringProperty btcCode = new SimpleStringProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    // final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();
    // final BooleanProperty isMainNet = new SimpleBooleanProperty();
    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> balance = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> missingCoin = new SimpleObjectProperty<>(Coin.ZERO);

    private BalanceListener balanceListener;
    PaymentAccount paymentAccount;
    private boolean isTabSelected;
    boolean useSavingsWallet;
    Coin totalAvailableBalance;
    private Notification walletFundedNotification;
    Fiat tradePrice;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    TakeOfferDataModel(TradeManager tradeManager, TradeWalletService tradeWalletService,
                       WalletService walletService, User user,
                       Preferences preferences, PriceFeed priceFeed, BlockchainService blockchainService,
                       BSFormatter formatter) {
        this.tradeManager = tradeManager;
        this.tradeWalletService = tradeWalletService;
        this.walletService = walletService;
        this.user = user;
        this.preferences = preferences;
        this.priceFeed = priceFeed;
        this.blockchainService = blockchainService;
        this.formatter = formatter;

        takerFeeAsCoin = FeePolicy.getTakeOfferFee();
        networkFeeAsCoin = FeePolicy.getFixedTxFeeForTrades();
        securityDepositAsCoin = FeePolicy.getSecurityDeposit();

        // isMainNet.set(preferences.getBitcoinNetwork() == BitcoinNetwork.MAINNET);
    }

    @Override
    protected void activate() {
        // when leaving screen we reset state
        offer.setState(Offer.State.UNDEFINED);

        addBindings();
        addListeners();

        updateBalance();

        // TODO In case that we have funded but restarted, or canceled but took again the offer we would need to 
        // store locally the result when we received the funding tx(s).
        // For now we just ignore that rare case and bypass the check by setting a sufficient value
        // if (isWalletFunded.get())
        //     feeFromFundingTxProperty.set(FeePolicy.getMinRequiredFeeForFundingTx());

        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeed.setCurrencyCode(offer.getCurrencyCode());

        tradeManager.checkOfferAvailability(offer, () -> {
        });
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
        if (offer != null)
            tradeManager.onCancelAvailabilityRequest(offer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate
    void initWithData(Offer offer) {
        this.offer = offer;
        tradePrice = offer.getPrice();
        addressEntry = walletService.getOrCreateAddressEntry(offer.getId(), AddressEntry.Context.OFFER_FUNDING);
        checkNotNull(addressEntry, "addressEntry must not be null");

        ObservableList<PaymentAccount> possiblePaymentAccounts = getPossiblePaymentAccounts();
        checkArgument(!possiblePaymentAccounts.isEmpty(), "possiblePaymentAccounts.isEmpty()");
        paymentAccount = possiblePaymentAccounts.get(0);

        amountAsCoin.set(offer.getAmount());

        if (DevFlags.DEV_MODE)
            amountAsCoin.set(offer.getAmount());

        calculateVolume();
        calculateTotalToPay();

        balanceListener = new BalanceListener(addressEntry.getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();

                /*if (isMainNet.get()) {
                    SettableFuture<Coin> future = blockchainService.requestFee(tx.getHashAsString());
                    Futures.addCallback(future, new FutureCallback<Coin>() {
                        public void onSuccess(Coin fee) {
                            UserThread.execute(() -> setFeeFromFundingTx(fee));
                        }

                        public void onFailure(@NotNull Throwable throwable) {
                            UserThread.execute(() -> new Popup()
                                    .warning("We did not get a response for the request of the mining fee used " +
                                            "in the funding transaction.\n\n" +
                                            "Are you sure you used a sufficiently high fee of at least " +
                                            formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + "?")
                                    .actionButtonText("Yes, I used a sufficiently high fee.")
                                    .onAction(() -> setFeeFromFundingTx(FeePolicy.getMinRequiredFeeForFundingTx()))
                                    .closeButtonText("No. Let's cancel that payment.")
                                    .onClose(() -> setFeeFromFundingTx(Coin.NEGATIVE_SATOSHI))
                                    .show());
                        }
                    });
                } else {
                    setFeeFromFundingTx(FeePolicy.getMinRequiredFeeForFundingTx());
                    isFeeFromFundingTxSufficient.set(feeFromFundingTx.compareTo(FeePolicy.getMinRequiredFeeForFundingTx()) >= 0);
                }*/
            }
        };

        offer.resetState();

        if (!preferences.getUseStickyMarketPrice())
            priceFeed.setCurrencyCode(offer.getCurrencyCode());
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeed.setCurrencyCode(offer.getCurrencyCode());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    // errorMessageHandler is used only in the check availability phase. As soon we have a trade we write the error msg in the trade object as we want to 
    // have it persisted as well.
    void onTakeOffer(TradeResultHandler tradeResultHandler) {
        tradeManager.onTakeOffer(amountAsCoin.get(),
                tradePrice.getValue(),
                totalToPayAsCoin.get().subtract(takerFeeAsCoin),
                offer,
                paymentAccount.getId(),
                useSavingsWallet,
                tradeResultHandler
        );
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null)
            this.paymentAccount = paymentAccount;
    }

    void fundFromSavingsWallet() {
        useSavingsWallet = true;
        updateBalance();
        if (!isWalletFunded.get())
            this.useSavingsWallet = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    Offer.Direction getDirection() {
        return offer.getDirection();
    }

    public Offer getOffer() {
        return offer;
    }

    ObservableList<PaymentAccount> getPossiblePaymentAccounts() {
        ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList(new ArrayList<>());
        for (PaymentAccount paymentAccount : user.getPaymentAccounts()) {
            if (paymentAccount.getPaymentMethod().equals(offer.getPaymentMethod())) {
                for (TradeCurrency tradeCurrency : paymentAccount.getTradeCurrencies()) {
                    if (tradeCurrency.getCode().equals(offer.getCurrencyCode())) {
                        paymentAccounts.add(paymentAccount);
                    }
                }
            }
        }
        return paymentAccounts;
    }

    boolean hasAcceptedArbitrators() {
        return user.getAcceptedArbitrators().size() > 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        btcCode.bind(preferences.btcDenominationProperty());
    }

    private void removeBindings() {
        btcCode.unbind();
    }

    private void addListeners() {
        walletService.addBalanceListener(balanceListener);
    }

    private void removeListeners() {
        walletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (tradePrice != null && offer != null &&
                amountAsCoin.get() != null &&
                !amountAsCoin.get().isZero()) {
            volumeAsFiat.set(new ExchangeRate(tradePrice).coinToFiat(amountAsCoin.get()));

            updateBalance();
        }
    }

    void setAmount(Coin amount) {
        amountAsCoin.set(amount);
        calculateTotalToPay();
    }

    void calculateTotalToPay() {
        if (offer != null && amountAsCoin.get() != null) {
            if (getDirection() == Offer.Direction.SELL)
                totalToPayAsCoin.set(takerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin));
            else
                totalToPayAsCoin.set(takerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin).add(amountAsCoin.get()));

            updateBalance();
            log.debug("totalToPayAsCoin " + totalToPayAsCoin.get().toFriendlyString());
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
        if (totalToPayAsCoin.get() != null && isWalletFunded.get() && walletFundedNotification == null) {
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

    public void swapTradeToSavings() {
        walletService.swapTradeEntryToAvailableEntry(offer.getId(), AddressEntry.Context.OFFER_FUNDING);
        walletService.swapTradeEntryToAvailableEntry(offer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
    }

  /*  private void setFeeFromFundingTx(Coin fee) {
        feeFromFundingTx = fee;
        isFeeFromFundingTxSufficient.set(feeFromFundingTx.compareTo(FeePolicy.getMinRequiredFeeForFundingTx()) >= 0);
    }*/

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (offer != null && amountAsCoin.get() != null)
            return !offer.getMinAmount().isGreaterThan(amountAsCoin.get());
        return true;
    }

    boolean isAmountLargerThanOfferAmount() {
        //noinspection SimplifiableIfStatement
        if (amountAsCoin.get() != null && offer != null)
            return amountAsCoin.get().isGreaterThan(offer.getAmount());
        return true;
    }

    boolean wouldCreateDustForOfferer() {
        //noinspection SimplifiableIfStatement
        if (amountAsCoin.get() != null && offer != null) {
            Coin customAmount = offer.getAmount().subtract(amountAsCoin.get());
            Coin dustAndFee = FeePolicy.getFixedTxFeeForTrades().add(Transaction.MIN_NONDUST_OUTPUT);
            return customAmount.isPositive() && customAmount.isLessThan(dustAndFee);
        } else {
            return true;
        }
    }

    public PaymentMethod getPaymentMethod() {
        return offer.getPaymentMethod();
    }

    public String getCurrencyCode() {
        return offer.getCurrencyCode();
    }

    public String getCurrencyNameAndCode() {
        return CurrencyUtil.getNameByCode(offer.getCurrencyCode());
    }

    public Coin getSecurityDepositAsCoin() {
        return securityDepositAsCoin;
    }

    public Coin getTakerFeeAsCoin() {
        return takerFeeAsCoin;
    }

    public Coin getNetworkFeeAsCoin() {
        return networkFeeAsCoin;
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
    }

    public List<Arbitrator> getArbitrators() {
        return user.getAcceptedArbitrators();
    }

    public Preferences getPreferences() {
        return preferences;
    }
}
