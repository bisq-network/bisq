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

package io.bitsquare.trade.protocol.availability.tasks;

import io.bitsquare.network.Peer;
import io.bitsquare.trade.listeners.GetPeerAddressListener;
import io.bitsquare.trade.protocol.availability.CheckOfferAvailabilityModel;
import io.bitsquare.util.tasks.Task;
import io.bitsquare.util.tasks.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPeerAddress extends Task<CheckOfferAvailabilityModel> {
    private static final Logger log = LoggerFactory.getLogger(GetPeerAddress.class);

    public GetPeerAddress(TaskRunner taskHandler, CheckOfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        model.getTradeMessageService().getPeerAddress(model.getOffer().getMessagePublicKey(), new GetPeerAddressListener() {
            @Override
            public void onResult(Peer peer) {
                log.trace("Found peer: " + peer.toString());
                
                model.setPeer(peer);
                
                complete();
            }

            @Override
            public void onFailed() {
                failed("DHT lookup for peer address failed.");
            }
        });
    }
}

