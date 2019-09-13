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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;
import org.junit.Test;

public class MoneroTest extends AbstractAssetTest {

    public MoneroTest() {
        super(new Monero());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("4BJHitCigGy6giuYsJFP26KGkTKiQDJ6HJP1pan2ir2CCV8Twc2WWmo4fu1NVXt8XLGYAkjo5cJ3yH68Lfz9ZXEUJ9MeqPW");
        assertValidAddress("46tM15KsogEW5MiVmBn7waPF8u8ZsB6aHjJk7BAv1wvMKfWhQ2h2so5BCJ9cRakfPt5BFo452oy3K8UK6L2u2v7aJ3Nf7P2");
        assertValidAddress("86iQTnEqQ9mXJFvBvbY3KU5do5Jh2NCkpTcZsw3TMZ6oKNJhELvAreZFQ1p8EknRRTKPp2vg9fJvy47Q4ARVChjLMuUAFQJ");

        // integrated addresses
        assertValidAddress("4LL9oSLmtpccfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRj5UzqtReoS44qo9mtmXCqY45DJ852K5Jv2bYXZKKQePHES9khPK");
        assertValidAddress("4GdoN7NCTi8a5gZug7PrwZNKjvHFmKeV11L6pNJPgj5QNEHsN6eeX3DaAQFwZ1ufD4LYCZKArktt113W7QjWvQ7CWD1FFMXoYHeE6M55P9");
        assertValidAddress("4GdoN7NCTi8a5gZug7PrwZNKjvHFmKeV11L6pNJPgj5QNEHsN6eeX3DaAQFwZ1ufD4LYCZKArktt113W7QjWvQ7CW82yHFEGvSG3NJRNtH");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("4BJHitCigGy6giuYsJFP26KGkTKiQDJ6HJP1pan2ir2CCV8Twc2WWmo4fu1NVXt8XLGYAkjo5cJ3yH68Lfz9ZXEUJ9MeqP");
        assertInvalidAddress("4BJHitCigGy6giuYsJFP26KGkTKiQDJ6HJP1pan2ir2CCV8Twc2WWmo4fu1NVXt8XLGYAkjo5cJ3yH68Lfz9ZXEUJ9MeqPWW");
        assertInvalidAddress("86iQTnEqQ9mXJFvBvbY3KU5do5Jh2NCkpTcZsw3TMZ6oKNJhELvAreZFQ1p8EknRRTKPp2vg9fJvy47Q4ARVChjLMuUAFQ!");
        assertInvalidAddress("76iQTnEqQ9mXJFvBvbY3KU5do5Jh2NCkpTcZsw3TMZ6oKNJhELvAreZFQ1p8EknRRTKPp2vg9fJvy47Q4ARVChjLMuUAFQJ");
    }
}
