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

package bisq.desktop.main.portfolio.failedtrades;

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.list.FilterBox;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.config.Config;
import bisq.common.util.Utilities;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXButton;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.event.EventHandler;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

@FxmlView
public class FailedTradesView extends ActivatableViewAndModel<VBox, FailedTradesViewModel> {

    @FXML
    TableView<FailedTradesListItem> tableView;
    @FXML
    TableColumn<FailedTradesListItem, FailedTradesListItem> priceColumn, amountColumn, volumeColumn,
            marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn, removeTradeColumn;
    @FXML
    FilterBox filterBox;
    @FXML
    Label numItems;
    @FXML
    Region footerSpacer;
    @FXML
    AutoTooltipButton exportButton;

    private final TradeDetailsWindow tradeDetailsWindow;
    private SortedList<FailedTradesListItem> sortedList;
    private FilteredList<FailedTradesListItem> filteredList;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;
    private final boolean allowFaultyDelayedTxs;

    @Inject
    public FailedTradesView(FailedTradesViewModel model,
                            TradeDetailsWindow tradeDetailsWindow,
                            @Named(Config.ALLOW_FAULTY_DELAYED_TXS) boolean allowFaultyDelayedTxs) {
        super(model);
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.allowFaultyDelayedTxs = allowFaultyDelayedTxs;
    }

    @Override
    public void initialize() {
        priceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.price")));
        amountColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())));
        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amount")));
        marketColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.market")));
        directionColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.offerType")));
        dateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.dateTime")));
        tradeIdColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.tradeId")));
        stateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.state")));

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.trades"))));

        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setMarketColumnCellFactory();
        setStateColumnCellFactory();
        setRemoveTradeColumnCellFactory();

        tradeIdColumn.setComparator(Comparator.comparing(o -> o.getTrade().getId()));
        dateColumn.setComparator(Comparator.comparing(o -> o.getTrade().getDate()));
        priceColumn.setComparator(Comparator.comparing(o -> o.getTrade().getPrice()));
        volumeColumn.setComparator(Comparator.comparing(o -> o.getTrade().getVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
        amountColumn.setComparator(Comparator.comparing(o -> o.getTrade().getAmount(), Comparator.nullsFirst(Comparator.naturalOrder())));
        stateColumn.setComparator(Comparator.comparing(FailedTradesListItem::getState));
        marketColumn.setComparator(Comparator.comparing(FailedTradesListItem::getMarketLabel));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        keyEventEventHandler = keyEvent -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.Y, keyEvent)) {
                var checkTxs = checkTxs();
                var checkUnfailString = checkUnfail();
                if (!checkTxs.isEmpty()) {
                    log.warn("Cannot unfail, error {}", checkTxs);
                    new Popup().warning(checkTxs)
                            .show();
                } else if (!checkUnfailString.isEmpty()) {
                    log.warn("Cannot unfail, error {}", checkUnfailString);
                    new Popup().warning(Res.get("portfolio.failed.cantUnfail", checkUnfailString))
                            .show();
                } else {
                    new Popup().warning(Res.get("portfolio.failed.unfail"))
                            .onAction(this::onUnfail)
                            .show();
                }
            }
        };

        numItems.setId("num-offers");
        numItems.setPadding(new Insets(-5, 0, 0, 10));
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox.setMargin(exportButton, new Insets(0, 10, 0, 0));
        exportButton.updateText(Res.get("shared.exportCSV"));
    }

    @Override
    protected void activate() {
        scene = root.getScene();
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }

        filteredList = new FilteredList<>(model.dataModel.getList());
        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        filterBox.initialize(filteredList, tableView); // here because filteredList is instantiated here
        filterBox.activate();

        numItems.setText(Res.get("shared.numItemsLabel", sortedList.size()));
        exportButton.setOnAction(event -> {
            ObservableList<TableColumn<FailedTradesListItem, ?>> tableColumns = tableView.getColumns();
            int reportColumns = tableColumns.size() - 1;    // CSV report excludes the last column (an icon)
            CSVEntryConverter<FailedTradesListItem> headerConverter = item -> {
                String[] columns = new String[reportColumns];
                for (int i = 0; i < columns.length; i++)
                    columns[i] = ((AutoTooltipLabel) tableColumns.get(i).getGraphic()).getText();
                return columns;
            };
            CSVEntryConverter<FailedTradesListItem> contentConverter = item -> {
                String[] columns = new String[reportColumns];
                columns[0] = item.getTrade().getShortId();
                columns[1] = item.getDateAsString();
                columns[2] = item.getMarketLabel();
                columns[3] = item.getPriceAsString();
                columns[4] = item.getAmountAsString();
                columns[5] = item.getVolumeAsString();
                columns[6] = item.getDirectionLabel();
                columns[7] = item.getState();
                return columns;
            };

            GUIUtil.exportCSV("failedTrades.csv",
                    headerConverter,
                    contentConverter,
                    null,
                    sortedList,
                    (Stage) root.getScene().getWindow());
        });
    }

    @Override
    protected void deactivate() {
        if (scene != null) {
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }

        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);
        filterBox.deactivate();
    }

    private void onUnfail() {
        Trade trade = sortedList.get(tableView.getSelectionModel().getFocusedIndex()).getTrade();
        model.dataModel.unfailTrade(trade);
    }

    private String checkUnfail() {
        Trade trade = sortedList.get(tableView.getSelectionModel().getFocusedIndex()).getTrade();
        return model.dataModel.checkUnfail(trade);
    }

    private String checkTxs() {
        Trade trade = sortedList.get(tableView.getSelectionModel().getFocusedIndex()).getTrade();
        log.info("Initiated unfail of trade {}", trade.getId());
        if (trade.getDepositTx() == null) {
            log.info("Check unfail found no depositTx for trade {}", trade.getId());
            return Res.get("portfolio.failed.depositTxNull");
        }
        if (trade.getDelayedPayoutTxBytes() == null) {
            log.info("Check unfail found no delayedPayoutTxBytes for trade {}", trade.getId());
            if (!allowFaultyDelayedTxs) {
                return Res.get("portfolio.failed.delayedPayoutTxNull");
            }
        }
        return "";
    }

    private void onRevertTrade(Trade trade) {
        new Popup().attention(Res.get("portfolio.failed.revertToPending.popup"))
                .onAction(() -> {
                    if (!onMoveTradeToPendingTrades(trade)) {
                        new Popup().warning(Res.get("portfolio.failed.revertToPending.failed")).show();
                    }
                })
                .actionButtonText(Res.get("shared.yes"))
                .closeButtonText(Res.get("shared.no"))
                .show();
    }

    private boolean onMoveTradeToPendingTrades(Trade trade) {
       return model.dataModel.onMoveTradeToPendingTrades(trade);
    }

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(TableColumn<FailedTradesListItem,
                            FailedTradesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getTrade().getId());
                                    field.setOnAction(event -> tradeDetailsWindow.show(item.getTrade()));
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
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
        dateColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(item.getDateAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (!empty && item != null) {
                                    setGraphic(new AutoTooltipLabel(item.getMarketLabel()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.getStyleClass().add("last-column");
        stateColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        stateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(item.getState()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }


    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (!empty && item != null) {
                                    setGraphic(new AutoTooltipLabel(item.getAmountAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (!empty && item != null) {
                                    setGraphic(new AutoTooltipLabel(item.getPriceAsString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(item.getVolumeAsString()));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        directionColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (!empty && item != null) {
                                    setGraphic(new AutoTooltipLabel(item.getDirectionLabel()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private TableColumn<FailedTradesListItem, FailedTradesListItem> setRemoveTradeColumnCellFactory() {
        removeTradeColumn.setCellValueFactory((trade) -> new ReadOnlyObjectWrapper<>(trade.getValue()));
        removeTradeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(TableColumn<FailedTradesListItem,
                            FailedTradesListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(FailedTradesListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (!empty && newItem != null) {
                                    Label icon = FormBuilder.getIcon(AwesomeIcon.UNDO);
                                    icon.getStyleClass().addAll("icon", "dao-remove-proposal-icon");
                                    JFXButton iconButton = new JFXButton("", icon);
                                    iconButton.setStyle("-fx-cursor: hand;");
                                    iconButton.getStyleClass().add("hidden-icon-button");
                                    iconButton.setTooltip(new Tooltip(Res.get("portfolio.failed.revertToPending")));
                                    iconButton.setOnAction(e -> onRevertTrade(newItem.getTrade()));
                                    setGraphic(iconButton);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return removeTradeColumn;
    }
}

