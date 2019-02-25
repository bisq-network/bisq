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

public class AdeptioTest extends AbstractAssetTest {

    public AdeptioTest() {
        super(new Adeptio());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("AP7rSyQMZRek9HGy9QB1bpung69xViesN7");
        assertValidAddress("AWVXtnMo4pS2vBSNrBPLVvMgYvJGD6gSXk");
        assertValidAddress("AHq8sM8DEeFoZXeDkaimfCLtnMuuSWXFE7");
        assertValidAddress("ANG52tPNJuVknLQiLUdzVFoZ3vyo8UzkDL");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("aP7rSyQMZRek9HGy9QB1bpung69xViesN7");
        assertInvalidAddress("DAeiBSH4nudXgoxS4kY6uhTPobc7AlrWDA");
        assertInvalidAddress("BGhVYBXk511m8TPvQA6YokzxdpdhRE3sG6");
        assertInvalidAddress("AZt2Kuy9cWFbTc888HNphppkuCTNyqu5PY");
        assertInvalidAddress("AbosH98t3TRKzyNb8pPQV9boupVcBAX6of");
        assertInvalidAddress("DTPAqTryNRCE2FgsxzohTtJXfCBIDnG6Rc");
    }
}
