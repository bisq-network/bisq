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

package bisq.desktop.main.offer.bisq_v1.takeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.main.offer.bisq_v1.OfferDataModel;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.TradeResultHandler;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import com.google.inject.Inject;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.ObservableList;

import java.util.Set;

import lombok.Getter;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static bisq.core.payment.payload.PaymentMethod.HAL_CASH_ID;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class TakeOfferDataModel extends OfferDataModel {
    private final TradeManager tradeManager;
    private final OfferBook offerBook;
    private final BsqWalletService bsqWalletService;
    private final User user;
    private final FeeService feeService;
    private final MempoolService mempoolService;
    private final FilterManager filterManager;
    final Preferences preferences;
    private final TxFeeEstimationService txFeeEstimationService;
    private final PriceFeedService priceFeedService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final Navigation navigation;
    private final P2PService p2PService;

    private Coin txFeeFromFeeService;
    private Coin securityDeposit;
    // Coin feeFromFundingTx = Coin.NEGATIVE_SATOSHI;

    private Offer offer;

    // final BooleanProperty isFeeFromFundingTxSufficient = new SimpleBooleanProperty();
    // final BooleanProperty isMainNet = new SimpleBooleanProperty();
    private final ObjectProperty<Coin> amount = new SimpleObjectProperty<>();
    final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();

    private BalanceListener balanceListener;
    private PaymentAccount paymentAccount;
    private boolean isTabSelected;
    Price tradePrice;
    // Use an average of a typical trade fee tx with 1 input, deposit tx and payout tx.
    private int feeTxVsize = 192;  // (175+233+169)/3
    private boolean freezeFee;
    private Coin txFeePerVbyteFromFeeService;
    @Getter
    protected final IntegerProperty mempoolStatus = new SimpleIntegerProperty();
    @Getter
    protected String mempoolStatusText;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    TakeOfferDataModel(TradeManager tradeManager,
                       OfferBook offerBook,
                       OfferUtil offerUtil,
                       BtcWalletService btcWalletService,
                       BsqWalletService bsqWalletService,
                       User user, FeeService feeService,
                       MempoolService mempoolService,
                       FilterManager filterManager,
                       Preferences preferences,
                       TxFeeEstimationService txFeeEstimationService,
                       PriceFeedService priceFeedService,
                       AccountAgeWitnessService accountAgeWitnessService,
                       Navigation navigation,
                       P2PService p2PService
    ) {
        super(btcWalletService, offerUtil);

        this.tradeManager = tradeManager;
        this.offerBook = offerBook;
        this.bsqWalletService = bsqWalletService;
        this.user = user;
        this.feeService = feeService;
        this.mempoolService = mempoolService;
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.txFeeEstimationService = txFeeEstimationService;
        this.priceFeedService = priceFeedService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.navigation = navigation;
        this.p2PService = p2PService;
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

        if (canTakeOffer()) {
            tradeManager.checkOfferAvailability(offer,
                    false,
                    () -> {
                    },
                    errorMessage -> new Popup().warning(errorMessage).show());
        }
    }

    @Override
    protected void deactivate() {
        removeListeners();
        if (offer != null) {
            offer.cancelAvailabilityRequest();
        }
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
        paymentAccount = getLastSelectedPaymentAccount();

        this.amount.set(Coin.valueOf(Math.min(offer.getAmount().value, getMaxTradeLimit())));

        securityDeposit = offer.getDirection() == OfferDirection.SELL ?
                getBuyerSecurityDeposit() :
                getSellerSecurityDeposit();

        // Taker pays 3 times the tx fee (taker fee, deposit, payout) because the mining fee might be different when maker created the offer
        // and reserved his funds. Taker creates at least taker fee and deposit tx at nearly the same moment. Just the payout will
        // be later and still could lead to issues if the required fee changed a lot in the meantime. using RBF and/or
        // multiple batch-signed payout tx with different fees might be an option but RBF is not supported yet in BitcoinJ
        // and batched txs would add more complexity to the trade protocol.

        // A typical trade fee tx has about 175 vbytes (if one input). The trade txs has about 169-263 vbytes.
        // We use 192 as average value.

        // trade fee tx: 175 vbytes (1 input)
        // deposit tx: 233 vbytes (1 MS output+ OP_RETURN) - 263 vbytes (1 MS output + OP_RETURN + change in case of smaller trade amount)
        // payout tx: 169 vbytes
        // disputed payout tx: 139 vbytes

        // Set the default values (in rare cases if the fee request was not done yet we get the hard coded default values)
        // But the "take offer" happens usually after that so we should have already the value from the estimation service.
        txFeePerVbyteFromFeeService = feeService.getTxFeePerVbyte();
        txFeeFromFeeService = getTxFeeByVsize(feeTxVsize);

        // We request to get the actual estimated fee
        log.info("Start requestTxFee: txFeeFromFeeService={}", txFeeFromFeeService);
        feeService.requestFees(() -> {
            if (!freezeFee) {
                txFeePerVbyteFromFeeService = feeService.getTxFeePerVbyte();
                txFeeFromFeeService = getTxFeeByVsize(feeTxVsize);
                calculateTotalToPay();
                log.info("Completed requestTxFee: txFeeFromFeeService={}", txFeeFromFeeService);
            } else {
                log.debug("We received the tx fee response after we have shown the funding screen and ignore that " +
                        "to avoid that the total funds to pay changes due changed tx fees.");
            }
        });

        mempoolStatus.setValue(-1);
        OfferPayload offerPayload = offer.getOfferPayload().orElseThrow();
        mempoolService.validateOfferMakerTx(offerPayload, (txValidator -> {
            mempoolStatus.setValue(txValidator.isFail() ? 0 : 1);
            if (txValidator.isFail()) {
                mempoolStatusText = txValidator.toString();
                log.info("Mempool check of OfferFeePaymentTxId returned errors: [{}]", mempoolStatusText);
            }
        }));

        calculateVolume();
        calculateTotalToPay();

        balanceListener = new BalanceListener(addressEntry.getAddress()) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        };

        offer.resetState();

        priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }

    // We don't want that the fee gets updated anymore after we show the funding screen.
    void onShowPayFundsScreen() {
        estimateTxVsize();
        freezeFee = true;
        calculateTotalToPay();
    }

    void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
        if (isTabSelected)
            priceFeedService.setCurrencyCode(offer.getCurrencyCode());
    }

    public void onClose(boolean removeOffer) {
        // We do not wait until the offer got removed by a network remove message but remove it
        // directly from the offer book. The broadcast gets now bundled and has 2 sec. delay so the
        // removal from the network is a bit slower as it has been before. To avoid that the taker gets
        // confused to see the same offer still in the offerbook we remove it manually. This removal has
        // only local effect. Other trader might see the offer for a few seconds
        // still (but cannot take it).
        if (removeOffer) {
            offerBook.removeOffer(checkNotNull(offer));
        }

        btcWalletService.resetAddressEntriesForOpenOffer(offer.getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    // errorMessageHandler is used only in the check availability phase. As soon we have a trade we write the error msg in the trade object as we want to
    // have it persisted as well.
    void onTakeOffer(TradeResultHandler<Trade> tradeResultHandler) {
        checkNotNull(txFeeFromFeeService, "txFeeFromFeeService must not be null");
        checkNotNull(getTakerFee(), "takerFee must not be null");

        Coin fundsNeededForTrade = getFundsNeededForTrade();
        if (isBuyOffer())
            fundsNeededForTrade = fundsNeededForTrade.add(amount.get());

        if (filterManager.isCurrencyBanned(offer.getCurrencyCode())) {
            new Popup().warning(Res.get("offerbook.warning.currencyBanned")).show();
        } else if (filterManager.isPaymentMethodBanned(offer.getPaymentMethod())) {
            new Popup().warning(Res.get("offerbook.warning.paymentMethodBanned")).show();
        } else if (filterManager.isOfferIdBanned(offer.getId())) {
            new Popup().warning(Res.get("offerbook.warning.offerBlocked")).show();
        } else if (filterManager.isNodeAddressBanned(offer.getMakerNodeAddress())) {
            new Popup().warning(Res.get("offerbook.warning.nodeBlocked")).show();
        } else if (filterManager.requireUpdateToNewVersionForTrading()) {
            new Popup().warning(Res.get("offerbook.warning.requireUpdateToNewVersion")).show();
        } else if (tradeManager.wasOfferAlreadyUsedInTrade(offer.getId())) {
            new Popup().warning(Res.get("offerbook.warning.offerWasAlreadyUsedInTrade")).show();
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
                    false,
                    tradeResultHandler,
                    errorMessage -> {
                        log.warn(errorMessage);
                        new Popup().warning(errorMessage).show();
                    }
            );
        }
    }

    // This works only if have already funds in the wallet
    // TODO: There still are issues if we get funded by very small inputs which are causing higher tx fees and the
    // changed total required amount is not updated. That will cause a InsufficientMoneyException and the user need to
    // start over again. To reproduce keep adding 0.002 BTC amounts while in the funding screen.
    // It would require a listener on changed balance and a new fee estimation with a correct recalculation of the required funds.
    // Another edge case not handled correctly is: If there are many small inputs and user add a large input later the
    // fee estimation is based on the large tx with many inputs but the actual tx will get created with the large input, thus
    // leading to a smaller tx and too high fees. Simply updating the fee estimation would lead to changed required funds
    // and if funds get higher (if tx get larger) the user would get confused (adding small inputs would increase total required funds).
    // So that would require more thoughts how to deal with all those cases.
    public void estimateTxVsize() {
        int txVsize = 0;
        if (btcWalletService.getBalance(Wallet.BalanceType.AVAILABLE).isPositive()) {
            Coin fundsNeededForTrade = getFundsNeededForTrade();
            if (isBuyOffer())
                fundsNeededForTrade = fundsNeededForTrade.add(amount.get());

            // As taker we pay 3 times the fee and currently the fee is the same for all 3 txs (trade fee tx, deposit
            // tx and payout tx).
            // We should try to change that in future to have the deposit and payout tx with a fixed fee as the vsize is
            // there more deterministic.
            // The trade fee tx can be in the worst case very large if there are many inputs so if we take that tx alone
            // for the fee estimation we would overpay a lot.
            // On the other side if we have the best case of a 1 input tx fee tx then it is only 175 vbytes but the
            // other 2 txs are different (233 and 169 vbytes) and may get a lower fee/vbyte as intended.
            // We apply following model to not overpay too much but be on the safe side as well.
            // We sum the taker fee tx and the deposit tx together as it can be assumed that both be in the same block and
            // as they are dependent txs the miner will pick both if the fee in total is good enough.
            // We make sure that the fee is sufficient to meet our intended fee/vbyte for the larger deposit tx with 233 vbytes.
            Tuple2<Coin, Integer> estimatedFeeAndTxVsize = txFeeEstimationService.getEstimatedFeeAndTxVsizeForTaker(fundsNeededForTrade,
                    getTakerFee());
            txFeeFromFeeService = estimatedFeeAndTxVsize.first;
            feeTxVsize = estimatedFeeAndTxVsize.second;
        } else {
            feeTxVsize = 233;
            txFeeFromFeeService = txFeePerVbyteFromFeeService.multiply(feeTxVsize);
            log.info("We cannot do the fee estimation because there are no funds in the wallet.\nThis is expected " +
                            "if the user has not funded their wallet yet.\n" +
                            "In that case we use an estimated tx vsize of 233 vbytes.\n" +
                            "txFee based on estimated vsize of {} vbytes. feeTxVsize = {} vbytes. Actual tx vsize = {} vbytes. TxFee is {} ({} sat/vbyte)",
                    feeTxVsize, feeTxVsize, txVsize, txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerVbyte());
        }
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null) {
            this.paymentAccount = paymentAccount;

            long myLimit = getMaxTradeLimit();
            this.amount.set(Coin.valueOf(Math.max(offer.getMinAmount().value, Math.min(amount.get().value, myLimit))));

            preferences.setTakeOfferSelectedPaymentAccountId(paymentAccount.getId());
        }
    }

    void fundFromSavingsWallet() {
        useSavingsWallet = true;
        updateBalance();
        if (!isBtcWalletFunded.get()) {
            this.useSavingsWallet = false;
            updateBalance();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    OfferDirection getDirection() {
        return offer.getDirection();
    }

    public Offer getOffer() {
        return offer;
    }

    ObservableList<PaymentAccount> getPossiblePaymentAccounts() {
        Set<PaymentAccount> paymentAccounts = user.getPaymentAccounts();
        checkNotNull(paymentAccounts, "paymentAccounts must not be null");
        return PaymentAccountUtil.getPossiblePaymentAccounts(offer, paymentAccounts, accountAgeWitnessService);
    }

    public PaymentAccount getLastSelectedPaymentAccount() {
        ObservableList<PaymentAccount> possiblePaymentAccounts = getPossiblePaymentAccounts();
        checkArgument(!possiblePaymentAccounts.isEmpty(), "possiblePaymentAccounts must not be empty");
        PaymentAccount firstItem = possiblePaymentAccounts.get(0);

        String id = preferences.getTakeOfferSelectedPaymentAccountId();
        if (id == null)
            return firstItem;

        return possiblePaymentAccounts.stream()
                .filter(e -> e.getId().equals(id))
                .findAny()
                .orElse(firstItem);
    }

    long getMaxTradeLimit() {
        if (paymentAccount != null) {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, getCurrencyCode(),
                    offer.getMirroredDirection());
        } else {
            return 0;
        }
    }

    boolean canTakeOffer() {
        return GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation, paymentAccount.getSelectedTradeCurrency()) &&
                GUIUtil.isBootstrappedOrShowPopup(p2PService);
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
            Volume volumeByAmount = tradePrice.getVolumeByAmount(amount.get());
            if (offer.getPaymentMethod().getId().equals(PaymentMethod.HAL_CASH_ID))
                volumeByAmount = VolumeUtil.getAdjustedVolumeForHalCash(volumeByAmount);
            else if (CurrencyUtil.isFiatCurrency(getCurrencyCode()))
                volumeByAmount = VolumeUtil.getRoundedFiatVolume(volumeByAmount);

            volume.set(volumeByAmount);

            updateBalance();
        }
    }

    void applyAmount(Coin amount) {
        this.amount.set(Coin.valueOf(Math.min(amount.value, getMaxTradeLimit())));

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
            log.debug("totalToPayAsCoin {}", totalToPayAsCoin.get().toFriendlyString());
        }
    }

    boolean isBuyOffer() {
        return getDirection() == OfferDirection.BUY;
    }

    boolean isSellOffer() {
        return getDirection() == OfferDirection.SELL;
    }

    boolean isCryptoCurrency() {
        return CurrencyUtil.isCryptoCurrency(getCurrencyCode());
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

    public void swapTradeToSavings() {
        log.debug("swapTradeToSavings, offerId={}", offer.getId());
        btcWalletService.resetAddressEntriesForOpenOffer(offer.getId());
    }

    // We use the sum of the vsize of the trade fee and the deposit tx to get an average.
    // Miners will take the trade fee tx if the total fee of both dependent txs are good enough.
    // With that we avoid that we overpay in case that the trade fee has many inputs and we would apply that fee for the
    // other 2 txs as well. We still might overpay a bit for the payout tx.
    private int getAverageVsize(int txVsize) {
        return (txVsize + 233) / 2;
    }

    private Coin getTxFeeByVsize(int vsizeInVbytes) {
        return txFeePerVbyteFromFeeService.multiply(getAverageVsize(vsizeInVbytes));
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
        boolean result;
        if (amount.get() != null && offer != null) {
            Coin customAmount = offer.getAmount().subtract(amount.get());
            result = customAmount.isPositive() && customAmount.isLessThan(Restrictions.getMinNonDustOutput());

            if (result)
                log.info("would create dust for maker, customAmount={},  Restrictions.getMinNonDustOutput()={}", customAmount, Restrictions.getMinNonDustOutput());
        } else {
            result = true;
        }
        return result;
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
        Coin totalTxFees = txFeeFromFeeService.add(getTxFeeForDepositTx()).add(getTxFeeForPayoutTx());
        if (isCurrencyForTakerFeeBtc()) {
            return totalTxFees;
        } else {
            // when BSQ is burnt to pay the Bisq taker fee, it has the benefit of those sats also going to the miners.
            // so that reduces the explicit mining fee for the taker transaction
            Coin takerFee = getTakerFee() != null ? getTakerFee() : Coin.ZERO;
            return totalTxFees.subtract(Coin.valueOf(Math.min(takerFee.longValue(), txFeeFromFeeService.longValue())));
        }
    }

    @NotNull
    private Coin getFundsNeededForTrade() {
        return getSecurityDeposit().add(getTxFeeForDepositTx()).add(getTxFeeForPayoutTx());
    }

    private Coin getTxFeeForDepositTx() {
        //TODO fix with new trade protocol!
        // Unfortunately we cannot change that to the correct fees as it would break backward compatibility
        // We still might find a way with offer version or app version checks so lets keep that commented out
        // code as that shows how it should be.
        return txFeeFromFeeService; //feeService.getTxFee(233);
    }

    private Coin getTxFeeForPayoutTx() {
        //TODO fix with new trade protocol!
        // Unfortunately we cannot change that to the correct fees as it would break backward compatibility
        // We still might find a way with offer version or app version checks so lets keep that commented out
        // code as that shows how it should be.
        return txFeeFromFeeService; //feeService.getTxFee(169);
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

    public Coin getUsableBsqBalance() {
        // we have to keep a minimum amount of BSQ == bitcoin dust limit
        // otherwise there would be dust violations for change UTXOs
        // essentially means the minimum usable balance of BSQ is 5.46
        Coin usableBsqBalance = bsqWalletService.getAvailableBalance().subtract(Restrictions.getMinNonDustOutput());
        if (usableBsqBalance.isNegative())
            usableBsqBalance = Coin.ZERO;
        return usableBsqBalance;
    }

    public boolean isUsingHalCashAccount() {
        return paymentAccount.hasPaymentMethodWithId(HAL_CASH_ID);
    }

    public boolean isCurrencyForTakerFeeBtc() {
        return offerUtil.isCurrencyForTakerFeeBtc(amount.get());
    }

    public void setPreferredCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
        preferences.setPayFeeInBtc(isCurrencyForTakerFeeBtc);
    }

    public boolean isPreferredFeeCurrencyBtc() {
        return preferences.isPayFeeInBtc();
    }

    public Coin getTakerFeeInBtc() {
        return offerUtil.getTakerFee(true, amount.get());
    }

    public Coin getTakerFeeInBsq() {
        return offerUtil.getTakerFee(false, amount.get());
    }

    boolean isTakerFeeValid() {
        return preferences.getPayFeeInBtc() || offerUtil.isBsqForTakerFeeAvailable(amount.get());
    }

    public boolean isBsqForFeeAvailable() {
        return offerUtil.isBsqForTakerFeeAvailable(amount.get());
    }

    public boolean isAttemptToBuyBsq() {
        // When you buy an asset you actually sell BTC.
        // This is why an offer to buy BSQ is actually an offer to sell BTC for BSQ.
        return !isBuyOffer() && getOffer().getCurrencyCode().equals("BSQ");
    }
}
