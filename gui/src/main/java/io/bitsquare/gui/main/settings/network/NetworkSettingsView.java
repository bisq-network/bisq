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
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.InitializableView;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;
import io.bitsquare.p2p.BootstrapNodes;
import io.bitsquare.p2p.ClientNode;
import io.bitsquare.user.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;

import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class NetworkSettingsView extends InitializableView {

    private final String bitcoinNetworkString;
    private final WalletService walletService;
    private BootstrapNodes bootstrapNodes;
    private final Preferences preferences;
    private final ClientNode clientNode;

    @FXML TextField bitcoinNetwork, connectionType, nodeAddress, connectedPeersBTC, connectedPeersP2P;
    @FXML CheckBox useUPnP;
    @FXML ComboBox<BitcoinNetwork> netWorkComboBox;
    @FXML TextArea bootstrapNodeAddress;

    @Inject
    public NetworkSettingsView(WalletService walletService, ClientNode clientNode, BootstrapNodes bootstrapNodes, Preferences preferences, BSFormatter
            formatter) {
        this.walletService = walletService;
        this.bootstrapNodes = bootstrapNodes;
        this.preferences = preferences;
        this.bitcoinNetworkString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork());
        this.clientNode = clientNode;
    }

    public void initialize() {
        bitcoinNetwork.setText(bitcoinNetworkString);
        connectedPeersBTC.textProperty().bind(createStringBinding(() -> String.valueOf(walletService.numPeersProperty().get()), walletService
                .numPeersProperty()));

        connectionType.setText(clientNode.getConnectionType().toString());
        connectedPeersP2P.textProperty().bind(createStringBinding(() -> String.valueOf(clientNode.numPeersProperty().get()), clientNode.numPeersProperty()));
        nodeAddress.setText(clientNode.getClientNodeInfo());
        String bootstrapNodesText = bootstrapNodes.getBootstrapNodes().stream().map(e -> e.toString() + "\n").collect(Collectors.toList()).toString()
                .replace(", ", "").replace("[", "").replace("\n]", "");
        bootstrapNodeAddress.setPrefRowCount(bootstrapNodes.getBootstrapNodes().size());
        bootstrapNodeAddress.setText(bootstrapNodesText);

        useUPnP.setSelected(preferences.getUseUPnP());

        netWorkComboBox.setItems(FXCollections.observableArrayList(BitcoinNetwork.values()));
        netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork());
        netWorkComboBox.setOnAction(e -> onSelectNetwork());
    }

    @FXML
    void onSelectUPnP() {
        preferences.setUseUPnP(useUPnP.isSelected());
    }

    void onSelectNetwork() {
        preferences.setBitcoinNetwork(netWorkComboBox.getSelectionModel().getSelectedItem());

        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.no")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "NO");
                org.controlsfx.dialog.Dialog.Actions.NO.handle(actionEvent);
            }
        });

        actions.add(new AbstractAction(BSResources.get("shared.yes")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "YES");
                org.controlsfx.dialog.Dialog.Actions.YES.handle(actionEvent);
            }
        });

        Action response = Popups.openConfirmPopup("Info", "You need to restart the application to apply the change of the Bitcoin network.",
                "Do you want to shutdown now?", actions);

        if (Popups.isYes(response))
            BitsquareApp.shutDownHandler.run();
    }
}

