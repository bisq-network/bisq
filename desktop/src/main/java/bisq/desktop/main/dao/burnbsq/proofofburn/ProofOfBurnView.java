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

package bisq.desktop.main.dao.burnbsq.proofofburn;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.dao.MessageSignatureWindow;
import bisq.desktop.main.dao.MessageVerificationWindow;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.SignVerifyService;
import bisq.core.dao.governance.proofofburn.MyProofOfBurnListService;
import bisq.core.dao.governance.proofofburn.ProofOfBurnService;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

@FxmlView
public class ProofOfBurnView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private final ProofOfBurnService proofOfBurnService;
    private final SignVerifyService signVerifyService;
    private final MyProofOfBurnListService myProofOfBurnListService;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final BsqValidator bsqValidator;

    private InputTextField amountInputTextField, preImageTextField;
    private TextField hashTextField;
    private Button burnButton;
    private TableView<MyProofOfBurnListItem> myItemsTableView;
    private TableView<ProofOfBurnListItem> allTxsTableView;
    private Hyperlink exportAsCSVHyperlink;

    private final ObservableList<MyProofOfBurnListItem> myItemsObservableList = FXCollections.observableArrayList();
    private final SortedList<MyProofOfBurnListItem> myItemsSortedList = new SortedList<>(myItemsObservableList);

    private final ObservableList<ProofOfBurnListItem> allItemsObservableList = FXCollections.observableArrayList();
    private final SortedList<ProofOfBurnListItem> allItemsSortedList = new SortedList<>(allItemsObservableList);

    private int gridRow = 0;

    private ChangeListener<Boolean> amountFocusOutListener, preImageFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener, preImageInputTextFieldListener;
    private InvalidationListener updateListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProofOfBurnView(BsqFormatter bsqFormatter,
                            BsqWalletService bsqWalletService,
                            BsqValidator bsqValidator,
                            ProofOfBurnService proofOfBurnService,
                            SignVerifyService signVerifyService,
                            MyProofOfBurnListService myProofOfBurnListService,
                            @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.bsqValidator = bsqValidator;
        this.proofOfBurnService = proofOfBurnService;
        this.signVerifyService = signVerifyService;
        this.myProofOfBurnListService = myProofOfBurnListService;
        this.btcFormatter = btcFormatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 4, Res.get("dao.proofOfBurn.header"));
        amountInputTextField = addInputTextField(root, ++gridRow, Res.get("dao.proofOfBurn.amount"), Layout.FIRST_ROW_DISTANCE);
        preImageTextField = addInputTextField(root, ++gridRow, Res.get("dao.proofOfBurn.preImage"));
        hashTextField = addTopLabelTextField(root, ++gridRow, Res.get("dao.proofOfBurn.hash")).second;
        burnButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.proofOfBurn.burn"));

        myItemsTableView = FormBuilder.<MyProofOfBurnListItem>addTableViewWithHeader(root, ++gridRow,
                Res.get("dao.proofOfBurn.myItems"), 30).first;
        createColumnsForMyItems();
        myItemsTableView.setItems(myItemsSortedList);

        allTxsTableView = FormBuilder.<ProofOfBurnListItem>addTableViewWithHeader(root, ++gridRow,
                Res.get("dao.proofOfBurn.allTxs"), 30, "last").first;
        createColumnsForAllTxs();
        allTxsTableView.setItems(allItemsSortedList);

        exportAsCSVHyperlink = new Hyperlink(Res.get("shared.exportCSV"));
        GridPane.setRowIndex(exportAsCSVHyperlink, ++gridRow);
        GridPane.setColumnIndex(exportAsCSVHyperlink, 0);
        GridPane.setMargin(exportAsCSVHyperlink, new Insets(-5, 0, 0, 0));
        GridPane.setHalignment(exportAsCSVHyperlink, HPos.RIGHT);
        root.getChildren().add(exportAsCSVHyperlink);
        exportAsCSVHyperlink.setOnAction(e -> exportToCSV());

        createListeners();
    }

    @Override
    protected void activate() {
        amountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        preImageTextField.textProperty().addListener(preImageInputTextFieldListener);
        preImageTextField.focusedProperty().addListener(preImageFocusOutListener);

        allItemsSortedList.comparatorProperty().bind(allTxsTableView.comparatorProperty());

        proofOfBurnService.getUpdateFlag().addListener(updateListener);
        bsqWalletService.addBsqBalanceListener(this);
        onUpdateAvailableBalance(bsqWalletService.getAvailableBalance());

        burnButton.setOnAction((event) -> {
            String preImageAsString = preImageTextField.getText();
            if (!preImageAsString.equals(preImageAsString.trim())) {
                new Popup().warning(Res.get("dao.proofOfBurn.preImage.notTrimmedWarning"))
                        .actionButtonText(Res.get("dao.proofOfBurn.preImage.notTrimmedWarning.ignore"))
                        .onAction(() -> confirmBurn(preImageAsString))
                        .closeButtonText(Res.get("shared.cancel"))
                        .show();
            } else {
                confirmBurn(preImageAsString);
            }
        });

        amountInputTextField.setValidator(bsqValidator);
        preImageTextField.setValidator(new InputValidator());

        updateList();
        GUIUtil.setFitToRowsForTableView(myItemsTableView, 41, 28, 4, 6);
        GUIUtil.setFitToRowsForTableView(allTxsTableView, 41, 28, 2, 10);
        updateButtonState();
    }

    private void confirmBurn(String preImageAsString) {
        Coin amount = getAmountFee();
        String formattedAmount = bsqFormatter.formatCoinWithCode(amount);
        new Popup().confirmation(Res.get("dao.proofOfBurn.preImage.confirmPopup", formattedAmount, preImageAsString))
                .actionButtonText(Res.get("dao.proofOfBurn.preImage.confirmPopup.confirm"))
                .onAction(() -> doBurn(preImageAsString, amount))
                .closeButtonText(Res.get("shared.cancel"))
                .show();
    }

    private void doBurn(String preImageAsString, Coin amount) {
        try {
            Transaction transaction = proofOfBurnService.burn(preImageAsString, amount.value);
            Coin miningFee = transaction.getFee();
            int txVsize = transaction.getVsize();

            if (!DevEnv.isDevMode()) {
                GUIUtil.showBsqFeeInfoPopup(amount, miningFee, txVsize, bsqFormatter, btcFormatter,
                        Res.get("dao.proofOfBurn.header"), () -> doPublishFeeTx(transaction, preImageAsString));
            } else {
                doPublishFeeTx(transaction, preImageAsString);
            }
        } catch (InsufficientMoneyException | TxException e) {
            e.printStackTrace();
            new Popup().error(e.toString()).show();
        }
    }

    @Override
    protected void deactivate() {
        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        allItemsSortedList.comparatorProperty().unbind();

        proofOfBurnService.getUpdateFlag().removeListener(updateListener);
        bsqWalletService.removeBsqBalanceListener(this);

        burnButton.setOnAction(null);
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
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        onUpdateAvailableBalance(availableBalance);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createListeners() {
        amountFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
            }
        };

        amountInputTextFieldListener = (observable, oldValue, newValue) -> {
            updateButtonState();
        };
        preImageFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
            }
        };

        preImageInputTextFieldListener = (observable, oldValue, newValue) -> {
            hashTextField.setText(proofOfBurnService.getHashAsString(newValue));
            updateButtonState();
        };

        updateListener = observable -> updateList();
    }

    private void onUpdateAvailableBalance(Coin availableBalance) {
        bsqValidator.setAvailableBalance(availableBalance);
        updateButtonState();
    }

    private void updateList() {
        myItemsObservableList.setAll(myProofOfBurnListService.getMyProofOfBurnList().stream()
                .map(myProofOfBurn -> new MyProofOfBurnListItem(myProofOfBurn, proofOfBurnService, bsqFormatter))
                .sorted(Comparator.comparing(MyProofOfBurnListItem::getDate).reversed())
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(myItemsTableView, 41, 28, 4, 6);

        allItemsObservableList.setAll(proofOfBurnService.getProofOfBurnTxList().stream()
                .map(tx -> new ProofOfBurnListItem(tx, proofOfBurnService, bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(allTxsTableView, 41, 28, 2, 10);
    }

    private void updateButtonState() {
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid &&
                preImageTextField.validate();
        burnButton.setDisable(!isValid);
    }

    private Coin getAmountFee() {
        return ParsingUtils.parseToCoin(amountInputTextField.getText(), bsqFormatter);
    }

    private void doPublishFeeTx(Transaction transaction, String preImageAsString) {
        proofOfBurnService.publishTransaction(transaction, preImageAsString,
                () -> {
                    if (!DevEnv.isDevMode())
                        new Popup().confirmation(Res.get("dao.tx.published.success")).show();
                },
                errorMessage -> new Popup().warning(errorMessage).show());

        amountInputTextField.clear();
        preImageTextField.clear();
        amountInputTextField.resetValidation();
        preImageTextField.resetValidation();
    }

    private void exportToCSV() {
        List<String> result = new ArrayList<>();
        String separator = "~";
        String tableColumns = "Amount~Date~Hash~TxId~Pubkey";
        CSVEntryConverter<String> headerConverter = item -> tableColumns.split(separator);
        CSVEntryConverter<String> contentConverter = item -> item.split(separator);
        for (ProofOfBurnListItem item : allItemsSortedList) {
            String line = bsqFormatter.formatCoin(item.getAmount())
                    + separator + item.getDateAsString()
                    + separator + item.getHashAsHex()
                    + separator + item.getTxId()
                    + separator + item.getPubKey();
            result.add(line);
        }
        GUIUtil.exportCSV("proofOfBurnTransactions.csv",
                headerConverter,
                contentConverter,
                "",
                result,
                (Stage) root.getScene().getWindow());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void createColumnsForMyItems() {
        TableColumn<MyProofOfBurnListItem, MyProofOfBurnListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.amount"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.getStyleClass().add("first-column");
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                    MyProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        myItemsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(MyProofOfBurnListItem::getAmount));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.date"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                    MyProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getDateAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        myItemsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(MyProofOfBurnListItem::getDate));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.preImage"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                    MyProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText("'" + item.getPreImage() + "'");
                        } else
                            setText("");
                    }
                };
            }
        });
        myItemsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(MyProofOfBurnListItem::getPreImage));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.hash"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                    MyProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getHashAsHex());
                        } else
                            setText("");
                    }
                };
            }
        });
        myItemsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(MyProofOfBurnListItem::getHashAsHex));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.txs"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                            MyProofOfBurnListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                //noinspection Duplicates
                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new ExternalHyperlink(transactionId, true);
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(transactionId));
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
        myItemsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(MyProofOfBurnListItem::getTxId));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.pubKey"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                    MyProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getPubKey());
                        } else
                            setText("");
                    }
                };
            }
        });
        myItemsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(MyProofOfBurnListItem::getPubKey));

        column = new AutoTooltipTableColumn<>("");
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.getStyleClass().add("last-column");
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<MyProofOfBurnListItem, MyProofOfBurnListItem> call(TableColumn<MyProofOfBurnListItem,
                            MyProofOfBurnListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final MyProofOfBurnListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("dao.proofOfBurn.sign"));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> new MessageSignatureWindow(signVerifyService, item.getTxId()).show());
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
        myItemsTableView.getColumns().add(column);
        column.setSortable(false);
    }

    private void createColumnsForAllTxs() {
        TableColumn<ProofOfBurnListItem, ProofOfBurnListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.amount"));
        column.setMinWidth(80);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ProofOfBurnListItem, ProofOfBurnListItem> call(TableColumn<ProofOfBurnListItem,
                    ProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        allTxsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ProofOfBurnListItem::getAmount));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.date"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ProofOfBurnListItem, ProofOfBurnListItem> call(TableColumn<ProofOfBurnListItem,
                    ProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getDateAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        allTxsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ProofOfBurnListItem::getDate));


        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.hash"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ProofOfBurnListItem, ProofOfBurnListItem> call(TableColumn<ProofOfBurnListItem,
                    ProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getHashAsHex());
                        } else
                            setText("");
                    }
                };
            }
        });
        allTxsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ProofOfBurnListItem::getHashAsHex));

        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.txs"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProofOfBurnListItem, ProofOfBurnListItem> call(TableColumn<ProofOfBurnListItem,
                            ProofOfBurnListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final ProofOfBurnListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                //noinspection Duplicates
                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new ExternalHyperlink(transactionId, true);
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(transactionId));
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
        allTxsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ProofOfBurnListItem::getTxId));


        column = new AutoTooltipTableColumn<>(Res.get("dao.proofOfBurn.pubKey"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ProofOfBurnListItem, ProofOfBurnListItem> call(TableColumn<ProofOfBurnListItem,
                    ProofOfBurnListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProofOfBurnListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getPubKey());
                        } else
                            setText("");
                    }
                };
            }
        });
        allTxsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ProofOfBurnListItem::getPubKey));


        column = new AutoTooltipTableColumn<>("");
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.getStyleClass().add("last-column");
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<ProofOfBurnListItem, ProofOfBurnListItem> call(TableColumn<ProofOfBurnListItem,
                            ProofOfBurnListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final ProofOfBurnListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("dao.proofOfBurn.verify"));
                                        setGraphic(button);
                                    }
                                    button.setOnAction(e -> new MessageVerificationWindow(signVerifyService, item.getTxId()).show());
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
        allTxsTableView.getColumns().add(column);
        column.setSortable(false);
    }
}
