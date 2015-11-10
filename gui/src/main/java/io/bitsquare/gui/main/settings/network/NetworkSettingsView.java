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
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.network.LocalhostNetworkNode;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;
import java.util.Set;

import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class NetworkSettingsView extends ActivatableViewAndModel<GridPane, Activatable> {

    private final String bitcoinNetworkString;
    private final WalletService walletService;
    private final Preferences preferences;
    private final P2PService p2PService;


    @FXML
    TextField bitcoinNetwork, onionAddress, connectedPeersBTC;
    @FXML
    ComboBox<BitcoinNetwork> netWorkComboBox;
    @FXML
    TextArea authenticatedPeersTextArea;
    @FXML
    Label authenticatedPeersLabel;

    private P2PServiceListener p2PServiceListener;
    private ChangeListener<Number> numAuthenticatedPeersChangeListener;
    private Set<Address> seedNodeAddresses;

    @Inject
    public NetworkSettingsView(WalletService walletService, P2PService p2PService, SeedNodesRepository seedNodesRepository, Preferences preferences, BSFormatter
            formatter) {
        this.walletService = walletService;
        this.preferences = preferences;
        BitcoinNetwork bitcoinNetwork = preferences.getBitcoinNetwork();
        this.bitcoinNetworkString = formatter.formatBitcoinNetwork(bitcoinNetwork);
        this.p2PService = p2PService;

        boolean useLocalhost = p2PService.getNetworkNode() instanceof LocalhostNetworkNode;
        this.seedNodeAddresses = seedNodesRepository.geSeedNodeAddresses(useLocalhost, bitcoinNetwork.ordinal());
    }

    public void initialize() {
        GridPane.setMargin(authenticatedPeersLabel, new Insets(4, 0, 0, 0));
        bitcoinNetwork.setText(bitcoinNetworkString);
        connectedPeersBTC.textProperty().bind(createStringBinding(() -> String.valueOf(walletService.numPeersProperty().get()), walletService
                .numPeersProperty()));

        netWorkComboBox.setItems(FXCollections.observableArrayList(BitcoinNetwork.values()));
        netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork());
        netWorkComboBox.setOnAction(e -> onSelectNetwork());
    }

    @Override
    public void activate() {
        Address address = p2PService.getAddress();
        if (address == null) {
            p2PServiceListener = new P2PServiceListener() {
                @Override
                public void onRequestingDataCompleted() {
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
            onionAddress.setText(address.getFullAddress());
        }

        numAuthenticatedPeersChangeListener = (observable, oldValue, newValue) -> updateAuthenticatedPeersTextArea();
        p2PService.numAuthenticatedPeers.addListener(numAuthenticatedPeersChangeListener);
        updateAuthenticatedPeersTextArea();
    }

    @Override
    public void deactivate() {
        if (p2PServiceListener != null)
            p2PService.removeP2PServiceListener(p2PServiceListener);
        if (numAuthenticatedPeersChangeListener != null)
            p2PService.numAuthenticatedPeers.removeListener(numAuthenticatedPeersChangeListener);
    }

    private void updateAuthenticatedPeersTextArea() {
        authenticatedPeersTextArea.clear();
        p2PService.getAuthenticatedPeerAddresses().stream().forEach(e -> {
            if (authenticatedPeersTextArea.getText().length() > 0)
                authenticatedPeersTextArea.appendText("\n");
            authenticatedPeersTextArea.appendText(e.getFullAddress());
            if (seedNodeAddresses.contains(e))
                authenticatedPeersTextArea.appendText(" (Seed node)");
        });
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

