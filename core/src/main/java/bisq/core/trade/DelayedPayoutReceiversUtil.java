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

package bisq.core.trade;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.util.FeeReceiverSelector;

import bisq.common.crypto.Hash;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelayedPayoutReceiversUtil {
    //TODO set date
    public static final Date ACTIVATION_DATE = Utilities.getUTCDate(2022, GregorianCalendar.OCTOBER, 25);
    public static final long MIN_REQUEST_AMOUNT = 50000; // We include only requests of at least 500 BSQ
    public static final long MIN_OUTPUT_AMOUNT = 5000; // 1 usd @20k price

    public static boolean isActivated() {
        return new Date().after(ACTIVATION_DATE);
    }

    public static Tuple2<List<Issuance>, byte[]> getIssuanceListAndHashTuple(DaoFacade daoFacade) {
        int minBlockHeight = daoFacade.getChainHeight() - FeeReceiverSelector.MAX_AGE;
        // We need a deterministic list so we sort by txId
        List<Issuance> sortedIssuanceList = daoFacade.getIssuanceSetForType(IssuanceType.COMPENSATION).stream()
                .sorted(Comparator.comparing(Issuance::getTxId))
                .filter(issuance -> issuance.getChainHeight() >= minBlockHeight)
                .filter(issuance -> issuance.getAmount() >= MIN_REQUEST_AMOUNT)
                .collect(Collectors.toList());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sortedIssuanceList.forEach(issuance -> {
            try {
                outputStream.write(issuance.toProtoMessage().toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        byte[] data = outputStream.toByteArray();
        try {
            outputStream.close();
        } catch (IOException ignore) {
        }
        byte[] hash = Hash.getSha256Ripemd160hash(data);
        return new Tuple2<>(sortedIssuanceList, hash);
    }

    public static List<Tuple2<Long, String>> getReceivers(DaoFacade daoFacade,
                                                          List<Issuance> issuanceList,
                                                          long inputAmount,
                                                          long tradeTxFee) {
        List<Tuple2<Long, String>> amountAddressTuples = FeeReceiverSelector.getAmountAddressList(daoFacade, issuanceList);
        if (amountAddressTuples.isEmpty()) {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the default address
            return List.of(new Tuple2<>(inputAmount, Param.RECIPIENT_BTC_ADDRESS.getDefaultValue()));
        }

        // We need to use the same txFeePerVbyte value for both traders.
        // We use the tradeTxFee value which is calculated from the average of taker fee tx size and deposit tx size.
        // Otherwise, we would need to sync the fee rate of both traders.
        // In case of very large taker fee tx we would get a too high fee, but as fee rate is anyway rather
        // arbitrary and volatile we are on the safer side. The delayed payout tx is published long after the
        // take offer event and the recommended fee at that moment might be very different to actual
        // recommended fee. To avoid that the delayed payout tx would get stuck due too low fees we use a
        // min. fee rate of 5 sat/vByte.
        double txSize = 246;   // Deposit tx has a clearly defined structure, so we know the size.
        long txFeePerVbyte = Math.max(5, Math.round(tradeTxFee / txSize));

        double factor = getSatPerWeightUnit(amountAddressTuples.size(),
                amountAddressTuples.stream().mapToDouble(e -> e.first).sum(),
                inputAmount,
                txFeePerVbyte);

        // We exclude entries with amounts below MIN_OUTPUT_AMOUNT
        List<Tuple2<Long, String>> trimmedAmountAddressList = amountAddressTuples.stream()
                .filter(tuple -> Math.round(tuple.first * factor) >= MIN_OUTPUT_AMOUNT)
                .collect(Collectors.toList());

        // Now we update the factor again. There might be still outputs smaller than MIN_OUTPUT_AMOUNT,
        // but we ignore that. It is a similar problem like fee estimation with potential alternating results leading
        // to an endless loop if done in iterations.
        double finalFactor = getSatPerWeightUnit(trimmedAmountAddressList.size(),
                trimmedAmountAddressList.stream().mapToDouble(e -> e.first).sum(),
                inputAmount,
                txFeePerVbyte);

        // With live data it should never happen if there are sufficient comp. requests.
        if (trimmedAmountAddressList.isEmpty()) {
            return List.of(new Tuple2<>(inputAmount, Param.RECIPIENT_BTC_ADDRESS.getDefaultValue()));
        }

        // With issuance data from Oct 2022 we get those results:
        // With a 0.0001 BTC trade we get 2 outputs.
        // With a 2 BTC trade we get 203 outputs. Largest output is 0.16867102 BTC (3507 USD)
        // Fee is 0.00065468 BTC (about 15 USD) with 10 sat/byte fee rate.
        // With a 0.25 BTC trade we get 195 outputs. Largest output is 0.0209077 BTC (434 USD)
        return trimmedAmountAddressList.stream()
                .map(tuple -> new Tuple2<>(Math.round(tuple.first * finalFactor), tuple.second))
                .filter(tuple -> tuple.first >= 500) // In case there are tiny outputs we remove them and the amount gets into miner fees
                .sorted(Comparator.comparing(tuple -> tuple.first))
                .collect(Collectors.toList());
    }

    private static double getSatPerWeightUnit(int numOutputs,
                                              double sumWeight,
                                              long inputAmount,
                                              long txFeePerVbyte) {
        // Output size: 32 bytes
        // Tx size without outputs: 51 bytes
        int txSize = 51 + numOutputs * 32;
        long minerFee = txFeePerVbyte * txSize;
        long spendableAmount = inputAmount - minerFee;
        return spendableAmount / sumWeight;
    }
}
