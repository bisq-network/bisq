/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.dao.wallet.tx;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.blockchain.Tx;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.core.user.Preferences;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.AddressWithIconAndDirection;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.dao.wallet.BalanceUtil;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.bisq.gui.util.FormBuilder.addLabelTextField;
import static io.bisq.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqTxView extends ActivatableView<GridPane, Void> {

    TableView<BsqTxListItem> tableView;
    private int gridRow = 0;
    private BsqFormatter bsqFormatter;
    private BsqWalletService bsqWalletService;
    private BsqBlockchainManager bsqBlockchainManager;
    private BtcWalletService btcWalletService;
    private BalanceUtil balanceUtil;
    private Preferences preferences;
    private ListChangeListener<Transaction> walletBsqTransactionsListener;
    private final ObservableList<BsqTxListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<BsqTxListItem> sortedList = new SortedList<>(observableList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqTxView(BsqFormatter bsqFormatter, BsqWalletService bsqWalletService,
                      BsqBlockchainManager bsqBlockchainManager,
                      BtcWalletService btcWalletService, BalanceUtil balanceUtil, Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockchainManager = bsqBlockchainManager;
        this.btcWalletService = btcWalletService;
        this.balanceUtil = balanceUtil;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, Res.get("shared.balance"));
        TextField balanceTextField = addLabelTextField(root, gridRow, Res.get("shared.bsqBalance"),
                Layout.FIRST_ROW_DISTANCE).second;
        balanceUtil.setBalanceTextField(balanceTextField);
        balanceUtil.initialize();

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(40, -10, 5, -10));
        root.getChildren().add(tableView);

        addDateColumn();
        addTxIdColumn();
        addAddressColumn();
        addAmountColumn();
        addConfidenceColumn();

        walletBsqTransactionsListener = change -> updateList();
    }

    @Override
    protected void activate() {
        balanceUtil.activate();
        bsqWalletService.getWalletBsqTransactions().addListener(walletBsqTransactionsListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        updateList();
    }

    @Override
    protected void deactivate() {
        balanceUtil.deactivate();
        sortedList.comparatorProperty().unbind();
        bsqWalletService.getWalletBsqTransactions().removeListener(walletBsqTransactionsListener);
        observableList.forEach(BsqTxListItem::cleanup);
    }

    private void updateList() {
        observableList.forEach(BsqTxListItem::cleanup);

        Map<String, Tx> burnedBSQTxIdMap = bsqBlockchainManager.getBsqTXOMap().getBurnedBSQTxMap();
        Set<BsqTxListItem> list = bsqWalletService.getWalletBsqTransactions().stream()
                .map(transaction -> new BsqTxListItem(transaction,
                        bsqWalletService,
                        btcWalletService,
                        burnedBSQTxIdMap.containsKey(transaction.getHashAsString())))
                .collect(Collectors.toSet());
        observableList.setAll(list);

        final Set<Transaction> invalidBsqTransactions = bsqWalletService.getInvalidBsqTransactions();
        if (!invalidBsqTransactions.isEmpty() && bsqBlockchainManager.isUtxoSyncWithChainHeadHeight()) {
            Set<String> txIds = invalidBsqTransactions.stream().map(t -> t.getHashAsString()).collect(Collectors.toSet());
            String key = "invalidBsqTransactionsWarning_" + txIds;
            if (DontShowAgainLookup.showAgain(key))
                new Popup().warning("We detected invalid Bsq transactions.\n" +
                        "This must not happen if you used the bisq application only to send or receive BSQ.\n\n" +
                        "invalidBsqTransactionIds=" + txIds.toString())
                        .width(800)
                        .dontShowAgainId(key)
                        .show();
        }
    }

    private void addDateColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new TableColumn<>(Res.get("shared.dateTime"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(180);
        column.setMaxWidth(180);
        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(bsqFormatter.formatDateTime(item.getDate()));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
        column.setComparator((o1, o2) -> o1.getDate().compareTo(o2.getDate()));
        column.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(column);
    }

    private void addTxIdColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new TableColumn<>(Res.get("shared.txId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

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

    private void addAddressColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new TableColumn<>(Res.get("shared.address"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(140);
        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {

                            private AddressWithIconAndDirection field;
                            private Label label;

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String addressString = item.getAddress();
                                    if (item.isBurnedBsqTx()) {
                                        if (field != null)
                                            field.setOnAction(null);

                                        label = new Label(addressString);
                                        setGraphic(label);
                                    } else {
                                        field = new AddressWithIconAndDirection(item.getDirection(), addressString,
                                                AwesomeIcon.EXTERNAL_LINK, item.isReceived());
                                        field.setOnAction(event -> openAddressInBlockExplorer(item));
                                        field.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", addressString)));
                                        setGraphic(field);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().add(column);
    }

    private void addAmountColumn() {
        TableColumn<BsqTxListItem, String> column = new TableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(bsqFormatter.formatCoin(item.getValue().getAmount())));
        column.setMinWidth(130);
        tableView.getColumns().add(column);
    }

    private void addConfidenceColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new TableColumn<>(Res.get("shared.confirmations"));
        column.setMinWidth(130);
        column.setMaxWidth(130);

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<TableColumn<BsqTxListItem, BsqTxListItem>,
                TableCell<BsqTxListItem, BsqTxListItem>>() {

            @Override
            public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                    BsqTxListItem> column) {
                return new TableCell<BsqTxListItem, BsqTxListItem>() {

                    @Override
                    public void updateItem(final BsqTxListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setGraphic(item.getTxConfidenceIndicator());
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        tableView.getColumns().add(column);
    }

    private void openTxInBlockExplorer(BsqTxListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + item.getTxId());
    }

    private void openAddressInBlockExplorer(BsqTxListItem item) {
        if (item.getAddress() != null) {
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().addressUrl + item.getAddress());
        }
    }

}

