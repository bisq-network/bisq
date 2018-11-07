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

package bisq.asset.tokens;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class TariTest extends AbstractAssetTest {

    public TariTest() {
        super(new Tari());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("GCQQRNVHDV5JGDLK7XNVF5HAULPQIPOAAEXOT3Y6LXU6GGAGMN2QAE35");
        assertValidAddress("GBCDXRHIHVEUXXA6ZWCLXRSR3M6SJKHZOSOWMQIFM3ONXNJY2F3QY34K");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("/../evil/GBCDXRHIHVEUXXA6ZWCLXRSR3M6SJKHZOSOWMQIFM3ONXDK");
        assertInvalidAddress("GCQQRNVHDV5JGDLK7XNVF5HAULPQIPOAAEXOT3Y6LXU6GGAGMN2QAE3");
        assertInvalidAddress("GCQQRNVHDV5JGDLK7XNVF5HAULPQIPOAAEXOT3Y6LXU6GGAGMN2QAE333");
    }
}
