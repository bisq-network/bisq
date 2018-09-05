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

public class GraftTest extends AbstractAssetTest {

    public GraftTest() {
        super(new Graft());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("G9Q2cPrRrYecQhsFqopQbiKqpJyzeDtxVWZb8Mytj65k538jxn8JTJ3aNvE7eQsBpYMKPN4pQBLFKALh6cZqL52RSgZWFmD");
        assertValidAddress("G5t9rcTchMDaEn4KyVLEo2adPbWjnRXCgBqVQpujcFU8PvdytNnZAVJHVHW2VEu2ELJEmgMqk1aznXA7i1vrVuqW6m7mg9Z");
        assertValidAddress("G6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuujFRoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1F");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("GWQ2cPrRrYecQhsFqopQbiKqpJyzeDtxVWZb8Mytj65k538jxn8JTJ3aNvE7eQsBpYMKPN4pQBLFKALh6cZqL52RSgZWFmD");
        assertInvalidAddress("Z6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuujFRoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1F");
        assertInvalidAddress("");
        assertInvalidAddress("G6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuuj#RoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1F");
        assertInvalidAddress("G9Q2cPrRrYecQhsFqopQbiKqpJyzeDtxVWZb8Mytj65k538jxn8JTJ3aNvE7eQsBpYMKPN4pQBLFKALh6cZqL52RSgZWFmDdddddddddddd");
        assertInvalidAddress("G6NHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuujFRoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1222222222");
        assertInvalidAddress("G5t9rcTchMDaEn4KyVLEo2adPbWjnRXCgBqVQpujcFU8PvdytNnZAVJHVHW2VEu2ELJEmgMqk1aznXA7i1vr");
        assertInvalidAddress("GDARp92UtmTWDjZatG8sduGTMu89ZasZjEyAwapnANNWCE2hJ4edcszj9hcZHdXr9vJzjHq2TfPrjateDz9Wc8ZJKuDayqJ$%");
        assertInvalidAddress("F3xQ8Gv6w1XE2SQcXBZEmpS5112RAPup7XRr7WkLG8aL98ZBsTAF5q4GdGrWjJTGz4676ymKvU4NzPY8Ca4gfJwL2yhhkJ7");
    }
}
