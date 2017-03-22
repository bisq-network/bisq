/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.offer.takeoffer;

import com.google.inject.Inject;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PaymentAccountUtil;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.handlers.TradeResultHandler;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.common.model.ActivatableDataModel;
import io.bisq.gui.main.overlays.notifications.Notification;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.protobuffer.payload.arbitration.Arbitrator;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

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
    private final BtcWalletService walletService;
    private final User user;
    private final FeeService feeService;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final BSFormatter formatter;

    private Coin takerFeeAsCoin;
    private Coin txFeeAsCoin;
    private Coin totalTxFeeAsCoin;
    private Coin securityDeposit;
    // Coin feeFromFundingTx = Coin.NEGATIVE_SATOSHI;

    private Offer offer;

    private AddressEntry addressEntry;
    final StringProperty btcCode = new SimpleStringProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    // final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();
    // final BooleanProperty isMainNet = new SimpleBooleanProperty();
    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> balance = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> missingCoin = new SimpleObjectProperty<>(Coin.ZERO);

    private BalanceListener balanceListener;
    private PaymentAccount paymentAccount;
    private boolean isTabSelected;
    private boolean useSavingsWallet;
    Coin totalAvailableBalance;
    private Notification walletFundedNotification;
    Price tradePrice;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    TakeOfferDataModel(TradeManager tradeManager,
                       BtcWalletService walletService, User user, FeeService feeService,
                       Preferences preferences, PriceFeedService priceFeedService,
                       BSFormatter formatter) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.user = user;
        this.feeService = feeService;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.formatter = formatter;

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
            priceFeedService.setCurrencyCode(offer.getCurrencyCode());

        tradeManager.checkOfferAvailability(offer,
                () -> {
                },
                errorMessage -> new Popup().warning(errorMessage).show());
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

        if (DevEnv.DEV_MODE)
            amountAsCoin.set(offer.getAmount());

        securityDeposit = offer.getDirection() == Offer.Direction.SELL ?
                getBuyerSecurityDeposit() :
                getSellerSecurityDeposit();

        // Taker pays 2 times the tx fee because the mining fee might be different when offerer created the offer
        // and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the takeOfferFee tx is deducted from the takeOfferFee and not visible to the trader

        // The taker pays the mining fee for the trade fee tx and the trade txs. 
        // A typical trade fee tx has about 226 bytes (if one input). The trade txs has about 336-414 bytes. 
        // We use 400 as a safe value.
        // We cannot use tx size calculation as we do not know initially how the input is funded. And we require the
        // fee for getting the funds needed.
        // So we use an estimated average size and risk that in some cases we might get a bit of delay if the actual required 
        // fee would be larger. 
        // As we use the best fee estimation (for 1 confirmation) that risk should not be too critical as long there are
        // not too many inputs. The trade txs have no risks as there cannot be more than about 414 bytes. 
        // Only the trade fee tx carries a risk that it might be larger.

        // trade fee tx: 226 bytes (1 input) - 374 bytes (2 inputs)   
        // deposit tx: 336 bytes (1 MS output+ OP_RETURN) - 414 bytes (1 MS output + OP_RETURN + change in case of smaller trade amount)          
        // payout tx: 371 bytes            
        // disputed payout tx: 408 bytes 

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But the "take offer" happens usually after that so we should have already the value from the estimation service.
        txFeeAsCoin = feeService.getTxFee(400);
        totalTxFeeAsCoin = txFeeAsCoin.multiply(3);

        // We request to get the actual estimated fee
        requestTxFee();

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
            priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }

    void requestTxFee() {
        feeService.requestFees(() -> {
            txFeeAsCoin = feeService.getTxFee(400);
            totalTxFeeAsCoin = txFeeAsCoin.multiply(3);
            calculateTotalToPay();
        }, null);
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (!preferences.getUseStickyMarketPrice() && isTabSelected)
            priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    // errorMessageHandler is used only in the check availability phase. As soon we have a trade we write the error msg in the trade object as we want to 
    // have it persisted as well.
    void onTakeOffer(TradeResultHandler tradeResultHandler) {
        checkNotNull(totalTxFeeAsCoin, "totalTxFeeAsCoin must not be null");
        Coin fundsNeededForTrade = totalToPayAsCoin.get().subtract(takerFeeAsCoin).subtract(txFeeAsCoin);
        tradeManager.onTakeOffer(amountAsCoin.get(),
                txFeeAsCoin,
                takerFeeAsCoin,
                tradePrice.getValue(),
                fundsNeededForTrade,
                offer,
                paymentAccount.getId(),
                useSavingsWallet,
                tradeResultHandler,
                errorMessage -> {
                    log.warn(errorMessage);
                    new Popup<>().warning(errorMessage).show();
                }
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
        return PaymentAccountUtil.getPossiblePaymentAccounts(offer, user.getPaymentAccounts());
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
            volume.set(tradePrice.getVolumeByAmount(amountAsCoin.get()));
            //volume.set(new ExchangeRate(tradePrice).coinToFiat(amountAsCoin.get()));

            updateBalance();
        }
    }

    void applyAmount(Coin amount) {
        amountAsCoin.set(amount);

        takerFeeAsCoin = CoinUtil.getFeePerBtc(feeService.getTakeOfferFeeInBtcPerBtc(), amount);
        takerFeeAsCoin = CoinUtil.maxCoin(takerFeeAsCoin, feeService.getMinTakeOfferFeeInBtc());

        calculateTotalToPay();
    }

    void calculateTotalToPay() {
        // Taker pays 2 times the tx fee because the mining fee might be different when offerer created the offer
        // and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the takeOfferFee tx is deducted from the createOfferFee and not visible to the trader
        if (offer != null && amountAsCoin.get() != null && takerFeeAsCoin != null) {
            Coin value = takerFeeAsCoin.add(totalTxFeeAsCoin).add(securityDeposit);
            if (getDirection() == Offer.Direction.SELL)
                totalToPayAsCoin.set(value);
            else
                totalToPayAsCoin.set(value.add(amountAsCoin.get()));

            updateBalance();
            log.debug("totalToPayAsCoin " + totalToPayAsCoin.get().toFriendlyString());
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
        //noinspection ConstantConditions,ConstantConditions
        if (totalToPayAsCoin.get() != null && isWalletFunded.get() && walletFundedNotification == null && !DevEnv.DEV_MODE) {
            walletFundedNotification = new Notification()
                    .headLine(Res.get("notification.walletUpdate.headline"))
                    .notification(Res.get("notification.walletUpdate.msg", formatter.formatCoinWithCode(totalToPayAsCoin.get())))
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
            Coin dustAndFee = totalTxFeeAsCoin.add(Transaction.MIN_NONDUST_OUTPUT);
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

    public Coin getTakerFeeAsCoin() {
        checkNotNull(totalTxFeeAsCoin, "totalTxFeeAsCoin must not be null");
        return takerFeeAsCoin;
    }

    public Coin getTotalTxFeeAsCoin() {
        return totalTxFeeAsCoin;
    }

    public Coin getTxFeeAsCoin() {
        return txFeeAsCoin;
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

    public Coin getSecurityDeposit() {
        return securityDeposit;
    }

    public Coin getBuyerSecurityDeposit() {
        return offer.getBuyerSecurityDeposit();
    }

    public Coin getSellerSecurityDeposit() {
        return offer.getSellerSecurityDeposit();
    }
}
