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

public class uPlexaTest extends AbstractAssetTest {

    public uPlexaTest() {
        super(new uPlexa());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("UPX1dz81hmfWc7AUhn16JATXJJgZeQZ4zLKA4tnHJHcdS5zoSaKQUoaGqDUQnTXecPL4mjJF1vkwRF3EEq5UJdSw8A84sXDjFP");
        assertValidAddress("UPi1S1uqRRNSgC26PjasZP8FwTBRwnAEmBnx5mAYsbGqRvsU46aficYEA3FAT621EuPeChyKQumS7j6jpF74zW9tLJMve8kUJLP5zUgR5ts8W");
        assertValidAddress("UmV7QTQs5Q47wMPggtuQSMTvuqNie1MRmbD4AG1xJXykZmxBG4P18p4CHqkV5sKDRXauXWbs76835PZoemQmPGJC1Dv2zdF43");
        assertValidAddress("UmWh1MthnAiRP4GuN3DEQxPt6kgeAZfJLUuX1krtufAj2XvUJxDYnuYTAQzEp25V2W8BAJQkfXj8yFNUqQphxddN35nRLnZeE");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("UPXLsinT9duNEtHGqAUicJKD2cmGiB9gB6sqHqWvV6suB4TtPSR8ynyh2vVVvNyDE6g7WEaBxCG8GD1KM2ffWPx7FLXgeJbNYrp");
        assertInvalidAddress("UPXsjCoYrxag2pPoDDTB4cRriKCNn8WjhY99kqjYuNdTfE4MU2Yo1CPdpyK7PXpxDcAd5YDNerE6WCc4cVQvEbxLaHk4UcvbRp2");
        assertInvalidAddress("UPXsinT9duNEtHGqAUicJKD2cmGiB9gB6sqHqWvV6suBx4TtPSR8ynyh2vVVvNyDE6g7W!!!xCG8GD1KM2ffWP7FLXgeJbNYrp2");
        assertInvalidAddress("UmVSrJ7ES1IIIIIGHFm69SU6dTTKt8Vi6V7BoC3wsLccd1Y2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTP");
        assertInvalidAddress("UmWrJ7ES1wGsikGHFm69SU6dTTKt8Vi6V7BoC3wsLcc1xY2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTPe");
        assertInvalidAddress("UPi12rJ7ES1wGsikGHFm69SU6dTTKt8Vi6V7BoC36sqHqWvwsLcc1Y2CXgQkW7vHSe5uArGU9TjUC5RtvzhCycVDnPPbThTmZA8VqDzTPeM1");
        assertInvalidAddress("UPisBB18NdcSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93vd6DEu3PcSywKDshsywbjc5uCi8ybSUtWgvM3LfzaYe93d96NjjvBCYU2SZD2of");
    }
}
