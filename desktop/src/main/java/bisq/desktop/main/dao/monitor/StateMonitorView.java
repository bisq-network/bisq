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

package bisq.desktop.main.dao.monitor;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.monitoring.model.StateBlock;
import bisq.core.dao.monitoring.model.StateHash;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.seed.SeedNodeRepository;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.io.File;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class StateMonitorView<StH extends StateHash,
        StB extends StateBlock<StH>,
        BLI extends StateBlockListItem<StH, StB>,
        CLI extends StateInConflictListItem<StH>>
        extends ActivatableView<GridPane, Void> implements DaoStateListener {
    protected final DaoStateService daoStateService;
    protected final DaoFacade daoFacade;
    protected final CycleService cycleService;
    protected final PeriodService periodService;
    protected final Set<NodeAddress> seedNodeAddresses;
    private final File storageDir;

    protected TextField statusTextField;
    protected Button resyncButton;
    protected TableView<BLI> tableView;
    protected TableView<CLI> conflictTableView;

    protected final ObservableList<BLI> listItems = FXCollections.observableArrayList();
    private final SortedList<BLI> sortedList = new SortedList<>(listItems);
    private final ObservableList<CLI> conflictListItems = FXCollections.observableArrayList();
    private final SortedList<CLI> sortedConflictList = new SortedList<>(conflictListItems);

    protected int gridRow = 0;
    private Subscription selectedItemSubscription;
    protected final BooleanProperty isInConflictWithNonSeedNode = new SimpleBooleanProperty();
    protected final BooleanProperty isInConflictWithSeedNode = new SimpleBooleanProperty();
    protected final BooleanProperty isDaoStateBlockChainNotConnecting = new SimpleBooleanProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected StateMonitorView(DaoStateService daoStateService,
                               DaoFacade daoFacade,
                               CycleService cycleService,
                               PeriodService periodService,
                               SeedNodeRepository seedNodeRepository,
                               File storageDir) {
        this.daoStateService = daoStateService;
        this.daoFacade = daoFacade;
        this.cycleService = cycleService;
        this.periodService = periodService;
        this.seedNodeAddresses = new HashSet<>(seedNodeRepository.getSeedNodeAddresses());
        this.storageDir = storageDir;
    }

    @Override
    public void initialize() {
        createTableView();
        createDetailsView();
    }

    @Override
    protected void activate() {
        selectedItemSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectItem);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        sortedConflictList.comparatorProperty().bind(conflictTableView.comparatorProperty());

        daoStateService.addDaoStateListener(this);

        resyncButton.visibleProperty().bind(isInConflictWithSeedNode
                .or(isDaoStateBlockChainNotConnecting));
        resyncButton.managedProperty().bind(isInConflictWithSeedNode
                .or(isDaoStateBlockChainNotConnecting));

        resyncButton.setOnAction(ev -> resyncDaoState());

        if (daoStateService.isParseBlockChainComplete()) {
            onDataUpdate();
        }

        GUIUtil.setFitToRowsForTableView(tableView, 25, 28, 2, 5);
        GUIUtil.setFitToRowsForTableView(conflictTableView, 38, 28, 2, 4);
    }

    @Override
    protected void deactivate() {
        selectedItemSubscription.unsubscribe();

        sortedList.comparatorProperty().unbind();
        sortedConflictList.comparatorProperty().unbind();

        daoStateService.removeDaoStateListener(this);

        resyncButton.visibleProperty().unbind();
        resyncButton.managedProperty().unbind();

        resyncButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract BLI getStateBlockListItem(StB e);

    protected abstract CLI getStateInConflictListItem(Map.Entry<String, StH> mapEntry);

    protected abstract void requestHashesFromGenesisBlockHeight(String peerAddress);

    protected abstract String getConflictsTableHeader();

    protected abstract String getPeersTableHeader();

    protected abstract String getHashTableHeader();

    protected abstract String getBlockHeightTableHeader();

    protected abstract String getRequestHashes();

    protected abstract String getTableHeadLine();

    protected abstract String getConflictTableHeadLine();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        onDataUpdate();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create table views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTableView() {
        TableGroupHeadline headline = new TableGroupHeadline(getTableHeadLine());
        GridPane.setRowIndex(headline, ++gridRow);
        GridPane.setMargin(headline, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        root.getChildren().add(headline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefHeight(100);

        createColumns();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        GridPane.setVgrow(tableView, Priority.SOMETIMES);
        GridPane.setMargin(tableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, -25, -10));
        root.getChildren().add(tableView);

        tableView.setItems(sortedList);
    }

    private void createDetailsView() {
        TableGroupHeadline conflictTableHeadline = new TableGroupHeadline(getConflictTableHeadLine());
        GridPane.setRowIndex(conflictTableHeadline, ++gridRow);
        GridPane.setMargin(conflictTableHeadline, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        root.getChildren().add(conflictTableHeadline);

        conflictTableView = new TableView<>();
        conflictTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        conflictTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        conflictTableView.setPrefHeight(100);

        createConflictColumns();
        GridPane.setRowIndex(conflictTableView, gridRow);
        GridPane.setHgrow(conflictTableView, Priority.ALWAYS);
        GridPane.setVgrow(conflictTableView, Priority.SOMETIMES);
        GridPane.setMargin(conflictTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, 5, -10));
        root.getChildren().add(conflictTableView);

        conflictTableView.setItems(sortedConflictList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSelectItem(BLI item) {
        if (item != null) {
            conflictListItems.setAll(item.getStateBlock().getInConflictMap().entrySet().stream()
                    .map(this::getStateInConflictListItem).collect(Collectors.toList()));
            GUIUtil.setFitToRowsForTableView(conflictTableView, 38, 28, 2, 4);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onDataUpdate() {
        if (isInConflictWithSeedNode.get()) {
            String msg = Res.get("dao.monitor.isInConflictWithSeedNode");
            log.warn(msg);
            statusTextField.setText(msg);
            statusTextField.getStyleClass().add("dao-inConflict");
        } else if (isInConflictWithNonSeedNode.get()) {
            statusTextField.setText(Res.get("dao.monitor.isInConflictWithNonSeedNode"));
            statusTextField.getStyleClass().remove("dao-inConflict");
        } else if (isDaoStateBlockChainNotConnecting.get()) {
            statusTextField.setText(Res.get("dao.monitor.isDaoStateBlockChainNotConnecting"));
            statusTextField.getStyleClass().add("dao-inConflict");
        } else {
            statusTextField.setText(Res.get("dao.monitor.daoStateInSync"));
            statusTextField.getStyleClass().remove("dao-inConflict");
        }

        GUIUtil.setFitToRowsForTableView(tableView, 25, 28, 2, 5);
    }

    private void resyncDaoState() {
        try {
            daoFacade.resyncDaoStateFromResources(storageDir);
            new Popup().attention(Res.get("setting.preferences.dao.resyncFromResources.popup"))
                    .useShutDownButton()
                    .hideCloseButton()
                    .show();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.toString());
            new Popup().error(t.toString()).show();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createColumns() {
        TableColumn<BLI, BLI> column;

        column = new AutoTooltipTableColumn<>(getBlockHeightTableHeader());
        column.setMinWidth(120);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BLI, BLI> call(
                            TableColumn<BLI, BLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final BLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHeight());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateBlock().getHeight()));
        column.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(column);
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(getHashTableHeader());
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BLI, BLI> call(
                            TableColumn<BLI, BLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final BLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(BLI::getHash));
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(getPeersTableHeader());
        column.setMinWidth(80);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BLI, BLI> call(
                            TableColumn<BLI, BLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final BLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumNetworkMessages());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateBlock().getPeersMap().size()));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(getConflictsTableHeader());
        column.setMinWidth(80);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BLI, BLI> call(
                            TableColumn<BLI, BLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final BLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumMisMatches());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateBlock().getInConflictMap().size()));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>("");
        column.setMinWidth(40);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BLI, BLI> call(
                            TableColumn<BLI, BLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final BLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    Label icon;
                                    if (!item.getStateBlock().getPeersMap().isEmpty()) {
                                        if (item.isInSync()) {
                                            icon = FormBuilder.getIcon(AwesomeIcon.OK_CIRCLE);
                                            icon.getStyleClass().addAll("icon", "dao-inSync");
                                        } else {
                                            icon = FormBuilder.getIcon(AwesomeIcon.REMOVE_CIRCLE);
                                            icon.getStyleClass().addAll("icon", "dao-inConflict");
                                        }
                                        setGraphic(icon);
                                    } else {
                                        setGraphic(null);
                                    }
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        column.setSortable(false);
        tableView.getColumns().add(column);
    }


    protected void createConflictColumns() {
        TableColumn<CLI, CLI> column;

        column = new AutoTooltipTableColumn<>(getBlockHeightTableHeader());
        column.setMinWidth(120);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CLI, CLI> call(
                            TableColumn<CLI, CLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHeight());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateHash().getHeight()));
        column.setSortType(TableColumn.SortType.DESCENDING);
        conflictTableView.getColumns().add(column);
        conflictTableView.getSortOrder().add(column);


        column = new AutoTooltipTableColumn<>(getPeersTableHeader());
        column.setMinWidth(150);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CLI, CLI> call(
                            TableColumn<CLI, CLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getPeerAddressString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CLI::getPeerAddress));
        conflictTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(getHashTableHeader());
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CLI, CLI> call(
                            TableColumn<CLI, CLI> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CLI::getHash));
        conflictTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>("");
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CLI, CLI> call(
                            TableColumn<CLI, CLI> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final CLI item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(getRequestHashes());
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> requestHashesFromGenesisBlockHeight(item.getPeerAddress()));
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
        column.setSortable(false);
        conflictTableView.getColumns().add(column);
    }
}
