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

package bisq.desktop.main.market.spread;

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

@FxmlView
public class SpreadView extends ActivatableViewAndModel<GridPane, SpreadViewModel> {
    private final CoinFormatter formatter;
    private TableView<SpreadItem> tableView;
    private SortedList<SpreadItem> sortedList;
    private ListChangeListener<SpreadItem> itemListChangeListener;
    private TableColumn<SpreadItem, SpreadItem> totalAmountColumn, numberOfOffersColumn, numberOfBuyOffersColumn, numberOfSellOffersColumn;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SpreadView(SpreadViewModel model, @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
        super(model);
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        tableView = new TableView<>();

        int gridRow = 0;
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noData"));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        TableColumn<SpreadItem, SpreadItem> currencyColumn = getCurrencyColumn();
        tableView.getColumns().add(currencyColumn);
        numberOfOffersColumn = getNumberOfOffersColumn();
        tableView.getColumns().add(numberOfOffersColumn);
        numberOfBuyOffersColumn = getNumberOfBuyOffersColumn();
        tableView.getColumns().add(numberOfBuyOffersColumn);
        numberOfSellOffersColumn = getNumberOfSellOffersColumn();
        tableView.getColumns().add(numberOfSellOffersColumn);
        totalAmountColumn = getTotalAmountColumn();
        tableView.getColumns().add(totalAmountColumn);
        TableColumn<SpreadItem, SpreadItem> spreadColumn = getSpreadColumn();
        tableView.getColumns().add(spreadColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        currencyColumn.setComparator(Comparator.comparing(o ->
                model.isIncludePaymentMethod() ? o.currencyCode : CurrencyUtil.getNameByCode(o.currencyCode)));
        numberOfOffersColumn.setComparator(Comparator.comparingInt(o3 -> o3.numberOfOffers));
        numberOfBuyOffersColumn.setComparator(Comparator.comparingInt(o3 -> o3.numberOfBuyOffers));
        numberOfSellOffersColumn.setComparator(Comparator.comparingInt(o2 -> o2.numberOfSellOffers));
        totalAmountColumn.setComparator(Comparator.comparing(o -> o.totalAmount));
        spreadColumn.setComparator(Comparator.comparingDouble(o -> o.percentageValue));

        numberOfOffersColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(numberOfOffersColumn);
        itemListChangeListener = c -> updateHeaders();
    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.spreadItems);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        sortedList.addListener(itemListChangeListener);
        updateHeaders();
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        sortedList.removeListener(itemListChangeListener);
    }

    private void updateHeaders() {
        int numberOfOffers = sortedList.stream().mapToInt(item -> item.numberOfOffers).sum();
        int numberOfBuyOffers = sortedList.stream().mapToInt(item -> item.numberOfBuyOffers).sum();
        int numberOfSellOffers = sortedList.stream().mapToInt(item -> item.numberOfSellOffers).sum();
        String total = formatter.formatCoin(Coin.valueOf(sortedList.stream().mapToLong(item -> item.totalAmount.value).sum()));

        numberOfOffersColumn.setGraphic(new AutoTooltipLabel(Res.get("market.spread.numberOfOffersColumn", numberOfOffers)));
        numberOfBuyOffersColumn.setGraphic(new AutoTooltipLabel(Res.get("market.spread.numberOfBuyOffersColumn", numberOfBuyOffers)));
        numberOfSellOffersColumn.setGraphic(new AutoTooltipLabel((Res.get("market.spread.numberOfSellOffersColumn", numberOfSellOffers))));
        totalAmountColumn.setGraphic(new AutoTooltipLabel(Res.get("market.spread.totalAmountColumn", total)));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<SpreadItem, SpreadItem> getCurrencyColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new AutoTooltipTableColumn<>(model.getKeyColumnName()) {
            {
                setMinWidth(160);
            }
        };
        column.getStyleClass().addAll("number-column", "first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    if (model.isIncludePaymentMethod())
                                        setText(item.currencyCode);
                                    else
                                        setText(CurrencyUtil.getNameAndCode(item.currencyCode));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getNumberOfOffersColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<>() {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(String.valueOf(item.numberOfOffers));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getNumberOfBuyOffersColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<>() {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(String.valueOf(item.numberOfBuyOffers));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getNumberOfSellOffersColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<>() {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(String.valueOf(item.numberOfSellOffers));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getTotalAmountColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<>() {
            {
                setMinWidth(140);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(model.getAmount(item.totalAmount), GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                else {
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getSpreadColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new AutoTooltipTableColumn<>(Res.get("market.spread.spreadColumn")) {
            {
                setMinWidth(110);
            }
        };
        column.getStyleClass().addAll("number-column", "last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    // TODO maybe show extra columns with item.priceSpread and use real amount diff
                                    // not % based
                                    if (item.priceSpread != null)
                                        setText(item.percentage);
                                    /*setText(item.percentage +
                                            " (" + formatter.formatPriceWithCode(item.priceSpread) + ")");*/
                                    else
                                        setText("-");
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        return column;
    }
}
