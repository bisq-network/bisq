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
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.network.LocalhostNetworkNode;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.bitcoinj.core.Peer;
import org.reactfx.util.FxTimer;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@FxmlView
public class NetworkSettingsView extends ActivatableViewAndModel<GridPane, Activatable> {

    private final WalletService walletService;
    private final Preferences preferences;
    private BSFormatter formatter;
    private final P2PService p2PService;


    @FXML
    TextField onionAddress;
    @FXML
    ComboBox<BitcoinNetwork> netWorkComboBox;
    @FXML
    TextArea bitcoinPeersTextArea, authenticatedPeersTextArea;
    @FXML
    Label bitcoinPeersLabel, authenticatedPeersLabel;

    private P2PServiceListener p2PServiceListener;
    private ChangeListener<Number> numAuthenticatedPeersChangeListener;
    private ChangeListener<List<Peer>> bitcoinPeersChangeListener;
    private Set<NodeAddress> seedNodeNodeAddresses;

    @Inject
    public NetworkSettingsView(WalletService walletService, P2PService p2PService, Preferences preferences,
                               SeedNodesRepository seedNodesRepository, BSFormatter formatter) {
        this.walletService = walletService;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.formatter = formatter;
        BitcoinNetwork bitcoinNetwork = preferences.getBitcoinNetwork();

        boolean useLocalhost = p2PService.getNetworkNode() instanceof LocalhostNetworkNode;
        this.seedNodeNodeAddresses = seedNodesRepository.getSeedNodeAddresses(useLocalhost, bitcoinNetwork.ordinal());
    }

    public void initialize() {
        GridPane.setMargin(bitcoinPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(bitcoinPeersLabel, VPos.TOP);
        GridPane.setMargin(authenticatedPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(authenticatedPeersLabel, VPos.TOP);
        bitcoinPeersTextArea.setPrefRowCount(12);
        netWorkComboBox.setItems(FXCollections.observableArrayList(BitcoinNetwork.values()));
        netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork());
        netWorkComboBox.setOnAction(e -> onSelectNetwork());
        netWorkComboBox.setConverter(new StringConverter<BitcoinNetwork>() {
            @Override
            public String toString(BitcoinNetwork bitcoinNetwork) {
                return formatter.formatBitcoinNetwork(bitcoinNetwork);
            }

            @Override
            public BitcoinNetwork fromString(String string) {
                return null;
            }
        });
    }

    @Override
    public void activate() {
        NodeAddress nodeAddress = p2PService.getAddress();
        if (nodeAddress == null) {
            p2PServiceListener = new P2PServiceListener() {
                @Override
                public void onRequestingDataCompleted() {
                }

                @Override
                public void onNoSeedNodeAvailable() {
                }

                @Override
                public void onNoPeersAvailable() {
                }

                @Override
                public void onFirstPeerAuthenticated() {
                }

                @Override
                public void onTorNodeReady() {
                }

                @Override
                public void onHiddenServicePublished() {
                    onionAddress.setText(p2PService.getAddress().getFullAddress());
                }

                @Override
                public void onSetupFailed(Throwable throwable) {
                }
            };
            p2PService.addP2PServiceListener(p2PServiceListener);
        } else {
            onionAddress.setText(nodeAddress.getFullAddress());
        }

        bitcoinPeersChangeListener = (observable, oldValue, newValue) -> updateBitcoinPeersTextArea();
        walletService.connectedPeersProperty().addListener(bitcoinPeersChangeListener);
        updateBitcoinPeersTextArea();

        numAuthenticatedPeersChangeListener = (observable, oldValue, newValue) -> updateAuthenticatedPeersTextArea();
        p2PService.getNumAuthenticatedPeers().addListener(numAuthenticatedPeersChangeListener);
        updateAuthenticatedPeersTextArea();
    }

    @Override
    public void deactivate() {
        if (p2PServiceListener != null)
            p2PService.removeP2PServiceListener(p2PServiceListener);

        if (bitcoinPeersChangeListener != null)
            walletService.connectedPeersProperty().removeListener(bitcoinPeersChangeListener);

        if (numAuthenticatedPeersChangeListener != null)
            p2PService.getNumAuthenticatedPeers().removeListener(numAuthenticatedPeersChangeListener);
    }

    private void updateAuthenticatedPeersTextArea() {
        authenticatedPeersTextArea.clear();
        p2PService.getAuthenticatedPeerNodeAddresses().stream().forEach(e -> {
            if (authenticatedPeersTextArea.getText().length() > 0)
                authenticatedPeersTextArea.appendText("\n");
            authenticatedPeersTextArea.appendText(e.getFullAddress());
            if (seedNodeNodeAddresses.contains(e))
                authenticatedPeersTextArea.appendText(" (Seed node)");
        });
    }

    private void updateBitcoinPeersTextArea() {
        bitcoinPeersTextArea.clear();
        List<Peer> peerList = walletService.connectedPeersProperty().get();
        if (peerList != null) {
            peerList.stream().forEach(e -> {
                if (bitcoinPeersTextArea.getText().length() > 0)
                    bitcoinPeersTextArea.appendText("\n");
                bitcoinPeersTextArea.appendText(e.getAddress().getSocketAddress().toString());
            });
        }
    }

    private void onSelectNetwork() {
        if (netWorkComboBox.getSelectionModel().getSelectedItem() != preferences.getBitcoinNetwork()) {
            if (netWorkComboBox.getSelectionModel().getSelectedItem() == BitcoinNetwork.MAINNET) {
                new Popup().warning("The application is not sufficiently tested yet. " +
                        "Please be aware that using Mainnet comes with the risk to lose funds in case of software bugs.\n" +
                        "To limit the possible losses the maximum allowed trading amount and the security deposit are " +
                        "reduced to 0.01 BTC on Mainnet.")
                        .actionButtonText("I will stick with Testnet for now")
                        .onAction(() -> UserThread.execute(() -> netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork())))
                        .closeButtonText("I understand the risk and want to use Mainnet")
                        .onClose(() -> selectNetwork())
                        .width(800)
                        .show();
            } else {
                selectNetwork();
            }
        }
    }

    private void selectNetwork() {
        //TODO restart
        new Popup().warning("You need to shut down and restart the application to apply the change of the Bitcoin network.\n\n" +
                "Do you want to shut down now?")
                .onAction(() -> {
                    preferences.setBitcoinNetwork(netWorkComboBox.getSelectionModel().getSelectedItem());
                    FxTimer.runLater(Duration.ofMillis(500), () -> BitsquareApp.shutDownHandler.run());
                })
                .actionButtonText("Shut down")
                .closeButtonText("Cancel")
                .onClose(() -> netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork()))
                .show();
    }
}

