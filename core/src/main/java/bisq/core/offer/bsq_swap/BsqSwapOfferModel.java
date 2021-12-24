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

package bisq.core.offer.bsq_swap;

import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.util.coin.CoinUtil;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class BsqSwapOfferModel {
    public final static String BSQ = "BSQ";

    private final OfferUtil offerUtil;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final FeeService feeService;

    // offer data
    @Setter
    @Getter
    private String offerId;
    @Getter
    private OfferDirection direction;
    @Getter
    private boolean isMaker;

    // amounts/price
    @Getter
    private final ObjectProperty<Coin> btcAmount = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> bsqAmount = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> minAmount = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Price> price = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Volume> volume = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Volume> minVolume = new SimpleObjectProperty<>();
    @Getter
    public final ObjectProperty<Coin> inputAmountAsCoin = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> payoutAmountAsCoin = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<Coin> missingFunds = new SimpleObjectProperty<>(Coin.ZERO);
    @Nullable
    private Coin txFee;
    @Getter
    private long txFeePerVbyte;

    private BalanceListener btcBalanceListener;
    private BsqBalanceListener bsqBalanceListener;

    //utils
    private final Predicate<ObjectProperty<Coin>> isNonZeroAmount = (c) -> c.get() != null && !c.get().isZero();
    private final Predicate<ObjectProperty<Price>> isNonZeroPrice = (p) -> p.get() != null && !p.get().isZero();
    private final Predicate<ObjectProperty<Volume>> isNonZeroVolume = (v) -> v.get() != null && !v.get().isZero();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqSwapOfferModel(OfferUtil offerUtil,
                             BtcWalletService btcWalletService,
                             BsqWalletService bsqWalletService,
                             FeeService feeService) {
        this.offerUtil = offerUtil;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.feeService = feeService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init(OfferDirection direction, boolean isMaker, @Nullable Offer offer) {
        this.direction = direction;
        this.isMaker = isMaker;

        if (offer != null) {
            setPrice(offer.getPrice());

            setBtcAmount(Coin.valueOf(Math.min(offer.getAmount().value, getMaxTradeLimit())));
            calculateVolumeForAmount(getBtcAmount());

            setMinAmount(offer.getMinAmount());
            calculateMinVolume();
        }

        createListeners();
        applyTxFeePerVbyte();

        calculateVolume();
        calculateInputAndPayout();
    }

    public void doActivate() {
        addListeners();
    }

    public void doDeactivate() {
        removeListeners();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void createListeners() {
        btcBalanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                calculateInputAndPayout();
            }
        };
        bsqBalanceListener = (availableBalance, availableNonBsqBalance, unverifiedBalance,
                              unconfirmedChangeBalance, lockedForVotingBalance, lockedInBondsBalance,
                              unlockingBondsBalance) -> calculateInputAndPayout();
    }

    public void addListeners() {
        btcWalletService.addBalanceListener(btcBalanceListener);
        bsqWalletService.addBsqBalanceListener(bsqBalanceListener);
    }

    public void removeListeners() {
        btcWalletService.removeBalanceListener(btcBalanceListener);
        bsqWalletService.removeBsqBalanceListener(bsqBalanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Calculations
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void calculateVolume() {
        if (isNonZeroPrice.test(price) && isNonZeroAmount.test(btcAmount)) {
            try {
                setVolume(calculateVolumeForAmount(btcAmount));
                calculateMinVolume();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    public void calculateMinVolume() {
        if (isNonZeroPrice.test(price) && isNonZeroAmount.test(minAmount)) {
            try {
                minVolume.set(calculateVolumeForAmount(minAmount));
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    public Volume calculateVolumeForAmount(ObjectProperty<Coin> amount) {
        return price.get().getVolumeByAmount(amount.get());
    }

    public void calculateAmount(Function<Coin, Coin> reduceTo4DecimalsFunction) {
        if (isNonZeroPrice.test(price) && isNonZeroVolume.test(volume)) {
            try {
                Coin amount = price.get().getAmountByVolume(volume.get());
                calculateVolume();
                btcAmount.set(reduceTo4DecimalsFunction.apply(amount));
                resetTxFeeAndMissingFunds();

                calculateInputAndPayout();
            } catch (Throwable t) {
                log.error(t.toString());
            }
        }
    }

    public void calculateInputAndPayout() {
        Coin bsqTradeAmountAsCoin = bsqAmount.get();
        Coin btcTradeAmountAsCoin = btcAmount.get();
        Coin tradeFeeAsCoin = getTradeFee();
        if (bsqTradeAmountAsCoin == null || btcTradeAmountAsCoin == null || tradeFeeAsCoin == null) {
            return;
        }
        long tradeFee = tradeFeeAsCoin.getValue();
        if (isBuyer()) {
            inputAmountAsCoin.set(BsqSwapCalculation.getBuyersBsqInputValue(bsqTradeAmountAsCoin.getValue(), tradeFee));
            try {
                payoutAmountAsCoin.set(BsqSwapCalculation.getBuyersBtcPayoutValue(bsqWalletService,
                        bsqTradeAmountAsCoin,
                        btcTradeAmountAsCoin,
                        txFeePerVbyte,
                        tradeFee));
            } catch (InsufficientBsqException e) {
                // As this is for the output we do not set the missingFunds here.

                // If we do not have sufficient funds we cannot calculate the required fee from the inputs and change,
                // so we use an estimated size for the tx fee.
                payoutAmountAsCoin.set(BsqSwapCalculation.getEstimatedBuyersBtcPayoutValue(btcTradeAmountAsCoin,
                        txFeePerVbyte,
                        tradeFee));
            }
        } else {
            try {
                inputAmountAsCoin.set(BsqSwapCalculation.getSellersBtcInputValue(btcWalletService,
                        btcTradeAmountAsCoin,
                        txFeePerVbyte,
                        tradeFee));
            } catch (InsufficientMoneyException e) {
                missingFunds.set(e.missing);

                // If we do not have sufficient funds we cannot calculate the required fee from the inputs and change,
                // so we use an estimated size for the tx fee.
                inputAmountAsCoin.set(BsqSwapCalculation.getEstimatedSellersBtcInputValue(btcTradeAmountAsCoin, txFeePerVbyte, tradeFee));
            }

            payoutAmountAsCoin.set(BsqSwapCalculation.getSellersBsqPayoutValue(bsqTradeAmountAsCoin.getValue(), tradeFee));
        }

        evaluateMissingFunds();
    }

    private void evaluateMissingFunds() {
        Coin walletBalance = isBuyer() ?
                bsqWalletService.getVerifiedBalance() :
                btcWalletService.getSavingWalletBalance();
        missingFunds.set(offerUtil.getBalanceShortage(inputAmountAsCoin.get(), walletBalance));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setBtcAmount(Coin btcAmount) {
        this.btcAmount.set(btcAmount);
        resetTxFeeAndMissingFunds();
    }

    public void setPrice(Price price) {
        this.price.set(price);
    }

    public void setVolume(Volume volume) {
        this.volume.set(volume);
        bsqAmount.set(volume != null ? BsqSwapCalculation.getBsqTradeAmount(volume) : null);
        resetTxFeeAndMissingFunds();
    }

    public void setMinAmount(Coin minAmount) {
        this.minAmount.set(minAmount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getTxFee() throws InsufficientMoneyException {
        if (txFee == null) {
            Coin tradeFeeAsCoin = getTradeFee();
            if (btcAmount.get() == null || tradeFeeAsCoin == null) {
                return txFee;
            }

            long tradeFee = tradeFeeAsCoin.getValue();
            Tuple2<List<RawTransactionInput>, Coin> btcInputsAndChange;
            if (isBuyer()) {
                btcInputsAndChange = BsqSwapCalculation.getBuyersBsqInputsAndChange(bsqWalletService,
                        bsqAmount.get().getValue(),
                        tradeFee);
            } else {
                btcInputsAndChange = BsqSwapCalculation.getSellersBtcInputsAndChange(btcWalletService,
                        btcAmount.get().getValue(),
                        txFeePerVbyte,
                        tradeFee);
            }
            int vBytes = BsqSwapCalculation.getVBytesSize(btcInputsAndChange.first, btcInputsAndChange.second.getValue());
            long adjustedTxFee = BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte, vBytes, tradeFee);
            txFee = Coin.valueOf(adjustedTxFee);
        }
        return txFee;
    }

    public Coin getEstimatedTxFee() {
        long adjustedTxFee = BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte,
                BsqSwapCalculation.ESTIMATED_V_BYTES,
                getTradeFee().getValue());
        return Coin.valueOf(adjustedTxFee);
    }

    public boolean hasMissingFunds() {
        evaluateMissingFunds();
        return missingFunds.get().isPositive();
    }

    public Coin getTradeFee() {
        return isMaker ? getMakerFee() : getTakerFee();
    }

    public Coin getTakerFee() {
        return CoinUtil.getTakerFee(false, btcAmount.get());
    }

    public Coin getMakerFee() {
        return CoinUtil.getMakerFee(false, btcAmount.get());
    }

    public boolean isBuyer() {
        return isMaker ? isBuyOffer() : isSellOffer();
    }

    public boolean isBuyOffer() {
        return direction == OfferDirection.BUY;
    }

    public boolean isSellOffer() {
        return direction == OfferDirection.SELL;
    }

    public boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmount.get() != null && btcAmount.get() != null)
            return !minAmount.get().isGreaterThan(btcAmount.get());
        return true;
    }

    public long getMaxTradeLimit() {
        return PaymentMethod.BSQ_SWAP.getMaxTradeLimitAsCoin(BSQ).getValue();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyTxFeePerVbyte() {
        // We only set the txFeePerVbyte at start, otherwise we might get diff. required amounts while user has view open
        txFeePerVbyte = feeService.getTxFeePerVbyte().getValue();
        resetTxFeeAndMissingFunds();
        feeService.requestFees(() -> {
            txFeePerVbyte = feeService.getTxFeePerVbyte().getValue();
            calculateInputAndPayout();
            resetTxFeeAndMissingFunds();
        });
    }

    private void resetTxFeeAndMissingFunds() {
        txFee = null;
        missingFunds.set(Coin.ZERO);
    }
}
