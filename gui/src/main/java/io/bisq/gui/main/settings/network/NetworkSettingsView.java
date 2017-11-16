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
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNodes;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.filter.Filter;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.user.Preferences;
import io.bisq.gui.app.BisqApp;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.TorNetworkSettingsWindow;
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
    Label onionAddressLabel, btcNodesLabel, useTorForBtcJLabel, totalTrafficLabel, bitcoinNodesLabel;
    @FXML
    InputTextField btcNodesInputTextField;
    @FXML
    TextField onionAddress, totalTrafficTextField;
    @FXML
    TextArea bitcoinPeersTextArea;
    @FXML
    Label bitcoinPeersLabel, p2PPeersLabel;
    @FXML
    CheckBox useTorForBtcJCheckBox;
    @FXML
    RadioButton useProvidedNodesRadio, useCustomNodesRadio, usePublicNodesRadio;
    @FXML
    TableView<P2pNetworkListItem> tableView;
    @FXML
    TableColumn<P2pNetworkListItem, String> onionAddressColumn, connectionTypeColumn, creationDateColumn,
            roundTripTimeColumn, sentBytesColumn, receivedBytesColumn, peerTypeColumn;
    @FXML
    Label reSyncSPVChainLabel;
    @FXML
    Button reSyncSPVChainButton, openTorSettingsButton;

    private final Preferences preferences;
    private final BitcoinNodes bitcoinNodes;
    private final FilterManager filterManager;
    private final BisqEnvironment bisqEnvironment;
    private final Clock clock;
    private final BSFormatter formatter;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;

    private final ObservableList<P2pNetworkListItem> networkListItems = FXCollections.observableArrayList();
    private final SortedList<P2pNetworkListItem> sortedList = new SortedList<>(networkListItems);

    private Subscription numP2PPeersSubscription;
    private Subscription bitcoinPeersSubscription;
    private Subscription nodeAddressSubscription;
    private ChangeListener<Boolean> btcNodesInputTextFieldFocusListener;
    private ToggleGroup bitcoinPeersToggleGroup;
    private BitcoinNodes.BitcoinNodesOption selectedBitcoinNodesOption;
    private ChangeListener<Toggle> bitcoinPeersToggleGroupListener;
    private ChangeListener<String> btcNodesInputTextFieldListener;
    private ChangeListener<Filter> filterPropertyListener;

    @Inject
    public NetworkSettingsView(WalletsSetup walletsSetup, P2PService p2PService, Preferences preferences, BitcoinNodes bitcoinNodes,
                               FilterManager filterManager, BisqEnvironment bisqEnvironment, Clock clock, BSFormatter formatter) {
        super();
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.preferences = preferences;
        this.bitcoinNodes = bitcoinNodes;
        this.filterManager = filterManager;
        this.bisqEnvironment = bisqEnvironment;
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
        bitcoinNodesLabel.setText(Res.get("settings.net.bitcoinNodesLabel"));
        useProvidedNodesRadio.setText(Res.get("settings.net.useProvidedNodesRadio"));
        useCustomNodesRadio.setText(Res.get("settings.net.useCustomNodesRadio"));
        usePublicNodesRadio.setText(Res.get("settings.net.usePublicNodesRadio"));
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
        openTorSettingsButton.setText(Res.get("settings.net.openTorSettingsButton"));

        GridPane.setMargin(bitcoinPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(bitcoinPeersLabel, VPos.TOP);
        GridPane.setMargin(p2PPeersLabel, new Insets(4, 0, 0, 0));
        GridPane.setValignment(p2PPeersLabel, VPos.TOP);

        bitcoinPeersTextArea.setPrefRowCount(4);

        tableView.setMinHeight(180);
        tableView.setPrefHeight(180);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Res.get("table.placeholder.noData")));
        tableView.getSortOrder().add(creationDateColumn);
        creationDateColumn.setSortType(TableColumn.SortType.ASCENDING);

        bitcoinPeersToggleGroup = new ToggleGroup();
        useProvidedNodesRadio.setToggleGroup(bitcoinPeersToggleGroup);
        useCustomNodesRadio.setToggleGroup(bitcoinPeersToggleGroup);
        usePublicNodesRadio.setToggleGroup(bitcoinPeersToggleGroup);

        useProvidedNodesRadio.setUserData(BitcoinNodes.BitcoinNodesOption.PROVIDED);
        useCustomNodesRadio.setUserData(BitcoinNodes.BitcoinNodesOption.CUSTOM);
        usePublicNodesRadio.setUserData(BitcoinNodes.BitcoinNodesOption.PUBLIC);

        selectedBitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()];
        // In case CUSTOM is selected but no custom nodes are set or
        // in case PUBLIC is selected but we blocked it (B2X risk) we revert to provided nodes
        if ((selectedBitcoinNodesOption == BitcoinNodes.BitcoinNodesOption.CUSTOM &&
                (preferences.getBitcoinNodes() == null || preferences.getBitcoinNodes().isEmpty())) ||
                (selectedBitcoinNodesOption == BitcoinNodes.BitcoinNodesOption.PUBLIC && isPreventPublicBtcNetwork())) {
            selectedBitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.PROVIDED;
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
        }
        if (!bitcoinNodes.useProvidedBtcNodes()) {
            selectedBitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.PUBLIC;
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
        }

        selectBitcoinPeersToggle();
        onBitcoinPeersToggleSelected(false);

        bitcoinPeersToggleGroupListener = (observable, oldValue, newValue) -> {
            selectedBitcoinNodesOption = (BitcoinNodes.BitcoinNodesOption) newValue.getUserData();
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
            onBitcoinPeersToggleSelected(true);
        };

        btcNodesInputTextFieldListener = (observable, oldValue, newValue) -> preferences.setBitcoinNodes(newValue);
        btcNodesInputTextFieldFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue)
                showShutDownPopup();
        };
        filterPropertyListener = (observable, oldValue, newValue) -> {
            applyPreventPublicBtcNetwork();
        };

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
        bitcoinPeersToggleGroup.selectedToggleProperty().addListener(bitcoinPeersToggleGroupListener);

        if (filterManager.getFilter() != null)
            applyPreventPublicBtcNetwork();

        filterManager.filterProperty().addListener(filterPropertyListener);

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

        btcNodesInputTextField.setText(preferences.getBitcoinNodes());
        btcNodesInputTextField.setPromptText(Res.get("settings.net.ips"));

        btcNodesInputTextField.textProperty().addListener(btcNodesInputTextFieldListener);
        btcNodesInputTextField.focusedProperty().addListener(btcNodesInputTextFieldFocusListener);

        openTorSettingsButton.setOnAction(e -> new TorNetworkSettingsWindow(preferences).show());
    }

    @Override
    public void deactivate() {
        bitcoinPeersToggleGroup.selectedToggleProperty().removeListener(bitcoinPeersToggleGroupListener);
        filterManager.filterProperty().removeListener(filterPropertyListener);

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
        btcNodesInputTextField.focusedProperty().removeListener(btcNodesInputTextFieldFocusListener);
        btcNodesInputTextField.textProperty().removeListener(btcNodesInputTextFieldListener);

        openTorSettingsButton.setOnAction(null);
    }

    private boolean isPreventPublicBtcNetwork() {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().isPreventPublicBtcNetwork();
    }

    private void selectBitcoinPeersToggle() {
        switch (selectedBitcoinNodesOption) {
            case CUSTOM:
                bitcoinPeersToggleGroup.selectToggle(useCustomNodesRadio);
                break;
            case PUBLIC:
                bitcoinPeersToggleGroup.selectToggle(usePublicNodesRadio);
                break;
            default:
            case PROVIDED:
                bitcoinPeersToggleGroup.selectToggle(useProvidedNodesRadio);
                break;
        }
    }

    private void showShutDownPopup() {
        new Popup<>()
                .information(Res.get("settings.net.needRestart"))
                .closeButtonText(Res.get("shared.cancel"))
                .useShutDownButton()
                .show();
    }

    private void onBitcoinPeersToggleSelected(boolean calledFromUser) {
        boolean bitcoinLocalhostNodeRunning = bisqEnvironment.isBitcoinLocalhostNodeRunning();
        useTorForBtcJLabel.setDisable(bitcoinLocalhostNodeRunning);
        useTorForBtcJCheckBox.setDisable(bitcoinLocalhostNodeRunning);
        bitcoinNodesLabel.setDisable(bitcoinLocalhostNodeRunning);
        btcNodesLabel.setDisable(bitcoinLocalhostNodeRunning);
        btcNodesInputTextField.setDisable(bitcoinLocalhostNodeRunning);
        useProvidedNodesRadio.setDisable(bitcoinLocalhostNodeRunning ||  !bitcoinNodes.useProvidedBtcNodes());
        useCustomNodesRadio.setDisable(bitcoinLocalhostNodeRunning);
        usePublicNodesRadio.setDisable(bitcoinLocalhostNodeRunning || isPreventPublicBtcNetwork());

        switch (selectedBitcoinNodesOption) {
            case CUSTOM:
                btcNodesInputTextField.setDisable(false);
                btcNodesLabel.setDisable(false);
                if (calledFromUser && !btcNodesInputTextField.getText().isEmpty()) {
                    if (isPreventPublicBtcNetwork()) {
                        new Popup<>().warning(Res.get("settings.net.warn.useCustomNodes.B2XWarning"))
                                .onAction(() -> {
                                    UserThread.runAfter(this::showShutDownPopup, 300, TimeUnit.MILLISECONDS);
                                }).show();
                    } else {
                        showShutDownPopup();
                    }
                }
                break;
            case PUBLIC:
                btcNodesInputTextField.setDisable(true);
                btcNodesLabel.setDisable(true);
                if (calledFromUser)
                    new Popup<>()
                            .warning(Res.get("settings.net.warn.usePublicNodes"))
                            .actionButtonText(Res.get("settings.net.warn.usePublicNodes.useProvided"))
                            .onAction(() -> {
                                UserThread.runAfter(() -> {
                                    selectedBitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.PROVIDED;
                                    preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
                                    selectBitcoinPeersToggle();
                                    onBitcoinPeersToggleSelected(false);
                                }, 300, TimeUnit.MILLISECONDS);
                            })
                            .closeButtonText(Res.get("settings.net.warn.usePublicNodes.usePublic"))
                            .onClose(() -> {
                                UserThread.runAfter(this::showShutDownPopup, 300, TimeUnit.MILLISECONDS);
                            })
                            .show();
                break;
            default:
            case PROVIDED:
                if (bitcoinNodes.useProvidedBtcNodes()) {
                    btcNodesInputTextField.setDisable(true);
                    btcNodesLabel.setDisable(true);
                    if (calledFromUser)
                        showShutDownPopup();
                } else {
                    selectedBitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.PUBLIC;
                    preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
                    selectBitcoinPeersToggle();
                    onBitcoinPeersToggleSelected(false);
                }
                break;
        }
    }


    private void applyPreventPublicBtcNetwork() {
        final boolean preventPublicBtcNetwork = isPreventPublicBtcNetwork();
        usePublicNodesRadio.setDisable(bisqEnvironment.isBitcoinLocalhostNodeRunning() || preventPublicBtcNetwork);
        if (preventPublicBtcNetwork && selectedBitcoinNodesOption == BitcoinNodes.BitcoinNodesOption.PUBLIC) {
            selectedBitcoinNodesOption = BitcoinNodes.BitcoinNodesOption.PROVIDED;
            preferences.setBitcoinNodesOptionOrdinal(selectedBitcoinNodesOption.ordinal());
            selectBitcoinPeersToggle();
            onBitcoinPeersToggleSelected(false);
        }
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

        if (bisqEnvironment.isBitcoinLocalhostNodeRunning())
            bitcoinPeersTextArea.appendText("\n\n" + Res.get("settings.net.localhostBtcNodeInfo"));
    }
}

