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

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Util class for getting the estimated tx fee for maker or taker fee tx.
 */
@Slf4j
public class TxFeeEstimationService {

//  Size/vsize of typical trade txs
//  Real txs size/vsize may vary in 1 or 2 bytes from the estimated values.
//  Values calculated with https://gist.github.com/oscarguindzberg/3d1349cb65d9fd9af9de0feaa3fd27ac
//  legacy fee tx with 1 input, maker/taker fee paid in btc size/vsize = 258
//  legacy deposit tx without change size/vsize = 381
//  legacy deposit tx with change size/vsize = 414
//  legacy payout tx size/vsize = 337
//  legacy delayed payout tx size/vsize = 302
//  segwit fee tx with 1 input, maker/taker fee paid in btc vsize = 173
//  segwit deposit tx without change vsize = 232
//  segwit deposit tx with change vsize = 263
//  segwit payout tx vsize = 169
//  segwit delayed payout tx vsize = 139
public static final int TYPICAL_TX_WITH_1_INPUT_VSIZE = 175;
    private static final int DEPOSIT_TX_VSIZE = 233;

    private static final int BSQ_INPUT_INCREASE = 70;
    private static final int MAX_ITERATIONS = 10;

    private final FeeService feeService;
    private final BtcWalletService btcWalletService;
    private final Preferences preferences;

    @Inject
    public TxFeeEstimationService(FeeService feeService,
                                  BtcWalletService btcWalletService,
                                  Preferences preferences) {

        this.feeService = feeService;
        this.btcWalletService = btcWalletService;
        this.preferences = preferences;
    }

    public Tuple2<Coin, Integer> getEstimatedFeeAndTxVsizeForTaker(Coin fundsNeededForTrade, Coin tradeFee) {
        return getEstimatedFeeAndTxVsize(true,
                fundsNeededForTrade,
                tradeFee,
                feeService,
                btcWalletService,
                preferences);
    }

    public Tuple2<Coin, Integer> getEstimatedFeeAndTxVsizeForMaker(Coin reservedFundsForOffer,
                                                                   Coin tradeFee) {
        return getEstimatedFeeAndTxVsize(false,
                reservedFundsForOffer,
                tradeFee,
                feeService,
                btcWalletService,
                preferences);
    }

    private Tuple2<Coin, Integer> getEstimatedFeeAndTxVsize(boolean isTaker,
                                                            Coin amount,
                                                            Coin tradeFee,
                                                            FeeService feeService,
                                                            BtcWalletService btcWalletService,
                                                            Preferences preferences) {
        Coin txFeePerVbyte = feeService.getTxFeePerVbyte();
        // We start with min taker fee vsize of 175
        int estimatedTxVsize = TYPICAL_TX_WITH_1_INPUT_VSIZE;
        try {
            estimatedTxVsize = getEstimatedTxVsize(List.of(tradeFee, amount), estimatedTxVsize, txFeePerVbyte, btcWalletService);
        } catch (InsufficientMoneyException e) {
            if (isTaker) {
                // If we cannot do the estimation, we use the vsize o the largest of our txs which is the deposit tx.
                estimatedTxVsize = DEPOSIT_TX_VSIZE;
            }
            log.info("We cannot do the fee estimation because there are not enough funds in the wallet. This is expected " +
                    "if the user pays from an external wallet. In that case we use an estimated tx vsize of {} vbytes.", estimatedTxVsize);
        }

        if (!preferences.isPayFeeInBtc()) {
            // If we pay the fee in BSQ we have one input more which adds about 150 bytes
            // TODO: Clarify if there is always just one additional input or if there can be more.
            estimatedTxVsize += BSQ_INPUT_INCREASE;
        }

        Coin txFee;
        int vsize;
        if (isTaker) {
            int averageVsize = (estimatedTxVsize + DEPOSIT_TX_VSIZE) / 2;  // deposit tx has about 233 vbytes
            // We use at least the vsize of the deposit tx to not underpay it.
            vsize = Math.max(DEPOSIT_TX_VSIZE, averageVsize);
            txFee = txFeePerVbyte.multiply(vsize);
            log.info("Fee estimation resulted in a tx vsize of {} vbytes.\n" +
                    "We use an average between the taker fee tx and the deposit tx (233 vbytes) which results in {} vbytes.\n" +
                    "The deposit tx has 233 vbytes, we use that as our min value. Vsize for fee calculation is {} vbytes.\n" +
                    "The tx fee of {} Sat", estimatedTxVsize, averageVsize, vsize, txFee.value);
        } else {
            vsize = estimatedTxVsize;
            txFee = txFeePerVbyte.multiply(vsize);
            log.info("Fee estimation resulted in a tx vsize of {} vbytes and a tx fee of {} Sat.", vsize, txFee.value);
        }

        return new Tuple2<>(txFee, vsize);
    }

    public Tuple2<Coin, Integer> getEstimatedFeeAndTxVsize(Coin amount,
                                                           BtcWalletService btcWalletService) {
        Coin txFeePerVbyte = btcWalletService.getTxFeeForWithdrawalPerVbyte();
        // We start with min taker fee vsize of 175
        int estimatedTxVsize = TYPICAL_TX_WITH_1_INPUT_VSIZE;
        try {
            estimatedTxVsize = getEstimatedTxVsize(List.of(amount), estimatedTxVsize, txFeePerVbyte, btcWalletService);
        } catch (InsufficientMoneyException e) {
            log.info("We cannot do the fee estimation because there are not enough funds in the wallet. This is expected " +
                    "if the user pays from an external wallet. In that case we use an estimated tx vsize of {} vbytes.", estimatedTxVsize);
        }

        Coin txFee = txFeePerVbyte.multiply(estimatedTxVsize);
        log.info("Fee estimation resulted in a tx vsize of {} vbytes and a tx fee of {} Sat.", estimatedTxVsize, txFee.value);

        return new Tuple2<>(txFee, estimatedTxVsize);
    }

    // We start with the initialEstimatedTxVsize for a tx with 1 input (175) vbytes and get from BitcoinJ a tx back which
    // contains the required inputs to fund that tx (outputs + miner fee). The miner fee in that case is based on
    // the assumption that we only need 1 input. Once we receive back the real tx vsize from the tx BitcoinJ has created
    // with the required inputs we compare if the vsize is not more then 20% different to our assumed tx vsize. If we are inside
    // that tolerance we use that tx vsize for our fee estimation, if not (if there has been more then 1 inputs) we
    // apply the new fee based on the reported tx vsize and request again from BitcoinJ to fill that tx with the inputs
    // to be sufficiently funded. The algorithm how BitcoinJ selects utxos is complex and contains several aspects
    // (minimize fee, don't create too many tiny utxos,...). We treat that algorithm as an unknown and it is not
    // guaranteed that there are more inputs required if we increase the fee (it could be that there is a better
    // selection of inputs chosen if we have increased the fee and therefore less inputs and smaller tx vsize). As the increased fee might
    // change the number of inputs we need to repeat that process until we are inside of a certain tolerance. To avoid
    // potential endless loops we add a counter (we use 10, usually it takes just very few iterations).
    // Worst case would be that the last vsize we got reported is > 20% off to
    // the real tx vsize but as fee estimation is anyway a educated guess in the best case we don't worry too much.
    // If we have underpaid the tx might take longer to get confirmed.
    @VisibleForTesting
    static int getEstimatedTxVsize(List<Coin> outputValues,
                                   int initialEstimatedTxVsize,
                                   Coin txFeePerVbyte,
                                   BtcWalletService btcWalletService)
            throws InsufficientMoneyException {
        boolean isInTolerance;
        int estimatedTxVsize = initialEstimatedTxVsize;
        int realTxVsize;
        int counter = 0;
        do {
            Coin txFee = txFeePerVbyte.multiply(estimatedTxVsize);
            realTxVsize = btcWalletService.getEstimatedFeeTxVsize(outputValues, txFee);
            isInTolerance = isInTolerance(estimatedTxVsize, realTxVsize, 0.2);
            if (!isInTolerance) {
                estimatedTxVsize = realTxVsize;
            }
            counter++;
        }
        while (!isInTolerance && counter < MAX_ITERATIONS);
        if (!isInTolerance) {
            log.warn("We could not find a tx which satisfies our tolerance requirement of 20%. " +
                            "realTxVsize={}, estimatedTxVsize={}",
                    realTxVsize, estimatedTxVsize);
        }
        return estimatedTxVsize;
    }

    @VisibleForTesting
    static boolean isInTolerance(int estimatedVsize, int txVsize, double tolerance) {
        checkArgument(estimatedVsize > 0, "estimatedVsize must be positive");
        checkArgument(txVsize > 0, "txVsize must be positive");
        checkArgument(tolerance > 0, "tolerance must be positive");
        double deviation = Math.abs(1 - ((double) estimatedVsize / (double) txVsize));
        return deviation <= tolerance;
    }
}
