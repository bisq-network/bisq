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

package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.GetPeerAddressListener;
import io.bitsquare.trade.handlers.ExceptionHandler;

import java.security.PublicKey;

import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPeerAddress {
    private static final Logger log = LoggerFactory.getLogger(GetPeerAddress.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler,
                           MessageFacade messageFacade, PublicKey messagePublicKey) {
        log.trace("Run task");
        messageFacade.getPeerAddress(messagePublicKey, new GetPeerAddressListener() {
            @Override
            public void onResult(PeerAddress peerAddress) {
                log.trace("Received address = " + peerAddress.toString());
                resultHandler.onResult(peerAddress);
            }

            @Override
            public void onFailed() {
                log.error("Lookup for peer address faultHandler.onFault.");
                exceptionHandler.onError(new Exception("Lookup for peer address faultHandler.onFault."));
            }
        });
    }

    public interface ResultHandler {
        void onResult(PeerAddress peerAddress);
    }
}

