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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Coin;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TradePeerTxInputValidator {
    private TradePeerTxInputValidator() {
    }

    public static void validateContribution(List<RawTransactionInput> rawTransactionInputs,
                                            Coin expectedContribution,
                                            BtcWalletService walletService,
                                            String peerRole) {
        checkNotNull(expectedContribution, "%s expected contribution must not be null", peerRole);
        checkArgument(expectedContribution.isPositive(), "%s expected contribution must be positive", peerRole);

        long actualContribution = getValidatedInputValue(rawTransactionInputs, walletService, peerRole);
        checkArgument(actualContribution == expectedContribution.value,
                "%s contribution mismatch. actualContribution=%s, expectedContribution=%s",
                peerRole, actualContribution, expectedContribution.value);
    }

    private static long getValidatedInputValue(List<RawTransactionInput> rawTransactionInputs,
                                               BtcWalletService walletService,
                                               String peerRole) {
        checkNotNull(rawTransactionInputs, "%s raw transaction inputs must not be null", peerRole);
        checkArgument(!rawTransactionInputs.isEmpty(), "%s raw transaction inputs must not be empty", peerRole);

        long inputValue = 0;
        for (RawTransactionInput input : rawTransactionInputs) {
            checkNotNull(input, "%s raw transaction input must not be null", peerRole);
            checkArgument(input.value > 0, "%s raw transaction input value must be positive", peerRole);
            input.validate(walletService);
            checkArgument(walletService.isP2WH(input), "%s input must be P2WH", peerRole);
            inputValue = Math.addExact(inputValue, input.value);
        }
        return inputValue;
    }
}
