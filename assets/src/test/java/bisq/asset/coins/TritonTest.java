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

public class TritonTest extends AbstractAssetTest {

    public TritonTest() {
        super(new Triton());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("Tvys67tHC2iUGoVWcKdT6bBxg8TRV826Tjf1NWosSDk4P6vDQ95WmTcXjCYGNsf9vZ9vbUeBeHVA5Qsv2yGP4KwV2jNcbzMqu");
        assertValidAddress("Tw1oBSkZRpH4PyiUk6NJ9yTwThBk39ybZ9vfVvEK8WkT9wyZFC64cNM6bFDcwPSc2s5fgPpYE1CMuA9VTdmqYY5u16vaAVASx");
        assertValidAddress("Tw1ZX9YXQD2Qm569E9nhTiho5NazHFY54GyQihpDYdWbguKibTpcuh6PHyuu5QhJoiQXGwvkfuY7wRWBq7PgiQuS11CbNVJrD");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("TWQ2cPrRrYecQhsFqopQbiKqpJyzeDtxVWZb8Mytj65k538jxn8JTJ3aNvE7eQsBpYMKPN4pQBLFKALh6cZqL52RSgZWFmD");
        assertInvalidAddress("Z6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuujFRoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1F");
        assertInvalidAddress("");
        assertInvalidAddress("T6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuuj#RoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1F");
        assertInvalidAddress("T9Q2cPrRrYecQhsFqopQbiKqpJyzeDtxVWZb8Mytj65k538jxn8JTJ3aNvE7eQsBpYMKPN4pQBLFKALh6cZqL52RSgZWFmDdddddddddddd");
        assertInvalidAddress("T6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuujFRoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1222222222");
        assertInvalidAddress("T5t9rcTchMDaEn4KyVLEo2adPbWjnRXCgBqVQpujcFU8PvdytNnZAVJHVHW2VEu2ELJEmgMqk1aznXA7i1vr");
        assertInvalidAddress("JDARp92UtmTWDjZatG8sduGTMu89ZasZjEyAwapnANNWCE2hJ4edcszj9hcZHdXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("T3xQ8Gv6w1XE2SQcXBZEmpS5112RAPup7XRr7WkLG8aL98ZBsTAF5q4GdGrWjJTGz4676ymKvU4NzPY8Ca4gfJwL2yhhkJ7");
    }
}
