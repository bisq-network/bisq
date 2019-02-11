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

public class HatchTest extends AbstractAssetTest {

    public HatchTest() {
        super(new Hatch());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("XgUfhrcfKWTVprA1GGhTggAA3VVQy1xqNp");
        assertValidAddress("Xo88XjP8RD2w3k7Fd16UT62y3oNcjbv4bz");
        assertValidAddress("XrA7ZGDLQkiLwUsfKT6y6tLrYjsvRLrZQG");

    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1XrA7ZGDLQkiLwUsfKT6y6tLrYjsvRLrZQG");
        assertInvalidAddress("XrA7ZGDLQkiLwUsfKT6y6tLrYjsvRLrZQGd");
        assertInvalidAddress("XrA7ZGDLQkiLwUsfKT6y6tLrYjsvRLrZQG#");
    }
}
