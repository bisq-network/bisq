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

import org.bitcoinj.core.Transaction;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

public class TransactionAwareTradableFactoryTest {
    @Test
    public void testCreateWhenNotOpenOfferOrTrade() {
        DisputeManager manager = mock(DisputeManager.class);

        TransactionAwareTradableFactory factory = new TransactionAwareTradableFactory(manager);

        Tradable delegate = mock(Tradable.class);
        assertFalse(delegate instanceof OpenOffer);
        assertFalse(delegate instanceof Trade);

        TransactionAwareTradable tradable = factory.create(delegate);

        assertFalse(tradable.isRelatedToTransaction(mock(Transaction.class)));
    }
}
