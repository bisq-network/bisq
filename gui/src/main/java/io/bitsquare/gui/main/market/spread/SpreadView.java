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

package io.bitsquare.gui.main.market.spread;

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
        spreadColumn.setComparator((o1, o2) -> o1.spread != null && o2.spread != null ? formatter.formatVolumeWithCode(o1.spread).compareTo(formatter.formatVolumeWithCode(o2.spread)) : 0);

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
        numberOfOffersColumn.setText("All offers (" + sortedList.stream().mapToInt(item -> item.numberOfOffers).sum() + ")");
        numberOfBuyOffersColumn.setText("Buy BTC offers (" + sortedList.stream().mapToInt(item -> item.numberOfBuyOffers).sum() + ")");
        numberOfSellOffersColumn.setText("Sell BTC offers (" + sortedList.stream().mapToInt(item -> item.numberOfSellOffers).sum() + ")");
        totalAmountColumn.setText("Total amount in BTC (" + formatter.formatCoin(Coin.valueOf(sortedList.stream().mapToLong(item -> item.totalAmount.value).sum())) + ")");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TableColumn<SpreadItem, SpreadItem> getCurrencyColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>("Currency") {
            {
                setMinWidth(110);
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>("Total offers") {
            {
                setMinWidth(100);
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>("Buy offers") {
            {
                setMinWidth(100);
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>("Sell offers") {
            {
                setMinWidth(100);
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
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>("Total amount") {
            {
                setMinWidth(170);
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
                                    setText(formatter.formatCoin(item.totalAmount));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<SpreadItem, SpreadItem> getSpreadColumn() {
        TableColumn<SpreadItem, SpreadItem> column = new TableColumn<SpreadItem, SpreadItem>("Spread") {
            {
                setMinWidth(130);
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
                                if (item != null && !empty) {
                                    if (item.spread != null)
                                        setText(formatter.formatVolumeWithCode(item.spread));
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
