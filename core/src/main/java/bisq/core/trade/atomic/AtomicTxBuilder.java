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

package bisq.core.trade.atomic;


import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.monetary.Price;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.protocol.TxData;

import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.script.ScriptPattern;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.text.MessageFormat;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.abs;


// Helper for creating atomic transactions.
//
// Takes BTC amount and BSQ Price to calculate BSQ amount be exchanged for BTC.
//
// TODO(sq)
// Creates complete atomic tx using BTC and BSQ inputs from buyer and seller and output
// to corresponding addresses
// TODO(sq)
// Creates partial tx from local wallets when the counterparty inputs and outputs are
// unknown by gathering inputs and creating the appropriate outputs
//
// TODO(sq)
// Verification checks that input amounts match output amounts and minging fee. Only
// confirmed BSQ can be used as inputs since the amounts and validity needs be verified.
//
// TODO(sq)
// Calculate fee required by each party separately, using the agreed fee.
//
// Present total amount of BTC and BSQ required locally as ObjectProperties

/* Tx format:
 * [1]     (BSQtradeAmount + BSQchange seller, sellerBSQAddress)
 * [0-1]   (BSQchange buyer, buyerBSQAddress)
 * [1]     (BTCtradeAmount + BTCchange, buyerBTCAddress) (Change from BTC for txFee and/or tradeFee)
 * [0-1]   (BTCchange, sellerBTCAddress) (Change from BTC for tradeAmount payment)
 * [0-1]   (BTCtradeFee, tradeFeeAddress)
 * [0]     (BTC txFee)
 */

@Slf4j
public class AtomicTxBuilder {
    private final FeeService feeService;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final TradeWalletService tradeWalletService;
    //    private Coin myTxFeePerVbyte = Coin.ZERO;
//    private Coin agreedTxFeePerVbyte = Coin.ZERO;
    @Getter
    private Coin txFeePerVbyte;
    @Getter
    private final BooleanProperty canBuildMySide = new SimpleBooleanProperty(false);
    private final boolean isBuyer;
    private final boolean isMaker;
    // My required amounts
    public final ObjectProperty<Coin> myBtc = new SimpleObjectProperty<>();
    public final ObjectProperty<Coin> myBsq = new SimpleObjectProperty<>();

    // Required input amounts per role
    private Coin buyerBtc = Coin.ZERO;
    private Coin buyerBsq = Coin.ZERO;
    private Coin sellerBtc = Coin.ZERO;
    private Coin sellerBsq = Coin.ZERO;
    @Getter
    private Coin buyerTradeFee;
    @Getter
    private boolean buyerTradeFeeIsBtc = true;
    @Getter
    private Coin sellerTradeFee;
    @Getter
    private boolean sellerTradeFeeIsBtc = true;

    // Trade data
    @Nullable
    private Coin btcAmount;
    @Nullable
    private Coin bsqAmount;
    @Nullable
    private Price price;

    private final String myBtcAddress;
    private final String myBsqAddress;
    private final String btcTradeFeeAddress;


    /**
     * @param feeService    feeService
     * @param isBuyer       user is buyer
     * @param isMaker       user is maker
     * @param price         trade price
     * @param btcAmount     trade amount
     * @param txFeePerVbyte tx fee per vbyte, null to use fee from feeService, otherwise
     *                      check that the given value doesn't differ too much from
     *                      feeService
     * @param resultHandler result handler
     * @param faultHandler  fault handler
     */
    public AtomicTxBuilder(FeeService feeService,
                           BtcWalletService btcWalletService,
                           BsqWalletService bsqWalletService,
                           TradeWalletService tradeWalletService,
                           boolean isBuyer,
                           boolean isMaker,
                           @Nullable Price price,
                           @Nullable Coin btcAmount,
                           Coin txFeePerVbyte,
                           String myBtcAddress,
                           String myBsqAddress,
                           String btcTradeFeeAddress,
                           @Nullable ResultHandler resultHandler,
                           @Nullable FaultHandler faultHandler) {
        this.feeService = feeService;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.isBuyer = isBuyer;
        this.isMaker = isMaker;
        this.price = price;
        this.btcAmount = btcAmount;
        this.txFeePerVbyte = txFeePerVbyte;
        if (txFeePerVbyte == null)
            this.txFeePerVbyte = Coin.ZERO;
        this.myBtcAddress = myBtcAddress;
        this.myBsqAddress = myBsqAddress;
        this.btcTradeFeeAddress = btcTradeFeeAddress;

        updateFee(resultHandler, faultHandler);
    }

    public void setBtcAmount(Coin btcAmount) {
        this.btcAmount = btcAmount;
        updateAmounts();
    }

    public void setPrice(Price price) {
        this.price = price;
        updateAmounts();
    }

    public void setBuyerTradeFee(boolean feeIsBtc, Coin amount) {
        buyerTradeFeeIsBtc = feeIsBtc;
        buyerTradeFee = amount;
        updateAmounts();
    }

    public void setSellerTradeFee(boolean feeIsBtc, Coin amount) {
        sellerTradeFeeIsBtc = feeIsBtc;
        sellerTradeFee = amount;
        updateAmounts();
    }

    public void setMyTradeFee(boolean feeIsBtc, Coin amount) {
        if (isBuyer) {
            setBuyerTradeFee(feeIsBtc, amount);
        } else {
            setSellerTradeFee(feeIsBtc, amount);
        }
    }

    public void setPeerTradeFee(boolean feeIsBtc, Coin amount) {
        if (isBuyer) {
            setSellerTradeFee(feeIsBtc, amount);
        } else {
            setBuyerTradeFee(feeIsBtc, amount);
        }
    }

    public Coin getMyTradeFee() {
        return isBuyer ? getBuyerTradeFee() : getSellerTradeFee();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Recursively tries to build my side of atomic tx, using the tx size of the previous
     * steps to calculate the mining fee of the current step. break recursion when two
     * steps in a row produce the same tx.
     *
     * To avoid a non convergent case where the signatures of two consecutive steps
     * differ in size, the tx size of the larger of the two previous steps is used.
     *
     * Break after 10 steps if not converging.
     *
     * @param step              Current recursion step
     * @param parent            TxData of previous recursion step
     * @param payForOverhead    Pay for overhead bytes
     *
     * @return TxData of a my side of atomic tx or null if unable to create my side
     */
    public TxData buildMySide(int step, @Nullable TxData parent, boolean payForOverhead) {
        if (step > 10) {
            return null;
        }
        checkNotNull(btcAmount, "btcAmount must not be null");
        checkNotNull(bsqAmount, "bsqAmount must not be null");
        try {
            var requiredBsq = isBuyer ? buyerBsq : sellerBsq;
            var requiredBtc = isBuyer ? buyerBtc : sellerBtc;
            requiredBtc = requiredBtc.add(getTxFee(parent, payForOverhead)).subtract(getMyBsqTradeFee());
            if (requiredBtc.isLessThan(Coin.ZERO)) {
                requiredBtc = Coin.ZERO;
            }

            // This might prepare a tx with no inputs if no BSQ is required
            var preparedBsq = bsqWalletService.prepareAtomicBsqInputs(requiredBsq);

            // Set outputs to change from inputs plus amount to receive in the trade
            var bsqOut = preparedBsq.second.add(isBuyer ? Coin.ZERO : bsqAmount);
            var btcOut = parent == null ? Coin.ZERO : parent.btcChange;
            btcOut = btcOut.add(isBuyer ? btcAmount : Coin.ZERO);

            // Build my side of tx with signed BTC inputs
            var txData = tradeWalletService.buildMySideAtomicTx(
                    preparedBsq.first,
                    requiredBtc,
                    myBsqAddress,
                    bsqOut,
                    myBtcAddress,
                    btcOut,
                    btcTradeFeeAddress,
                    getMyBtcTradeFee());

            // Sign BSQ inputs
            txData.setTx(bsqWalletService.signInputs(txData.tx, txData.bsqInputs));

            // No more inputs are needed if this iteration didn't generate a different tx
            if (parent != null && txData.tx.equals(parent.tx)) {
                log.debug("feePerVbyte={} My fee={} vsize={} Raw myside tx={}",
                        txFeePerVbyte,
                        getTxFee(parent, payForOverhead),
                        txData.tx.getVsize(),
                        Utilities.bytesAsHexString(txData.tx.bitcoinSerialize()));
                return txData;
            }

            return buildMySide(step + 1, txData, payForOverhead);
        } catch (Throwable t) {
            log.error("Exception while building my side of atomicTx {}", t.getMessage());
        }
        return null;
    }

    public static Coin bsqFromBtc(Coin btcAmount, Price price) {
        // Round BSQ down
        return Coin.valueOf(price.getVolumeByAmount(btcAmount).getMonetary().getValue() / 1_000_000);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateFee(@Nullable ResultHandler resultHandler, @Nullable FaultHandler faultHandler) {
        log.info("Start requestTxFee: txFeePerVbyte={}", txFeePerVbyte);
        feeService.requestFees(() -> {
                    if (txFeePerVbyte.isZero()) {
                        // Use fee from fee service
                        txFeePerVbyte = feeService.getTxFeePerVbyte();
                    } else if (!isAcceptableTxFee(feeService.getTxFeePerVbyte(), txFeePerVbyte)) {
                        var errorMessage = MessageFormat.format("TxFee disagreement myFee={0} otherFee={1}",
                                feeService.getTxFeePerVbyte(), txFeePerVbyte);
                        if (faultHandler != null) {
                            faultHandler.handleFault(errorMessage, null);
                        }
                        return;
                    }
                    log.info("Completed requestTxFee: txFeePerVbyte={}", txFeePerVbyte);
                    updateAmounts();
                    if (resultHandler != null) {
                        resultHandler.handleResult();
                    }
                },
                faultHandler);
    }

    private void updateAmounts() {
        if (price == null ||
                btcAmount == null ||
                txFeePerVbyte.isZero()) {
            bsqAmount = null;
            resetBuyerValues();
            resetSellerValues();
            updateMyValues();
            return;
        }

        bsqAmount = bsqFromBtc(btcAmount, price);

        if (buyerTradeFee != null) {
            buyerBtc = Coin.ZERO;
            buyerBsq = bsqAmount;
            if (buyerTradeFeeIsBtc) {
                buyerBtc = buyerBtc.add(buyerTradeFee);
            } else {
                buyerBsq = buyerBsq.add(buyerTradeFee);
            }
        } else {
            resetBuyerValues();
        }

        if (sellerTradeFee != null) {
            sellerBtc = btcAmount;
            sellerBsq = Coin.ZERO;
            if (sellerTradeFeeIsBtc) {
                sellerBtc = sellerBtc.add(sellerTradeFee);
            } else {
                sellerBsq = sellerBsq.add(sellerTradeFee);
            }
        } else {
            resetSellerValues();
        }

        updateMyValues();
    }

    private void resetBuyerValues() {
        buyerBtc = Coin.ZERO;
        buyerBsq = Coin.ZERO;
    }

    private void resetSellerValues() {
        sellerBtc = Coin.ZERO;
        sellerBsq = Coin.ZERO;
    }

    private void updateMyValues() {
        if (isBuyer) {
            myBtc.setValue(buyerBtc);
            myBsq.setValue(buyerBsq);
        } else {
            myBtc.setValue(sellerBtc);
            myBsq.setValue(sellerBsq);
        }
        canBuildMySide.set(myBtc.get().isPositive() || myBsq.get().isPositive());
    }

    private boolean isAcceptableTxFee(Coin myFee, Coin peerFee) {
        var fee1 = (double) myFee.getValue();
        var fee2 = (double) peerFee.getValue();
        // Allow for 10% diff in mining fee, ie, maker will accept taker fee that's 10%
        // off their own fee from service. Both parties will use the same fee while
        // creating the atomic tx
        return abs(1 - fee1 / fee2) < 0.1;
    }

    private Coin getMyBtcTradeFee() {
        if (isBuyer) {
            return buyerTradeFeeIsBtc ? buyerTradeFee : Coin.ZERO;
        }
        return sellerTradeFeeIsBtc ? sellerTradeFee : Coin.ZERO;
    }

    private Coin getMyBsqTradeFee() {
        if (isBuyer) {
            return buyerTradeFeeIsBtc ? Coin.ZERO : buyerTradeFee;
        }
        return sellerTradeFeeIsBtc ? Coin.ZERO : sellerTradeFee;
    }

    /* Tx size:
     * Overhead:    10
     * Inputs:      41 * N
     * Outputs:     31 * N_sw + 34 * N_p2pkh
     * Sig p2pkh:   108
     * Sig SW:      29
     */
    private Coin getTxFee(@Nullable TxData parent, boolean payForOverhead) {
        if (parent == null) {
            return Coin.ZERO;
        }

        // Overhead
        var size = payForOverhead ? 10L : 0L;

        // Inputs and signatures
        for (var input : parent.tx.getInputs()) {
            size += 41 + (input.hasWitness() ? 29 : 108);
        }

        // Outputs
        for (var output : parent.tx.getOutputs()) {
            size += ScriptPattern.isP2WPKH(output.getScriptPubKey()) ? 31 : 34;
        }

        return txFeePerVbyte.multiply(size);
    }

}
