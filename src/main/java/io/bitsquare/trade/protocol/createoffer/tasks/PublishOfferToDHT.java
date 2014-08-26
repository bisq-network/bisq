/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.createoffer.tasks;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.ResultHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublishOfferToDHT {
    private static final Logger log = LoggerFactory.getLogger(PublishOfferToDHT.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, MessageFacade messageFacade,
                           Offer offer) {
        messageFacade.addOffer(offer, new MessageFacade.AddOfferListener() {
            @Override
            public void onComplete() {
                resultHandler.onResult();
            }

            @Override
            public void onFailed(String reason, Throwable throwable) {
                faultHandler.onFault("Publish offer to DHT failed.", throwable);
            }
        });
    }
}
