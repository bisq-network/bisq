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
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PeerInfoIcon;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Contract;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Named;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.function.Function;

@FxmlView
public class ClosedTradesView extends ActivatableViewAndModel<VBox, ClosedTradesViewModel> {
    private final boolean useDevPrivilegeKeys;
    private final OfferDetailsWindow offerDetailsWindow;
    private final CoinFormatter formatter;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final PrivateNotificationManager privateNotificationManager;
    @FXML
    TableView<ClosedTradableListItem> tableView;
    @FXML
    TableColumn<ClosedTradableListItem, ClosedTradableListItem> priceColumn, amountColumn, volumeColumn,
            makerMiningFeeColumn, takerMiningFeeColumn, makerTradingFeeColumn, takerTradingFeeColumn, buyerSecurityDepositColumn,
            sellerSecurityDepositColumn, marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn, avatarColumn;
    @FXML
    HBox footerBox;
    @FXML
    AutoTooltipLabel filterLabel;
    @FXML
    InputTextField filterTextField;
    @FXML
    Pane spacer;
    @FXML
    AutoTooltipButton exportButton;
    private Preferences preferences;
    private SortedList<ClosedTradableListItem> sortedList;
    private FilteredList<ClosedTradableListItem> filteredList;
    private ChangeListener<String> filterTextFieldListener;

    @Inject
    public ClosedTradesView(ClosedTradesViewModel model,
                            OfferDetailsWindow offerDetailsWindow,
                            Preferences preferences,
                            TradeDetailsWindow tradeDetailsWindow,
                            PrivateNotificationManager privateNotificationManager,
                            @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                            @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.offerDetailsWindow = offerDetailsWindow;
        this.preferences = preferences;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.formatter = formatter;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    private static <T extends Comparable<T>> Comparator<ClosedTradableListItem> nullsFirstComparing(Function<Tradable, T> keyExtractor) {
        return Comparator.comparing(
                o -> o.getTradable() != null ? keyExtractor.apply(o.getTradable()) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    private static <T extends Comparable<T>> Comparator<ClosedTradableListItem> nullsFirstComparingAsTrade(Function<Trade, T> keyExtractor) {
        return Comparator.comparing(
                o -> o.getTradable() instanceof Trade ? keyExtractor.apply((Trade) o.getTradable()) : null,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
    }

    @Override
    public void initialize() {
        makerMiningFeeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.txFee")));
        takerMiningFeeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.takerFee")));
        makerTradingFeeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.makerTradingFee")));
        takerTradingFeeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.takerTradingFee")));
        buyerSecurityDepositColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.buyerSecurityDeposit")));
        sellerSecurityDepositColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.sellerSecurityDeposit")));
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
        setMakerMiningFeeColumnCellFactory();
        setTakerMiningFeeColumnCellFactory();
        setMakerTradingFeeColumnCellFactory();
        setTakerTradingFeeColumnCellFactory();
        setTakerMiningFeeColumnCellFactory();
        setBuyerSecurityDepositColumnCellFactory();
        setSellerSecurityDepositColumnCellFactory();
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
                return address1.compareTo(address2);
            } else
                return 0;
        });

        makerMiningFeeColumn.setComparator(new Comparator<ClosedTradableListItem>() {
            @Override
            public int compare(ClosedTradableListItem o1, ClosedTradableListItem o2) {
                return model.getMakerMiningFee(o1).compareTo(model.getMakerMiningFee(o2));
            }
        });
        makerMiningFeeColumn.setSortType(TableColumn.SortType.ASCENDING);

        takerMiningFeeColumn.setComparator(new Comparator<ClosedTradableListItem>() {
            @Override
            public int compare(ClosedTradableListItem o1, ClosedTradableListItem o2) {
                return model.getTakerMiningFee(o1).compareTo(model.getTakerMiningFee(o2));
            }
        });
        takerMiningFeeColumn.setSortType(TableColumn.SortType.ASCENDING);

        makerTradingFeeColumn.setComparator(new Comparator<ClosedTradableListItem>() {
            @Override
            public int compare(ClosedTradableListItem o1, ClosedTradableListItem o2) {
                return model.getMakerTradingFee(o1).compareTo(model.getMakerTradingFee(o2));
            }
        });
        makerTradingFeeColumn.setSortType(TableColumn.SortType.ASCENDING);

        takerTradingFeeColumn.setComparator(new Comparator<ClosedTradableListItem>() {
            @Override
            public int compare(ClosedTradableListItem o1, ClosedTradableListItem o2) {
                return model.getTakerTradingFee(o1).compareTo(model.getTakerTradingFee(o2));
            }
        });
        takerTradingFeeColumn.setSortType(TableColumn.SortType.ASCENDING);

        buyerSecurityDepositColumn.setComparator((o1, o2) -> {
            final Tradable tradable1 = o1.getTradable();
            final Tradable tradable2 = o2.getTradable();
            Coin txFee1 = null;
            Coin txFee2 = null;
            if (tradable1 != null && tradable1.getOffer() != null)
                txFee1 = tradable1.getOffer().getBuyerSecurityDeposit();
            if (tradable2 != null && tradable2.getOffer() != null)
                txFee2 = tradable2.getOffer().getBuyerSecurityDeposit();
            return txFee1 != null && txFee2 != null ? txFee1.compareTo(txFee2) : 0;
        });
        sellerSecurityDepositColumn.setComparator((o1, o2) -> {
            final Tradable tradable1 = o1.getTradable();
            final Tradable tradable2 = o2.getTradable();
            Coin txFee1 = null;
            Coin txFee2 = null;
            if (tradable1 != null && tradable1.getOffer() != null)
                txFee1 = tradable1.getOffer().getSellerSecurityDeposit();
            if (tradable2 != null && tradable2.getOffer() != null)
                txFee2 = tradable2.getOffer().getSellerSecurityDeposit();
            return txFee1 != null && txFee2 != null ? txFee1.compareTo(txFee2) : 0;
        });

        stateColumn.setComparator(Comparator.comparing(model::getState));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        filterLabel.setText(Res.getWithCol("support.filter"));
        filterTextField.setPromptText(Res.get("support.filter.prompt"));
        HBox.setMargin(filterLabel, new Insets(5, 0, 0, 10));
        filterTextFieldListener = (observable, oldValue, newValue) -> applyFilteredListPredicate(filterTextField.getText());
        footerBox.setSpacing(5);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        exportButton.updateText(Res.get("shared.exportCSV"));
        HBox.setMargin(exportButton, new Insets(0, 10, 0, 0));
    }

    @Override
    protected void activate() {
        filteredList = new FilteredList<>(model.getList());

        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        tableView.setItems(sortedList);

        exportButton.setOnAction(event -> {
            final ObservableList<TableColumn<ClosedTradableListItem, ?>> tableColumns = tableView.getColumns();
            CSVEntryConverter<ClosedTradableListItem> headerConverter = transactionsListItem -> {
                String[] columns = new String[14];
                for (int i = 0; i < columns.length; i++)
                    columns[i] = ((AutoTooltipLabel) tableColumns.get(i).getGraphic()).getText();
                return columns;
            };
            CSVEntryConverter<ClosedTradableListItem> contentConverter = item -> {
                String[] columns = new String[14];
                columns[0] = model.getTradeId(item);
                columns[1] = model.getDate(item);
                columns[2] = model.getMarketLabel(item);
                columns[3] = model.getPrice(item);
                columns[4] = model.getAmount(item);
                columns[5] = model.getVolume(item);
                columns[6] = model.getMakerMiningFee(item);
                columns[7] = model.getTakerMiningFee(item);
                columns[8] = model.getMakerTradingFee(item);
                columns[9] = model.getTakerTradingFee(item);
                columns[10] = model.getBuyerSecurityDeposit(item);
                columns[11] = model.getSellerSecurityDeposit(item);
                columns[12] = model.getDirectionLabel(item);
                columns[13] = model.getState(item);
                return columns;
            };

            GUIUtil.exportCSV("tradeHistory.csv", headerConverter, contentConverter,
                    new ClosedTradableListItem(null), sortedList, (Stage) root.getScene().getWindow());
        });

        filterTextField.textProperty().addListener(filterTextFieldListener);
        applyFilteredListPredicate(filterTextField.getText());
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);

        filterTextField.textProperty().removeListener(filterTextFieldListener);
    }

    private void applyFilteredListPredicate(String filterString) {
        filteredList.setPredicate(item -> {
            if (filterString.isEmpty())
                return true;

            Offer offer = item.getTradable().getOffer();
            boolean matchesId = offer.getId().contains(filterString);
            boolean matchesOfferDate = DisplayUtils.formatDate(offer.getDate()).contains(filterString);
            boolean isMakerOnion = offer.getMakerNodeAddress().getFullAddress().contains(filterString);

            if (item.getTradable() instanceof Trade) {
                boolean isBuyerOnion = false;
                boolean isSellerOnion = false;
                boolean matchesBuyersPaymentAccountData = false;
                boolean matchesSellersPaymentAccountData = false;

                Trade trade = (Trade) item.getTradable();
                boolean matchesTradeDate = DisplayUtils.formatDate(trade.getTakeOfferDate()).contains(filterString);
                Contract contract = trade.getContract();
                if (contract != null) {
                    isBuyerOnion = contract.getBuyerNodeAddress().getFullAddress().contains(filterString);
                    isSellerOnion = contract.getSellerNodeAddress().getFullAddress().contains(filterString);
                    matchesBuyersPaymentAccountData = contract.getBuyerPaymentAccountPayload().getPaymentDetails().contains(filterString);
                    matchesSellersPaymentAccountData = contract.getSellerPaymentAccountPayload().getPaymentDetails().contains(filterString);
                }
                return matchesId || matchesOfferDate || isMakerOnion ||
                        matchesTradeDate || isBuyerOnion || isSellerOnion ||
                        matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;
            } else {
                return matchesId || matchesOfferDate || isMakerOnion;
            }
        });
    }

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(TableColumn<ClosedTradableListItem,
                            ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final ClosedTradableListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty && newItem.getTradable() instanceof Trade) {
                                    Trade trade = (Trade) newItem.getTradable();
                                    int numPastTrades = model.getNumPastTrades(trade);
                                    final NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIcon(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            trade,
                                            preferences,
                                            model.accountAgeWitnessService,
                                            useDevPrivilegeKeys);
                                    setPadding(new Insets(1, 15, 0, 0));
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
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
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getDirectionLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setMakerMiningFeeColumnCellFactory() {
        makerMiningFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        makerMiningFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getMakerMiningFee(item)));
                            }
                        };
                    }
                });
    }

    private void setTakerMiningFeeColumnCellFactory() {
        takerMiningFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        takerMiningFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getTakerMiningFee(item)));
                            }
                        };
                    }
                });
    }

    private void setMakerTradingFeeColumnCellFactory() {
        makerTradingFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        makerTradingFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getMakerTradingFee(item)));
                            }
                        };
                    }
                });
    }

    private void setTakerTradingFeeColumnCellFactory() {
        takerTradingFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        takerTradingFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getTakerTradingFee(item)));
                            }
                        };
                    }
                });
    }

    private void setBuyerSecurityDepositColumnCellFactory() {
        buyerSecurityDepositColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        buyerSecurityDepositColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getBuyerSecurityDeposit(item)));
                            }
                        };
                    }
                });
    }

    private void setSellerSecurityDepositColumnCellFactory() {
        sellerSecurityDepositColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        sellerSecurityDepositColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getSellerSecurityDeposit(item)));
                            }
                        };
                    }
                });
    }

}
