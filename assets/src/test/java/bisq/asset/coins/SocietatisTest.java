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

public class SocietatisTest extends AbstractAssetTest {

    public SocietatisTest() {
        super(new Societatis());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("SCTSEqvrYDcP9Ach4LDn1u1mbP8LZBH2qbPpDiFBvhwwcKqKCZJTYUbMJMdv4k98KyNri8ttLMhdYaAnkovPUJxc2JNxjc2nRy");
        assertValidAddress("SCTSdysFWQ8DSaTAjJKEddccdyVDvE6MECKThhEzyRnHTcvg7cadomt1wqQGbxFiHVZpMubFMMjdkHPeVRDCz3fz82TGuUbk9E");
        assertValidAddress("SCTSdBURdFgMh4UvnudamtdRhgCLayrAG6niHk9vULsNFKWsX9FTnHjXMFiUZ9qjmsLAeDwS4Pjd6FSLddsWy8s76MY42MAJ4r");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("KSTSdBURdFgMh4UvnudamtdRhgCLayrAG6niHk9vULsNFKWsX9FTnHjXMFiUZ9qjmsLAeDwS4Pjd6FSLddsWy8s76MY42MAJ4r");
        assertInvalidAddress("KLKhQSLuBvevvTzM5Y7BpA5YSyvtyAtq18SY45waqo1L1bPvktbpscXx227jvTaNo8Y4Leyv7hGcc54RArVQfYB4MV4srzV4F");
        assertInvalidAddress("SCTSdysFWQ8DSaTAjJKEddccdyVDvE6MECKThhEzyRnHTcvg7cadomt1wqQGbxFiHVZpMubFMMjdkHPeVRDCz3fz82TGuU1");
    }
}
