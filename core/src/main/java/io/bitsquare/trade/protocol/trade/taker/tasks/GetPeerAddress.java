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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.listeners.GetPeerAddressListener;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPeerAddress extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(GetPeerAddress.class);

    public GetPeerAddress(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
        errorMessage = "DHT lookup for peer address failed. Maybe the offerer was offline for too long time.";
    }

    @Override
    protected void doRun() {
        model.getTradeMessageService().getPeerAddress(model.getOffer().getMessagePublicKey(), new GetPeerAddressListener() {
            @Override
            public void onResult(Peer peer) {
                log.trace("Found peer: " + peer.toString());

                model.setOfferer(peer);
                complete();
            }

            @Override
            public void onFailed() {
                model.getOffer().setState(Offer.State.OFFERER_OFFLINE);
                failed();
            }
        });
    }

    @Override
    protected void updateStateOnFault() {
        if (model.getOffer().getState() != Offer.State.OFFERER_OFFLINE)
            model.getOffer().setState(Offer.State.AVAILABILITY_CHECK_FAILED);
    }
}

