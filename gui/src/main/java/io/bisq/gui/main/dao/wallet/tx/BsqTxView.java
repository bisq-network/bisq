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
import io.bisq.core.dao.blockchain.BsqChainStateListener;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.core.user.Preferences;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.AddressWithIconAndDirection;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.dao.wallet.BsqBalanceUtil;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.GUIUtil;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@FxmlView
public class BsqTxView extends ActivatableView<GridPane, Void> {

    TableView<BsqTxListItem> tableView;
    private Pane rootParent;

    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final BsqBlockchainManager bsqBlockchainManager;
    private final BsqChainState bsqChainState;
    private final BtcWalletService btcWalletService;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final Preferences preferences;
    private final ObservableList<BsqTxListItem> observableList = FXCollections.observableArrayList();
    // Need to be DoubleProperty as we pass it as reference
    private final DoubleProperty initialOccupiedHeight = new SimpleDoubleProperty(-1);
    private final SortedList<BsqTxListItem> sortedList = new SortedList<>(observableList);
    private ChangeListener<Number> parentHeightListener;
    private ListChangeListener<Transaction> walletBsqTransactionsListener;
    private int gridRow = 0;
    private Label chainHeightLabel;
    private BsqChainStateListener bsqChainStateListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqTxView(BsqFormatter bsqFormatter, BsqWalletService bsqWalletService,
                      BsqBlockchainManager bsqBlockchainManager,
                      Preferences preferences,
                      BsqChainState bsqChainState,
                      BtcWalletService btcWalletService, BsqBalanceUtil bsqBalanceUtil) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockchainManager = bsqBlockchainManager;
        this.preferences = preferences;
        this.bsqChainState = bsqChainState;
        this.btcWalletService = btcWalletService;
        this.bsqBalanceUtil = bsqBalanceUtil;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        addDateColumn();
        addTxIdColumn();
        addAddressColumn();
        addAmountColumn();
        addConfidenceColumn();

        chainHeightLabel = FormBuilder.addLabel(root, ++gridRow, "");
        chainHeightLabel.setId("num-offers");
        chainHeightLabel.setPadding(new Insets(-5, 0, -10, 5));

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        GridPane.setRowIndex(vBox, ++gridRow);
        GridPane.setColumnSpan(vBox, 2);
        GridPane.setMargin(vBox, new Insets(40, -10, 5, -10));
        vBox.getChildren().addAll(tableView, chainHeightLabel);
        root.getChildren().add(vBox);

        walletBsqTransactionsListener = change -> updateList();
        parentHeightListener = (observable, oldValue, newValue) -> layout();
        bsqChainStateListener = this::onChainHeightChanged;
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();
        bsqWalletService.getWalletTransactions().addListener(walletBsqTransactionsListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        bsqBlockchainManager.addBsqChainStateListener(bsqChainStateListener);
        if (root.getParent() instanceof Pane) {
            rootParent = (Pane) root.getParent();
            rootParent.heightProperty().addListener(parentHeightListener);
        }

        updateList();
        onChainHeightChanged();
        layout();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        sortedList.comparatorProperty().unbind();
        bsqWalletService.getWalletTransactions().removeListener(walletBsqTransactionsListener);
        observableList.forEach(BsqTxListItem::cleanup);
        bsqBlockchainManager.removeBsqChainStateListener(bsqChainStateListener);

        if (rootParent != null)
            rootParent.heightProperty().removeListener(parentHeightListener);
    }

    private void onChainHeightChanged() {
        chainHeightLabel.setText(Res.get("dao.wallet.chainHeight",
                bsqChainState.getChainHeadHeight(),
                bsqWalletService.getBestChainHeight()));
    }

    private void updateList() {
        observableList.forEach(BsqTxListItem::cleanup);

        // clone to avoid ConcurrentModificationException
        final List<Transaction> walletTransactions = new ArrayList<>(bsqWalletService.getWalletTransactions());
        Set<BsqTxListItem> list = walletTransactions.stream()
                .map(transaction -> {
                            // The burned fee is added to all outputs of a tx, so we just ask at index 0
                            return new BsqTxListItem(transaction,
                                    bsqWalletService,
                                    btcWalletService,
                                    bsqChainState.hasTxBurntFee(transaction.getHashAsString()),
                                    bsqFormatter);
                        }
                )
                .collect(Collectors.toSet());
        observableList.setAll(list);

        final Set<Transaction> invalidBsqTransactions = bsqWalletService.getInvalidBsqTransactions();
        if (!invalidBsqTransactions.isEmpty() && bsqBlockchainManager.isParseBlockchainComplete()) {
            Set<String> txIds = invalidBsqTransactions.stream()
                    .filter(t -> t != null)
                    .map(Transaction::getHashAsString).collect(Collectors.toSet());

            log.error("invalidBsqTransactions " + txIds);
            String key = "invalidBsqTransactionsWarning_" + txIds;
            if (DontShowAgainLookup.showAgain(key))
                new Popup<>().warning("We detected invalid Bsq transactions.\n" +
                        "This must not happen if you used the bisq application only to send or receive BSQ.\n\n" +
                        "invalidBsqTransactionIds=" + txIds.toString())
                        .width(800)
                        .dontShowAgainId(key)
                        .show();
        }
    }

    private void layout() {
        GUIUtil.fillAvailableHeight(root, tableView, initialOccupiedHeight);
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
                                    if (item.isBurnedBsqTx() || item.getAmount().isZero()) {
                                        if (field != null)
                                            field.setOnAction(null);

                                        label = new Label(item.isBurnedBsqTx() ?
                                                Res.get("dao.wallet.bsqFee") : Res.get("funds.tx.direction.self"));
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
        TableColumn<BsqTxListItem, BsqTxListItem> column = new TableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(130);
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
                            setText(bsqFormatter.formatCoin(item.getAmount()));
                        } else {
                            setText("");
                        }
                    }
                };
            }
        });
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

