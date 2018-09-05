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

public class AngelcoinTest extends AbstractAssetTest {

    public AngelcoinTest() {
        super(new Angelcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("AQJTNtWcP7opxuR52Lf5vmoQTC8EHQ6GxV");
        assertValidAddress("ALEK7jttmqtx2ZhXHg69Zr426qKBnzYA9E");
        assertValidAddress("AP1egWUthPoYvZL57aBk4RPqUgjG1fJGn6");
        assertValidAddress("AST3zfvPdZ35npxAVC8ABgVCxxDLwTmAHU");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1AQJTNtWcP7opxuR52Lf5vmoQTC8EHQ6GxV");
        assertInvalidAddress("1ALEK7jttmqtx2ZhXHg69Zr426qKBnzYA9E");
        assertInvalidAddress("1AP1egWUthPoYvZL57aBk4RPqUgjG1fJGn6");
    }
}
