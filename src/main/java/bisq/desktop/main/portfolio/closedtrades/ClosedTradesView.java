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

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.PeerInfoIcon;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import bisq.common.locale.Res;
import bisq.common.monetary.Price;
import bisq.common.monetary.Volume;

import org.bitcoinj.core.Coin;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import com.google.inject.name.Named;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

@FxmlView
public class ClosedTradesView extends ActivatableViewAndModel<VBox, ClosedTradesViewModel> {
    private final boolean useDevPrivilegeKeys;
    @FXML
    TableView<ClosedTradableListItem> tableView;
    @FXML
    TableColumn<ClosedTradableListItem, ClosedTradableListItem> priceColumn, amountColumn, volumeColumn,
            marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn, avatarColumn;
    @FXML
    Button exportButton;
    private final OfferDetailsWindow offerDetailsWindow;
    private Preferences preferences;
    private final BSFormatter formatter;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final PrivateNotificationManager privateNotificationManager;
    private final Stage stage;
    private SortedList<ClosedTradableListItem> sortedList;

    @Inject
    public ClosedTradesView(ClosedTradesViewModel model,
                            OfferDetailsWindow offerDetailsWindow,
                            Preferences preferences,
                            TradeDetailsWindow tradeDetailsWindow,
                            PrivateNotificationManager privateNotificationManager,
                            Stage stage,
                            BSFormatter formatter,
                            @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.offerDetailsWindow = offerDetailsWindow;
        this.preferences = preferences;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.stage = stage;
        this.preferences = preferences;
        this.formatter = formatter;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
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
        avatarColumn.setText("");

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
        setAvatarColumnCellFactory();

        tradeIdColumn.setComparator(Comparator.comparing(o -> o.getTradable().getId()));
        dateColumn.setComparator(Comparator.comparing(o -> o.getTradable().getDate()));
        directionColumn.setComparator(Comparator.comparing(o -> o.getTradable().getOffer().getDirection()));
        marketColumn.setComparator(Comparator.comparing(model::getMarketLabel));

        priceColumn.setComparator((o1, o2) -> {
            final Tradable tradable1 = o1.getTradable();
            final Tradable tradable2 = o2.getTradable();
            Price price1 = null;
            Price price2 = null;
            if (tradable1 != null)
                price1 = tradable1 instanceof Trade ? ((Trade) tradable1).getTradePrice() : tradable1.getOffer().getPrice();
            if (tradable2 != null)
                price2 = tradable2 instanceof Trade ? ((Trade) tradable2).getTradePrice() : tradable2.getOffer().getPrice();
            return price1 != null && price2 != null ? price1.compareTo(price2) : 0;
        });
        volumeColumn.setComparator((o1, o2) -> {
            if (o1.getTradable() instanceof Trade && o2.getTradable() instanceof Trade) {
                Volume tradeVolume1 = ((Trade) o1.getTradable()).getTradeVolume();
                Volume tradeVolume2 = ((Trade) o2.getTradable()).getTradeVolume();
                return tradeVolume1 != null && tradeVolume2 != null ? tradeVolume1.compareTo(tradeVolume2) : 0;
            } else
                return 0;
        });
        amountColumn.setComparator((o1, o2) -> {
            if (o1.getTradable() instanceof Trade && o2.getTradable() instanceof Trade) {
                Coin amount1 = ((Trade) o1.getTradable()).getTradeAmount();
                Coin amount2 = ((Trade) o2.getTradable()).getTradeAmount();
                return amount1 != null && amount2 != null ? amount1.compareTo(amount2) : 0;
            } else
                return 0;
        });
        avatarColumn.setComparator((o1, o2) -> {
            if (o1.getTradable() instanceof Trade && o2.getTradable() instanceof Trade) {
                NodeAddress tradingPeerNodeAddress1 = ((Trade) o1.getTradable()).getTradingPeerNodeAddress();
                NodeAddress tradingPeerNodeAddress2 = ((Trade) o2.getTradable()).getTradingPeerNodeAddress();
                String address1 = tradingPeerNodeAddress1 != null ? tradingPeerNodeAddress1.getFullAddress() : "";
                String address2 = tradingPeerNodeAddress2 != null ? tradingPeerNodeAddress2.getFullAddress() : "";
                return address1 != null && address2 != null ? address1.compareTo(address2) : 0;
            } else
                return 0;
        });
        stateColumn.setComparator((o1, o2) -> model.getState(o1).compareTo(model.getState(o2)));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
        exportButton.setText(Res.get("shared.exportCSV"));
    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.getList());
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        exportButton.setOnAction(event -> {
            final ObservableList<TableColumn<ClosedTradableListItem, ?>> tableColumns = tableView.getColumns();
            CSVEntryConverter<ClosedTradableListItem> headerConverter = transactionsListItem -> {
                String[] columns = new String[7];
                for (int i = 0; i < columns.length; i++)
                    columns[i] = tableColumns.get(i).getText();

                return columns;
            };
            CSVEntryConverter<ClosedTradableListItem> contentConverter = item -> {
                String[] columns = new String[7];
                columns[0] = model.getTradeId(item);
                columns[1] = model.getDate(item);
                columns[2] = model.getAmount(item);
                columns[3] = model.getPrice(item);
                columns[4] = model.getVolume(item);
                columns[5] = model.getDirectionLabel(item);
                columns[6] = model.getState(item);
                return columns;
            };

            GUIUtil.exportCSV("tradeHistory.csv", headerConverter, contentConverter,
                    new ClosedTradableListItem(null), sortedList, stage);
        });
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);
    }


    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {

                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(TableColumn<ClosedTradableListItem,
                            ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getTradeId(item));
                                    field.setOnAction(event -> {
                                        Tradable tradable = item.getTradable();
                                        if (tradable instanceof Trade)
                                            tradeDetailsWindow.show((Trade) tradable);
                                        else if (tradable instanceof OpenOffer)
                                            offerDetailsWindow.show(tradable.getOffer());
                                    });
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
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(model.getDate(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getMarketLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        stateColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(model.getState(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<ClosedTradableListItem, ClosedTradableListItem> setAvatarColumnCellFactory() {
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {

                            @Override
                            public void updateItem(final ClosedTradableListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty && newItem.getTradable() instanceof Trade) {
                                    Trade trade = (Trade) newItem.getTradable();
                                    int numPastTrades = model.getNumPastTrades(trade);
                                    final NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
                                    final Offer offer = trade.getOffer();
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIcon(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            offer,
                                            preferences,
                                            model.accountAgeWitnessService,
                                            formatter,
                                            useDevPrivilegeKeys);
                                    setPadding(new Insets(1, 0, 0, 0));
                                    setGraphic(peerInfoIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return avatarColumn;
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getAmount(item)));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getPrice(item)));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setGraphic(new AutoTooltipLabel(model.getVolume(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getDirectionLabel(item)));
                            }
                        };
                    }
                });
    }
}

