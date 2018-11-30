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

public class IdaPayTest extends AbstractAssetTest {

    public IdaPayTest() {
        super(new IdaPay());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Cj6A8JJvovgSTiMc4r6PaJPrfwQnwnHDpg");
        assertValidAddress("D4SEkXMAcxRBu2Gc1KpgcGunAu5rWttjRy");
        assertValidAddress("CopBThXxkziyQEG6WxEfx36Ty46DygzHTW");
        assertValidAddress("D3bEgYWDS7fxfu9y1zTSrcdP681w3MKw6W");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("Cj6A8JJv0vgSTiMc4r6PaJPrfwQnwnHDpg");
        assertInvalidAddress("D4SEkXMAcxxRBu2Gc1KpgcGunAu5rWttjRy");
        assertInvalidAddress("CopBThXxkziyQEG6WxEfx36Ty4#DygzHTW");
        assertInvalidAddress("13bEgYWDS7fxfu9y1zTSrcdP681w3MKw6W");
    }
}
