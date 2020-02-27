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

package bisq.core.trade.statistics;

import org.junit.Test;

import static bisq.core.trade.statistics.TradeStatistics2Maker.dayZeroTrade;
import static bisq.core.trade.statistics.TradeStatistics2Maker.depositTxId;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.withNull;
import static org.junit.Assert.assertTrue;


public class TradeStatistics2Test {

    @Test
    public void isValid_WithDepositTxId() {

        TradeStatistics2 tradeStatistic = make(dayZeroTrade);

        assertTrue(tradeStatistic.isValid());
    }

    @Test
    public void isValid_WithEmptyDepositTxId() {
        TradeStatistics2 tradeStatistic = make(dayZeroTrade.but(withNull(depositTxId)));

        assertTrue(tradeStatistic.isValid());
    }
}
