/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.markets.statistics;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CurrencyUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

@FxmlView
public class MarketsStatisticsView extends ActivatableViewAndModel<GridPane, MarketsStatisticViewModel> {
    private final BSFormatter formatter;
    private final int gridRow = 0;
    private TableView<MarketStatisticItem> tableView;
    private SortedList<MarketStatisticItem> sortedList;
    private ListChangeListener<MarketStatisticItem> itemListChangeListener;
    private TableColumn<MarketStatisticItem, MarketStatisticItem> totalAmountColumn;
    private TableColumn<MarketStatisticItem, MarketStatisticItem> numberOfOffersColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketsStatisticsView(MarketsStatisticViewModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        TableGroupHeadline header = new TableGroupHeadline("Statistics");
        GridPane.setRowIndex(header, gridRow);
        GridPane.setMargin(header, new Insets(0, -10, -10, -10));
        root.getChildren().add(header);

        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(20, -10, -10, -10));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);
        Label placeholder = new Label("Currently there is no data available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        TableColumn<MarketStatisticItem, MarketStatisticItem> currencyColumn = getCurrencyColumn();
        tableView.getColumns().add(currencyColumn);
        numberOfOffersColumn = getNumberOfOffersColumn();
        tableView.getColumns().add(numberOfOffersColumn);
        totalAmountColumn = getTotalAmountColumn();
        tableView.getColumns().add(totalAmountColumn);
        TableColumn<MarketStatisticItem, MarketStatisticItem> spreadColumn = getSpreadColumn();
        tableView.getColumns().add(spreadColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        currencyColumn.setComparator((o1, o2) -> CurrencyUtil.getNameByCode(o1.currencyCode).compareTo(CurrencyUtil.getNameByCode(o2.currencyCode)));
        numberOfOffersColumn.setComparator((o1, o2) -> Integer.valueOf(o1.numberOfOffers).compareTo(o2.numberOfOffers));
        totalAmountColumn.setComparator((o1, o2) -> o1.totalAmount.compareTo(o2.totalAmount));
        spreadColumn.setComparator((o1, o2) -> o1.spread != null && o2.spread != null ? formatter.formatFiatWithCode(o1.spread).compareTo(formatter.formatFiatWithCode(o2.spread)) : 0);

        numberOfOffersColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(numberOfOffersColumn);
        itemListChangeListener = c -> updateHeaders();
    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.marketStatisticItems);
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
        numberOfOffersColumn.setText("Total offers (" + sortedList.stream().mapToInt(item -> item.numberOfOffers).sum() + ")");
        totalAmountColumn.setText("Total amount (" + formatter.formatCoinWithCode(Coin.valueOf(sortedList.stream().mapToLong(item -> item.totalAmount.value).sum())) + ")");
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getCurrencyColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Currency") {
            {
                setMinWidth(100);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(CurrencyUtil.getNameByCode(item.currencyCode));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getNumberOfOffersColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Total offers") {
            {
                setMinWidth(100);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
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

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getTotalAmountColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Total amount") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatCoin(item.totalAmount));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<MarketStatisticItem, MarketStatisticItem> getSpreadColumn() {
        TableColumn<MarketStatisticItem, MarketStatisticItem> column = new TableColumn<MarketStatisticItem, MarketStatisticItem>("Spread") {
            {
                setMinWidth(130);
            }
        };
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<TableColumn<MarketStatisticItem, MarketStatisticItem>, TableCell<MarketStatisticItem,
                        MarketStatisticItem>>() {
                    @Override
                    public TableCell<MarketStatisticItem, MarketStatisticItem> call(
                            TableColumn<MarketStatisticItem, MarketStatisticItem> column) {
                        return new TableCell<MarketStatisticItem, MarketStatisticItem>() {
                            @Override
                            public void updateItem(final MarketStatisticItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (item.spread != null)
                                        setText(formatter.formatFiatWithCode(item.spread));
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
