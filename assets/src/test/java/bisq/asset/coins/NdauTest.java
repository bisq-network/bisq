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

/*
 * Copyright © 2019 Oneiro NA, Inc.
 */

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class NdauTest extends AbstractAssetTest {
    public NdauTest() {super(new Ndau());}

    @Test
    public void testValidAddresses() {
        assertValidAddress("ndaaacj4gbv5xgwikt6adcujqyvd37ksadj4mg9v3jqtbe9f");
        assertValidAddress("ndnbeju3vmcxf9n96rb652eaeri79anqz47budnw8vwv3nyv");
        assertValidAddress("ndeatpdkx5stu28n3v6pie96bma5k8pzbvbdpu8dchyn46nw");
        assertValidAddress("ndxix97gyubjrkqbu4a5m3kpxyz4qhap3c3ui7359pzskwv4");
        assertValidAddress("ndbjhkkcvj88beqcamr439z6d6icm5mjwth5r7vrgfbnxktr");
        assertValidAddress("ndmpdkab97bi4ea73scjh6xpt8njjjhha4rarpr2zzzrv88u");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("ndaaacj4gbv5xgwikt6adcujqyvd37ksadj4mg9v3jqtbe9");
        assertInvalidAddress("ndnbeju3vmcxf9n96rb652eaeri79anqz47budnw8vwv3nyvw");
        assertInvalidAddress("ndpatpdkx5stu28n3v6pie96bma5k8pzbvbdpu8dchyn46nw");
        assertInvalidAddress("ndx1x97gyubjrkqbu4a5m3kpxyz4qhap3c3ui7359pzskwv4");
        assertInvalidAddress("ndbjhklcvj88beqcamr439z6d6icm5mjwth5r7vrgfbnxktr");
        assertInvalidAddress("ndmpdkab97bi4ea73scjh6xpt8njjjhhaArarpr2zzzrv88u");
    }
}
