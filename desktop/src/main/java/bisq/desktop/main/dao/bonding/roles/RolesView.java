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

package bisq.desktop.main.dao.bonding.roles;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
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

import java.util.Comparator;
import java.util.stream.Collectors;

@FxmlView
public class RolesView extends ActivatableView<GridPane, Void> {
    private TableView<RolesListItem> tableView;

    private final BondingViewUtils bondingViewUtils;
    private final BsqFormatter bsqFormatter;
    private final DaoFacade daoFacade;
    private final Preferences preferences;

    private final ObservableList<RolesListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<RolesListItem> sortedList = new SortedList<>(observableList);

    private ListChangeListener<BondedRole> bondedRoleListChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RolesView(BsqFormatter bsqFormatter,
                      BondingViewUtils bondingViewUtils,
                      DaoFacade daoFacade,
                      Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.bondingViewUtils = bondingViewUtils;
        this.daoFacade = daoFacade;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        int gridRow = 0;
        tableView = FormBuilder.addTableViewWithHeader(root, gridRow, Res.get("dao.bond.bondedRoles"), "last");
        createColumns();
        tableView.setItems(sortedList);
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        bondedRoleListChangeListener = c -> updateList();
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        daoFacade.getBondedRoles().addListener(bondedRoleListChangeListener);
        updateList();
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 30);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        daoFacade.getBondedRoles().removeListener(bondedRoleListChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        observableList.setAll(daoFacade.getAcceptedBondedRoles().stream()
                .map(bond -> new RolesListItem(bond, daoFacade))
                .sorted(Comparator.comparing(RolesListItem::getLockupDate).reversed())
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 30);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<RolesListItem, RolesListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.name"));
        nameColumn.setComparator(Comparator.comparing(RolesListItem::getName, String.CASE_INSENSITIVE_ORDER));
        nameColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setMinWidth(80);
        nameColumn.getStyleClass().add("first-column");
        nameColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem, RolesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getName());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(nameColumn);

        TableColumn<RolesListItem, RolesListItem> linkColumn = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.link"));
        linkColumn.setComparator(Comparator.comparing(RolesListItem::getLink));
        linkColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        linkColumn.setMinWidth(60);
        linkColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem, RolesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    String link = item.getLink();
                                    hyperlinkWithIcon = new ExternalHyperlink(link);
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openWebPage(link));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("shared.openURL", link)));
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
        tableView.getColumns().add(linkColumn);

        TableColumn<RolesListItem, RolesListItem> bondTypeColumn = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondType"));
        bondTypeColumn.setComparator(Comparator.comparing(RolesListItem::getTypeAsString));
        bondTypeColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        bondTypeColumn.setMinWidth(80);
        bondTypeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem, RolesListItem> column) {
                        return new TableCell<>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(item.getTypeAsString());
                                    hyperlink.setOnAction(event -> new RoleDetailsWindow(
                                            item.getType(),
                                            bondingViewUtils.getAcceptedBondedRoleProposal(item.getRole()),
                                            daoFacade,
                                            bsqFormatter
                                    ).show());
                                    hyperlink.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails", item.getTypeAsString())));
                                    setGraphic(hyperlink);
                                } else {
                                    setGraphic(null);
                                    if (hyperlink != null)
                                        hyperlink.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(bondTypeColumn);

        TableColumn<RolesListItem, RolesListItem> lockupTxIdColumn = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockupTxId"));
        lockupTxIdColumn.setComparator(Comparator.comparing(RolesListItem::getLockupTxId, Comparator.nullsFirst(Comparator.naturalOrder())));
        lockupTxIdColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        lockupTxIdColumn.setMinWidth(80);
        lockupTxIdColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem, RolesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;
                            private Label label;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String lockupTxId = item.getLockupTxId();
                                    if (lockupTxId != null) {
                                        hyperlinkWithIcon = new ExternalHyperlink(lockupTxId);
                                        hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(lockupTxId, preferences));
                                        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", lockupTxId)));
                                        setGraphic(hyperlinkWithIcon);
                                    } else {
                                        label = new Label("-");
                                        setGraphic(label);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                    if (label != null)
                                        label = null;
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(lockupTxIdColumn);

        TableColumn<RolesListItem, RolesListItem> bondStateColumn = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondState"));
        bondStateColumn.setComparator(Comparator.comparing(RolesListItem::getBondStateAsString));
        bondStateColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        bondStateColumn.setMinWidth(120);
        bondStateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem, RolesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getBondStateAsString());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(bondStateColumn);

        TableColumn<RolesListItem, RolesListItem> actionColumn = new TableColumn<>();
        actionColumn.setComparator(Comparator.comparing(RolesListItem::isLockupButtonVisible).thenComparing(RolesListItem::isRevokeButtonVisible));
        actionColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        actionColumn.setMinWidth(80);
        actionColumn.getStyleClass().add("last-column");
        actionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem, RolesListItem> column) {
                        return new TableCell<>() {
                            AutoTooltipButton button;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty && (item.isLockupButtonVisible() || item.isRevokeButtonVisible())) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(
                                                item.isLockupButtonVisible()
                                                        ? Res.get("dao.bond.table.button.lockup")
                                                        : Res.get("dao.bond.table.button.revoke")
                                        );
                                        button.setMinWidth(70);
                                        button.setOnAction(e -> {
                                            if (item.isLockupButtonVisible() ) {
                                                bondingViewUtils.lockupBondForBondedRole(item.getRole(), txId -> {});
                                            } else if (item.isRevokeButtonVisible()) {
                                                bondingViewUtils.unLock(item.getLockupTxId(), txId -> {});
                                            }
                                        });
                                        setGraphic(button);
                                    }
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
        tableView.getColumns().add(actionColumn);
    }
}
