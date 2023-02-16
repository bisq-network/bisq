package bisq.desktop.main.overlays.windows.supporttool;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.locale.Res;
import bisq.core.support.DelayedPayoutRecoveryPayload;
import bisq.core.support.DelayedPayoutRecoveryStorageService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.Hex;
import bisq.common.util.Utilities;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Transaction;

import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.geometry.Pos;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class DelayedPayoutsPane extends CommonPane {

    private final TradeWalletService tradeWalletService;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final InputTextField promptText;

    // ========================================================================
    // No translation of texts here as it is a support tool (infrequently used)
    // ========================================================================

    DelayedPayoutsPane(DelayedPayoutRecoveryStorageService delayedPayoutRecoveryStorageService,
                       TradeWalletService tradeWalletService,
                       P2PService p2PService,
                       WalletsSetup walletsSetup,
                       BtcWalletService btcWalletService) {
        this.tradeWalletService = tradeWalletService;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        int rowIndex = 0;
        InputTextField passKeyTextField = new InputTextField();
        passKeyTextField.setPrefWidth(800);
        passKeyTextField.setLabelFloat(true);
        passKeyTextField.setPromptText("Decryption key");
        passKeyTextField.setText(Utilities.bytesAsHexString(btcWalletService.getHashOfRecoveryPhrase()));
        passKeyTextField.setPrefWidth(600);
        add(passKeyTextField, 0, ++rowIndex);
        add(new Label(""), 0, ++rowIndex);  // spacer

        int chainHeight = walletsSetup.chainHeightProperty().get();
        InputTextField blockHeightMin = new InputTextField();
        blockHeightMin.setLabelFloat(true);
        blockHeightMin.setPromptText("Min Block Height");
        blockHeightMin.setText(Integer.toString(chainHeight - 30));
        InputTextField blockHeightMax = new InputTextField();
        blockHeightMax.setLabelFloat(true);
        blockHeightMax.setPromptText("Max Block Height");
        blockHeightMax.setText(Integer.toString(chainHeight + 30));
        Button buttonSearch = new AutoTooltipButton("Search");
        buttonSearch.setStyle("-fx-min-width: 126; -fx-pref-height: 26; -fx-padding: 0 0 0 0; ");
        Label spacer = new Label("");
        spacer.setPrefWidth(300);
        HBox blockHeightHbox = new HBox(12, blockHeightMin, blockHeightMax, spacer, buttonSearch);
        blockHeightHbox.setAlignment(Pos.CENTER_LEFT);
        add(blockHeightHbox, 0, ++rowIndex);
        add(new Label(""), 0, ++rowIndex);  // spacer

        promptText = new InputTextField();
        promptText.setEditable(false);
        add(promptText, 0, ++rowIndex);

        ObservableList<DelayedPayoutRecoveryPayload> observableList = FXCollections.observableArrayList();
        SortedList<DelayedPayoutRecoveryPayload> sortedList = new SortedList<>(observableList);
        TableView<DelayedPayoutRecoveryPayload> tableView = new TableView<>();
        tableView.setMaxHeight(250);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        GridPane.setVgrow(tableView, Priority.SOMETIMES);
        add(tableView, 0, ++rowIndex);
        createTableColumns(tableView);

        buttonSearch.setOnAction(e -> {
            observableList.clear();
            int filterMin = 0;
            int filterMax = walletsSetup.chainHeightProperty().get();
            try {
                filterMin = Integer.parseInt(blockHeightMin.getText());
                filterMax = Integer.parseInt(blockHeightMax.getText());
            } catch (NumberFormatException ignored) { }
            // decrypt using the provided passphrase
            observableList.addAll(delayedPayoutRecoveryStorageService.getDptBackupsDecrypted(
                    filterMin, filterMax, Utilities.decodeFromHex(passKeyTextField.getText())));
            observableList.sort(Comparator.comparing(DelayedPayoutRecoveryPayload::getBlockHeight).reversed());
            tableView.setItems(sortedList);
            if (sortedList.size() > 0) {
                promptText.setText("Double-click on an item to see details and copy to clipboard.");
            } else {
                promptText.setText("This passkey decrypted no records.");
            }
        });

        tableView.setRowFactory( tv -> {
            TableRow<DelayedPayoutRecoveryPayload> row = new TableRow<>();
            // double-click on a row opens details
            row.setOnMouseClicked(event -> {
                DelayedPayoutRecoveryPayload dpt = row.getItem();
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showDetails(dpt);
                }
            });
            // copy the DPT txHex to clipboard e.g. so you can broadcast it yourself.
            final ContextMenu rowMenu = new ContextMenu();
            MenuItem copyToClipboard = new MenuItem(Res.get("shared.copyToClipboard"));
            copyToClipboard.setOnAction((event) -> Utilities.copyToClipboard((Hex.encode(row.getItem().getDecryptedTxBytes()))));
            rowMenu.getItems().add(copyToClipboard);
            row.contextMenuProperty().bind(
                    Bindings.when(Bindings.isNotNull(row.itemProperty()))
                            .then(rowMenu)
                            .otherwise((ContextMenu) null));
            return row;
        });

    }

    @Override
    public String getName() {
        return "DPT Backups";
    }

    private static void createTableColumns(TableView<DelayedPayoutRecoveryPayload> tableView) {
        TableColumn<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> column;

        column = new AutoTooltipTableColumn<>("Blockheight");
        column.setMinWidth(75);
        column.setPrefWidth(85);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> call(TableColumn<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DelayedPayoutRecoveryPayload item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(getItemPart(getInfoFromDelayedPayout(item), 0));
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>("Value");
        column.setMinWidth(75);
        column.setPrefWidth(95);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> call(TableColumn<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DelayedPayoutRecoveryPayload item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(getItemPart(getInfoFromDelayedPayout(item), 1));
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>("Deposit TxId");
        column.setMinWidth(150);
        column.setPrefWidth(180);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> call(TableColumn<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DelayedPayoutRecoveryPayload item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(getItemPart(getInfoFromDelayedPayout(item), 2));
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>("Delayed Payout TxHex");
        column.setMinWidth(350);
        column.setPrefWidth(550);
        column.setMaxWidth(1000);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> call(TableColumn<DelayedPayoutRecoveryPayload, DelayedPayoutRecoveryPayload> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final DelayedPayoutRecoveryPayload item, boolean empty) {
                                super.updateItem(item, empty);
                                String dpt = getItemPart(getInfoFromDelayedPayout(item), 3);
                                setText(dpt.length() < 40 ? dpt : dpt.substring(0, 40) + "...");    // dont display really large DPT string int table column
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }

    private static String getItemPart(String item, int part) {
        if (item == null) {
            return "";
        }
        String[] items = item.split("~");
        return items.length > part ? items[part] : "?";
    }

    private static String getInfoFromDelayedPayout(DelayedPayoutRecoveryPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload.getDecryptedTxBytes() == null || payload.getDecryptedTxBytes().length < 1) {
            // failure to decrypt the payload
            return "" + payload.getBlockHeight() + "~?~?~?";
        }
        String txHex = Utilities.encodeToHex(payload.getDecryptedTxBytes());
        String result = "";
        try {
            Transaction delayedPayoutTx = new Transaction(Config.baseCurrencyNetworkParameters(), Hex.decode(txHex));
            result += Long.toString(delayedPayoutTx.getLockTime());
            result += "~";
            result += delayedPayoutTx.getOutputSum().toPlainString();
            result += "~";
            result += delayedPayoutTx.getInput(0).getOutpoint().getHash().toString();
            result += "~";
            result += txHex;
        } catch (Exception ex) {
            // failed to parse the DPT, let the user check the txHex themselves
            result = payload.getBlockHeight() + "~?~?~" + txHex;
        }
        return result;
    }

    private void showDetails(DelayedPayoutRecoveryPayload payload) {
        String item = getInfoFromDelayedPayout(payload);
        int fieldIdx = -1;
        String report = "BlockHeight= " + getItemPart(item, ++fieldIdx) + System.lineSeparator();
        report += "Value= " + getItemPart(item, ++fieldIdx) + System.lineSeparator();
        String depositTxId = getItemPart(item, ++fieldIdx);
        report += "Deposit Tx= " + depositTxId + System.lineSeparator();
        String dpt = getItemPart(item, ++fieldIdx);
        report += "DPT= " + dpt + System.lineSeparator();

        try {
            // if the deposit tx paid out, the DPT is no longer relevant.
            Transaction depositTx = btcWalletService.getTransaction(depositTxId);
            if (!depositTx.getOutput(0).isAvailableForSpending()) {
                report += "The deposit tx has been spent, so this delayed payout transaction is no longer necessary." + System.lineSeparator();
                report += "Spent by= " + depositTx.getOutput(0).getSpentBy().getConnectedTransaction().getTxId().toString();
            }
        } catch (Exception ignored) { }

        new Popup()
                .headLine("Delayed Payout Information")
                .feedback(report)
                .width(900)
                .secondaryActionButtonText("Publish DPT")
                .onSecondaryAction(() -> askToBroadcast(dpt))
                .actionButtonText("View Deposit Tx using a block explorer")
                .onAction(() -> GUIUtil.openTxInBlockExplorer(depositTxId))
                .show();
    }

    private void askToBroadcast(String dpt) {
        new Popup().headLine("Are you sure you want to publish the DPT?")
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> broadcast(dpt))
                .closeButtonText(Res.get("shared.no"))
                .show();
    }

    private void broadcast(String dpt) {
        TxBroadcaster.Callback callback = new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                UserThread.execute(() -> {
                    String txId = result != null ? result.getTxId().toString() : "null";
                    new Popup().information(Res.get("shared.txId") + " " + txId).show();
                });
            }
            @Override
            public void onFailure(TxBroadcastException exception) {
                log.error(exception.toString());
                UserThread.execute(() -> new Popup().warning(exception.toString()).show());
            }
        };
        if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            try {
                tradeWalletService.emergencyPublishPayoutTxFrom2of2MultiSig(dpt, callback);
            } catch (AddressFormatException | WalletException | TransactionVerificationException ee) {
                log.error(ee.toString());
                ee.printStackTrace();
                UserThread.execute(() -> new Popup().warning(ee.toString()).show());
            }
        }
    }
}
