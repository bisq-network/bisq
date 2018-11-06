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

package bisq.desktop.main.dao.bonding.reputation;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.HexStringValidator;
import bisq.core.util.validation.IntegerValidator;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.base.Charsets;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class MyBondedReputationView extends ActivatableView<GridPane, Void> implements DaoStateListener, BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BondingViewUtils bondingViewUtils;
    private final HexStringValidator hexStringValidator;
    private final BsqValidator bsqValidator;
    private final DaoFacade daoFacade;
    private final Preferences preferences;
    private final IntegerValidator timeInputTextFieldValidator;

    private int gridRow = 0;
    private InputTextField amountInputTextField, timeInputTextField, saltInputTextField;
    private Button lockupButton;
    private TableView<MyBondedReputationListItem> tableView;
    private ChangeListener<Boolean> amountFocusOutListener, timeFocusOutListener, saltFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener, timeInputTextFieldListener, saltInputTextFieldListener;
    private final ObservableList<MyBondedReputationListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<MyBondedReputationListItem> sortedList = new SortedList<>(observableList);
    private ListChangeListener<Transaction> walletBsqTransactionsListener;
    private ChangeListener<Number> walletChainHeightListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MyBondedReputationView(BsqWalletService bsqWalletService,
                                   BsqFormatter bsqFormatter,
                                   BondingViewUtils bondingViewUtils,
                                   HexStringValidator hexStringValidator,
                                   BsqValidator bsqValidator,
                                   DaoFacade daoFacade,
                                   Preferences preferences) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.bondingViewUtils = bondingViewUtils;
        this.hexStringValidator = hexStringValidator;
        this.bsqValidator = bsqValidator;
        this.daoFacade = daoFacade;
        this.preferences = preferences;

        timeInputTextFieldValidator = new IntegerValidator();
        timeInputTextFieldValidator.setMinValue(BondConsensus.getMinLockTime());
        timeInputTextFieldValidator.setMaxValue(BondConsensus.getMaxLockTime());
    }

    @Override
    public void initialize() {
        int columnSpan = 3;
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 3, Res.get("dao.bonding.reputation.header"));
        GridPane.setColumnSpan(titledGroupBg, columnSpan);

        amountInputTextField = addInputTextField(root, gridRow, Res.get("dao.bonding.lock.amount"),
                Layout.FIRST_ROW_DISTANCE);
        amountInputTextField.setValidator(bsqValidator);
        GridPane.setColumnSpan(amountInputTextField, columnSpan);

        timeInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.time"));
        GridPane.setColumnSpan(timeInputTextField, columnSpan);
        timeInputTextField.setValidator(timeInputTextFieldValidator);

        saltInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.salt"));
        GridPane.setColumnSpan(saltInputTextField, columnSpan);
        saltInputTextField.setValidator(hexStringValidator);

        lockupButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.bonding.lock.lockupButton"));

        createTableView(columnSpan);

        createListeners();
    }

    @Override
    protected void activate() {
        amountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        timeInputTextField.textProperty().addListener(timeInputTextFieldListener);
        timeInputTextField.focusedProperty().addListener(timeFocusOutListener);

        saltInputTextField.textProperty().addListener(saltInputTextFieldListener);
        saltInputTextField.focusedProperty().addListener(saltFocusOutListener);

        bsqWalletService.getWalletTransactions().addListener(walletBsqTransactionsListener);
        bsqWalletService.addBsqBalanceListener(this);
        bsqWalletService.getChainHeightProperty().addListener(walletChainHeightListener);

        lockupButton.setOnAction((event) -> {
            Coin lockupAmount = bsqFormatter.parseToCoin(amountInputTextField.getText());
            int lockupTime = Integer.parseInt(timeInputTextField.getText());
            byte[] salt = Utilities.decodeFromHex(saltInputTextField.getText());
            bondingViewUtils.lockupBondForReputation(lockupAmount,
                    lockupTime,
                    salt,
                    txId -> {
                    });
            amountInputTextField.setText("");
            timeInputTextField.setText("");
            setNewRandomSalt();
        });

        daoFacade.addBsqStateListener(this);

        amountInputTextField.resetValidation();
        timeInputTextField.resetValidation();
        setNewRandomSalt();

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        onUpdateBalances();
        updateList();
    }

    @Override
    protected void deactivate() {
        observableList.forEach(MyBondedReputationListItem::cleanup);

        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        timeInputTextField.textProperty().removeListener(timeInputTextFieldListener);
        timeInputTextField.focusedProperty().removeListener(timeFocusOutListener);

        saltInputTextField.textProperty().removeListener(saltInputTextFieldListener);
        saltInputTextField.focusedProperty().removeListener(saltFocusOutListener);

        bsqWalletService.getWalletTransactions().removeListener(walletBsqTransactionsListener);
        bsqWalletService.addBsqBalanceListener(this);
        bsqWalletService.getChainHeightProperty().removeListener(walletChainHeightListener);

        lockupButton.setOnAction(null);

        sortedList.comparatorProperty().unbind();

        daoFacade.removeBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        updateList();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        bsqValidator.setAvailableBalance(confirmedBalance);
        updateButtonState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createListeners() {
        amountFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };
        timeFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };
        saltFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };

        amountInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        timeInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        saltInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();

        walletBsqTransactionsListener = change -> updateList();
        walletChainHeightListener = (observable, oldValue, newValue) -> updateList();
    }

    private void createTableView(int columnSpan) {
        TitledGroupBg titledGroupBg2 = addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.bonding.reputation.list.header"), 20);
        GridPane.setColumnSpan(titledGroupBg2, columnSpan);

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        createColumns();

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        GridPane.setRowIndex(vBox, ++gridRow);
        GridPane.setColumnSpan(vBox, columnSpan);
        GridPane.setMargin(vBox, new Insets(50, -10, 5, -10));
        vBox.getChildren().addAll(tableView);
        root.getChildren().add(vBox);
    }

    private void setNewRandomSalt() {
        byte[] randomBytes = UUID.randomUUID().toString().getBytes(Charsets.UTF_8);
        byte[] hashOfRandomBytes = Hash.getSha256Ripemd160hash(randomBytes);
        saltInputTextField.setText(Utilities.bytesAsHexString(hashOfRandomBytes));
        saltInputTextField.resetValidation();
    }

    private void onUpdateBalances() {
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
    }

    private void updateButtonState() {
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid &&
                timeInputTextFieldValidator.validate(timeInputTextField.getText()).isValid &&
                hexStringValidator.validate(saltInputTextField.getText()).isValid;
        lockupButton.setDisable(!isValid);
    }

    private void openTxInBlockExplorer(MyBondedReputationListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + item.getTxId());
    }

    private void updateList() {
        List<MyBondedReputationListItem> items = daoFacade.getMyBondedReputations().stream()
                .map(myBondedReputation -> {
                    return new MyBondedReputationListItem(myBondedReputation, daoFacade, bondingViewUtils, bsqFormatter);
                })
                .sorted(Comparator.comparing(MyBondedReputationListItem::getLockupDate).reversed())
                .collect(Collectors.toList());
        observableList.setAll(items);
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 10);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<MyBondedReputationListItem, MyBondedReputationListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(120);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                    MyBondedReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmount());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.lockTime"));
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                    MyBondedReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getLockTime());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.bondState"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(120);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                            MyBondedReputationListItem> column) {
                        return new TableCell<>() {
                            Label label;

                            @Override
                            public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (label == null) {
                                        label = item.getStateLabel();
                                        setGraphic(label);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (label != null)
                                        label = null;
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.header.lockupDate"));
        column.setMinWidth(140);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                    MyBondedReputationListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getLockupDate());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bonding.bonds.table.lockupTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                            MyBondedReputationListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                //noinspection Duplicates
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
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bonding.unlock.salt"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                    MyBondedReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getSalt());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bonding.unlock.hash"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                    MyBondedReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getHash());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new TableColumn<>();
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyBondedReputationListItem, MyBondedReputationListItem> call(TableColumn<MyBondedReputationListItem,
                            MyBondedReputationListItem> column) {
                        return new TableCell<>() {
                            Button button;

                            @Override
                            public void updateItem(final MyBondedReputationListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = item.getButton();
                                        setGraphic(button);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (button != null)
                                        button = null;
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }
}
