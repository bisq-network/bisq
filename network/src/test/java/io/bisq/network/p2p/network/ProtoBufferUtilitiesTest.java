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

package io.bisq.network.p2p.network;

import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.ProtoBufferUtilities;
import io.bisq.protobuffer.payload.offer.OfferPayload;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProtoBufferUtilitiesTest {

    @Test
    public void testEnum() {
        PB.OfferPayload.Direction direction = PB.OfferPayload.Direction.SELL;
        PB.OfferPayload.Direction direction2 = PB.OfferPayload.Direction.BUY;
        OfferPayload.Direction realDirection = ProtoBufferUtilities.getDirection(direction);
        OfferPayload.Direction realDirection2 = ProtoBufferUtilities.getDirection(direction2);
        assertEquals("SELL", realDirection.name());
        assertEquals("BUY", realDirection2.name());
    }
}