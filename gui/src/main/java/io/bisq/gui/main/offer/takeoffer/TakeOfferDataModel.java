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

package io.bisq.gui.main.offer.takeoffer;

import com.google.inject.Inject;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PaymentAccountUtil;
import io.bisq.core.payment.payload.PaymentMethod;
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
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;
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
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final User user;
    private final FeeService feeService;
    private final FilterManager filterManager;
    final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final TradeWalletService tradeWalletService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BSFormatter formatter;

    private Coin txFeeFromFeeService;
    private Coin securityDeposit;
    // Coin feeFromFundingTx = Coin.NEGATIVE_SATOSHI;

    private Offer offer;

    private AddressEntry addressEntry;
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    // final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();
    // final BooleanProperty isMainNet = new SimpleBooleanProperty();
    private final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
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
    private int feeTxSize = 320; // 260 kb is size of typical trade fee tx with 1 input but trade tx (deposit and payout) are larger so we adjust to 320
    private int feeTxSizeEstimationRecursionCounter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    TakeOfferDataModel(TradeManager tradeManager,
                       BtcWalletService btcWalletService, BsqWalletService bsqWalletService,
                       User user, FeeService feeService, FilterManager filterManager,
                       Preferences preferences, PriceFeedService priceFeedService, TradeWalletService tradeWalletService,
                       AccountAgeWitnessService accountAgeWitnessService, BSFormatter formatter) {
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.user = user;
        this.feeService = feeService;
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.tradeWalletService = tradeWalletService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.formatter = formatter;

        // isMainNet.set(preferences.getBaseCryptoNetwork() == BitcoinNetwork.BTC_MAINNET);
    }

    @Override
    protected void activate() {
        // when leaving screen we reset state
        offer.setState(Offer.State.UNKNOWN);

        addListeners();

        updateBalance();

        // TODO In case that we have funded but restarted, or canceled but took again the offer we would need to
        // store locally the result when we received the funding tx(s).
        // For now we just ignore that rare case and bypass the check by setting a sufficient value
        // if (isWalletFunded.get())
        //     feeFromFundingTxProperty.set(FeePolicy.getMinRequiredFeeForFundingTx());

        if (isTabSelected)
            priceFeedService.setCurrencyCode(offer.getCurrencyCode());

        tradeManager.checkOfferAvailability(offer,
                () -> {
                },
                errorMessage -> new Popup<>().warning(errorMessage).show());
    }

    @Override
    protected void deactivate() {
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
        addressEntry = btcWalletService.getOrCreateAddressEntry(offer.getId(), AddressEntry.Context.OFFER_FUNDING);
        checkNotNull(addressEntry, "addressEntry must not be null");

        ObservableList<PaymentAccount> possiblePaymentAccounts = getPossiblePaymentAccounts();
        checkArgument(!possiblePaymentAccounts.isEmpty(), "possiblePaymentAccounts.isEmpty()");
        paymentAccount = possiblePaymentAccounts.get(0);

        long myLimit = accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode());
        this.amount.set(Coin.valueOf(Math.min(offer.getAmount().value, myLimit)));

        securityDeposit = offer.getDirection() == OfferPayload.Direction.SELL ?
                getBuyerSecurityDeposit() :
                getSellerSecurityDeposit();

        // We request to get the actual estimated fee
        requestTxFee();


        // Taker pays 2 times the tx fee because the mining fee might be different when maker created the offer
        // and reserved his funds, so that would not work well with dynamic fees.

        // A typical trade fee tx has about 260 bytes (if one input). The trade txs has about 336-414 bytes.
        // We use 320 as a average value.

        // trade fee tx: 260 bytes (1 input)
        // deposit tx: 336 bytes (1 MS output+ OP_RETURN) - 414 bytes (1 MS output + OP_RETURN + change in case of smaller trade amount)
        // payout tx: 371 bytes
        // disputed payout tx: 408 bytes

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But the "take offer" happens usually after that so we should have already the value from the estimation service.
        txFeeFromFeeService = feeService.getTxFee(feeTxSize);

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
                            UserThread.execute(() -> new Popup<>()
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

        priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }

    void requestTxFee() {
        feeService.requestFees(() -> {
            txFeeFromFeeService = feeService.getTxFee(feeTxSize);
            calculateTotalToPay();
        }, null);
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (isTabSelected)
            priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }

    public void onClose() {
        btcWalletService.resetAddressEntriesForOpenOffer(offer.getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    // errorMessageHandler is used only in the check availability phase. As soon we have a trade we write the error msg in the trade object as we want to
    // have it persisted as well.
    void onTakeOffer(TradeResultHandler tradeResultHandler) {
        checkNotNull(txFeeFromFeeService, "txFeeFromFeeService must not be null");
        checkNotNull(getTakerFee(), "takerFee must not be null");

        Coin fundsNeededForTrade = getSecurityDeposit().add(txFeeFromFeeService).add(txFeeFromFeeService);
        if (isBuyOffer())
            fundsNeededForTrade = fundsNeededForTrade.add(amount.get());

        if (filterManager.isCurrencyBanned(offer.getCurrencyCode())) {
            new Popup<>().warning(Res.get("offerbook.warning.currencyBanned")).show();
        } else if (filterManager.isPaymentMethodBanned(offer.getPaymentMethod())) {
            new Popup<>().warning(Res.get("offerbook.warning.paymentMethodBanned")).show();
        } else if (filterManager.isOfferIdBanned(offer.getId())) {
            new Popup<>().warning(Res.get("offerbook.warning.offerBlocked")).show();
        } else if (filterManager.isNodeAddressBanned(offer.getMakerNodeAddress())) {
            new Popup<>().warning(Res.get("offerbook.warning.nodeBlocked")).show();
        } else {
            tradeManager.onTakeOffer(amount.get(),
                    txFeeFromFeeService,
                    getTakerFee(),
                    isCurrencyForTakerFeeBtc(),
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
    }

    // This works only if have already funds in the wallet
    public void estimateTxSize() {
        txFeeFromFeeService = feeService.getTxFee(feeTxSize);
        Address fundingAddress = btcWalletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
        Address reservedForTradeAddress = btcWalletService.getOrCreateAddressEntry(offer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
        Address changeAddress = btcWalletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();

        Coin reservedFundsForOffer = getSecurityDeposit();
        if (!isBuyOffer())
            reservedFundsForOffer = reservedFundsForOffer.add(amount.get());

        checkNotNull(user.getAcceptedArbitrators(), "user.getAcceptedArbitrators() must not be null");
        checkArgument(!user.getAcceptedArbitrators().isEmpty(), "user.getAcceptedArbitrators() must not be empty");
        String dummyArbitratorAddress = user.getAcceptedArbitrators().get(0).getBtcAddress();
        try {
            log.info("We create a dummy tx to see if our estimated size is in the accepted range. feeTxSize={}," +
                            " txFee based on feeTxSize: {}, recommended txFee is {} sat/byte",
                    feeTxSize, txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
            Transaction tradeFeeTx = tradeWalletService.estimateBtcTradingFeeTxSize(
                    fundingAddress,
                    reservedForTradeAddress,
                    changeAddress,
                    reservedFundsForOffer,
                    true,
                    getTakerFee(),
                    txFeeFromFeeService,
                    dummyArbitratorAddress);

            final int txSize = tradeFeeTx.bitcoinSerialize().length;
            // use feeTxSizeEstimationRecursionCounter to avoid risk for endless loop
            if (txSize > feeTxSize * 1.2 && feeTxSizeEstimationRecursionCounter < 10) {
                feeTxSizeEstimationRecursionCounter++;
                log.info("txSize is {} bytes but feeTxSize used for txFee calculation was {} bytes. We try again with an " +
                        "adjusted txFee to reach the target tx fee.", txSize, feeTxSize);
                feeTxSize = txSize;
                txFeeFromFeeService = feeService.getTxFee(feeTxSize);
                // lets try again with the adjusted txSize and fee.
                estimateTxSize();
            } else {
                log.info("feeTxSize {} bytes", feeTxSize);
                log.info("txFee based on estimated size: {}, recommended txFee is {} sat/byte",
                        txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
            }
        } catch (InsufficientMoneyException e) {
            // If we need to fund from an external wallet we can assume we only have 1 input (260 bytes).
            log.warn("We cannot do the fee estimation because there are not enough funds in the wallet. This is expected " +
                    "if the user pays from an external wallet. In that case we use an estimated tx size of 260 bytes.");
            feeTxSize = 260;
            txFeeFromFeeService = feeService.getTxFee(feeTxSize);
            log.info("feeTxSize {} bytes", feeTxSize);
            log.info("txFee based on estimated size: {}, recommended txFee is {} sat/byte",
                    txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
        }
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null) {
            this.paymentAccount = paymentAccount;

            long myLimit = accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode());
            this.amount.set(Coin.valueOf(Math.min(amount.get().value, myLimit)));
        }
    }

    void fundFromSavingsWallet() {
        useSavingsWallet = true;
        updateBalance();
        if (!isWalletFunded.get())
            this.useSavingsWallet = false;
    }

    void setIsCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
        preferences.setPayFeeInBtc(isCurrencyForTakerFeeBtc);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    OfferPayload.Direction getDirection() {
        return offer.getDirection();
    }

    public Offer getOffer() {
        return offer;
    }

    ObservableList<PaymentAccount> getPossiblePaymentAccounts() {
        return PaymentAccountUtil.getPossiblePaymentAccounts(offer, user.getPaymentAccounts());
    }

    boolean hasAcceptedArbitrators() {
        final List<Arbitrator> acceptedArbitrators = user.getAcceptedArbitrators();
        return acceptedArbitrators != null && acceptedArbitrators.size() > 0;
    }

    boolean isCurrencyForTakerFeeBtc() {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable();
    }

    boolean isTakerFeeValid() {
        return preferences.getPayFeeInBtc() || isBsqForFeeAvailable();
    }

    boolean isBsqForFeeAvailable() {
        final Coin takerFee = getTakerFee(false);
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                takerFee != null &&
                bsqWalletService.getAvailableBalance() != null &&
                !bsqWalletService.getAvailableBalance().subtract(takerFee).isNegative();
    }

    long getMaxTradeLimit() {
        if (paymentAccount != null)
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode());
        else
            return 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addListeners() {
        btcWalletService.addBalanceListener(balanceListener);
    }

    private void removeListeners() {
        btcWalletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (tradePrice != null && offer != null &&
                amount.get() != null &&
                !amount.get().isZero()) {
            volume.set(tradePrice.getVolumeByAmount(amount.get()));
            //volume.set(new ExchangeRate(tradePrice).coinToFiat(amountAsCoin.get()));

            updateBalance();
        }
    }

    void applyAmount(Coin amount) {
        long myLimit = accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode());
        this.amount.set(Coin.valueOf(Math.min(amount.value, myLimit)));

        calculateTotalToPay();
    }

    void calculateTotalToPay() {
        // Taker pays 2 times the tx fee because the mining fee might be different when maker created the offer
        // and reserved his funds, so that would not work well with dynamic fees.
        // The mining fee for the takeOfferFee tx is deducted from the createOfferFee and not visible to the trader
        final Coin takerFee = getTakerFee();
        if (offer != null && amount.get() != null && takerFee != null) {
            Coin feeAndSecDeposit = getTotalTxFee().add(securityDeposit);
            if (isCurrencyForTakerFeeBtc()) {
                feeAndSecDeposit = feeAndSecDeposit.add(takerFee);
            }
            if (isBuyOffer())
                totalToPayAsCoin.set(feeAndSecDeposit.add(amount.get()));
            else
                totalToPayAsCoin.set(feeAndSecDeposit);

            updateBalance();
            log.debug("totalToPayAsCoin " + totalToPayAsCoin.get().toFriendlyString());
        }
    }

    private boolean isBuyOffer() {
        return getDirection() == OfferPayload.Direction.BUY;
    }

    @Nullable
    Coin getTakerFee(boolean isCurrencyForTakerFeeBtc) {
        Coin amount = this.amount.get();
        if (amount != null) {
            // TODO write unit test for that
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    @Nullable
    public Coin getTakerFee() {
        return getTakerFee(isCurrencyForTakerFeeBtc());
    }


    @SuppressWarnings("PointlessBooleanExpression")
    private void updateBalance() {
        Coin tradeWalletBalance = btcWalletService.getBalanceForAddress(addressEntry.getAddress());
        if (useSavingsWallet) {
            Coin savingWalletBalance = btcWalletService.getSavingWalletBalance();
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
        log.debug("swapTradeToSavings, offerId={}", offer.getId());
        btcWalletService.resetAddressEntriesForOpenOffer(offer.getId());
    }

  /*  private void setFeeFromFundingTx(Coin fee) {
        feeFromFundingTx = fee;
        isFeeFromFundingTxSufficient.set(feeFromFundingTx.compareTo(FeePolicy.getMinRequiredFeeForFundingTx()) >= 0);
    }*/

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (offer != null && amount.get() != null)
            return !offer.getMinAmount().isGreaterThan(amount.get());
        return true;
    }

    boolean isAmountLargerThanOfferAmount() {
        //noinspection SimplifiableIfStatement
        if (amount.get() != null && offer != null)
            return amount.get().isGreaterThan(offer.getAmount());
        return true;
    }

    boolean wouldCreateDustForMaker() {
        //noinspection SimplifiableIfStatement
        if (amount.get() != null && offer != null) {
            Coin customAmount = offer.getAmount().subtract(amount.get());
            Coin dustAndFee = getTotalTxFee().add(Restrictions.getMinNonDustOutput());
            return customAmount.isPositive() && customAmount.isLessThan(dustAndFee);
        } else {
            return true;
        }
    }

    ReadOnlyObjectProperty<Coin> getAmount() {
        return amount;
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

    public Coin getTotalTxFee() {
        if (isCurrencyForTakerFeeBtc())
            return txFeeFromFeeService.multiply(3);
        else
            return txFeeFromFeeService.multiply(3).subtract(getTakerFee() != null ? getTakerFee() : Coin.ZERO);
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
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

    public Coin getBsqBalance() {
        return bsqWalletService.getAvailableBalance();
    }
}
