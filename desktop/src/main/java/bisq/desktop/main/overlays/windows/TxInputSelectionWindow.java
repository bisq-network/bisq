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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.BalanceTextField;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

public class TxInputSelectionWindow extends Overlay<TxInputSelectionWindow> {
    private static class TransactionOutputItem {
        @Getter
        private final TransactionOutput transactionOutput;
        @Getter
        @Setter
        private boolean isSelected;

        public TransactionOutputItem(TransactionOutput transactionOutput, boolean isSelected) {
            this.transactionOutput = transactionOutput;
            this.isSelected = isSelected;
        }
    }

    private final List<TransactionOutput> spendableTransactionOutputs;
    @Getter
    private final Set<TransactionOutput> candidates;
    private final Preferences preferences;
    private final CoinFormatter formatter;

    private BalanceTextField balanceTextField;
    private TableView<TransactionOutputItem> tableView;

    public TxInputSelectionWindow(List<TransactionOutput> spendableTransactionOutputs,
                                  Set<TransactionOutput> candidates,
                                  Preferences preferences,
                                  CoinFormatter formatter) {
        this.spendableTransactionOutputs = spendableTransactionOutputs;
        this.candidates = candidates;
        this.preferences = preferences;
        this.formatter = formatter;
        type = Type.Attention;
    }

    public void show() {
        rowIndex = 0;
        width = 900;
        if (headLine == null) {
            headLine = Res.get("inputControlWindow.headline");
        }
        createGridPane();
        gridPane.setHgap(15);
        addHeadLine();
        addContent();
        addButtons();
        addDontShowAgainCheckBox();
        applyStyles();
        display();
    }

    protected void addContent() {
        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        GridPane.setRowIndex(tableView, rowIndex++);
        GridPane.setMargin(tableView, new Insets(Layout.GROUP_DISTANCE, 0, 0, 0));
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        gridPane.getChildren().add(tableView);
        createColumns();
        ObservableList<TransactionOutputItem> items = FXCollections.observableArrayList(spendableTransactionOutputs.stream()
                .map(transactionOutput -> new TransactionOutputItem(transactionOutput, candidates.contains(transactionOutput)))
                .collect(Collectors.toList()));
        tableView.setItems(new SortedList<>(items));
        GUIUtil.setFitToRowsForTableView(tableView, 26, 28, 3, 15);

        balanceTextField = FormBuilder.addBalanceTextField(gridPane, rowIndex++, Res.get("inputControlWindow.balanceLabel"), Layout.FIRST_ROW_DISTANCE);
        balanceTextField.setFormatter(formatter);

        updateBalance();
    }

    private void updateBalance() {
        balanceTextField.setBalance(Coin.valueOf(candidates.stream()
                .mapToLong(transactionOutput -> transactionOutput.getValue().value)
                .sum()));
    }

    private void onChangeCheckBox(TransactionOutputItem transactionOutputItem) {
        if (transactionOutputItem.isSelected()) {
            candidates.add(transactionOutputItem.getTransactionOutput());
        } else {
            candidates.remove(transactionOutputItem.getTransactionOutput());
        }

        updateBalance();
    }

    private void createColumns() {
        TableColumn<TransactionOutputItem, TransactionOutputItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.select"));
        column.getStyleClass().add("first-column");
        column.setSortable(false);
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TransactionOutputItem, TransactionOutputItem> call(
                            TableColumn<TransactionOutputItem, TransactionOutputItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(TransactionOutputItem item, boolean empty) {
                                super.updateItem(item, empty);
                                final CheckBox checkBox = new AutoTooltipCheckBox();
                                if (item != null && !empty) {
                                    checkBox.setSelected(item.isSelected());
                                    checkBox.setOnAction(e -> {
                                        item.setSelected(checkBox.isSelected());
                                        onChangeCheckBox(item);
                                    });
                                    setGraphic(checkBox);
                                } else {
                                    if (checkBox != null) {
                                        checkBox.setOnAction(null);
                                    }
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("shared.balance"));
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TransactionOutputItem, TransactionOutputItem> call(
                            TableColumn<TransactionOutputItem, TransactionOutputItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(TransactionOutputItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(formatter.formatCoinWithCode(item.getTransactionOutput().getValue()));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("shared.utxo"));
        column.setSortable(false);
        column.setMinWidth(550);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TransactionOutputItem, TransactionOutputItem> call(
                            TableColumn<TransactionOutputItem, TransactionOutputItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(TransactionOutputItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    TransactionOutput transactionOutput = item.getTransactionOutput();
                                    String txId = transactionOutput.getParentTransaction().getTxId().toString();
                                    hyperlinkWithIcon = new ExternalHyperlink(txId + ":" + transactionOutput.getIndex());
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + txId, false));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", txId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    if (hyperlinkWithIcon != null) {
                                        hyperlinkWithIcon.setOnAction(null);
                                    }
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }
}
