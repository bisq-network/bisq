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

public class ProtoBufferUtilitiesTest {

//TODO CoreProtobufferResolver is not accessible here
// We should refactor it so that the classes themselves know how to deserialize 
// so we don't get dependencies from core objects here

  /*  @Test
    public void testEnum() {
        PB.OfferPayload.Direction direction = PB.OfferPayload.Direction.SELL;
        PB.OfferPayload.Direction direction2 = PB.OfferPayload.Direction.BUY;
        OfferPayload.Direction realDirection = getDirection(direction);
        OfferPayload.Direction realDirection2 = getDirection(direction2);
        assertEquals("SELL", realDirection.name());
        assertEquals("BUY", realDirection2.name());
    }
    public static OfferPayload.Direction getDirection(PB.OfferPayload.Direction direction) {
        return OfferPayload.Direction.valueOf(direction.name());
    }*/
}