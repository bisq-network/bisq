/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.settings.network;

import io.bisq.common.Clock;
import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.user.Preferences;
import io.bisq.gui.app.BisqApp;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.network.Statistic;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Peer;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@FxmlView
public class NetworkSettingsView extends ActivatableViewAndModel<GridPane, Activatable> {

    @FXML
    TitledGroupBg p2pHeader, btcHeader;
    @FXML
    Label onionAddressLabel, btcNodesLabel, useTorForBtcJLabel, totalTrafficLabel;
    @FXML
    InputTextField btcNodes;
    @FXML
    TextField onionAddress, totalTrafficTextField;
    @FXML
    TextArea bitcoinPeersTextArea;
    @FXML
    Label bitcoinPeersLabel, p2PPeersLabel;
    @FXML
    CheckBox useTorForBtcJCheckBox;
    @FXML
    TableView<P2pNetworkListItem> tableView;
    @FXML
    TableColumn<P2pNetworkListItem, String> onionAddressColumn, connectionTypeColumn, creationDateColumn,
            roundTripTimeColumn, sentBytesColumn, receivedBytesColumn, peerTypeColumn;
    @FXML
    Label reSyncSPVChainLabel;
    @FXML
    Button reSyncSPVChainButton;

    private final Preferences preferences;
    private final Clock clock;
    private final BSFormatter formatter;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;

    private final ObservableList<P2pNetworkListItem> networkListItems = FXCollections.observableArrayList();
    private final SortedList<P2pNetworkListItem> sortedList = new SortedList<>(networkListItems);

    private Subscription numP2PPeersSubscription;
    private Subscription bitcoinPeersSubscription;
    private Subscription nodeAddressSubscription;
    private ChangeListener<Boolean> btcNodesFocusListener;
    private String btcNodesPreFocusText;

    @Inject
    public NetworkSettingsView(WalletsSetup walletsSetup, P2PService p2PService, Preferences preferences,
                               Clock clock, BSFormatter formatter) {
        super();
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.clock = clock;
        this.formatter = formatter;
    }

    public void initialize() {
        btcHeader.setText(Res.get("settings.net.btcHeader"));
        p2pHeader.setText(Res.get("settings.net.p2pHeader"));
        onionAddressLabel.setText(Res.get("settings.net.onionAddressLabel"));
        btcNodesLabel.setText(Res.get("settings.net.btcNodesLabel"));
        bitcoinPeersLabel.setText(Res.get("settings.net.bitcoinPeersLabel"));
        useTorForBtcJLabel.setText(Res.get("settings.net.useTorForBtcJLabel"));
        reSyncSPVChainLabel.setText(Res.getWithCol("settings.net.reSyncSPVChainLabel"));
        reSyncSPVChainButton.setText(Res.get("settings.net.reSyncSPVChainButton"));
        p2PPeersLabel.setText(Res.get("settings.net.p2PPeersLabel"));
        onionAddressColumn.setText(Res.get("settings.net.onionAddressColumn"));
        creationDateColumn.setText(Res.get("settings.net.creationDateColumn"));
        connectionTypeColumn.setText(Res.get("settings.net.connectionTypeColumn"));
        totalTrafficLabel.setText(Res.get("settings.net.totalTrafficLabel"));
        roundTripTimeColumn.setText(Res.get("settings.net.roundTripTimeColumn"));
        sentBytesColumn.setText(Res.get("settings.net.sentBytesColumn"));
        receivedBytesColumn.setText(Res.get("settings.net.receivedBytesColumn"));
        peerTypeColumn.setText(Res.get("settings.net.peerTypeColumn"));

        GridPane.setMargin(bitcoinPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(bitcoinPeersLabel, VPos.TOP);
        GridPane.setMargin(p2PPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(p2PPeersLabel, VPos.TOP);

        bitcoinPeersTextArea.setPrefRowCount(6);

        tableView.setMinHeight(230);
        tableView.setPrefHeight(230);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Res.get("table.placeholder.noData")));
        tableView.getSortOrder().add(creationDateColumn);
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
        useTorForBtcJCheckBox.setSelected(preferences.getUseTorForBitcoinJ());
        useTorForBtcJCheckBox.setOnAction(event -> {
            boolean selected = useTorForBtcJCheckBox.isSelected();
            if (selected != preferences.getUseTorForBitcoinJ()) {
                new Popup<>().information(Res.get("settings.net.needRestart"))
                        .actionButtonText(Res.get("shared.applyAndShutDown"))
                        .onAction(() -> {
                            preferences.setUseTorForBitcoinJ(selected);
                            UserThread.runAfter(BisqApp.shutDownHandler::run, 500, TimeUnit.MILLISECONDS);
                        })
                        .closeButtonText(Res.get("shared.cancel"))
                        .onClose(() -> useTorForBtcJCheckBox.setSelected(!selected))
                        .show();
            }
        });

        reSyncSPVChainButton.setOnAction(event -> {
            if (walletsSetup.reSyncSPVChain()) {
                new Popup<>().feedback(Res.get("settings.net.reSyncSPVSuccess"))
                        .useShutDownButton()
                        .actionButtonText(Res.get("shared.shutDown"))
                        .onAction(() -> {
                            preferences.setResyncSpvRequested(true);
                            UserThread.runAfter(BisqApp.shutDownHandler::run, 100, TimeUnit.MILLISECONDS);
                        })
                        .hideCloseButton()
                        .show();
            } else {
                new Popup<>().error(Res.get("settings.net.reSyncSPVFailed")).show();
            }
        });

        bitcoinPeersSubscription = EasyBind.subscribe(walletsSetup.connectedPeersProperty(),
                connectedPeers -> updateBitcoinPeersTextArea());

        nodeAddressSubscription = EasyBind.subscribe(p2PService.getNetworkNode().nodeAddressProperty(),
                nodeAddress -> onionAddress.setText(nodeAddress == null ?
                        Res.get("settings.net.notKnownYet") :
                        p2PService.getAddress().getFullAddress()));
        numP2PPeersSubscription = EasyBind.subscribe(p2PService.getNumConnectedPeers(), numPeers -> updateP2PTable());
        totalTrafficTextField.textProperty().bind(EasyBind.combine(Statistic.totalSentBytesProperty(),
                Statistic.totalReceivedBytesProperty(),
                (sent, received) -> Res.get("settings.net.sentReceived",
                        formatter.formatBytes((long) sent),
                        formatter.formatBytes((long) received))));

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        btcNodes.setText(preferences.getBitcoinNodes());
        btcNodes.setPromptText(Res.get("settings.net.ips"));
        btcNodesFocusListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                btcNodesPreFocusText = btcNodes.getText();
            }
            if (oldValue && !newValue && !btcNodesPreFocusText.equals(btcNodes.getText())) {
                new Popup<>().information(Res.get("settings.net.needRestart"))
                        .actionButtonText(Res.get("shared.applyAndShutDown"))
                        .onAction(() -> {
                            if (btcNodes.getText().isEmpty()) {
                                preferences.setBitcoinNodes("");
                            } else {
                                preferences.setBitcoinNodes(btcNodes.getText());
                            }
                            UserThread.runAfter(BisqApp.shutDownHandler::run, 500, TimeUnit.MILLISECONDS);
                        })
                        .closeButtonText(Res.get("shared.cancel"))
                        .onClose(() -> btcNodes.setText(btcNodesPreFocusText))
                        .show();
            }
        };
        btcNodes.focusedProperty().addListener(btcNodesFocusListener);
    }

    @Override
    public void deactivate() {
        useTorForBtcJCheckBox.setOnAction(null);

        if (nodeAddressSubscription != null)
            nodeAddressSubscription.unsubscribe();

        if (bitcoinPeersSubscription != null)
            bitcoinPeersSubscription.unsubscribe();

        if (numP2PPeersSubscription != null)
            numP2PPeersSubscription.unsubscribe();

        totalTrafficTextField.textProperty().unbind();

        sortedList.comparatorProperty().unbind();
        tableView.getItems().forEach(P2pNetworkListItem::cleanup);
        btcNodes.focusedProperty().removeListener(btcNodesFocusListener);
    }

    private void updateP2PTable() {
        tableView.getItems().forEach(P2pNetworkListItem::cleanup);
        networkListItems.clear();
        networkListItems.setAll(p2PService.getNetworkNode().getAllConnections().stream()
                .map(connection -> new P2pNetworkListItem(connection, clock, formatter))
                .collect(Collectors.toList()));
    }

    private void updateBitcoinPeersTextArea() {
        bitcoinPeersTextArea.clear();
        List<Peer> peerList = walletsSetup.connectedPeersProperty().get();
        if (peerList != null) {
            peerList.stream().forEach(e -> {
                if (bitcoinPeersTextArea.getText().length() > 0)
                    bitcoinPeersTextArea.appendText("\n");
                bitcoinPeersTextArea.appendText(e.toString());
            });
        }
    }
}

