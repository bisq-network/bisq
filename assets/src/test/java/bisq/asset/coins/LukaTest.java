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

public class LukaTest extends AbstractAssetTest {

    public LukaTest() {
        super(new Luka());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("LMRhx6YYESyK4PT3Qg1fTNZkh37XcwuPyJAcQU7krqEzWXSFT3kFDfpVAPvtFP6gEtDZF6Jp32vrt5ZqtsQVRu8G5r4bbdW");
        assertValidAddress("LJwRsB2QggSH5V1apxMr4qiCAxXBMd9szS23bgxXLP5TEyeanoCEyDn4XLq99M98ZQaBqJ89LQPwiVsTcn2QKexCETWvw9o");
        assertValidAddress("LK3Rwd2ffUxAUHj6bW8zkECLmn1SXawJ4C3oNzJJtcBh5AWJ5BfVwBs3NrzUxway5tNkcFBF333tR47eQLJXNQ3ECm6XbJV");        
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("LaaMRhx6YYESyK4PT3Qg1fTNZkh37XcwuPyJAcQU7krqEzWXSFT3kFDfpVAPvtFP6gEtDZF6Jp32vrt5ZqtsQVRu8G5r4bbdW");
        assertInvalidAddress("L1JwRsB2QggSH5V1apxMr4qiCAxXBMd9szS23bgxXLP5TEyeanoCEyDn4XLq99M98ZQaBqJ89LQPwiVsTcn2QKexCETWvw9o");
        assertInvalidAddress("LK3Rwd2ffUxAUHj6bW8zkECLmn1SXawJ4C3oNzJJtcBh5AWJ5BfVwBs3NrzUxway5tNkcFBF333tR47eQLJXNQ3ECm6XbJV!");
        assertInvalidAddress("xK3Rwd2ffUxAUHj6bW8zkECLmn1SXawJ4C3oNzJJtcBh5AWJ5BfVwBs3NrzUxway5tNkcFBF333tR47eQLJXNQ3ECm6XbJV");
    }
}
