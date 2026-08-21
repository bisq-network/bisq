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

import org.junit.jupiter.api.Test;

public class WAMCoinTest extends AbstractAssetTest {

    public WAMCoinTest() {
        super(new WAMCoin());
    }

    // Every address below was produced or judged by wamd itself on WAM
    // mainnet, not written by hand: the valid ones come from getnewaddress,
    // and validateaddress rejects each of the invalid ones.

    @Test
    public void testValidAddresses() {
        // P2PKH, version byte 73
        assertValidAddress("Wif4JEpEgvgZnneibRBDodYUe1We3ft1j4");
        assertValidAddress("WWWEvpC98mfzjRMZHtaRaucMjopqH2viQz");
        assertValidAddress("WNg2svm2qApxheBKndKGQ9sRwporvRgRpT");
        // P2SH, version byte 135
        assertValidAddress("wM6b6wkri2n9Ni2KyNC6ftHjfBf6iXoP1n");
        // P2WPKH, hrp "wam"
        assertValidAddress("wam1q85enqu8zptx20fzrdr07p5vt5ffx9udnvgv366");
        assertValidAddress("wam1q4vgyrjdjk742nk5cdztp9fj72g2hspjzm6gf0f");
        assertValidAddress("wam1qv8amdskgpwg5whapwgdaafe5t7xhs7q5aqkhvq");
    }

    @Test
    public void testInvalidAddresses() {
        // Bitcoin, whose version byte is not ours
        assertInvalidAddress("1LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("bc1qxtm55gultqzhqzl2p3ks50hg2478y3hehuj6dz");
        // WAM testnet, which must not be accepted as mainnet
        assertInvalidAddress("twam1q8fwe4ucywdzf2p80zqhpucwj8dy9kgk4x4s40m");
        // A valid address with its last character changed: the checksum fails
        assertInvalidAddress("WWWEvpC98mfzjRMZHtaRaucMjopqH2viQx");
        // Right prefix, one character short
        assertInvalidAddress("Wif4JEpEgvgZnneibRBDodYUe1We3ft1j");
        assertInvalidAddress("Wif4JEpEgvgZnneibRBDodYUe1We3ft1j4#");
        // The blank case is left to AbstractAssetTest, which already asserts it
        // for every asset.
    }
}
