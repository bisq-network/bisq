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

 public class ArqmaTest extends AbstractAssetTest {

     public ArqmaTest() {
        super(new Arqma());
    }

     @Test
    public void testValidAddresses() {
        assertValidAddress("ar3ZLUTSac5DhxhyLJB11gcXWLYPKJchg7c8hoaKmqchC9TtHEdXzxGgt2vzCLUYwtSvkJQTXNCjzCR7KZiFUySV138PEopVC");
        assertValidAddress("aRS3V2hXuVAGAb5XWcDvN7McsSyqrEZ3XWyfMdEDCqioWNmVUuoKyNxDo7rwPCg55Ugb6KHXLN7hLZEGcnZzbm8M7uJ9YdVpeN");
        assertValidAddress("ar3mXR6SQeC3P9Dmq2LGsAeq5eDvjiNnYaywtqdNzixe6xLr38DiNVaaRKMkAQkR3NV3TuVAwAwEGH3QDgXJF3th1RwxABa9a");
    }

     @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("ar3ZLUTSac5DhxhyLJB11gcXWLYPKJchg7c8hoaKmqchC9TtHEdXzxGgt2vzCLUYwtSvkJQTXNCjzCR7KZiFUySV138PEopV");
        assertInvalidAddress("aRS3V2hXuVAGAb5XWcDvN7McsSyqrEZ3XWyfMdEDCqioWNmVUuoKyNxDo7rwPCg55Ugb6KHXLN7hLZEGcnZzbm8M7uJ9YdVpeNZz");
        assertInvalidAddress("aRV3V2hXuVAGAb5XWcDvN7McsSyqrEZ3XWyfMdEDCqioWNmVUuoKyNxDo7rwPCg55Ugb6KHXLN7hLZEGcnZzbm8M7uJ9YdVpeN");
        assertInvalidAddress("ar3mXR6SQeC3P9Dmq2LGsAeq5eDvjiNnYaywtqdNzi#exLr38DiNVaaRKMkAQkR3NV3TuVAwAwEGH3QDgXJF3th1RwxABa9a");
    }
}
