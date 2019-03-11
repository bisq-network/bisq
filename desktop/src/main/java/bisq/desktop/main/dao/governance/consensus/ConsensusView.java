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

package bisq.desktop.main.dao.governance.consensus;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.monitoring.DaoStateMonitoringService;
import bisq.core.locale.Res;

import javax.inject.Inject;

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

import java.util.Comparator;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ConsensusView extends ActivatableView<GridPane, Void> implements DaoStateListener, DaoStateMonitoringService.Listener {
    private final DaoStateService daoStateService;
    private final DaoFacade daoFacade;
    private final DaoStateMonitoringService daoStateMonitoringService;

    private TextField statusTextField;
    private Button resyncButton;
    private TableView<DaoStateBlockListItem> tableView;
    private TableView<DaoStateInConflictListItem> conflictTableView;

    private final ObservableList<DaoStateBlockListItem> listItems = FXCollections.observableArrayList();
    private final SortedList<DaoStateBlockListItem> sortedList = new SortedList<>(listItems);
    private final ObservableList<DaoStateInConflictListItem> daoStateInConflictListItems = FXCollections.observableArrayList();
    private final SortedList<DaoStateInConflictListItem> sortedConflictList = new SortedList<>(daoStateInConflictListItems);

    private int gridRow = 0;
    private Subscription selectedItemSubscription;
    private BooleanProperty isInConflict = new SimpleBooleanProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ConsensusView(DaoStateService daoStateService,
                          DaoFacade daoFacade,
                          DaoStateMonitoringService daoStateMonitoringService) {
        this.daoStateService = daoStateService;
        this.daoFacade = daoFacade;
        this.daoStateMonitoringService = daoStateMonitoringService;
    }

    @Override
    public void initialize() {
        super.initialize();

        addTitledGroupBg(root, gridRow, 3, Res.get("dao.governance.consensus.headline"));

        statusTextField = FormBuilder.addTopLabelTextField(root, ++gridRow,
                Res.get("dao.governance.consensus.state")).second;
        resyncButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.governance.consensus.resync"), 10);

        createTableView();
        createDetailsView();
    }

    @Override
    protected void activate() {
        selectedItemSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectItem);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        sortedConflictList.comparatorProperty().bind(conflictTableView.comparatorProperty());

        daoStateService.addDaoStateListener(this);
        daoStateMonitoringService.addListener(this);

        resyncButton.visibleProperty().bind(isInConflict);
        resyncButton.managedProperty().bind(isInConflict);

        resyncButton.setOnAction(e -> daoFacade.resyncDao(() ->
                new Popup<>().attention(Res.get("setting.preferences.dao.resync.popup"))
                        .useShutDownButton()
                        .hideCloseButton()
                        .show())
        );

        onDataUpdate();
    }

    @Override
    protected void deactivate() {
        selectedItemSubscription.unsubscribe();

        sortedList.comparatorProperty().unbind();
        sortedConflictList.comparatorProperty().unbind();

        daoStateService.removeDaoStateListener(this);
        daoStateMonitoringService.removeListener(this);

        resyncButton.visibleProperty().unbind();
        resyncButton.managedProperty().unbind();

        resyncButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        onDataUpdate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateMonitoringService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDaoStateBlockchainChanged() {
        if (daoStateService.isParseBlockChainComplete()) {
            onDataUpdate();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create table views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTableView() {
        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.governance.consensus.table.headline"));
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
        GridPane.setMargin(tableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, -25, -10));
        root.getChildren().add(tableView);

        tableView.setItems(sortedList);
    }

    private void createDetailsView() {
        TableGroupHeadline conflictTableHeadline = new TableGroupHeadline(Res.get("dao.governance.consensus.conflictTable.headline"));
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
        GridPane.setMargin(conflictTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, -25, -10));
        root.getChildren().add(conflictTableView);

        conflictTableView.setItems(sortedConflictList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSelectItem(DaoStateBlockListItem item) {
        if (item != null) {
            daoStateInConflictListItems.setAll(item.getDaoStateBlock().getInConflictMap().entrySet().stream()
                    .map(e -> new DaoStateInConflictListItem(e.getKey(), e.getValue())).collect(Collectors.toList()));
            GUIUtil.setFitToRowsForTableView(conflictTableView, 25, 28, 2, 4);
            conflictTableView.layout();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onDataUpdate() {
        isInConflict.set(daoStateMonitoringService.isInConflict());
        if (isInConflict.get()) {
            statusTextField.setText(Res.get("dao.governance.consensus.daoStateNotInSync"));
            statusTextField.getStyleClass().add("dao-inConflict");
        } else {
            statusTextField.setText(Res.get("dao.governance.consensus.daoStateInSync"));
            statusTextField.getStyleClass().remove("dao-inConflict");
        }

        listItems.setAll(daoStateMonitoringService.getDaoStateBlockchain().stream()
                .map(DaoStateBlockListItem::new)
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 25, 28, 2, 5);
        tableView.layout();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<DaoStateBlockListItem, DaoStateBlockListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.blockHeight"));
        column.setMinWidth(110);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateBlockListItem, DaoStateBlockListItem> call(
                            TableColumn<DaoStateBlockListItem, DaoStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHeight());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateBlockListItem::getHeight));
        column.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(column);
        tableView.getSortOrder().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.hash"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateBlockListItem, DaoStateBlockListItem> call(
                            TableColumn<DaoStateBlockListItem, DaoStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateBlockListItem::getHash));
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.prev"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<DaoStateBlockListItem, DaoStateBlockListItem> call(TableColumn<DaoStateBlockListItem,
                            DaoStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getPrevHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateBlockListItem::getPrevHash));
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.numPeer"));
        column.setMinWidth(80);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateBlockListItem, DaoStateBlockListItem> call(
                            TableColumn<DaoStateBlockListItem, DaoStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumNetworkMessages());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateBlockListItem::getNumNetworkMessages));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.numMisMatches"));
        column.setMinWidth(80);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateBlockListItem, DaoStateBlockListItem> call(
                            TableColumn<DaoStateBlockListItem, DaoStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumMisMatches());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateBlockListItem::getNumMisMatches));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>("");
        column.setMinWidth(40);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateBlockListItem, DaoStateBlockListItem> call(
                            TableColumn<DaoStateBlockListItem, DaoStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    Label icon;
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
                            }
                        };
                    }
                });
        column.setSortable(false);
        tableView.getColumns().add(column);
    }

    private void createConflictColumns() {
        TableColumn<DaoStateInConflictListItem, DaoStateInConflictListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.blockHeight"));
        column.setMinWidth(80);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateInConflictListItem, DaoStateInConflictListItem> call(
                            TableColumn<DaoStateInConflictListItem, DaoStateInConflictListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getBlockHeight());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateInConflictListItem::getBlockHeight));
        column.setSortType(TableColumn.SortType.DESCENDING);
        conflictTableView.getColumns().add(column);
        conflictTableView.getSortOrder().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.conflictTable.peer"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateInConflictListItem, DaoStateInConflictListItem> call(
                            TableColumn<DaoStateInConflictListItem, DaoStateInConflictListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getPeerAddress());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        // column.setComparator(Comparator.comparing(DaoStateInConflictListItem::getPeerAddress));
        conflictTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.hash"));
        column.setMinWidth(150);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateInConflictListItem, DaoStateInConflictListItem> call(
                            TableColumn<DaoStateInConflictListItem, DaoStateInConflictListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateInConflictListItem::getHash));
        conflictTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.governance.consensus.table.prev"));
        column.setMinWidth(150);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateInConflictListItem, DaoStateInConflictListItem> call(
                            TableColumn<DaoStateInConflictListItem, DaoStateInConflictListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DaoStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getPrevHash());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(DaoStateInConflictListItem::getPrevHash));
        conflictTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>("");
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DaoStateInConflictListItem, DaoStateInConflictListItem> call(
                            TableColumn<DaoStateInConflictListItem, DaoStateInConflictListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final DaoStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("dao.governance.consensus.conflictTable.request"));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> daoStateMonitoringService.requestHashesFromGenesisBlockHeight(item.getPeerAddress()));
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
