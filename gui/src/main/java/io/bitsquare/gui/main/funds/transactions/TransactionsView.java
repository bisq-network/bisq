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

package io.bitsquare.gui.main.funds.transactions;

import com.googlecode.jcsv.writer.CSVEntryConverter;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Tuple4;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressWithIconAndDirection;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.text.DateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FxmlView
public class TransactionsView extends ActivatableView<VBox, Void> {

    @FXML
    TableView<TransactionsListItem> tableView;
    @FXML
    TableColumn<TransactionsListItem, TransactionsListItem> dateColumn, detailsColumn, addressColumn, transactionColumn, amountColumn, confidenceColumn, revertTxColumn;
    @FXML
    Button exportButton;

    private final ObservableList<TransactionsListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<TransactionsListItem> sortedList = new SortedList<>(observableList);

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final BSFormatter formatter;
    private final Preferences preferences;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final DisputeManager disputeManager;
    private Stage stage;
    private final OfferDetailsWindow offerDetailsWindow;
    private WalletEventListener walletEventListener;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TransactionsView(WalletService walletService, TradeManager tradeManager, OpenOfferManager openOfferManager,
                             ClosedTradableManager closedTradableManager, FailedTradesManager failedTradesManager,
                             BSFormatter formatter, Preferences preferences, TradeDetailsWindow tradeDetailsWindow,
                             DisputeManager disputeManager, Stage stage,
                             OfferDetailsWindow offerDetailsWindow) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.formatter = formatter;
        this.preferences = preferences;
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.disputeManager = disputeManager;
        this.stage = stage;
        this.offerDetailsWindow = offerDetailsWindow;
    }

    @Override
    public void initialize() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("No transactions available"));


        setDateColumnCellFactory();
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setTransactionColumnCellFactory();
        setAmountColumnCellFactory();
        setConfidenceColumnCellFactory();
        setRevertTxColumnCellFactory();

        dateColumn.setComparator((o1, o2) -> o1.getDate().compareTo(o2.getDate()));
        detailsColumn.setComparator((o1, o2) -> {
            String id1 = o1.getTradable() != null ? o1.getTradable().getId() : o1.getDetails();
            String id2 = o2.getTradable() != null ? o2.getTradable().getId() : o2.getDetails();
            return id1.compareTo(id2);
        });
        addressColumn.setComparator((o1, o2) -> o1.getAddressString().compareTo(o2.getAddressString()));
        transactionColumn.setComparator((o1, o2) -> o1.getTxId().compareTo(o2.getTxId()));
        amountColumn.setComparator((o1, o2) -> o1.getAmountAsCoin().compareTo(o2.getAmountAsCoin()));
        confidenceColumn.setComparator((o1, o2) -> Double.valueOf(o1.getTxConfidenceIndicator().getProgress())
                .compareTo(o2.getTxConfidenceIndicator().getProgress()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);

        walletEventListener = new WalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                updateList();
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                updateList();
            }

            @Override
            public void onReorganize(Wallet wallet) {
                updateList();
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            }

            @Override
            public void onWalletChanged(Wallet wallet) {
                updateList();
            }

            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                updateList();
            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {
                updateList();
            }
        };

        keyEventEventHandler = event -> {
            if (new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN).match(event))
                revertTxColumn.setVisible(!revertTxColumn.isVisible());
            else if (new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN).match(event))
                showStatisticsPopup();
        };

        exportButton.setText("Export to csv");
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        updateList();

        walletService.getWallet().addEventListener(walletEventListener);

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
                    new TransactionsListItem(), sortedList, stage);
        });
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        observableList.forEach(TransactionsListItem::cleanup);
        walletService.getWallet().removeEventListener(walletEventListener);

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

        exportButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        Stream<Tradable> concat1 = Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream());
        Stream<Tradable> concat2 = Stream.concat(concat1, closedTradableManager.getClosedTrades().stream());
        Stream<Tradable> concat3 = Stream.concat(concat2, failedTradesManager.getFailedTrades().stream());
        Set<Tradable> all = concat3.collect(Collectors.toSet());

        Set<Transaction> transactions = walletService.getWallet().getTransactions(true);
        List<TransactionsListItem> transactionsListItems = transactions.stream()
                .map(transaction -> {
                    Optional<Tradable> tradableOptional = all.stream()
                            .filter(tradable -> {
                                String txId = transaction.getHashAsString();
                                if (tradable instanceof OpenOffer)
                                    return tradable.getOffer().getOfferFeePaymentTxID().equals(txId);
                                else if (tradable instanceof Trade) {
                                    Trade trade = (Trade) tradable;
                                    boolean isTakeOfferFeeTx = txId.equals(trade.getTakeOfferFeeTxId());
                                    boolean isOfferFeeTx = trade.getOffer() != null &&
                                            txId.equals(trade.getOffer().getOfferFeePaymentTxID());
                                    boolean isDepositTx = trade.getDepositTx() != null &&
                                            trade.getDepositTx().getHashAsString().equals(txId);
                                    boolean isPayoutTx = trade.getPayoutTx() != null &&
                                            trade.getPayoutTx().getHashAsString().equals(txId);

                                    boolean isDisputedPayoutTx = disputeManager.getDisputesAsObservableList().stream()
                                            .filter(dispute -> txId.equals(dispute.getDisputePayoutTxId()) &&
                                                    tradable.getId().equals(dispute.getTradeId()))
                                            .findAny()
                                            .isPresent();

                                    return isTakeOfferFeeTx || isOfferFeeTx || isDepositTx || isPayoutTx || isDisputedPayoutTx;
                                } else
                                    return false;
                            })
                            .findAny();
                    return new TransactionsListItem(transaction, walletService, tradableOptional, formatter);
                })
                .collect(Collectors.toList());

        // are sorted by getRecentTransactions
        observableList.forEach(TransactionsListItem::cleanup);
        observableList.setAll(transactionsListItems);
    }

    private void openTxInBlockExplorer(TransactionsListItem item) {
        if (item.getTxId() != null) {
            try {
                Utilities.openWebPage(preferences.getBlockChainExplorer().txUrl + item.getTxId());
            } catch (Exception e) {
                log.error(e.getMessage());
                new Popup().warning("Opening browser failed. Please check your internet " +
                        "connection.").show();
            }
        }
    }

    private void openAddressInBlockExplorer(TransactionsListItem item) {
        if (item.getAddressString() != null) {
            try {
                Utilities.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
            } catch (Exception e) {
                log.error(e.getMessage());
                new Popup().warning("Opening browser failed. Please check your internet " +
                        "connection.").show();
            }
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
                                    setText(item.getDateString());
                                } else {
                                    setText("");
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
                                        field.setTooltip(new Tooltip("Open popup for details"));
                                        setGraphic(field);
                                    } else {
                                        setGraphic(new Label(item.getDetails()));
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
                                    field.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "address: " + addressString));
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
                                    hyperlinkWithIcon.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "transaction: " + transactionId));
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
                                    setText(item.getAmount());
                                } else {
                                    setText("");
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
                                    if (walletService.getConfidenceForTxId(item.getTxId()).getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) {
                                        if (button == null) {
                                            button = new Button("Revert");
                                            button.setOnAction(e -> revertTransaction(item.getTxId(), item.getTradable()));
                                            setGraphic(button);
                                        }
                                    } else {
                                        if (button != null) {
                                            button.setOnAction(null);
                                            button = null;
                                            setGraphic(null);
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
        try {
            walletService.doubleSpendTransaction(txId, () -> {
                if (tradable != null)
                    walletService.swapAnyTradeEntryContextToAvailableEntry(tradable.getId());

                new Popup().information("Transaction successfully sent to a new address in the local Bitsquare wallet.").show();
            }, errorMessage -> {
                new Popup().warning(errorMessage).show();
            });
        } catch (Throwable e) {
            new Popup().warning(e.getMessage()).show();
        }
    }

    private void showStatisticsPopup() {
        Map<Long, List<Coin>> map = new HashMap<>();
        Map<String, Tuple4<Date, Integer, Integer, Integer>> dataByDayMap = new HashMap<>();
        observableList.stream().forEach(item -> {
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

            if (!dataByDayMap.containsKey(day)) {
                int numOffers = 0;
                int numTrades = 0;
                if (amountAsCoin.compareTo(FeePolicy.getCreateOfferFee().subtract(FeePolicy.getFixedTxFeeForTrades())) == 0)
                    numOffers++;
                else if (amountAsCoin.compareTo(FeePolicy.getTakeOfferFee().subtract(FeePolicy.getFixedTxFeeForTrades())) == 0)
                    numTrades++;

                dataByDayMap.put(day, new Tuple4<>(item.getDate(), 1, numOffers, numTrades));
            } else {
                Tuple4<Date, Integer, Integer, Integer> tuple = dataByDayMap.get(day);
                int prev = tuple.second;
                int numOffers = tuple.third;
                int numTrades = tuple.forth;
                if (amountAsCoin.compareTo(FeePolicy.getCreateOfferFee().subtract(FeePolicy.getFixedTxFeeForTrades())) == 0)
                    numOffers++;
                else if (amountAsCoin.compareTo(FeePolicy.getTakeOfferFee().subtract(FeePolicy.getFixedTxFeeForTrades())) == 0)
                    numTrades++;

                dataByDayMap.put(day, new Tuple4<>(tuple.first, ++prev, numOffers, numTrades));
            }
        });

        StringBuilder stringBuilder = new StringBuilder();
        map.entrySet().stream().forEach(e -> {
            stringBuilder.append("Nr. of transactions for amount ").
                    append(formatter.formatCoinWithCode(Coin.valueOf(e.getKey()))).
                    append(": ").
                    append(e.getValue().size()).
                    append("\n");
        });

        List<Tuple4<String, Date, Integer, Tuple2<Integer, Integer>>> sortedDataByDayList = dataByDayMap.entrySet().stream().
                map(e -> {
                    Tuple4<Date, Integer, Integer, Integer> data = e.getValue();
                    return new Tuple4<>(e.getKey(), data.first, data.second, new Tuple2<>(data.third, data.forth));
                }).
                collect(Collectors.toList());
        sortedDataByDayList.sort((o1, o2) -> o2.second.compareTo(o1.second));
        StringBuilder transactionsByDayStringBuilder = new StringBuilder();
        StringBuilder offersStringBuilder = new StringBuilder();
        StringBuilder tradesStringBuilder = new StringBuilder();
        StringBuilder allStringBuilder = new StringBuilder();
        allStringBuilder.append("Date").append(";").append("Offers").append(";").append("Trades").append("\n");
        sortedDataByDayList.stream().forEach(tuple4 -> {
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
        String message = stringBuilder.toString() + "\nNr. of transactions by day:" + transactionsByDayStringBuilder.toString();
        new Popup().headLine("Statistical info")
                .information(message)
                .actionButtonText("Copy")
                .onAction(() -> Utilities.copyToClipboard(message +
                        "\n\nCSV (Offers):\n" + offersStringBuilder.toString() +
                        "\n\nCSV (Trades):\n" + tradesStringBuilder.toString() +
                        "\n\nCSV (all):\n" + allStringBuilder.toString()))
                .show();
    }

}

