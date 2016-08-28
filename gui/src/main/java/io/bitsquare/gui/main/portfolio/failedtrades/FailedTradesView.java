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

package io.bitsquare.gui.main.portfolio.failedtrades;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import javax.inject.Inject;

@FxmlView
public class FailedTradesView extends ActivatableViewAndModel<VBox, FailedTradesViewModel> {

    @FXML
    TableView<FailedTradesListItem> tableView;
    @FXML
    TableColumn<FailedTradesListItem, FailedTradesListItem> priceColumn, amountColumn, volumeColumn,
            marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn;
    private final TradeDetailsWindow tradeDetailsWindow;
    private SortedList<FailedTradesListItem> sortedList;

    @Inject
    public FailedTradesView(FailedTradesViewModel model, TradeDetailsWindow tradeDetailsWindow) {
        super(model);
        this.tradeDetailsWindow = tradeDetailsWindow;
    }

    @Override
    public void initialize() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("No closed trades available"));

        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setMarketColumnCellFactory();
        setStateColumnCellFactory();

        tradeIdColumn.setComparator((o1, o2) -> o1.getTrade().getId().compareTo(o2.getTrade().getId()));
        dateColumn.setComparator((o1, o2) -> o1.getTrade().getDate().compareTo(o2.getTrade().getDate()));
        priceColumn.setComparator((o1, o2) -> o1.getTrade().getTradePrice().compareTo(o2.getTrade().getTradePrice()));

        volumeColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradeVolume() != null && o2.getTrade().getTradeVolume() != null)
                return o1.getTrade().getTradeVolume().compareTo(o2.getTrade().getTradeVolume());
            else
                return 0;
        });
        amountColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradeAmount() != null && o2.getTrade().getTradeAmount() != null)
                return o1.getTrade().getTradeAmount().compareTo(o2.getTrade().getTradeAmount());
            else
                return 0;
        });
        
        stateColumn.setComparator((o1, o2) -> model.getState(o1).compareTo(model.getState(o2)));
        marketColumn.setComparator((o1, o2) -> model.getMarketLabel(o1).compareTo(model.getMarketLabel(o2)));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.getList());
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
    }


    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {

                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(TableColumn<FailedTradesListItem,
                            FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getTradeId(item), true);
                                    field.setOnAction(event -> tradeDetailsWindow.show(item.getTrade()));
                                    field.setTooltip(new Tooltip("Open popup for details"));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getDate(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getMarketLabel(item));
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        stateColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getState(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }


    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }
}

