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

package io.bitsquare.msg.actor.command;


import java.util.Collection;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

/**
 * <p>Command to initialize TomP2P Peer.</p>
 */
public class InitializePeer {

    private final Number160 peerId;
    private final Integer port;
    private final String interfaceHint;

    private final Collection<PeerAddress> bootstrapPeers;

    public InitializePeer(Number160 peerId, Integer port, String interfaceHint,
                          Collection<PeerAddress> bootstrapPeers) {
        this.peerId = peerId;
        this.port = port;
        this.interfaceHint = interfaceHint;
        this.bootstrapPeers = bootstrapPeers;
    }

    public Number160 getPeerId() {
        return peerId;
    }

    public Integer getPort() {
        return port;
    }

    public String getInterfaceHint() {
        return interfaceHint;
    }

    public Collection<PeerAddress> getBootstrapPeers() {
        return bootstrapPeers;
    }
}
