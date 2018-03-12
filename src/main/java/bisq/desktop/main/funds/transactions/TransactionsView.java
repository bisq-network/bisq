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

package bisq.desktop.main.funds.transactions;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AddressWithIconAndDirection;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.locale.Res;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletEventListener;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.fxml.FXML;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.event.EventHandler;

import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.text.DateFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

@FxmlView
public class TransactionsView extends ActivatableView<VBox, Void> {

    @FXML
    TableView<TransactionsListItem> tableView;
    @FXML
    TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, detailsColumn, addressColumn, transactionColumn, amountColumn, confidenceColumn, revertTxColumn;
    @FXML
    Button exportButton;

    private final DisplayedTransactions displayedTransactions;
    private final SortedList<TransactionsListItem> sortedDisplayedTransactions;

    private final BtcWalletService btcWalletService;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final Stage stage;
    private final OfferDetailsWindow offerDetailsWindow;
    @SuppressWarnings("deprecation")
    private WalletEventListener walletEventListener;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TransactionsView(BtcWalletService btcWalletService,
                             P2PService p2PService,
                             WalletsSetup walletsSetup,
                             BSFormatter formatter,
                             Preferences preferences,
                             TradeDetailsWindow tradeDetailsWindow,
                             Stage stage,
                             OfferDetailsWindow offerDetailsWindow,
                             DisplayedTransactionsFactory displayedTransactionsFactory) {
        this.btcWalletService = btcWalletService;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.formatter = formatter;
        this.preferences = preferences;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.stage = stage;
        this.offerDetailsWindow = offerDetailsWindow;
        this.displayedTransactions = displayedTransactionsFactory.create();
        this.sortedDisplayedTransactions = displayedTransactions.asSortedList();
    }

    @Override
    public void initialize() {
        dateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.dateTime")));
        detailsColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.details")));
        addressColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.address")));
        transactionColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.txId", Res.getBaseCurrencyCode())));
        amountColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())));
        confidenceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.confirmations", Res.getBaseCurrencyCode())));
        revertTxColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.revert", Res.getBaseCurrencyCode())));

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("funds.tx.noTxAvailable")));

        setDateColumnCellFactory();
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setTransactionColumnCellFactory();
        setAmountColumnCellFactory();
        setConfidenceColumnCellFactory();
        setRevertTxColumnCellFactory();

        dateColumn.setComparator(Comparator.comparing(TransactionsListItem::getDate));
        detailsColumn.setComparator((o1, o2) -> {
            String id1 = o1.getTradable() != null ? o1.getTradable().getId() : o1.getDetails();
            String id2 = o2.getTradable() != null ? o2.getTradable().getId() : o2.getDetails();
            return id1.compareTo(id2);
        });
        addressColumn.setComparator(Comparator.comparing(TransactionsListItem::getAddressString));
        transactionColumn.setComparator(Comparator.comparing(TransactionsListItem::getTxId));
        amountColumn.setComparator(Comparator.comparing(TransactionsListItem::getAmountAsCoin));
        confidenceColumn.setComparator(Comparator.comparingDouble(item -> item.getTxConfidenceIndicator().getProgress()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        //noinspection deprecation
        walletEventListener = new WalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                displayedTransactions.update();
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                displayedTransactions.update();
            }

            @Override
            public void onReorganize(Wallet wallet) {
                displayedTransactions.update();
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            }

            @Override
            public void onWalletChanged(Wallet wallet) {
                displayedTransactions.update();
            }

            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                displayedTransactions.update();
            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {
                displayedTransactions.update();
            }
        };

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.R, event))
                revertTxColumn.setVisible(!revertTxColumn.isVisible());
            else if (Utilities.isAltOrCtrlPressed(KeyCode.A, event))
                showStatisticsPopup();
        };

        exportButton.setText(Res.get("shared.exportCSV"));
    }

    @Override
    protected void activate() {
        sortedDisplayedTransactions.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedDisplayedTransactions);
        displayedTransactions.update();

        btcWalletService.addEventListener(walletEventListener);

        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        exportButton.setOnAction(event -> {
            final ObservableList<TableColumn<TransactionsListItem, ?>> tableColumns = tableView.getColumns();
            CSVEntryConverter<TransactionsListItem> headerConverter = transactionsListItem -> {
                String[] columns = new String[6];
                for (int i = 0; i < columns.length; i++)
                    columns[i] = tableColumns.get(i).getText();

                return columns;
            };
            CSVEntryConverter<TransactionsListItem> contentConverter = item -> {
                String[] columns = new String[6];
                columns[0] = item.getDateString();
                columns[1] = item.getDetails();
                columns[2] = item.getDirection() + " " + item.getAddressString();
                columns[3] = item.getTxId();
                columns[4] = item.getAmount();
                columns[5] = item.getNumConfirmations();
                return columns;
            };

            GUIUtil.exportCSV("transactions.csv", headerConverter, contentConverter,
                    new TransactionsListItem(), sortedDisplayedTransactions, stage);
        });
    }

    @Override
    protected void deactivate() {
        sortedDisplayedTransactions.comparatorProperty().unbind();
        displayedTransactions.forEach(TransactionsListItem::cleanup);
        btcWalletService.removeEventListener(walletEventListener);

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        exportButton.setOnAction(null);
    }

    private void openTxInBlockExplorer(TransactionsListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().txUrl + item.getTxId());
    }

    private void openAddressInBlockExplorer(TransactionsListItem item) {
        if (item.getAddressString() != null) {
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
        }
    }

    private void openDetailPopup(TransactionsListItem item) {
        if (item.getTradable() instanceof OpenOffer)
            offerDetailsWindow.show(item.getTradable().getOffer());
        else if (item.getTradable() instanceof Trade)
            tradeDetailsWindow.show((Trade) item.getTradable());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        dateColumn.setMaxWidth(200);
        dateColumn.setMinWidth(dateColumn.getMaxWidth());
        dateColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getDateString()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDetailsColumnCellFactory() {
        detailsColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        detailsColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (item.getDetailsAvailable()) {
                                        field = new HyperlinkWithIcon(item.getDetails(), AwesomeIcon.INFO_SIGN);
                                        field.setOnAction(event -> openDetailPopup(item));
                                        field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                        setGraphic(field);
                                    } else {
                                        setGraphic(new AutoTooltipLabel(item.getDetails()));
                                    }
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

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        addressColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            private AddressWithIconAndDirection field;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String addressString = item.getAddressString();
                                    field = new AddressWithIconAndDirection(item.getDirection(), addressString,
                                            AwesomeIcon.EXTERNAL_LINK, item.getReceived());
                                    field.setOnAction(event -> openAddressInBlockExplorer(item));
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", addressString)));
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

    private void setTransactionColumnCellFactory() {
        transactionColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        transactionColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(item.getAmount()));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getTxConfidenceIndicator());
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setRevertTxColumnCellFactory() {
        revertTxColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        revertTxColumn.setCellFactory(
                new Callback<TableColumn<TransactionsListItem, TransactionsListItem>, TableCell<TransactionsListItem,
                        TransactionsListItem>>() {

                    @Override
                    public TableCell<TransactionsListItem, TransactionsListItem> call(TableColumn<TransactionsListItem,
                            TransactionsListItem> column) {
                        return new TableCell<TransactionsListItem, TransactionsListItem>() {
                            Button button;

                            @Override
                            public void updateItem(final TransactionsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    TransactionConfidence confidence = btcWalletService.getConfidenceForTxId(item.getTxId());
                                    if (confidence != null) {
                                        if (confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) {
                                            if (button == null) {
                                                button = new AutoTooltipButton(Res.get("funds.tx.revert"));
                                                setGraphic(button);
                                            }
                                            button.setOnAction(e -> revertTransaction(item.getTxId(), item.getTradable()));
                                        } else {
                                            setGraphic(null);
                                            if (button != null) {
                                                button.setOnAction(null);
                                                button = null;
                                            }
                                        }
                                    }
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void revertTransaction(String txId, @Nullable Tradable tradable) {
        if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
            try {
                btcWalletService.doubleSpendTransaction(txId, () -> {
                    if (tradable != null)
                        btcWalletService.swapAnyTradeEntryContextToAvailableEntry(tradable.getId());

                    new Popup<>().information(Res.get("funds.tx.txSent")).show();
                }, errorMessage -> new Popup<>().warning(errorMessage).show());
            } catch (Throwable e) {
                new Popup<>().warning(e.getMessage()).show();
            }
        } else {
            GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
        }
    }

    // This method is not intended for the public so we don't translate here
    private void showStatisticsPopup() {
        Map<Long, List<Coin>> map = new HashMap<>();
        Map<String, Tuple4<Date, Integer, Integer, Integer>> dataByDayMap = new HashMap<>();
        displayedTransactions.forEach(item -> {
            Coin amountAsCoin = item.getAmountAsCoin();
            List<Coin> amounts;
            long key = amountAsCoin.getValue();
            if (!map.containsKey(key)) {
                amounts = new ArrayList<>();
                map.put(key, amounts);
            } else {
                amounts = map.get(key);
            }
            amounts.add(amountAsCoin);

            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.US);
            String day = dateFormatter.format(item.getDate());

            // TODO fee is dynamic now
            Coin txFee = Coin.valueOf(20_000);
            Coin createOfferFee = Coin.valueOf(50_000);
            Coin takeOfferFee = Coin.valueOf(100_000);

            if (!dataByDayMap.containsKey(day)) {
                int numOffers = 0;
                int numTrades = 0;
                if (amountAsCoin.compareTo(createOfferFee.subtract(txFee)) == 0)
                    numOffers++;
                else if (amountAsCoin.compareTo(takeOfferFee.subtract(txFee)) == 0)
                    numTrades++;

                dataByDayMap.put(day, new Tuple4<>(item.getDate(), 1, numOffers, numTrades));
            } else {
                Tuple4<Date, Integer, Integer, Integer> tuple = dataByDayMap.get(day);
                int prev = tuple.second;
                int numOffers = tuple.third;
                int numTrades = tuple.forth;
                if (amountAsCoin.compareTo(createOfferFee.subtract(txFee)) == 0)
                    numOffers++;
                else if (amountAsCoin.compareTo(takeOfferFee.subtract(txFee)) == 0)
                    numTrades++;

                dataByDayMap.put(day, new Tuple4<>(tuple.first, ++prev, numOffers, numTrades));
            }
        });

        StringBuilder stringBuilder = new StringBuilder();
        map.forEach((key, value) -> {
            // This is not intended for the public so we don't translate here
            stringBuilder.append("No. of transactions for amount ").
                    append(formatter.formatCoinWithCode(Coin.valueOf(key))).
                    append(": ").
                    append(value.size()).
                    append("\n");
        });

        List<Tuple4<String, Date, Integer, Tuple2<Integer, Integer>>> sortedDataByDayList = dataByDayMap.entrySet().stream().
                map(e -> {
                    Tuple4<Date, Integer, Integer, Integer> data = e.getValue();
                    return new Tuple4<>(e.getKey(), data.first, data.second, new Tuple2<>(data.third, data.forth));
                }).sorted((o1, o2) -> o2.second.compareTo(o1.second))
                .collect(Collectors.toList());
        StringBuilder transactionsByDayStringBuilder = new StringBuilder();
        StringBuilder offersStringBuilder = new StringBuilder();
        StringBuilder tradesStringBuilder = new StringBuilder();
        StringBuilder allStringBuilder = new StringBuilder();
        // This is not intended for the public so we don't translate here
        allStringBuilder.append(Res.get("shared.date")).append(";").append("Offers").append(";").append("Trades").append("\n");
        sortedDataByDayList.forEach(tuple4 -> {
            offersStringBuilder.append(tuple4.forth.first).append(",");
            tradesStringBuilder.append(tuple4.forth.second).append(",");
            allStringBuilder.append(tuple4.first).append(";").append(tuple4.forth.first).append(";").append(tuple4.forth.second).append("\n");
            transactionsByDayStringBuilder.append("\n").
                    append(tuple4.first).
                    append(": ").
                    append(tuple4.third).
                    append(" (Offers: ").
                    append(tuple4.forth.first).
                    append(" / Trades: ").
                    append(tuple4.forth.second).
                    append(")");
        });
        // This is not intended for the public so we don't translate here
        String message = stringBuilder.toString() + "\nNo. of transactions by day:" + transactionsByDayStringBuilder.toString();
        new Popup<>().headLine("Statistical info")
                .information(message)
                .actionButtonText("Copy")
                .onAction(() -> Utilities.copyToClipboard(message +
                        "\n\nCSV (Offers):\n" + offersStringBuilder.toString() +
                        "\n\nCSV (Trades):\n" + tradesStringBuilder.toString() +
                        "\n\nCSV (all):\n" + allStringBuilder.toString()))
                .show();
    }

}

