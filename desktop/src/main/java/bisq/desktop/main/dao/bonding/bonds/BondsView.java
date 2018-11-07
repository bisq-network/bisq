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

package bisq.desktop.main.dao.bonding.bonds;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.reputation.BondedReputation;
import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class BondsView extends ActivatableView<GridPane, Void> {
    private TableView<BondListItem> tableView;

    private final BsqFormatter bsqFormatter;
    private final BondedRolesRepository bondedRolesRepository;
    private final BondedReputationRepository bondedReputationRepository;
    private final Preferences preferences;

    private int gridRow = 0;

    private final ObservableList<BondListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<BondListItem> sortedList = new SortedList<>(observableList);

    private ListChangeListener<BondedRole> bondedRolesListener;
    private ListChangeListener<BondedReputation> bondedReputationListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BondsView(BsqFormatter bsqFormatter,
                      BondedRolesRepository bondedRolesRepository,
                      BondedReputationRepository bondedReputationRepository,
                      Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.bondedRolesRepository = bondedRolesRepository;
        this.bondedReputationRepository = bondedReputationRepository;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        tableView = FormBuilder.addTableViewWithHeader(root, ++gridRow, Res.get("dao.bonding.bonds.table.header"));
        tableView.setItems(sortedList);
        addColumns();

        bondedReputationListener = c -> updateList();
        bondedRolesListener = c -> updateList();
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        bondedReputationRepository.getBonds().addListener(bondedReputationListener);
        bondedRolesRepository.getBonds().addListener(bondedRolesListener);
        updateList();
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        bondedReputationRepository.getBonds().removeListener(bondedReputationListener);
        bondedRolesRepository.getBonds().removeListener(bondedRolesListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        List<Bond> combined = new ArrayList<>(bondedReputationRepository.getBonds());
        combined.addAll(bondedRolesRepository.getBonds());
        observableList.setAll(combined.stream()
                .map(bond -> {
                    return new BondListItem(bond, bsqFormatter);
                })
                .sorted(Comparator.comparing(BondListItem::getLockupDateString).reversed())
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 37, 28, 2, 30);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addColumns() {
        TableColumn<BondListItem, BondListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem, BondListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BondListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmount());
                        } else
                            setText("");
                    }
                };
            }
        });

        tableView.getColumns().add(column);
        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.lockTime"));
        column.setMinWidth(40);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem, BondListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BondListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getLockTime());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.bondState"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem,
                            BondListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final BondListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(item.getBondStateString());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.bondType"));
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem, BondListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final BondListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getBondType());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.details"));
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem, BondListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final BondListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getBondDetails());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.lockupDate"));
        column.setMinWidth(140);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem, BondListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BondListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getLockupDateString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bonding.bonds.table.lockupTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem,
                            BondListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final BondListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String lockupTxId = item.getLockupTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(lockupTxId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(lockupTxId, preferences));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", lockupTxId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }
}
