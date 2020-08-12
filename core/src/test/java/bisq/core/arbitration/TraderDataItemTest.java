package bisq.core.arbitration;

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.support.dispute.arbitration.TraderDataItem;
import bisq.core.payment.payload.PaymentAccountPayload;

import org.bitcoinj.core.Coin;

import java.security.PublicKey;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

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
public class TraderDataItemTest {
    private TraderDataItem traderDataItem1;
    private TraderDataItem traderDataItem2;
    private TraderDataItem traderDataItem3;
    private AccountAgeWitness accountAgeWitness1;
    private AccountAgeWitness accountAgeWitness2;
    private byte[] hash1 = "1".getBytes();
    private byte[] hash2 = "2".getBytes();

    @Before
    public void setup() {
        accountAgeWitness1 = new AccountAgeWitness(hash1, 123);
        accountAgeWitness2 = new AccountAgeWitness(hash2, 124);
        traderDataItem1 = new TraderDataItem(mock(PaymentAccountPayload.class), accountAgeWitness1, Coin.valueOf(546),
                mock(PublicKey.class));
        traderDataItem2 = new TraderDataItem(mock(PaymentAccountPayload.class), accountAgeWitness1, Coin.valueOf(547),
                mock(PublicKey.class));
        traderDataItem3 = new TraderDataItem(mock(PaymentAccountPayload.class), accountAgeWitness2, Coin.valueOf(548),
                mock(PublicKey.class));
    }

    @Test
    public void testEquals() {
        assertEquals(traderDataItem1, traderDataItem2);
        assertNotEquals(traderDataItem1, traderDataItem3);
        assertNotEquals(traderDataItem2, traderDataItem3);
    }

    @Test
    public void testHashCode() {
        assertEquals(traderDataItem1.hashCode(), traderDataItem2.hashCode());
        assertNotEquals(traderDataItem1.hashCode(), traderDataItem3.hashCode());
    }
}
