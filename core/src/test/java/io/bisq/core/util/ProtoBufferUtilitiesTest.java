/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.util;

import io.bisq.core.offer.AvailabilityResult;
import io.bisq.core.offer.OpenOffer;
import io.bisq.generated.protobuffer.PB;
import io.bisq.generated.protobuffer.PB.OfferPayload;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ProtoBufferUtilitiesTest {

//TODO CoreProtobufferResolver is not accessible here
// We should refactor it so that the classes themselves know how to deserialize 
// so we don't get dependencies from core objects here

    @Test
    public void testEnum() {
        OfferPayload.Direction direction = OfferPayload.Direction.SELL;
        OfferPayload.Direction direction2 = OfferPayload.Direction.BUY;
        OfferPayload.Direction realDirection = getDirection(direction);
        OfferPayload.Direction realDirection2 = getDirection(direction2);
        assertEquals("SELL", realDirection.name());
        assertEquals("BUY", realDirection2.name());
    }

    @Test
    public void testUnknownEnum() {
        PB.OpenOffer.State result = PB.OpenOffer.State.UNKNOWN_FAILURE;
        try {
            OpenOffer.State finalResult = OpenOffer.State.valueOf(result.name());
            fail();
        } catch(IllegalArgumentException e) {
        }
    }

    public static OfferPayload.Direction getDirection(OfferPayload.Direction direction) {
        return OfferPayload.Direction.valueOf(direction.name());
    }
}