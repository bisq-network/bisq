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

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.InitializableView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.user.Preferences;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import javax.inject.Inject;

import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class NetworkSettingsView extends InitializableView {

    private final String bitcoinNetworkString;
    private final WalletService walletService;
    private final SeedNodesRepository bootstrapNodes;
    private final Preferences preferences;
    private final P2PService p2PService;

    @FXML
    TextField bitcoinNetwork, connectionType, nodeAddress, connectedPeersBTC, connectedPeersP2P;
    @FXML
    CheckBox useUPnP;
    @FXML
    ComboBox<BitcoinNetwork> netWorkComboBox;
    @FXML
    TextArea bootstrapNodeAddress;

    @Inject
    public NetworkSettingsView(WalletService walletService, P2PService p2PService, SeedNodesRepository bootstrapNodes, Preferences preferences, BSFormatter
            formatter) {
        this.walletService = walletService;
        this.bootstrapNodes = bootstrapNodes;
        this.preferences = preferences;
        this.bitcoinNetworkString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork());
        this.p2PService = p2PService;
    }

    public void initialize() {
        bitcoinNetwork.setText(bitcoinNetworkString);
        connectedPeersBTC.textProperty().bind(createStringBinding(() -> String.valueOf(walletService.numPeersProperty().get()), walletService
                .numPeersProperty()));

       /* if (networkService.getNetworkInfo() instanceof TomP2PNetworkInfo) {
            TomP2PNetworkInfo networkInfo = (TomP2PNetworkInfo) networkService.getNetworkInfo();
            connectionType.setText(networkInfo.getConnectionType().toString());
            connectedPeersP2P.textProperty().bind(createStringBinding(() -> String.valueOf(networkInfo.numPeersProperty().get()), networkInfo.numPeersProperty()));
            nodeAddress.setText(networkInfo.getClientNodeInfo());
        }*/

        /*List<NodeSpec> bootstrapNodeSpecs = bootstrapNodes.getBootstrapNodes();
        String bootstrapNodesText = bootstrapNodeSpecs.stream().map(e -> e.toString() + "\n").collect(Collectors.toList()).toString()
                .replace(", ", "").replace("[", "").replace("\n]", "");
        bootstrapNodeAddress.setPrefRowCount(bootstrapNodeSpecs.size());
        bootstrapNodeAddress.setText(bootstrapNodesText);*/

        useUPnP.setSelected(preferences.getUseUPnP());

        netWorkComboBox.setItems(FXCollections.observableArrayList(BitcoinNetwork.values()));
        netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork());
        netWorkComboBox.setOnAction(e -> onSelectNetwork());
    }

    @FXML
    void onSelectUPnP() {
        preferences.setUseUPnP(useUPnP.isSelected());
    }

    private void onSelectNetwork() {
        if (netWorkComboBox.getSelectionModel().getSelectedItem() != preferences.getBitcoinNetwork()) {
            if (netWorkComboBox.getSelectionModel().getSelectedItem() == BitcoinNetwork.MAINNET) {
                new Popup().warning("The application is under heavy development. " +
                        "Using the mainnet network with Bitcoin is not recommended at that stage.\n\n" +
                        "Are you sure you want to switch to mainnet?")
                        .onAction(() -> selectNetwork())
                        .onClose(() -> UserThread.execute(() -> netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork())))
                        .show();
            } else {
                selectNetwork();
            }
        }
    }

    private void selectNetwork() {
        preferences.setBitcoinNetwork(netWorkComboBox.getSelectionModel().getSelectedItem());
        new Popup().warning("You need to restart the application to apply the change of the Bitcoin network..\n\n" +
                "Do you want to restart now?")
                .onAction(() -> BitsquareApp.restartDownHandler.run())
                .show();
    }
}

