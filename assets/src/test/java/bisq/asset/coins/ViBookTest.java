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

public class ViBookTest extends AbstractAssetTest {

    public ViBookTest() {
        super(new ViBook());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("VESZy7B2kptnXjjc3hYRW9shATCynmatJd");
        assertValidAddress("VXcRxR2nCMNVHisAjviSEwPtDxCyjE8fc2");
        assertValidAddress("VWDWn4PiARKuMty1fXmGi96UZazqsXL4wR");
        assertValidAddress("VPVJddq66YKY4Sogh7YmxMkaz6NJJnsSAp");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("VEASZy7B2kptnXjjc3hYRW9shATCynmatJd");
        assertInvalidAddress("VXcRxFR2nCMNVHisAjviSEwPtDxCyjE8fc2");
        assertInvalidAddress("VWDWn4PiARKuMty1fXmGi96UZazqsXL4wRs");
        assertInvalidAddress("VPVJddq66YKY4Sogh7YmxMkaz6sNJJnsSAp");
    }
}