package bisq.core.arbitration;

import bisq.core.account.witness.AccountAgeWitness;
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
public class BuyerDataItemTest {
    private BuyerDataItem buyerDataItem1;
    private BuyerDataItem buyerDataItem2;
    private BuyerDataItem buyerDataItem3;
    private AccountAgeWitness accountAgeWitness1;
    private AccountAgeWitness accountAgeWitness2;
    private byte[] hash1 = "1".getBytes();
    private byte[] hash2 = "2".getBytes();

    @Before
    public void setup() {
        accountAgeWitness1 = new AccountAgeWitness(hash1, 123);
        accountAgeWitness2 = new AccountAgeWitness(hash2, 124);
        buyerDataItem1 = new BuyerDataItem(mock(PaymentAccountPayload.class), accountAgeWitness1, Coin.valueOf(546),
                mock(PublicKey.class));
        buyerDataItem2 = new BuyerDataItem(mock(PaymentAccountPayload.class), accountAgeWitness1, Coin.valueOf(547),
                mock(PublicKey.class));
        buyerDataItem3 = new BuyerDataItem(mock(PaymentAccountPayload.class), accountAgeWitness2, Coin.valueOf(548),
                mock(PublicKey.class));
    }

    @Test
    public void testEquals() {
        assertEquals(buyerDataItem1, buyerDataItem2);
        assertNotEquals(buyerDataItem1, buyerDataItem3);
        assertNotEquals(buyerDataItem2, buyerDataItem3);
    }

    @Test
    public void testHashCode() {
        assertEquals(buyerDataItem1.hashCode(), buyerDataItem2.hashCode());
        assertNotEquals(buyerDataItem1.hashCode(), buyerDataItem3.hashCode());
    }
}
