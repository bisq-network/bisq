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
import io.bitsquare.common.Clock;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.network.Statistic;
import io.bitsquare.user.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.bitcoinj.core.Peer;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.reactfx.util.FxTimer;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class NetworkSettingsView extends ActivatableViewAndModel<GridPane, Activatable> {

    private final WalletService walletService;
    private final Preferences preferences;
    private Clock clock;
    private final BSFormatter formatter;
    private final P2PService p2PService;


    @FXML
    TextField onionAddress, totalTraffic;
    @FXML
    ComboBox<BitcoinNetwork> netWorkComboBox;
    @FXML
    TextArea bitcoinPeersTextArea;
    @FXML
    Label bitcoinPeersLabel, p2PPeersLabel;
    @FXML
    CheckBox useTorCheckBox;
    @FXML
    TableView<P2pNetworkListItem> p2PPeerTable;
    @FXML
    TableColumn<P2pNetworkListItem, String> onionAddressColumn, connectionTypeColumn, creationDateColumn,
            lastActivityColumn, sentBytesColumn, receivedBytesColumn, peerTypeColumn;
    /* TableColumn<NetworkStatisticListItem, NetworkStatisticListItem> onionAddressColumn, connectionTypeColumn, creationDateColumn,
             lastActivityColumn, sentBytesColumn, receivedBytesColumn, peerTypeColumn;
 */
    private Subscription numP2PPeersSubscription;
    private Subscription bitcoinPeersSubscription;
    private Subscription nodeAddressSubscription;
    private ObservableList<P2pNetworkListItem> networkListItems = FXCollections.observableArrayList();

    @Inject
    public NetworkSettingsView(WalletService walletService, P2PService p2PService, Preferences preferences, Clock clock,
                               BSFormatter formatter) {
        this.walletService = walletService;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.clock = clock;
        this.formatter = formatter;
    }

    public void initialize() {
        GridPane.setMargin(bitcoinPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(bitcoinPeersLabel, VPos.TOP);
        GridPane.setMargin(p2PPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(p2PPeersLabel, VPos.TOP);

        bitcoinPeersTextArea.setPrefRowCount(10);
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

        p2PPeerTable.setMinHeight(300);
        p2PPeerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        p2PPeerTable.setPlaceholder(new Label("No connections are available"));
        p2PPeerTable.getSortOrder().add(creationDateColumn);
        creationDateColumn.setSortType(TableColumn.SortType.ASCENDING);


        //TODO sorting needs other NetworkStatisticListItem as columns type
       /* creationDateColumn.setComparator((o1, o2) ->
                o1.statistic.getCreationDate().compareTo(o2.statistic.getCreationDate()));
        sentBytesColumn.setComparator((o1, o2) ->
                ((Integer) o1.statistic.getSentBytes()).compareTo(((Integer) o2.statistic.getSentBytes())));
        receivedBytesColumn.setComparator((o1, o2) ->
                ((Integer) o1.statistic.getReceivedBytes()).compareTo(((Integer) o2.statistic.getReceivedBytes())));*/
    }

    @Override
    public void activate() {
        useTorCheckBox.setSelected(preferences.getUseTorForBitcoinJ());
        useTorCheckBox.setOnAction(event -> {
            boolean selected = useTorCheckBox.isSelected();
            if (selected != preferences.getUseTorForBitcoinJ()) {
                new Popup().information("You need to restart the application to apply that change.\n" +
                        "Do you want to do that now?")
                        .actionButtonText("Apply and shut down")
                        .onAction(() -> {
                            preferences.setUseTorForBitcoinJ(selected);
                            FxTimer.runLater(Duration.ofMillis(500), BitsquareApp.shutDownHandler::run);
                        })
                        .closeButtonText("Cancel")
                        .onClose(() -> useTorCheckBox.setSelected(!selected))
                        .show();
            }
        });
        bitcoinPeersSubscription = EasyBind.subscribe(walletService.connectedPeersProperty(), connectedPeers -> updateBitcoinPeersTextArea());

        nodeAddressSubscription = EasyBind.subscribe(p2PService.getNetworkNode().nodeAddressProperty(),
                nodeAddress -> onionAddress.setText(nodeAddress == null ? "Not known yet..." : p2PService.getAddress().getFullAddress()));
        numP2PPeersSubscription = EasyBind.subscribe(p2PService.getNumConnectedPeers(), numPeers -> updateP2PTable());
        totalTraffic.textProperty().bind(EasyBind.combine(Statistic.totalSentBytesProperty(), Statistic.totalReceivedBytesProperty(),
                (sent, received) -> "Sent: " + formatter.formatBytes((int) sent) + ", received: " + formatter.formatBytes((int) received)));

        p2PPeerTable.setItems(networkListItems);
        p2PPeerTable.sort();
    }

    @Override
    public void deactivate() {
        useTorCheckBox.setOnAction(null);

        if (nodeAddressSubscription != null)
            nodeAddressSubscription.unsubscribe();

        if (bitcoinPeersSubscription != null)
            bitcoinPeersSubscription.unsubscribe();

        if (numP2PPeersSubscription != null)
            numP2PPeersSubscription.unsubscribe();

        p2PPeerTable.getItems().forEach(P2pNetworkListItem::cleanup);
        totalTraffic.textProperty().unbind();
    }

    private void updateP2PTable() {
        p2PPeerTable.getItems().forEach(P2pNetworkListItem::cleanup);
        networkListItems.clear();
        networkListItems.setAll(p2PService.getNetworkNode().getAllConnections().stream()
                .map(connection -> new P2pNetworkListItem(connection, clock, formatter))
                .collect(Collectors.toList()));
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
        if (netWorkComboBox.getSelectionModel().getSelectedItem() != preferences.getBitcoinNetwork())
            selectNetwork();
    }

    private void selectNetwork() {
        //TODO restart
        new Popup().warning("You need to shut down and restart the application to apply the change of the Bitcoin network.\n\n" +
                "Do you want to shut down now?")
                .onAction(() -> {
                    preferences.setBitcoinNetwork(netWorkComboBox.getSelectionModel().getSelectedItem());
                    FxTimer.runLater(Duration.ofMillis(500), BitsquareApp.shutDownHandler::run);
                })
                .actionButtonText("Shut down")
                .closeButtonText("Cancel")
                .onClose(() -> netWorkComboBox.getSelectionModel().select(preferences.getBitcoinNetwork()))
                .show();
    }
}

