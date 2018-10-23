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

 public class BlurTest extends AbstractAssetTest {

     public BlurTest() {
        super(new Blur());
    }

     @Test
    public void testValidAddresses() {
        assertValidAddress("bL3W1g1d12sbxQDTQ6q8bgU2bBp2rkfFFKfNvQuUQTHqgQHRaxKTHqK5Nqdm53BU3ibPnsqbdYAnnJMyqJ6FfN9m3CSZSNqDE");
        assertValidAddress("bL2zBGUBDkQdyYasdoAdvQCxWLa9Mk5Q1PW8Zk7S38vx9xu7T7NMPPWNfieXqUyswo544ZSB3C1n9jLMfsUvR6p91rnrSdx9h");
        assertValidAddress("Ry49oErHtqyHucxADDT2DfEJ9pRv2ciSpKV9XseCuWmx1PK1CZi4gbPKxhWBdtvLJNNc94c4yDutmZrD3WrsHPYV1nvE9X4Cc");
    }

     @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("bl4E2BCFY31DPLjeqF6Gu7TEUM5v2JwpmudFX64AubQtFDYEPBvgvQPzidaawDhjAmHeZSw92wEBnUfdfY5144Sad2ZCknZzC");
        assertInvalidAddress("Ry49oErHtqyHucxADDT2DfEJ9pRv2ciSpKV9XseCuWmx1PK1CZi4gbPKxhWBdtvLJNNc94c4yDutmZrD3WrsHPYV1nvE9X40");
        assertInvalidAddress("bLNHRh8pFh5Y14bhBVAoD4cvqHyoPsQJqB3dr49zoF6bNDFrts96tuuj#RoUKWRwpTHmYt4Kf78FES7LCXAXKXFf6bMsx1sdgz");
        assertInvalidAddress("82zBGUBDkQdyYasdoAdvQCxWLa9Mk5Q1PW#8Zk7S38vx9xu7T7NMPPWNfieXqUyswo544ZSB3C1n9jLMfsUvR6p91rnrSdxwd");
    }
}
