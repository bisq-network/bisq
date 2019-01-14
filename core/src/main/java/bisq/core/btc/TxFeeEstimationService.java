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
    public static int TYPICAL_TX_WITH_1_INPUT_SIZE = 260;
    private static int DEPOSIT_TX_SIZE = 320;
    private static int PAYOUT_TX_SIZE = 380;
    private static int BSQ_INPUT_INCREASE = 150;
    private static int MAX_ITERATIONS = 10;
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

    public Tuple2<Coin, Integer> getEstimatedFeeAndTxSizeForTaker(Coin fundsNeededForTrade, Coin tradeFee) {
        return getEstimatedFeeAndTxSize(true,
                fundsNeededForTrade,
                tradeFee,
                feeService,
                btcWalletService,
                preferences);
    }

    public Tuple2<Coin, Integer> getEstimatedFeeAndTxSizeForMaker(Coin reservedFundsForOffer,
                                                                  Coin tradeFee) {
        return getEstimatedFeeAndTxSize(false,
                reservedFundsForOffer,
                tradeFee,
                feeService,
                btcWalletService,
                preferences);
    }

    private Tuple2<Coin, Integer> getEstimatedFeeAndTxSize(boolean isTaker,
                                                           Coin amount,
                                                           Coin tradeFee,
                                                           FeeService feeService,
                                                           BtcWalletService btcWalletService,
                                                           Preferences preferences) {
        Coin txFeePerByte = feeService.getTxFeePerByte();
        // We start with min taker fee size of 260
        int estimatedTxSize = TYPICAL_TX_WITH_1_INPUT_SIZE;
        try {
            estimatedTxSize = getEstimatedTxSize(List.of(tradeFee, amount), estimatedTxSize, txFeePerByte, btcWalletService);
        } catch (InsufficientMoneyException e) {
            if (isTaker) {
                // if we cannot do the estimation we use the payout tx size
                estimatedTxSize = PAYOUT_TX_SIZE;
            }
            log.info("We cannot do the fee estimation because there are not enough funds in the wallet. This is expected " +
                    "if the user pays from an external wallet. In that case we use an estimated tx size of {} bytes.", estimatedTxSize);
        }

        if (!preferences.isPayFeeInBtc()) {
            // If we pay the fee in BSQ we have one input more which adds about 150 bytes
            estimatedTxSize += BSQ_INPUT_INCREASE;
        }

        Coin txFee;
        int size;
        if (isTaker) {
            int averageSize = (estimatedTxSize + DEPOSIT_TX_SIZE) / 2;  // deposit tx has about 320 bytes
            // We use at least the size of the payout tx to not underpay at payout.
            size = Math.max(PAYOUT_TX_SIZE, averageSize);
            txFee = txFeePerByte.multiply(size);
            log.info("Fee estimation resulted in a tx size of {} bytes.\n" +
                    "We use an average between the taker fee tx and the deposit tx (320 bytes) which results in {} bytes.\n" +
                    "The payout tx has 380 bytes, we use that as our min value. Size for fee calculation is {} bytes.\n" +
                    "The tx fee of {} Sat", estimatedTxSize, averageSize, size, txFee.value);
        } else {
            size = estimatedTxSize;
            txFee = txFeePerByte.multiply(size);
            log.info("Fee estimation resulted in a tx size of {} bytes and a tx fee of {} Sat.", size, txFee.value);
        }

        return new Tuple2<>(txFee, size);
    }

    @VisibleForTesting
    static int getEstimatedTxSize(List<Coin> outputValues,
                                  int initialEstimatedTxSize,
                                  Coin txFeePerByte,
                                  BtcWalletService btcWalletService)
            throws InsufficientMoneyException {
        boolean isInTolerance;
        int estimatedTxSize = initialEstimatedTxSize;
        int realTxSize;
        int counter = 0;
        do {
            Coin txFee = txFeePerByte.multiply(estimatedTxSize);
            realTxSize = btcWalletService.getEstimatedFeeTxSize(outputValues, txFee);
            isInTolerance = isInTolerance(estimatedTxSize, realTxSize, 0.2);
            if (!isInTolerance) {
                estimatedTxSize = realTxSize;
            }
            counter++;
        }
        while (!isInTolerance && counter < MAX_ITERATIONS);
        if (!isInTolerance) {
            log.warn("We could not find a tx which satisfies our tolerance requirement of 20%. " +
                            "realTxSize={}, estimatedTxSize={}",
                    realTxSize, estimatedTxSize);
        }
        return estimatedTxSize;
    }

    @VisibleForTesting
    static boolean isInTolerance(int estimatedSize, int txSize, double tolerance) {
        checkArgument(estimatedSize > 0, "estimatedSize must be positive");
        checkArgument(txSize > 0, "txSize must be positive");
        checkArgument(tolerance > 0, "tolerance must be positive");
        double deviation = Math.abs(1 - ((double) estimatedSize / (double) txSize));
        return deviation <= tolerance;
    }
}
