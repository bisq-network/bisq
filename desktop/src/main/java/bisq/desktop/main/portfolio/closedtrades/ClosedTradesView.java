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
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PeerInfoIcon;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Contract;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

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

    private enum ColumnNames {
        TRADE_ID(Res.get("shared.tradeId")),
        DATE(Res.get("shared.dateTime")),
        MARKET(Res.get("shared.market")),
        PRICE(Res.get("shared.price")),
        DEVIATION(Res.get("shared.deviation")),
        AMOUNT(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())),
        VOLUME(Res.get("shared.amount")),
        TX_FEE(Res.get("shared.txFee")),
        TRADE_FEE(Res.get("shared.tradeFee")),
        BUYER_SEC(Res.get("shared.buyerSecurityDeposit")),
        SELLER_SEC(Res.get("shared.sellerSecurityDeposit")),
        OFFER_TYPE(Res.get("shared.offerType")),
        STATUS(Res.get("shared.state"));

        private final String text;

        ColumnNames(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @FXML
    TableView<ClosedTradableListItem> tableView;
    @FXML
    TableColumn<ClosedTradableListItem, ClosedTradableListItem> priceColumn, deviationColumn, amountColumn, volumeColumn,
            txFeeColumn, tradeFeeColumn, buyerSecurityDepositColumn, sellerSecurityDepositColumn,
            marketColumn, directionColumn, dateColumn, tradeIdColumn, stateColumn, avatarColumn;
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

    private final OfferDetailsWindow offerDetailsWindow;
    private final Preferences preferences;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final PrivateNotificationManager privateNotificationManager;
    private SortedList<ClosedTradableListItem> sortedList;
    private FilteredList<ClosedTradableListItem> filteredList;
    private ChangeListener<String> filterTextFieldListener;
    private ChangeListener<Number> widthListener;

    @Inject
    public ClosedTradesView(ClosedTradesViewModel model,
                            OfferDetailsWindow offerDetailsWindow,
                            Preferences preferences,
                            TradeDetailsWindow tradeDetailsWindow,
                            PrivateNotificationManager privateNotificationManager,
                            @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.offerDetailsWindow = offerDetailsWindow;
        this.preferences = preferences;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.privateNotificationManager = privateNotificationManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        widthListener = (observable, oldValue, newValue) -> onWidthChange((double) newValue);
        txFeeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TX_FEE.toString()));
        tradeFeeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRADE_FEE.toString()));
        buyerSecurityDepositColumn.setGraphic(new AutoTooltipLabel(ColumnNames.BUYER_SEC.toString()));
        sellerSecurityDepositColumn.setGraphic(new AutoTooltipLabel(ColumnNames.SELLER_SEC.toString()));
        priceColumn.setGraphic(new AutoTooltipLabel(ColumnNames.PRICE.toString()));
        deviationColumn.setGraphic(new AutoTooltipTableColumn<>(ColumnNames.DEVIATION.toString(),
                Res.get("portfolio.closedTrades.deviation.help")).getGraphic());
        amountColumn.setGraphic(new AutoTooltipLabel(ColumnNames.AMOUNT.toString()));
        volumeColumn.setGraphic(new AutoTooltipLabel(ColumnNames.VOLUME.toString()));
        marketColumn.setGraphic(new AutoTooltipLabel(ColumnNames.MARKET.toString()));
        directionColumn.setGraphic(new AutoTooltipLabel(ColumnNames.OFFER_TYPE.toString()));
        dateColumn.setGraphic(new AutoTooltipLabel(ColumnNames.DATE.toString()));
        tradeIdColumn.setGraphic(new AutoTooltipLabel(ColumnNames.TRADE_ID.toString()));
        stateColumn.setGraphic(new AutoTooltipLabel(ColumnNames.STATUS.toString()));
        avatarColumn.setText("");

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.trades"))));

        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setTxFeeColumnCellFactory();
        setTradeFeeColumnCellFactory();
        setBuyerSecurityDepositColumnCellFactory();
        setSellerSecurityDepositColumnCellFactory();
        setPriceColumnCellFactory();
        setDeviationColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setMarketColumnCellFactory();
        setStateColumnCellFactory();
        setAvatarColumnCellFactory();

        tradeIdColumn.setComparator(Comparator.comparing(o -> o.getTradable().getId()));
        dateColumn.setComparator(Comparator.comparing(o -> o.getTradable().getDate()));
        directionColumn.setComparator(Comparator.comparing(o -> o.getTradable().getOffer().getDirection()));
        marketColumn.setComparator(Comparator.comparing(model::getMarketLabel));
        priceColumn.setComparator(Comparator.comparing(model::getPrice, Comparator.nullsFirst(Comparator.naturalOrder())));
        deviationColumn.setComparator(Comparator.comparing(o ->
                        o.getTradable().getOffer().isUseMarketBasedPrice() ? o.getTradable().getOffer().getMarketPriceMargin() : 1,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        volumeColumn.setComparator(nullsFirstComparingAsTrade(Trade::getTradeVolume));
        amountColumn.setComparator(Comparator.comparing(model::getAmount, Comparator.nullsFirst(Comparator.naturalOrder())));
        avatarColumn.setComparator(Comparator.comparing(
                o -> model.getNumPastTrades(o.getTradable()),
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));
        txFeeColumn.setComparator(nullsFirstComparing(o ->
                o instanceof Trade ? ((Trade) o).getTxFee() : o.getOffer().getTxFee()
        ));
        txFeeColumn.setComparator(Comparator.comparing(model::getTxFee, Comparator.nullsFirst(Comparator.naturalOrder())));

        //
        tradeFeeColumn.setComparator(Comparator.comparing(item -> {
            String tradeFee = model.getTradeFee(item);
            // We want to separate BSQ and BTC fees so we use a prefix
            if (item.getTradable().getOffer().isCurrencyForMakerFeeBtc()) {
                return "BTC" + tradeFee;
            } else {
                return "BSQ" + tradeFee;
            }
        }, Comparator.nullsFirst(Comparator.naturalOrder())));
        buyerSecurityDepositColumn.setComparator(nullsFirstComparing(o ->
                o.getOffer() != null ? o.getOffer().getBuyerSecurityDeposit() : null
        ));
        sellerSecurityDepositColumn.setComparator(nullsFirstComparing(o ->
                o.getOffer() != null ? o.getOffer().getSellerSecurityDeposit() : null
        ));
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
                String[] columns = new String[ColumnNames.values().length];
                for (ColumnNames m : ColumnNames.values()) {
                    columns[m.ordinal()] = m.toString();
                }
                return columns;
            };
            CSVEntryConverter<ClosedTradableListItem> contentConverter = item -> {
                String[] columns = new String[ColumnNames.values().length];
                columns[ColumnNames.TRADE_ID.ordinal()] = model.getTradeId(item);
                columns[ColumnNames.DATE.ordinal()] = model.getDate(item);
                columns[ColumnNames.MARKET.ordinal()] = model.getMarketLabel(item);
                columns[ColumnNames.PRICE.ordinal()] = model.getPrice(item);
                columns[ColumnNames.DEVIATION.ordinal()] = model.getPriceDeviation(item);
                columns[ColumnNames.AMOUNT.ordinal()] = model.getAmount(item);
                columns[ColumnNames.VOLUME.ordinal()] = model.getVolume(item);
                columns[ColumnNames.TX_FEE.ordinal()] = model.getTxFee(item);
                columns[ColumnNames.TRADE_FEE.ordinal()] = model.getTradeFee(item);
                columns[ColumnNames.BUYER_SEC.ordinal()] = model.getBuyerSecurityDeposit(item);
                columns[ColumnNames.SELLER_SEC.ordinal()] = model.getSellerSecurityDeposit(item);
                columns[ColumnNames.OFFER_TYPE.ordinal()] = model.getDirectionLabel(item);
                columns[ColumnNames.STATUS.ordinal()] = model.getState(item);
                return columns;
            };

            GUIUtil.exportCSV("tradeHistory.csv", headerConverter, contentConverter,
                    new ClosedTradableListItem(null), sortedList, (Stage) root.getScene().getWindow());
        });

        filterTextField.textProperty().addListener(filterTextFieldListener);
        applyFilteredListPredicate(filterTextField.getText());
        root.widthProperty().addListener(widthListener);
        onWidthChange(root.getWidth());
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        exportButton.setOnAction(null);

        filterTextField.textProperty().removeListener(filterTextFieldListener);
        root.widthProperty().removeListener(widthListener);
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

    private void onWidthChange(double width) {
        log.error("onWidthChange " + width);
        txFeeColumn.setVisible(width > 1200);
        tradeFeeColumn.setVisible(width > 1300);
        buyerSecurityDepositColumn.setVisible(width > 1400);
        sellerSecurityDepositColumn.setVisible(width > 1500);
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

    private void setDeviationColumnCellFactory() {
        deviationColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        deviationColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getPriceDeviation(item)));
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

    private void setTxFeeColumnCellFactory() {
        txFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        txFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getTxFee(item)));
                            }
                        };
                    }
                });
    }

    private void setTradeFeeColumnCellFactory() {
        tradeFeeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        tradeFeeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getTradeFee(item)));
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
