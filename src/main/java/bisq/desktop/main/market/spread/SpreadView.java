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
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.common.locale.CurrencyUtil;
import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

@FxmlView
public class SpreadView extends ActivatableViewAndModel<GridPane, SpreadViewModel> {
    private final BSFormatter formatter;
    private final int gridRow = 0;
    private TableView<SpreadItem> tableView;
    private SortedList<SpreadItem> sortedList;
    private ListChangeListener<SpreadItem> itemListChangeListener;
    private TableColumn<SpreadItem, SpreadItem> totalAmountColumn, numberOfOffersColumn, numberOfBuyOffersColumn, numberOfSellOffersColumn;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SpreadView(SpreadViewModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(-10, -15, -10, -15));
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

        currencyColumn.setComparator((o1, o2) -> CurrencyUtil.getNameByCode(o1.currencyCode).compareTo(CurrencyUtil.getNameByCode(o2.currencyCode)));
        numberOfOffersColumn.setComparator((o1, o2) -> Integer.valueOf(o1.numberOfOffers).compareTo(o2.numberOfOffers));
        numberOfBuyOffersColumn.setComparator((o1, o2) -> Integer.valueOf(o1.numberOfBuyOffers).compareTo(o2.numberOfBuyOffers));
        numberOfSellOffersColumn.setComparator((o1, o2) -> Integer.valueOf(o1.numberOfSellOffers).compareTo(o2.numberOfSellOffers));
        totalAmountColumn.setComparator((o1, o2) -> o1.totalAmount.compareTo(o2.totalAmount));
        spreadColumn.setComparator((o1, o2) -> {
            Long spreadO1 = o1.priceSpread != null ? o1.priceSpread.getValue() : 0;
            Long spreadO2 = o2.priceSpread != null ? o2.priceSpread.getValue() : 0;
            return spreadO1.compareTo(spreadO2);
        });

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
        TableColumn<SpreadItem, SpreadItem> column = new AutoTooltipTableColumn<SpreadItem, SpreadItem>(Res.get("shared.currency")) {
            {
                setMinWidth(160);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<SpreadItem, SpreadItem>, TableCell<SpreadItem,
                        SpreadItem>>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<SpreadItem, SpreadItem>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>() {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<SpreadItem, SpreadItem>, TableCell<SpreadItem,
                        SpreadItem>>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<SpreadItem, SpreadItem>() {
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>() {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<SpreadItem, SpreadItem>, TableCell<SpreadItem,
                        SpreadItem>>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<SpreadItem, SpreadItem>() {
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>() {
            {
                setMinWidth(100);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<SpreadItem, SpreadItem>, TableCell<SpreadItem,
                        SpreadItem>>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<SpreadItem, SpreadItem>() {
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>() {
            {
                setMinWidth(140);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<SpreadItem, SpreadItem>, TableCell<SpreadItem,
                        SpreadItem>>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<SpreadItem, SpreadItem>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(model.getAmount(item.totalAmount), GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getSpreadColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new AutoTooltipTableColumn<SpreadItem, SpreadItem>(Res.get("market.spread.spreadColumn")) {
            {
                setMinWidth(110);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<SpreadItem, SpreadItem>, TableCell<SpreadItem,
                        SpreadItem>>() {
                    @Override
                    public TableCell<SpreadItem, SpreadItem> call(
                            TableColumn<SpreadItem, SpreadItem> column) {
                        return new TableCell<SpreadItem, SpreadItem>() {
                            @Override
                            public void updateItem(final SpreadItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    // TODO maybe show exra colums with item.priceSpread and use real amount diff
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
