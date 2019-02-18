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

package bisq.core.trade;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;

import bisq.common.storage.Storage;

import io.bisq.generated.protobuffer.PB;

import mockit.Mocked;

import org.junit.Test;

import static io.bisq.generated.protobuffer.PB.PersistableEnvelope.MessageCase.TRADABLE_LIST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

//TODO cannot be run in IntelliJ IDE as parameter is not supported. OfferPayload is final so it is not so trivial to
// replace that.
public class TradableListTest {

    @Test
    public void protoTesting(@Mocked OfferPayload offerPayload) {
        Storage<TradableList<OpenOffer>> storage = new Storage<>(null, null);
        TradableList<OpenOffer> openOfferTradableList = new TradableList<>(storage, "filename");
        PB.PersistableEnvelope message = (PB.PersistableEnvelope) openOfferTradableList.toProtoMessage();
        assertTrue(message.getMessageCase().equals(TRADABLE_LIST));

        // test adding an OpenOffer and convert toProto
        Offer offer = new Offer(offerPayload);
        OpenOffer openOffer = new OpenOffer(offer, storage);
        //openOfferTradableList = new TradableList<OpenOffer>(storage,Lists.newArrayList(openOffer));
        openOfferTradableList.add(openOffer);
        message = (PB.PersistableEnvelope) openOfferTradableList.toProtoMessage();
        assertTrue(message.getMessageCase().equals(TRADABLE_LIST));
        assertEquals(1, message.getTradableList().getTradableList().size());
    }
}
