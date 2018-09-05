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

package bisq.desktop.main.funds.transactions;

import bisq.core.arbitration.DisputeManager;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;

import javax.inject.Inject;

public class TransactionAwareTradableFactory {
    private final DisputeManager disputeManager;

    @Inject
    TransactionAwareTradableFactory(DisputeManager disputeManager) {
        this.disputeManager = disputeManager;
    }

    TransactionAwareTradable create(Tradable delegate) {
        if (delegate instanceof OpenOffer) {
            return new TransactionAwareOpenOffer((OpenOffer) delegate);
        } else if (delegate instanceof Trade) {
            return new TransactionAwareTrade((Trade) delegate, disputeManager);
        } else {
            return new DummyTransactionAwareTradable(delegate);
        }
    }
}
