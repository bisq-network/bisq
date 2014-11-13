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

package io.bitsquare.gui.main.preferences.network;

import io.bitsquare.BitsquareException;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.msg.tomp2p.BootstrappedPeerFactory;
import io.bitsquare.msg.tomp2p.TomP2PNode;
import io.bitsquare.network.BootstrapState;
import io.bitsquare.network.Node;

import org.bitcoinj.core.NetworkParameters;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import net.tomp2p.peers.PeerSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkPreferencesPM extends PresentationModel {
    private static final Logger log = LoggerFactory.getLogger(NetworkPreferencesPM.class);

    final String bitcoinNetworkType;
    final String p2pNetworkConnection;
    final String p2pNetworkAddress;
    final String bootstrapAddress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    NetworkPreferencesPM(NetworkParameters networkParameters,
                         BootstrappedPeerFactory bootstrappedPeerFactory,
                         TomP2PNode tomP2PNode,
                         @Named(BootstrappedPeerFactory.BOOTSTRAP_NODE_KEY) Node bootstrapNode) {

        switch (networkParameters.getId()) {
            case NetworkParameters.ID_REGTEST:
                bitcoinNetworkType = "Regtest";
                break;
            case NetworkParameters.ID_TESTNET:
                bitcoinNetworkType = "Testnet";
                break;
            case NetworkParameters.ID_MAINNET:
                bitcoinNetworkType = "Mainnet";
                break;
            default:
                bitcoinNetworkType = "Undefined";
                throw new BitsquareException("Invalid networkParameters " + networkParameters.getId());
        }

        PeerSocketAddress socketAddress = tomP2PNode.getPeerDHT().peerAddress().peerSocketAddress();
        p2pNetworkAddress = "IP: " + socketAddress.inetAddress().getHostAddress()
                + ", TCP port: " + socketAddress.tcpPort()
                + ", UDP port: " + socketAddress.udpPort();

        bootstrapAddress = "ID: " + bootstrapNode.getName()
                + ", IP: " + bootstrapNode.getIp()
                + ", Port: " + bootstrapNode.getPortAsString();

        BootstrapState state = bootstrappedPeerFactory.bootstrapState.get();
        if (state == BootstrapState.DIRECT_SUCCESS)
            p2pNetworkConnection = "Direct connection";
        else if (state == BootstrapState.NAT_SUCCESS)
            p2pNetworkConnection = "Connected with automatic port forwarding";
        else if (state == BootstrapState.RELAY_SUCCESS)
            p2pNetworkConnection = "Relayed by other peers";
        else
            throw new BitsquareException("Invalid BootstrapState " + state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize() {
        super.initialize();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


}
