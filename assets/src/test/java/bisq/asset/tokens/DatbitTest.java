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

 public class DatbitTest extends AbstractAssetTest {

     public DatbitTest() {
         super(new Datbit());
     }

     @Test
     public void testValidAddresses() {
         assertValidAddress("0xA6849FA24ccbD7A5E87a68095257e5f26361a448");
         assertValidAddress("A6849FA24ccbD7A5E87a68095257e5f26361a448");
     }

     @Test
     public void testInvalidAddresses() {
         assertInvalidAddress("0xA6849FA24ccbD7A5E87a68095257e5f26361a4488");
         assertInvalidAddress("0xA6849FA24ccbD7A5E87a68095257e5f26361a44g");
         assertInvalidAddress("A6849FA24ccbD7A5E87a68095257e5f26361a44g");
     }
 }
