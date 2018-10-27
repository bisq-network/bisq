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

public class MonetaryUnitTest extends AbstractAssetTest {

    public MonetaryUnitTest() {
        super(new MonetaryUnit());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("7VjG4Vjnu488k14QdpxswKk1acVgySqV6c");
        assertValidAddress("7U42XyYEf7CsLsaq84mRxMaMfst7f3r4By");
        assertValidAddress("7hbLQSY9SnyHf1RwiTniMt8vT94x7pqJEr");
        assertValidAddress("7oM4HbCStpDQ8imocHPVjNWGVj9gg54erh");
        assertValidAddress("7SUheC6Xp12G9CCgoMJ2dT8e9zwnFRwjrU");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("0U42XyYEf7CsLsaq84mRxMaMfst7f3r4By");
        assertInvalidAddress("#7VjG4Vjnu488k14QdpxswKk1acVgySqV6c");
        assertInvalidAddress("7SUheC6Xp12G9CCgoMJ2dT8e9zwnFRwjr");
        assertInvalidAddress("7AUheX6X");
    }
}
