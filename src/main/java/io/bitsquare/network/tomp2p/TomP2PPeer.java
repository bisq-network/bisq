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

package io.bitsquare.network.tomp2p;

import io.bitsquare.network.Peer;

import com.google.common.base.Objects;

import net.tomp2p.peers.PeerAddress;

/**
 * A {@link Peer} implementation that encapsulates a TomP2P {@link PeerAddress}.
 *
 * @author Chris Beams
 */
public class TomP2PPeer implements Peer {

    private final PeerAddress peerAddress;

    public TomP2PPeer(PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
    }

    public PeerAddress getPeerAddress() {
        return peerAddress;
    }

    public String toString() {
        return Objects.toStringHelper(this)
                .add("peerAddress", peerAddress)
                .toString();
    }
}
