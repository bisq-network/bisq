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

package bisq.desktop.main.dao.wallet.tx;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AddressWithIconAndDirection;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.main.funds.transactions.TradableRepository;
import bisq.desktop.main.overlays.windows.BsqTradeDetailsWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.Res;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXProgressBar;

import javafx.stage.Stage;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@FxmlView
public class BsqTxView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, DaoStateListener,
        BsqWalletService.WalletTransactionsChangeListener {

    private TableView<BsqTxListItem> tableView;
    private AutoTooltipButton exportButton;

    private final DaoFacade daoFacade;
    private final DaoStateService daoStateService;
    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final Preferences preferences;
    private final TradableRepository tradableRepository;
    private final BsqTradeDetailsWindow bsqTradeDetailsWindow;

    private final ObservableList<BsqTxListItem> observableList = FXCollections.observableArrayList();
    // Need to be DoubleProperty as we pass it as reference
    private final SortedList<BsqTxListItem> sortedList = new SortedList<>(observableList);
    private int gridRow = 0;
    private Label chainHeightLabel;
    private ProgressBar chainSyncIndicator;
    private ChangeListener<Number> walletChainHeightListener;
    private Timer updateAnyChainHeightTimer;
    private int walletChainHeight;
    private int blockHeightBeforeProcessing;
    private int missingBlocks;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqTxView(DaoFacade daoFacade,
                      DaoStateService daoStateService,
                      BsqWalletService bsqWalletService,
                      Preferences preferences,
                      BtcWalletService btcWalletService,
                      BsqBalanceUtil bsqBalanceUtil,
                      BsqFormatter bsqFormatter,
                      TradableRepository tradableRepository,
                      BsqTradeDetailsWindow bsqTradeDetailsWindow) {
        this.daoFacade = daoFacade;
        this.daoStateService = daoStateService;
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.btcWalletService = btcWalletService;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.tradableRepository = tradableRepository;
        this.bsqTradeDetailsWindow = bsqTradeDetailsWindow;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        addDateColumn();
        addTxIdColumn();
        addInformationColumn();
        addAmountColumn();
        addConfidenceColumn();
        addTxTypeColumn();

        chainSyncIndicator = new JFXProgressBar();
        chainSyncIndicator.setPrefWidth(120);
        if (DevEnv.isDaoActivated())
            chainSyncIndicator.setProgress(-1);
        else
            chainSyncIndicator.setProgress(0);
        chainSyncIndicator.setPadding(new Insets(-6, 0, -10, 5));

        chainHeightLabel = FormBuilder.addLabel(root, ++gridRow, "");
        chainHeightLabel.setId("num-offers");
        chainHeightLabel.setPadding(new Insets(-5, 0, -10, 5));
        exportButton = new AutoTooltipButton();
        exportButton.updateText(Res.get("shared.exportCSV"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(chainHeightLabel, chainSyncIndicator, spacer, exportButton);

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        GridPane.setVgrow(vBox, Priority.ALWAYS);
        GridPane.setRowIndex(vBox, ++gridRow);
        GridPane.setColumnSpan(vBox, 3);
        GridPane.setRowSpan(vBox, 2);
        GridPane.setMargin(vBox, new Insets(40, -10, 5, -10));
        vBox.getChildren().addAll(tableView, hBox);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(vBox);

        walletChainHeightListener = (observable, oldValue, newValue) -> {
            walletChainHeight = bsqWalletService.getBestChainHeight();
            onUpdateAnyChainHeight();
        };
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();
        bsqWalletService.addWalletTransactionsChangeListener(this);
        bsqWalletService.addBsqBalanceListener(this);
        btcWalletService.getChainHeightProperty().addListener(walletChainHeightListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        daoFacade.addBsqStateListener(this);

        updateList();

        walletChainHeight = bsqWalletService.getBestChainHeight();
        blockHeightBeforeProcessing = daoFacade.getChainHeight();
        missingBlocks = walletChainHeight - blockHeightBeforeProcessing;
        if (!daoStateService.isParseBlockChainComplete()) {
            updateAnyChainHeightTimer = UserThread.runPeriodically(this::onUpdateAnyChainHeight, 100, TimeUnit.MILLISECONDS);
        }
        onUpdateAnyChainHeight();

        exportButton.setOnAction(event -> {
            CSVEntryConverter<BsqTxListItem> headerConverter = item -> {
                ObservableList<TableColumn<BsqTxListItem, ?>> tableColumns = tableView.getColumns();
                String[] columns = new String[8];
                columns[0] = ((AutoTooltipLabel) tableColumns.get(0).getGraphic()).getText();
                columns[1] = ((AutoTooltipLabel) tableColumns.get(1).getGraphic()).getText();
                // Table col 2 (information is split up into 3 different ones for cvs)
                columns[2] = Res.get("shared.details");
                columns[3] = Res.get("shared.address");
                columns[4] = Res.get("funds.tx.receivedFunds");
                columns[5] = ((AutoTooltipLabel) tableColumns.get(3).getGraphic()).getText();
                columns[6] = ((AutoTooltipLabel) tableColumns.get(4).getGraphic()).getText();
                columns[7] = ((AutoTooltipLabel) tableColumns.get(5).getGraphic()).getText();
                return columns;
            };
            CSVEntryConverter<BsqTxListItem> contentConverter = item -> {
                String[] columns = new String[8];
                columns[0] = item.getDateAsString();
                columns[1] = item.getTxId();
                columns[2] = item.getDirection();
                columns[3] = item.getAddress();
                columns[4] = String.valueOf(item.isReceived());
                columns[5] = item.getAmountAsString();
                columns[6] = String.valueOf(item.getConfirmations());
                columns[7] = item.getTxType().name();
                return columns;
            };

            GUIUtil.exportCSV("BSQ_transactions.csv", headerConverter, contentConverter,
                    new BsqTxListItem(), sortedList, (Stage) root.getScene().getWindow());
        });
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        sortedList.comparatorProperty().unbind();
        bsqWalletService.removeWalletTransactionsChangeListener(this);
        bsqWalletService.removeBsqBalanceListener(this);
        btcWalletService.getChainHeightProperty().removeListener(walletChainHeightListener);
        daoFacade.removeBsqStateListener(this);
        exportButton.setOnAction(null);

        observableList.forEach(BsqTxListItem::cleanup);

        if (updateAnyChainHeightTimer != null) {
            updateAnyChainHeightTimer.stop();
            updateAnyChainHeightTimer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin availableBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance,
                                 Coin unlockingBondsBalance) {
        updateList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        onUpdateAnyChainHeight();
    }

    @Override
    public void onParseBlockChainComplete() {
        if (updateAnyChainHeightTimer != null) {
            updateAnyChainHeightTimer.stop();
            updateAnyChainHeightTimer = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqWalletService.WalletTransactionsChangeListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onWalletTransactionsChange() {
        updateList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // If chain height from wallet or from the BSQ blockchain parsing changed we update our state.
    private void onUpdateAnyChainHeight() {
        int currentBlockHeight = daoFacade.getChainHeight();
        if (walletChainHeight > 0) {
            int processedBlocks = currentBlockHeight - blockHeightBeforeProcessing;
            double progress = (double) processedBlocks / (double) missingBlocks;
            boolean synced = walletChainHeight == currentBlockHeight;
            chainSyncIndicator.setVisible(!synced);
            chainSyncIndicator.setManaged(!synced);
            if (synced) {
                chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSynced", currentBlockHeight));
            } else {
                chainSyncIndicator.setProgress(progress);
                if (walletChainHeight > currentBlockHeight) {
                    // Normally we get the latest block height from BitcoinJ as the target height,
                    // and we request BSQ blocks from seed nodes up to latest block
                    chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSyncing",
                            currentBlockHeight,
                            walletChainHeight));
                } else {
                    // Our wallet chain height is behind our BSQ chain height. That can be the case at SPV resync or if
                    // we updated manually our DaoStateStore with a newer version. We do not want to show sync state
                    // as we do not know at that moment if we are missing blocks. Once Btc wallet has synced we will
                    // trigger a check and request more blocks in case we are the lite node.
                    chainSyncIndicator.setVisible(false);
                    chainSyncIndicator.setManaged(false);
                    chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSynced", currentBlockHeight));
                }
            }
        } else {
            chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSyncing",
                    currentBlockHeight,
                    walletChainHeight));
        }
        updateList();
    }

    private void updateList() {
        observableList.forEach(BsqTxListItem::cleanup);

        List<Transaction> walletTransactions = bsqWalletService.getClonedWalletTransactions();
        List<BsqTxListItem> items = walletTransactions.stream()
                .map(transaction -> {
                    return new BsqTxListItem(transaction,
                            bsqWalletService,
                            btcWalletService,
                            daoFacade,
                            // Use tx.getIncludedInBestChainAt() when available, otherwise use tx.getUpdateTime()
                            transaction.getIncludedInBestChainAt() != null ? transaction.getIncludedInBestChainAt() : transaction.getUpdateTime(),
                            bsqFormatter,
                            tradableRepository);
                })
                .collect(Collectors.toList());
        observableList.setAll(items);
    }

    private boolean isValidType(TxType txType) {
        switch (txType) {
            case UNDEFINED:
            case UNDEFINED_TX_TYPE:
            case UNVERIFIED:
            case INVALID:
                return false;
            case GENESIS:
            case TRANSFER_BSQ:
            case PAY_TRADE_FEE:
            case PROPOSAL:
            case COMPENSATION_REQUEST:
            case REIMBURSEMENT_REQUEST:
            case BLIND_VOTE:
            case VOTE_REVEAL:
            case LOCKUP:
            case UNLOCK:
            case ASSET_LISTING_FEE:
            case PROOF_OF_BURN:
                return true;
            case IRREGULAR:
                return false;
            default:
                return false;
        }
    }

    private void addDateColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.dateTime"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(180);
        column.setMaxWidth(column.getMinWidth() + 20);
        column.getStyleClass().add("first-column");

        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(item.getDateAsString());
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BsqTxListItem::getDate));
        column.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(column);
    }

    private void addTxIdColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.txId"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                //noinspection Duplicates
                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new ExternalHyperlink(transactionId);
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
        tableView.getColumns().add(column);
    }

    private void addInformationColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.details"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(160);
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<>() {

                            private AddressWithIconAndDirection field;

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    TxType txType = item.getTxType();
                                    String labelString = Res.get("dao.tx.type.enum." + txType.name());
                                    Label label;
                                    if (item.getConfirmations() > 0 && isValidType(txType)) {
                                        if (item.getOptionalBsqTrade().isPresent()) {
                                            if (field != null)
                                                field.setOnAction(null);

                                            BsqSwapTrade bsqSwapTrade = item.getOptionalBsqTrade().get();
                                            String text = Res.get("dao.tx.bsqSwapTrade", bsqSwapTrade.getShortId());
                                            HyperlinkWithIcon hyperlinkWithIcon = new HyperlinkWithIcon(text, AwesomeIcon.INFO_SIGN);
                                            hyperlinkWithIcon.setOnAction(e -> bsqTradeDetailsWindow.show(bsqSwapTrade));
                                            hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                            setGraphic(hyperlinkWithIcon);
                                        } else if (txType == TxType.COMPENSATION_REQUEST &&
                                                daoFacade.isIssuanceTx(item.getTxId(), IssuanceType.COMPENSATION)) {
                                            if (field != null)
                                                field.setOnAction(null);

                                            labelString = Res.get("dao.tx.issuanceFromCompReq");
                                            label = new AutoTooltipLabel(labelString);
                                            setGraphic(label);
                                        } else if (txType == TxType.REIMBURSEMENT_REQUEST &&
                                                daoFacade.isIssuanceTx(item.getTxId(), IssuanceType.REIMBURSEMENT)) {
                                            if (field != null)
                                                field.setOnAction(null);

                                            labelString = Res.get("dao.tx.issuanceFromReimbursement");
                                            label = new AutoTooltipLabel(labelString);
                                            setGraphic(label);
                                        } else if (item.isBurnedBsqTx() || item.getAmount().isZero()) {
                                            if (field != null)
                                                field.setOnAction(null);

                                            if (txType == TxType.TRANSFER_BSQ &&
                                                    item.getAmount().isZero() &&
                                                    item.getTxType() != TxType.UNLOCK) {
                                                labelString = Res.get("funds.tx.direction.self");
                                            }

                                            label = new AutoTooltipLabel(labelString);
                                            setGraphic(label);
                                        } else {
                                            // Received
                                            String addressString = item.getAddress();
                                            field = new AddressWithIconAndDirection(item.getDirection(), addressString,
                                                    item.isReceived());
                                            field.setOnAction(event -> openAddressInBlockExplorer(item));
                                            field.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", addressString)));
                                            setGraphic(field);
                                        }
                                    } else {
                                        if (item.isWithdrawalToBTCWallet())
                                            labelString = Res.get("dao.tx.withdrawnFromWallet");

                                        label = new AutoTooltipLabel(labelString);
                                        setGraphic(label);
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
        tableView.getColumns().add(column);
    }

    private void addAmountColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(120);
        column.setMaxWidth(column.getMinWidth());

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {

            @Override
            public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                    BsqTxListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final BsqTxListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            TxType txType = item.getTxType();

                            String bsqAmount = Res.get("shared.na");

                            if (item.getConfirmations() > 0) {
                                if (isValidType(txType))
                                    bsqAmount = item.getAmountAsString();
                                else if (item.isWithdrawalToBTCWallet())
                                    bsqAmount = bsqFormatter.formatBSQSatoshis(0L);
                            }

                            setText(bsqAmount);
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
    }

    private void addConfidenceColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.confirmations"));
        column.setMinWidth(130);
        column.setMaxWidth(column.getMinWidth());

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {

            @Override
            public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                    BsqTxListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final BsqTxListItem item, boolean empty) {
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
        tableView.getColumns().add(column);
    }

    private void addTxTypeColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("dao.wallet.tx.type"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(70);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    String style;
                                    AwesomeIcon awesomeIcon;
                                    TxType txType = item.getTxType();
                                    String toolTipText = Res.get("dao.tx.type.enum." + txType.name());
                                    boolean doRotate = false;
                                    switch (txType) {
                                        case UNDEFINED_TX_TYPE:
                                            awesomeIcon = AwesomeIcon.REMOVE_CIRCLE;
                                            style = "dao-tx-type-unverified-icon";
                                            break;
                                        case UNVERIFIED:
                                            awesomeIcon = AwesomeIcon.QUESTION_SIGN;
                                            style = "dao-tx-type-unverified-icon";
                                            break;
                                        case INVALID:
                                            awesomeIcon = AwesomeIcon.WARNING_SIGN;
                                            style = "dao-tx-type-invalid-icon";
                                            break;
                                        case GENESIS:
                                            awesomeIcon = AwesomeIcon.ROCKET;
                                            style = "dao-tx-type-genesis-icon";
                                            break;
                                        case TRANSFER_BSQ:
                                            if (item.getAmount().isZero()) {
                                                awesomeIcon = AwesomeIcon.RETWEET;
                                                style = "dao-tx-type-self-icon";
                                            } else {
                                                awesomeIcon = item.isReceived() ? AwesomeIcon.SIGNIN : AwesomeIcon.SIGNOUT;
                                                doRotate = item.isReceived();
                                                style = item.isReceived() ? "dao-tx-type-received-funds-icon" : "dao-tx-type-sent-funds-icon";
                                                toolTipText = item.isReceived() ?
                                                        Res.get("dao.tx.type.enum.received." + txType.name()) :
                                                        Res.get("dao.tx.type.enum.sent." + txType.name());
                                            }
                                            break;
                                        case PAY_TRADE_FEE:
                                            // We do not detect a BSQ swap tx. It is considered a PAY_TRADE_FEE tx
                                            // which is correct as well as it pays a trade fee.
                                            // Locally we can derive the information to distinguish a BSQ swap tx
                                            // by looking up our closed trades. Globally (like on the explorer) we do
                                            // not have the data to make that distinction.
                                            if (item.isBsqSwapTx()) {
                                                awesomeIcon = AwesomeIcon.EXCHANGE;
                                                style = "dao-tx-type-bsq-swap-icon";
                                                toolTipText = Res.get("dao.tx.bsqSwapTx");
                                            } else {
                                                awesomeIcon = AwesomeIcon.LEAF;
                                                style = "dao-tx-type-trade-fee-icon";
                                            }
                                            break;
                                        case PROPOSAL:
                                        case COMPENSATION_REQUEST:
                                            String txId = item.getTxId();
                                            if (daoFacade.isIssuanceTx(txId, IssuanceType.COMPENSATION)) {
                                                awesomeIcon = AwesomeIcon.MONEY;
                                                style = "dao-tx-type-issuance-icon";
                                                int issuanceBlockHeight = daoFacade.getIssuanceBlockHeight(txId);
                                                long blockTime = daoFacade.getBlockTime(issuanceBlockHeight);
                                                String formattedDate = DisplayUtils.formatDateTime(new Date(blockTime));
                                                toolTipText = Res.get("dao.tx.issuanceFromCompReq.tooltip", formattedDate);
                                            } else {
                                                awesomeIcon = AwesomeIcon.FILE_TEXT;
                                                style = "dao-tx-type-proposal-fee-icon";
                                            }
                                            break;
                                        case REIMBURSEMENT_REQUEST:
                                            txId = item.getTxId();
                                            if (daoFacade.isIssuanceTx(txId, IssuanceType.REIMBURSEMENT)) {
                                                awesomeIcon = AwesomeIcon.MONEY;
                                                style = "dao-tx-type-issuance-icon";
                                                int issuanceBlockHeight = daoFacade.getIssuanceBlockHeight(txId);
                                                long blockTime = daoFacade.getBlockTime(issuanceBlockHeight);
                                                String formattedDate = DisplayUtils.formatDateTime(new Date(blockTime));
                                                toolTipText = Res.get("dao.tx.issuanceFromReimbursement.tooltip", formattedDate);
                                            } else {
                                                awesomeIcon = AwesomeIcon.FILE_TEXT;
                                                style = "dao-tx-type-proposal-fee-icon";
                                            }
                                            break;
                                        case BLIND_VOTE:
                                            awesomeIcon = AwesomeIcon.EYE_CLOSE;
                                            style = "dao-tx-type-vote-icon";
                                            break;
                                        case VOTE_REVEAL:
                                            awesomeIcon = AwesomeIcon.EYE_OPEN;
                                            style = "dao-tx-type-vote-reveal-icon";
                                            break;
                                        case LOCKUP:
                                            awesomeIcon = AwesomeIcon.LOCK;
                                            style = "dao-tx-type-lockup-icon";
                                            break;
                                        case UNLOCK:
                                            awesomeIcon = AwesomeIcon.UNLOCK;
                                            style = "dao-tx-type-unlock-icon";
                                            break;
                                        case ASSET_LISTING_FEE:
                                            awesomeIcon = AwesomeIcon.FILE_TEXT;
                                            style = "dao-tx-type-proposal-fee-icon";
                                            break;
                                        case PROOF_OF_BURN:
                                            awesomeIcon = AwesomeIcon.FILE_TEXT;
                                            style = "dao-tx-type-proposal-fee-icon";
                                            break;
                                        case IRREGULAR:
                                            awesomeIcon = AwesomeIcon.WARNING_SIGN;
                                            style = "dao-tx-type-invalid-icon";
                                            break;
                                        default:
                                            awesomeIcon = AwesomeIcon.QUESTION_SIGN;
                                            style = "dao-tx-type-unverified-icon";
                                            break;
                                    }
                                    Label label = FormBuilder.getIcon(awesomeIcon);
                                    label.getStyleClass().addAll("icon", style);
                                    label.setTooltip(new Tooltip(toolTipText));
                                    if (doRotate)
                                        label.setRotate(180);
                                    setGraphic(label);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().add(column);
    }

    private void openTxInBlockExplorer(BsqTxListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + item.getTxId(), false);
    }

    private void openAddressInBlockExplorer(BsqTxListItem item) {
        if (item.getAddress() != null) {
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().addressUrl + item.getAddress(), false);
        }
    }
}

