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

public class BitDaricTest extends AbstractAssetTest {

    public BitDaricTest() {
        super(new BitDaric());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("RN8spHmkV6ZtRsquaTJMRZJujRQkkDNh2G");
        assertValidAddress("RTD9jtKybd7TeM597t5MkNof84GPka34R7");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("LAPc1FumbYifKpfBpGbRLuPcLAJwHUxeyu");
        assertInvalidAddress("ROPc1FumbYifKpfBpGbRLuPcLAJwHUxeyu");
        assertInvalidAddress("rN8spHmkV6ZtROquaTJMRZJujRQkkDNh2G");
        assertInvalidAddress("1NxrMzHCjG8X9kqTEZBXUNB5PC58DSXAht");
    }
}
