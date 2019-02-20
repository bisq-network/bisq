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

public class GalilelTest extends AbstractAssetTest {

    public GalilelTest() {
        super(new Galilel());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("UVwXGh5B1NZbYdgWThqf2cLdkEupVXEVNi");
        assertValidAddress("UbNJbC1hZgBH5tQ4HyrrQMEPswKxwwfziw");
        assertValidAddress("UgqDDV8aekEXFP7BWLmTNpSQfk7uVk1jCF");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1UgqDDV8aekEXFP7BWLmTNpSQfk7uVk1jCF");
        assertInvalidAddress("UgqDDV8aekEXFP7BWLmTNpSQfk7uVk1jCFd");
        assertInvalidAddress("UgqDDV8aekEXFP7BWLmTNpSQfk7uVk1jCF#");
    }
}
