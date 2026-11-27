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

package bisq.desktop.main.funds.withdrawal;

import bisq.core.btc.model.WithdrawalEstimate;
import bisq.core.locale.Res;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WithdrawalViewConfirmationMessageTest {
    private static final String FROM = "from-address";
    private static final String TO = "to-address";

    private final CoinFormatter formatter =
            new ImmutableCoinFormatter(Config.baseCurrencyNetworkParameters().getMonetaryFormat());

    @BeforeAll
    static void setUpRes() {
        Res.setup();
    }

    @Test
    void showsTheAmountsOfTheEstimate() {
        WithdrawalEstimate estimate = new WithdrawalEstimate(Coin.valueOf(7_670), Coin.valueOf(330), 110, Coin.ZERO);

        assertEquals(details(), WithdrawalView.getConfirmationMessage(estimate, FROM, TO, formatter));
    }

    @Test
    void notesTheDustThatIsPartOfTheFee() {
        WithdrawalEstimate estimate = new WithdrawalEstimate(Coin.valueOf(7_670), Coin.valueOf(330), 110,
                Coin.valueOf(300));

        assertEquals(Res.get("funds.withdrawal.dustIncludedInFee", 300L) + "\n\n" + details(),
                WithdrawalView.getConfirmationMessage(estimate, FROM, TO, formatter));
    }

    // The sender's amount is the receiver's amount plus the fee: 8000 sat for the estimate of the tests
    private String details() {
        return Res.get("shared.sendFundsDetailsWithFee",
                formatter.formatCoinWithCode(Coin.valueOf(8_000)),
                FROM,
                TO,
                formatter.formatCoinWithCode(Coin.valueOf(330)),
                3.0,
                0.11,
                formatter.formatCoinWithCode(Coin.valueOf(7_670)));
    }
}
