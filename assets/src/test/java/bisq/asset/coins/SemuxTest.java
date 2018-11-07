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

public class SemuxTest extends AbstractAssetTest {

    public SemuxTest() {
        super(new Semux());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0x541365fe0818ea0d2d7ab7f7bc79f719f5f72227");
        assertValidAddress("0x1504263ee17446ea5f8b288e1c35d05749c0e47d");
        assertValidAddress("0xe30c510f3efc6e2bf98ff8f725548e6ece568f89");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("541365fe0818ea0d2d7ab7f7bc79f719f5f72227");
        assertInvalidAddress("0x541365fe0818ea0d2d7ab7f7bc79f719f5f7222");
        assertInvalidAddress("0x541365fe0818ea0d2d7ab7f7bc79f719f5f72227abc");
    }
}
