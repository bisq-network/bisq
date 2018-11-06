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
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.role.BondedRolesService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BondsView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, DaoStateListener {
    private TableView<BondListItem> tableView;

    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BsqValidator bsqValidator;
    private final BondingViewUtils bondingViewUtils;
    private final BondedRolesService bondedRolesService;
    private final DaoFacade daoFacade;
    private final Preferences preferences;

    private int gridRow = 0;

    private final ObservableList<BondListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<BondListItem> sortedList = new SortedList<>(observableList);

    private ListChangeListener<Transaction> walletBsqTransactionsListener;
    private ChangeListener<Number> walletChainHeightListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BondsView(BsqWalletService bsqWalletService,
                      BsqFormatter bsqFormatter,
                      BsqValidator bsqValidator,
                      BondingViewUtils bondingViewUtils,
                      BondedRolesService bondedRolesService,
                      DaoFacade daoFacade,
                      Preferences preferences) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.bsqValidator = bsqValidator;
        this.bondingViewUtils = bondingViewUtils;
        this.bondedRolesService = bondedRolesService;
        this.daoFacade = daoFacade;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 2, Res.get("dao.bonding.bonds.table.header"));

        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setMargin(tableView, new Insets(30, -10, 5, -10));
        root.getChildren().add(tableView);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        addColumns();

        walletBsqTransactionsListener = change -> updateList();
        walletChainHeightListener = (observable, oldValue, newValue) -> updateList();
    }

    @Override
    protected void activate() {
        bsqWalletService.addBsqBalanceListener(this);
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());

        bsqWalletService.getWalletTransactions().addListener(walletBsqTransactionsListener);
        bsqWalletService.addBsqBalanceListener(this);
        bsqWalletService.getChainHeightProperty().addListener(walletChainHeightListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        daoFacade.addBsqStateListener(this);

        updateList();
    }

    @Override
    protected void deactivate() {
        bsqWalletService.removeBsqBalanceListener(this);

        bsqWalletService.getWalletTransactions().removeListener(walletBsqTransactionsListener);
        bsqWalletService.removeBsqBalanceListener(this);
        bsqWalletService.getChainHeightProperty().removeListener(walletChainHeightListener);
        daoFacade.removeBsqStateListener(this);

        sortedList.comparatorProperty().unbind();

        observableList.forEach(BondListItem::cleanup);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        bsqValidator.setAvailableBalance(confirmedBalance);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
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

    private void openTxInBlockExplorer(BondListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + item.getTxId());
    }

    private void updateList() {
        List<BondListItem> items = daoFacade.getAllBonds().stream()
                .map(bond -> {
                    return new BondListItem(bond,
                            daoFacade,
                            bondedRolesService,
                            bondingViewUtils,
                            bsqFormatter);
                })
                .sorted(Comparator.comparing(BondListItem::getLockupDate).reversed())
                .collect(Collectors.toList());
        observableList.setAll(items);
        GUIUtil.setFitToRowsForTableView(tableView, 37, 28, 2, 10);
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
                            Label label;

                            @Override
                            public void updateItem(final BondListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (label == null) {
                                        label = item.getStateLabel();
                                        setGraphic(label);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (label != null)
                                        label = null;
                                }
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
                            setText(item.getLockupDate());
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

                                //noinspection Duplicates
                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
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
