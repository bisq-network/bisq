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

 public class AeonTest extends AbstractAssetTest {

     public AeonTest() {
        super(new Aeon());
    }

     @Test
    public void testValidAddresses() {
        assertValidAddress("WmsSXcudnpRFjXr5qZzEY5AF64J6CpFKHYXJS92rF9WjHVjQvJxrmSGNQnSfwwJtGGeUMKvLYn5nz2yL9f6M4FN51Z5r8zt4C");
        assertValidAddress("XnY88EywrSDKiQkeeoq261dShCcz1vEDwgk3Wxz77AWf9JBBtDRMTD9Fe3BMFAVyMPY1sP44ovKKpi4UrAR26o661aAcATQ1k");
        assertValidAddress("Wmu42kYBnVJgDhBUPEtK5dicGPEtQLDUVWTHW74GYvTv1Zrki2DWqJuWKcWV4GVcqnEMgb1ZiufinCi7WXaGAmiM2Bugn9yTx");
    }

     @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("WmsSXcudnpRFjXr5qZzEY5AF64J6CpFKHYXJS92rF9WjHVjQvJxrmSGNQnSfwwJtGGeUMKvLYn5nz2yL9f6M4FN51Z5r8zt4");
        assertInvalidAddress("XnY88EywrSDKiQkeeoq261dShCcz1vEDwgk3Wxz77AWf9JBBtDRMTD9Fe3BMFAVyMPY1sP44ovKKpi4UrAR26o661aAcATQ1kZz");
        assertInvalidAddress("XaY88EywrSDKiQkeeoq261dShCcz1vEDwgk3Wxz77AWf9JBBtDRMTD9Fe3BMFAVyMPY1sP44ovKKpi4UrAR26o661aAcATQ1k");
        assertInvalidAddress("Wmu42kYBnVJgDhBUPEtK5dicGPEtQLDUVWTHW74GYv#vZrki2DWqJuWKcWV4GVcqnEMgb1ZiufinCi7WXaGAmiM2Bugn9yTx");
    }
}
