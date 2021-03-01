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


import bisq.core.monetary.Price;
import bisq.core.provider.fee.FeeService;

import org.bitcoinj.core.Coin;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;


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

@Slf4j
public class AtomicTxBuilder {
    private final FeeService feeService;
    private Coin myTxFeePerVbyte = Coin.ZERO;
    private Coin agreedTxFeePerVbyte = Coin.ZERO;
    @Getter
    private final BooleanProperty canBuildMySide = new SimpleBooleanProperty(false);
    private final boolean isBuyer;
    private final boolean isMaker;
    // My required amounts
    public final ObjectProperty<Coin> myBtc = new SimpleObjectProperty<>();
    public final ObjectProperty<Coin> myBsq = new SimpleObjectProperty<>();

    // Required amounts per role
    private Coin buyerBtc = Coin.ZERO;
    private Coin buyerBsq = Coin.ZERO;
    private Coin sellerBtc = Coin.ZERO;
    private Coin sellerBsq = Coin.ZERO;
    @Getter
    private Coin buyerFee;
    @Getter
    private boolean buyerFeeIsBtc = true;
    @Getter
    private Coin sellerFee;
    @Getter
    private boolean sellerFeeIsBtc = true;

    // Trade data
    @Nullable
    private Coin btcAmount;
    @Nullable
    private Price price;

    public AtomicTxBuilder(FeeService feeService,
                           boolean isBuyer,
                           boolean isMaker) {
        this.feeService = feeService;
        this.isBuyer = isBuyer;
        this.isMaker = isMaker;

        updateFee();
    }

    public void setBtcAmount(Coin btcAmount) {
        this.btcAmount = btcAmount;
        updateAmounts();
    }

    public void setPrice(Price price) {
        this.price = price;
        updateAmounts();
    }

    public void setBuyerFee(boolean feeIsBtc, Coin amount) {
        buyerFeeIsBtc = feeIsBtc;
        buyerFee = amount;
        updateAmounts();
    }

    public void setSellerFee(boolean feeIsBtc, Coin amount) {
        sellerFeeIsBtc = feeIsBtc;
        sellerFee = amount;
        updateAmounts();
    }

    public void setMyTradeFee(boolean feeIsBtc, Coin amount) {
        if (isBuyer) {
            setBuyerFee(feeIsBtc, amount);
        } else {
            setSellerFee(feeIsBtc, amount);
        }
    }

    public Coin getMyTradeFee() {
        return isBuyer ? getBuyerFee() : getSellerFee();
    }

    private void updateFee() {
        log.info("Start requestTxFee: myTxFeePerVbyte={} agreedTxFeePerVbyte={}", myTxFeePerVbyte, agreedTxFeePerVbyte);
        feeService.requestFees(() -> {
            myTxFeePerVbyte = feeService.getTxFeePerVbyte();
            if (agreedTxFeePerVbyte.isZero()) {
                agreedTxFeePerVbyte = myTxFeePerVbyte;
            }
            log.info("Completed requestTxFee: myTxFeePerVbyte={} agreedTxFeePerVbyte={}",
                    myTxFeePerVbyte, agreedTxFeePerVbyte);
            updateAmounts();
        });
    }

    private void updateAmounts() {
        if (price == null ||
                btcAmount == null ||
                agreedTxFeePerVbyte.isZero()) {
            resetBuyerValues();
            resetSellerValues();
            updateMyValues();
            return;
        }

        // TODO(sq): include required mining fees and account for burnt BSQ
        if (buyerFee != null) {
            buyerBtc = Coin.ZERO;
            // Round BSQ amount down
            buyerBsq = Coin.valueOf(price.getVolumeByAmount(btcAmount).getMonetary().getValue() / 1000000);
            if (buyerFeeIsBtc) {
                buyerBtc = buyerBtc.add(buyerFee);
            } else {
                buyerBsq = buyerBsq.add(buyerFee);
            }
        } else {
            resetBuyerValues();
        }

        if (sellerFee != null) {
            sellerBtc = btcAmount;
            sellerBsq = Coin.ZERO;
            if (sellerFeeIsBtc) {
                sellerBtc = sellerBtc.add(sellerFee);
            } else {
                sellerBsq = sellerBsq.add(sellerFee);
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
}
