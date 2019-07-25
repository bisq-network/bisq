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

package bisq.core.util;

import bisq.core.offer.OpenOffer;

import bisq.common.proto.ProtoUtil;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;



import protobuf.OfferPayload;

@SuppressWarnings("UnusedAssignment")
public class ProtoUtilTest {

    //TODO Use NetworkProtoResolver, PersistenceProtoResolver or ProtoResolver which are all in bisq.common.
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
        protobuf.OpenOffer.State result = protobuf.OpenOffer.State.PB_ERROR;
        try {
            OpenOffer.State finalResult = OpenOffer.State.valueOf(result.name());
            fail();
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Test
    public void testUnknownEnumFix() {
        protobuf.OpenOffer.State result = protobuf.OpenOffer.State.PB_ERROR;
        try {
            OpenOffer.State finalResult = ProtoUtil.enumFromProto(OpenOffer.State.class, result.name());
            assertEquals(OpenOffer.State.AVAILABLE, ProtoUtil.enumFromProto(OpenOffer.State.class, "AVAILABLE"));
        } catch (IllegalArgumentException e) {
            fail();
        }
    }

    public static OfferPayload.Direction getDirection(OfferPayload.Direction direction) {
        return OfferPayload.Direction.valueOf(direction.name());
    }
}
