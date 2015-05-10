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

package io.bitsquare.gui.main.settings.network;

import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.InitializableView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.ClientNode;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class NetworkSettingsView extends InitializableView {

    private final String bitcoinNetworkString;
    private final WalletService walletService;
    private final ClientNode clientNode;

    @FXML TextField bitcoinNetwork, connectionType, nodeAddress, bootstrapNodeAddress, connectedPeersBTC, connectedPeersP2P;

    @Inject
    public NetworkSettingsView(BitcoinNetwork bitcoinNetwork, WalletService walletService, ClientNode clientNode, BSFormatter formatter) {
        this.walletService = walletService;
        this.bitcoinNetworkString = formatter.formatBitcoinNetwork(bitcoinNetwork);
        this.clientNode = clientNode;
    }

    public void initialize() {
        bitcoinNetwork.setText(bitcoinNetworkString);
        connectedPeersBTC.textProperty().bind(createStringBinding(() -> String.valueOf(walletService.numPeersProperty().get()), walletService
                .numPeersProperty()));

        connectionType.setText(clientNode.getConnectionType().toString());
        connectedPeersP2P.textProperty().bind(createStringBinding(() -> String.valueOf(clientNode.numPeersProperty().get()), clientNode.numPeersProperty()));
        nodeAddress.setText(clientNode.getAddress().toString());
        bootstrapNodeAddress.setText(clientNode.getBootstrapNodeAddress().toString());
    }
}

