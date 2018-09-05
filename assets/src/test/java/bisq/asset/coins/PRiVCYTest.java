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

public class PRiVCYTest extends AbstractAssetTest {

    public PRiVCYTest() {
        super(new PRiVCY());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PEVFjfwsjKcPTDGdbaticuuARwzb3PEUdc");
        assertValidAddress("PDwYxxuVi6buPTWypE1xCY65b58yedygAt");
        assertValidAddress("PMJQkfnW4nXYpaYsmFE1UMoqHeEF68kjU3");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("DEVFjfwsjKcPTDGdbaticuuARwzb3PEUdc");
        assertInvalidAddress("MJJGFhcf1PSxjxRG6DMyyi188UEXJbgZcY");
        assertInvalidAddress("ZMJQkfnW4nXYpaYsmFE1UMoqHeEF68kjU3");
    }
}
