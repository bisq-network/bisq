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

public class MoXTest extends AbstractAssetTest {

    public MoXTest() {
        super(new MoX());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XwoHEJVYYZEBXB99yPP1AWNYYTDLPGHZ11jTia4RWRpwbohuChbpPngF42RCoaKaJciCmhwdKWsBBQPt8Ci5dr9p3BejTRxXV");
        assertValidAddress("XwoG8c8N8VZQy9usuHj88DK5DsezY5YrkZoSCEKg8sFfhKLhFV2NwVMPFNogZkjpPw1RiV16JQ1Mg6ygYpntKADJ2kSRv21Lc");
        assertValidAddress("XwoABgJx6dt96eihXdGwj31AKqsN7dTbb1vMshfmj87YRYxmieBh8zHY26AYnwDE9Ce4Mg4eB4huEHYM26bEWrN72xa6zBf17");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("XwoHEJVYYZEBXB99yPP1AWNYYTDLPGHZ11jTia4RWRpwbohuChbpPngF42RCoaKaJciCmhwdKWsBBQPt8Ci5dr9p3BejTRxX");
        assertInvalidAddress("XwoHEJVYYZEBXB99yPP1AWNYYTDLPGHZ11jTia4RWRpwbohuChbpPngF42RCoaKaJciCmhwdKWsBBQPt8Ci5dr9p3BejTRxXVV");
        assertInvalidAddress("woHEJVYYZEBXB99yPP1AWNYYTDLPGHZ11jTia4RWRpwbohuChbpPngF42RCoaKaJciCmhwdKWsBBQPt8Ci5dr9p3BejTRxXVV");
        assertInvalidAddress("Xizx2PdSDC6B4xwcxr6ZsHAiShnj7XcXSEmf4GQRTmpDFum1MyohsekDvRQpN4eQwyZyCw4Hs2UKyJSygXwA2QhyGcS5NRVsYrM9t2SCPsxzT");
        assertInvalidAddress("");
        assertInvalidAddress("XwoHEJVYYZEBXB99yPP1AWNYYTDLPGHZ11jTia4RWRpwbohuChbpPngF42RCoaKaJciCmhwdKWsBBQPt8Ci5dr9p3BejTRxXV#aFejf");
        assertInvalidAddress("1jRo3rcp9fjdfjdSGpx");
        assertInvalidAddress("GDARp92UtmTWDjZatG8sdurzouheiuRRRTbbRtbr3atrHSXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("F3xQ8Gv6xnvDhUrM57z71bfFvu9HeofXtXpZRLnrCN2s2cKvkQowrWjJTGz4676ymKvU4NzPY8Cadgsdhsdfhg4gfJwL2yhhkJ7");
    }
}
