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
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.role.BondedRoleType;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.stream.Collectors;

@FxmlView
public class BondedRolesView extends ActivatableView<GridPane, Void> implements BsqStateListener {
    private TableView<BondedRolesListItem> tableView;

    private final BsqFormatter bsqFormatter;
    private final DaoFacade daoFacade;
    private final Preferences preferences;

    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;

    private int gridRow = 0;

    private final ObservableList<BondedRolesListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<BondedRolesListItem> sortedList = new SortedList<>(observableList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BondedRolesView(BsqFormatter bsqFormatter,
                            BsqBalanceUtil bsqBalanceUtil,
                            DaoFacade daoFacade,
                            Preferences preferences,
                            WalletsSetup walletsSetup,
                            P2PService p2PService) {
        this.bsqFormatter = bsqFormatter;
        this.daoFacade = daoFacade;
        this.preferences = preferences;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
    }

    @Override
    public void initialize() {
        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.bond.table.header"));
        GridPane.setRowIndex(headline, gridRow);
        GridPane.setMargin(headline, new Insets(0, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        root.getChildren().add(headline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns();

        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(20, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        root.getChildren().add(tableView);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    @Override
    protected void activate() {
        daoFacade.addBsqStateListener(this);

        updateList();
    }

    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);

        observableList.forEach(e -> BondedRolesListItem.cleanup());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onEmptyBlockAdded(Block block) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        updateList();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void updateList() {
        observableList.forEach(e -> BondedRolesListItem.cleanup());
        observableList.setAll(daoFacade.getBondedRoleList().stream()
                .map(bondedRole -> new BondedRolesListItem(bondedRole, daoFacade, bsqFormatter))
                .collect(Collectors.toList()));
    }

    private void onButtonClick() {
        if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
            // revoke TODO
       /*     Optional<TxOutput> lockupTxOutput = daoFacade.getBondedRolesOutput(selectedItem.getTxId());
            if (!lockupTxOutput.isPresent()) {
                log.warn("Lockup output not found, txId = ", selectedItem.getTxId());
                return;
            }

            Coin unlockAmount = Coin.valueOf(lockupTxOutput.get().getValue());
            Optional<Integer> opLockTime = daoFacade.getLockTime(selectedItem.getTxId());
            int lockTime = opLockTime.orElse(-1);

            try {
                new Popup<>().headLine(Res.get("dao.bonding.unlock.sendTx.headline"))
                        .confirmation(Res.get("dao.bonding.unlock.sendTx.details",
                                bsqFormatter.formatCoinWithCode(unlockAmount),
                                lockTime
                        ))
                        .actionButtonText(Res.get("shared.yes"))
                        .onAction(() -> {
                            daoFacade.publishUnlockTx(selectedItem.getTxId(),
                                    () -> {
                                        new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                                    },
                                    errorMessage -> new Popup<>().warning(errorMessage.toString()).show()
                            );
                        })
                        .closeButtonText(Res.get("shared.cancel"))
                        .show();
            } catch (Throwable t) {
                log.error(t.toString());
                t.printStackTrace();
                new Popup<>().warning(t.getMessage()).show();
            }*/
        } else {
            GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
        }
        // log.info("unlock tx: {}", selectedItem.getTxId());
    }


    private void openTxInBlockExplorer(String transactionId) {
        if (transactionId != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + transactionId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<BondedRolesListItem, BondedRolesListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.name"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(120);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getBondedRole().getName());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.linkToAccount"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    String link = item.getBondedRole().getLinkToAccount();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(link, AwesomeIcon.EXTERNAL_LINK);
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.bondedRoleType"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(120);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    BondedRoleType bondedRoleType = item.getBondedRole().getBondedRoleType();
                                    String type = bondedRoleType.getDisplayString();
                                    hyperlink = new Hyperlink(type);
                                    hyperlink.setOnAction(event -> {
                                        new BondedRoleTypeWindow(bondedRoleType, bsqFormatter).show();
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.startDate"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getStartDate());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.revokeDate"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getRevokeDate());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.lockupTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;
                            private Label label;

                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getBondedRole().getLockupTxId();
                                    if (transactionId != null) {
                                        hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, AwesomeIcon.EXTERNAL_LINK);
                                        hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(transactionId));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.unlockTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {

                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;
                            private Label label;

                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getBondedRole().getUnlockTxId();
                                    if (transactionId != null) {
                                        hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, AwesomeIcon.EXTERNAL_LINK);
                                        hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(transactionId));
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

        column = new TableColumn<>();
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BondedRolesListItem, BondedRolesListItem>, TableCell<BondedRolesListItem,
                        BondedRolesListItem>>() {
                    @Override
                    public TableCell<BondedRolesListItem, BondedRolesListItem> call(TableColumn<BondedRolesListItem,
                            BondedRolesListItem> column) {
                        return new TableCell<BondedRolesListItem, BondedRolesListItem>() {
                            Button button;

                            @Override
                            public void updateItem(final BondedRolesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = item.getButton();
                                        button.setOnAction(e -> {
                                            onButtonClick();
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
