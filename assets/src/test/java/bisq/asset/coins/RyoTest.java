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

public class RyoTest extends AbstractAssetTest {

    public RyoTest() {
        super(new Ryo());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("RYoLsinT9duNEtHGqAUicJKD2cmGiB9gB6sqHqWvV6suB4TtPSR8ynyh2vVVvNyDE6g7WEaBxCG8GD1KM2ffWP7FLXgeJbNYrp2");
        assertValidAddress("RYoSrJ7ES1wGsikGHFm69SU6dTTKt8Vi6V7BoC3wsLcc1Y2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTPeM");
        assertValidAddress("RYoKst8YBCucSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d4qqpsJ");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("RYoLsinT9duNEtHGqAUicJKD2cmGiB9gB6sqHqWvV6suB4TtPSR8ynyh2vVVvNyDE6g7WEaBxCG8GD1KM2ffWP7FLXgeJbNYrp");
        assertInvalidAddress("RYoLsjCoYrxag2pPoDDTB4cRriKCNn8WjhY99kqjYuNTfE4MU2Yo1CPdpyK7PXpxDcAd5YDNerE6WCc4cVQvEbxLaHk4UcvbRp23");
        assertInvalidAddress("RYoLsinT9duNEtHGqAUicJKD2cmGiB9gB6sqHqWvV6suB4TtPSR8ynyh2vVVvNyDE6g7W!!!xCG8GD1KM2ffWP7FLXgeJbNYrp2");
        assertInvalidAddress("RYoSrJ7ES1IIIIIGHFm69SU6dTTKt8Vi6V7BoC3wsLcc1Y2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTPeM");
        assertInvalidAddress("RYoSrJ7ES1wGsikGHFm69SU6dTTKt8Vi6V7BoC3wsLcc1Y2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTPe");
        assertInvalidAddress("RYoSrJ7ES1wGsikGHFm69SU6dTTKt8Vi6V7BoC3wsLcc1Y2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTPeM1");
        assertInvalidAddress("RYoNsBB18NdcSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d6DEu3PcSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d96NjjvBCYU2SZD2of");
        assertInvalidAddress("RYoKst8YBCucSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d4qqpsJC");
        assertInvalidAddress("RYoKst8YBCucSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d4qqps");
        assertInvalidAddress("RYost8YBCucSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d4qqpsJ");
    }
}
