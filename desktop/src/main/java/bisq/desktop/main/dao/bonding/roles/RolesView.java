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
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.RoleProposal;
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
import java.util.Optional;
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
        TableColumn<RolesListItem, RolesListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.name"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.getStyleClass().add("first-column");
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem,
                            RolesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getRole().getName());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.link"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem,
                            RolesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    String link = item.getRole().getLink();
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
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondType"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem,
                            RolesListItem> column) {
                        return new TableCell<>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    BondedRoleType bondedRoleType = item.getRole().getBondedRoleType();
                                    String type = bondedRoleType.getDisplayString();
                                    hyperlink = new Hyperlink(type);
                                    hyperlink.setOnAction(event -> {
                                        Optional<RoleProposal> roleProposal = bondingViewUtils.getAcceptedBondedRoleProposal(item.getRole());
                                        new RoleDetailsWindow(bondedRoleType, roleProposal, daoFacade, bsqFormatter).show();
                                    });
                                    hyperlink.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails", type)));
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
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockupTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem,
                            RolesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;
                            private Label label;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getBondedRole().getLockupTxId();
                                    if (transactionId != null) {
                                        hyperlinkWithIcon = new ExternalHyperlink(transactionId);
                                        hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(transactionId, preferences));
                                        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
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
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondState"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(120);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem,
                            RolesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
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

        column = new TableColumn<>();
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.getStyleClass().add("last-column");
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<RolesListItem, RolesListItem> call(TableColumn<RolesListItem,
                            RolesListItem> column) {
                        return new TableCell<>() {
                            AutoTooltipButton button;

                            @Override
                            public void updateItem(final RolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty && item.isButtonVisible()) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(item.getButtonText());
                                        button.setMinWidth(70);
                                        button.setOnAction(e -> {
                                            if (item.getBondState() == BondState.READY_FOR_LOCKUP) {
                                                bondingViewUtils.lockupBondForBondedRole(item.getRole(),
                                                        txId -> {
                                                        });
                                            } else if (item.getBondState() == BondState.LOCKUP_TX_CONFIRMED) {
                                                bondingViewUtils.unLock(item.getLockupTxId(),
                                                        txId -> {
                                                        });
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
        tableView.getColumns().add(column);
    }
}
