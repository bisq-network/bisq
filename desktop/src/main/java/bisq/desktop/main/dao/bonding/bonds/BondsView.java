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
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InfoAutoTooltipLabel;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.reputation.BondedReputation;
import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

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

    private Bond selectedBond;

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
        tableView = FormBuilder.addTableViewWithHeader(root, ++gridRow, Res.get("dao.bond.allBonds.header"), "last");
        tableView.setItems(sortedList);
        GridPane.setVgrow(tableView, Priority.ALWAYS);
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
        GUIUtil.setFitToRowsForTableView(tableView, 37, 28, 2, 30);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        bondedReputationRepository.getBonds().removeListener(bondedReputationListener);
        bondedRolesRepository.getBonds().removeListener(bondedRolesListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSelectedBond(Bond bond) {
        // Set the selected bond if it's found in the tableView, which listens to sortedList.
        // If this is called before the sortedList has been populated the selected bond is stored and
        // we try to apply again after the next update.
        tableView.getItems().stream()
                .filter(item -> item.getBond() == bond)
                .findFirst()
                .ifPresentOrElse(item -> tableView.getSelectionModel().select(item),
                        () -> this.selectedBond = bond);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        List<Bond> combined = new ArrayList<>(bondedReputationRepository.getBonds());
        combined.addAll(bondedRolesRepository.getBonds());
        observableList.setAll(combined.stream()
                .map(bond -> new BondListItem(bond, bsqFormatter))
                .sorted(Comparator.comparing(BondListItem::getLockupDateString).reversed())
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 37, 28, 2, 30);
        if (selectedBond != null) {
            Bond bond = selectedBond;
            selectedBond = null;
            setSelectedBond(bond);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addColumns() {
        TableColumn<BondListItem, BondListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(80);
        column.getStyleClass().add("first-column");
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
        column.setComparator(Comparator.comparing(e -> e.getBond().getAmount()));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockTime"));
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
        column.setComparator(Comparator.comparing(e -> e.getBond().getLockTime()));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondState"));
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
        column.setComparator(Comparator.comparing(BondListItem::getBondStateString));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondType"));
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
        column.setComparator(Comparator.comparing(BondListItem::getBondType));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.details"));
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BondListItem, BondListItem> call(TableColumn<BondListItem, BondListItem> column) {
                return new TableCell<>() {
                    private InfoAutoTooltipLabel infoTextField;

                    @Override
                    public void updateItem(final BondListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            String info = Res.get("shared.id") + ": " + item.getBond().getBondedAsset().getUid();

                            if (item.getBond() instanceof BondedRole) {
                                info = item.getBondDetails() + "\n" + info;
                            }

                            infoTextField = new InfoAutoTooltipLabel(item.getBondDetails(),
                                    AwesomeIcon.INFO_SIGN,
                                    ContentDisplay.LEFT,
                                    info,
                                    350
                            );
                            setGraphic(infoTextField);
                        } else
                            setGraphic(null);
                    }
                };
            }
        });
        column.setComparator(Comparator.comparing(BondListItem::getBondDetails));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockupDate"));
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
        column.setComparator(Comparator.comparing(e -> e.getBond().getLockupDate()));
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockupTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.getStyleClass().add("last-column");
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
                                    hyperlinkWithIcon = new ExternalHyperlink(lockupTxId);
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(lockupTxId, preferences));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", lockupTxId)));
                                    if (item.getLockupDateString().equals("-")) hyperlinkWithIcon.hideIcon();
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
        column.setComparator(Comparator.comparing(BondListItem::getLockupTxId));
        tableView.getColumns().add(column);
    }
}
