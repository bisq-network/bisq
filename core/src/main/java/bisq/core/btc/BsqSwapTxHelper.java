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

package bisq.core.btc;


import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.script.ScriptPattern;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


/*  Helper for creating bsq swap transactions.
 * Tx output format:
 * At a minimum there will be 1 BSQ out and 1 BTC out
 * [0-1]   (Maker BSQ)
 * [0-1]   (Taker BSQ)
 * [0-1]   (Taker BTC)
 * [0-1]   (Maker BTC)
 *
 * BTC buyer pays trade fee for both parties since they already have BSQ.
 * BTC seller pays buyer in BTC for the burnt BSQ
 */

@Slf4j
public class BsqSwapTxHelper {
    private final BsqWalletService bsqWalletService;
    private final TradeWalletService tradeWalletService;
    @Getter
    private Coin txFeePerVbyte;
    @Getter
    private final BooleanProperty canBuildMySide = new SimpleBooleanProperty(false);
    private final boolean isBuyer;
    // My required amounts
    public final ObjectProperty<Coin> myBtc = new SimpleObjectProperty<>();
    public final ObjectProperty<Coin> myBsq = new SimpleObjectProperty<>();

    // Required input amounts per role
    private Coin buyerBsqIn = Coin.ZERO;
    private Coin sellerBtcIn = Coin.ZERO;
    @Getter
    private Coin buyerTradeFee;
    @Getter
    private Coin sellerTradeFee;

    // Amounts traded
    @Nullable
    private Coin btcAmount;
    @Nullable
    private Coin bsqAmount;
    @Nullable
    private Price price;

    private final String myBtcAddress;
    private final String myBsqAddress;


    /**
     * @param bsqWalletService      bsqWalletService
     * @param tradeWalletService    tradeWalletService
     * @param isBuyer               user is buyer
     * @param price                 trade price
     * @param btcAmount             trade amount
     * @param txFeePerVbyte         tx fee per vbyte
     * @param myBtcAddress          my BTC address
     * @param myBsqAddress          my BSQ address
     *
     */
    public BsqSwapTxHelper(BsqWalletService bsqWalletService,
                           TradeWalletService tradeWalletService,
                           boolean isBuyer,
                           @Nullable Price price,
                           @Nullable Coin btcAmount,
                           Coin txFeePerVbyte,
                           String myBtcAddress,
                           String myBsqAddress) {
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.isBuyer = isBuyer;
        this.price = price;
        this.btcAmount = btcAmount;
        this.txFeePerVbyte = txFeePerVbyte;
        this.myBtcAddress = myBtcAddress;
        this.myBsqAddress = myBsqAddress;

        updateAmounts();
    }

    public void setBtcAmount(Coin btcAmount) {
        this.btcAmount = btcAmount;
        updateAmounts();
    }

    public void setPrice(Price price) {
        this.price = price;
        updateAmounts();
    }

    public void setBuyerTradeFee(Coin amount) {
        buyerTradeFee = amount;
        updateAmounts();
    }

    public void setSellerTradeFee(Coin amount) {
        sellerTradeFee = amount;
        updateAmounts();
    }

    public void setMyTradeFee(Coin amount) {
        if (isBuyer) {
            setBuyerTradeFee(amount);
        } else {
            setSellerTradeFee(amount);
        }
    }

    public void setPeerTradeFee(Coin amount) {
        if (isBuyer) {
            setSellerTradeFee(amount);
        } else {
            setBuyerTradeFee(amount);
        }
    }

    public Coin getMyTradeFee() {
        return isBuyer ? getBuyerTradeFee() : getSellerTradeFee();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Recursively tries to build my side of bsq swap tx, using the tx size of the previous
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
     * @return TxData of a my side of bsq swap tx or null if unable to create my side
     */
    public TxData buildMySide(int step, @Nullable TxData parent, boolean payForOverhead) {
        if (step > 10) {
            return null;
        }
        checkNotNull(btcAmount, "btcAmount must not be null");
        checkNotNull(bsqAmount, "bsqAmount must not be null");
        try {
            // Buyer pays trade fee by burning BSQ
            // Seller pays trade fee by buying extra BSQ for buyer to burn
            // This is calculated in updateAmounts()
            var txFee = getTxFee(parent, payForOverhead);
            var requiredBsq = isBuyer ? buyerBsqIn : Coin.ZERO;
            var requiredBtc = isBuyer ? Coin.ZERO : sellerBtcIn.add(txFee);
            checkArgument(!requiredBsq.isNegative(), "required BSQ cannot be negative");
            checkArgument(!requiredBtc.isNegative(), "required BTC cannot be negative");

            // This prepares a tx with no inputs if no BSQ is required
            var preparedBsq = bsqWalletService.prepareBsqInputsForBsqSwap(requiredBsq);

            // Set outputs to change from inputs plus amount to receive in the trade
            var bsqOut = preparedBsq.second.add(isBuyer ? Coin.ZERO : bsqAmount);
            var btcOut = parent == null ? Coin.ZERO : parent.btcChange;
            if (isBuyer) {
                // Buyer pays tx fee by using the burnt BSQ satoshis, and if that's not
                // enough, reducing the BTC output received in the trade (or increasing
                // if more BSQ is burnt than the tx fee)
                var txFeeAdjustment = txFee.subtract(buyerTradeFee).subtract(sellerTradeFee);
                btcOut = btcOut.add(btcAmount).subtract(txFeeAdjustment);
            }

            // Build my side of tx with signed BTC inputs
            var txData = tradeWalletService.buildMySideBsqSwapTx(
                    preparedBsq.first,
                    requiredBtc,
                    myBsqAddress,
                    bsqOut,
                    myBtcAddress,
                    btcOut
            );

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
            log.error("Exception while building my side of tx {}", t.getMessage());
        }
        return null;
    }

    private static Coin bsqFromBtc(Coin btcAmount, Price price) {
        // Round BSQ down
        return Coin.valueOf(price.getVolumeByAmount(btcAmount).getMonetary().getValue() / 1_000_000);
    }

    public static Coin btcFromBsq(Coin bsqAmount, Price price) {
        var monetary = Altcoin.valueOf(price.getCurrencyCode(), bsqAmount.getValue());
        return price.getAmountByVolume(new Volume(monetary));
    }

    public void setTxFeePerVbyte(Coin txFeePerVbyte) {
        this.txFeePerVbyte = txFeePerVbyte;
        updateAmounts();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateAmounts() {
        if (price == null ||
                btcAmount == null ||
                txFeePerVbyte.isZero() ||
                buyerTradeFee == null ||
                sellerTradeFee == null) {
            bsqAmount = null;
            resetValues();
            updateMyValues();
            return;
        }

        bsqAmount = bsqFromBtc(btcAmount, price);

        // BTC buyer pays trade fee for both buyer and seller by burning BSQ
        buyerBsqIn = bsqAmount.add(buyerTradeFee).add(sellerTradeFee);
        // BTC seller pays buyer for the trade fee, basically selling some extra BTC to
        // get some more BSQ that are burnt
        sellerBtcIn = btcAmount.add(btcFromBsq(sellerTradeFee, price));

        updateMyValues();
    }

    private void resetValues() {
        buyerBsqIn = Coin.ZERO;
        sellerBtcIn = Coin.ZERO;
    }

    private void updateMyValues() {
        if (isBuyer) {
            myBtc.setValue(Coin.ZERO);
            myBsq.setValue(buyerBsqIn);
        } else {
            myBtc.setValue(sellerBtcIn);
            myBsq.setValue(Coin.ZERO);
        }
        canBuildMySide.set(!txFeePerVbyte.isZero() &&
                (myBtc.get().isPositive() || myBsq.get().isPositive()));
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
