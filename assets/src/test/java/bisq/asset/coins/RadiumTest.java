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

import org.junit.Test;



import bisq.asset.AbstractAssetTest;

public class RadiumTest extends AbstractAssetTest {

    public RadiumTest() {
        super(new Radium());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XfrvQw3Uv4oGgc535TYyBCT2uNU7ofHGDU");
        assertValidAddress("Xwgof4wf1t8TnQUJ2UokZRVwHxRt4t6Feb");
        assertValidAddress("Xep8KxEZUsCxQuvCfPdt2VHuHbp43nX7Pm");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW");
        assertInvalidAddress("1K5B7SDcuZvd2oUTaW9d62gwqsZkteXqA4");
        assertInvalidAddress("1GckU1XSCknLBcTGnayBVRjNsDjxqopNav");
    }
}
