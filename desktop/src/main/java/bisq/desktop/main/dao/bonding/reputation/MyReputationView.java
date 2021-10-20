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
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.reputation.MyBondedReputation;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.validation.HexStringValidator;
import bisq.core.util.validation.IntegerValidator;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.base.Charsets;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class MyReputationView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private InputTextField amountInputTextField, timeInputTextField, saltInputTextField;
    private Button lockupButton;
    private TableView<MyReputationListItem> tableView;

    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final BondingViewUtils bondingViewUtils;
    private final HexStringValidator hexStringValidator;
    private final BsqValidator bsqValidator;
    private final DaoFacade daoFacade;
    private final Preferences preferences;

    private final IntegerValidator timeInputTextFieldValidator;

    private final ObservableList<MyReputationListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<MyReputationListItem> sortedList = new SortedList<>(observableList);

    private int gridRow = 0;

    private ChangeListener<Boolean> amountFocusOutListener, timeFocusOutListener, saltFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener, timeInputTextFieldListener, saltInputTextFieldListener;
    private ListChangeListener<MyBondedReputation> myBondedReputationsChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MyReputationView(BsqFormatter bsqFormatter,
                             BsqWalletService bsqWalletService,
                             BondingViewUtils bondingViewUtils,
                             HexStringValidator hexStringValidator,
                             BsqValidator bsqValidator,
                             DaoFacade daoFacade,
                             Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
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
        addTitledGroupBg(root, gridRow, 3, Res.get("dao.bond.reputation.header"));

        amountInputTextField = addInputTextField(root, gridRow, Res.get("dao.bond.reputation.amount"),
                Layout.FIRST_ROW_DISTANCE);
        amountInputTextField.setValidator(bsqValidator);

        timeInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bond.reputation.time"));
        timeInputTextField.setValidator(timeInputTextFieldValidator);

        saltInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bond.reputation.salt"));
        saltInputTextField.setValidator(hexStringValidator);

        lockupButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.bond.reputation.lockupButton"));

        tableView = FormBuilder.addTableViewWithHeader(root, ++gridRow, Res.get("dao.bond.reputation.table.header"), 20, "last");
        createColumns();
        tableView.setItems(sortedList);
        GridPane.setVgrow(tableView, Priority.ALWAYS);

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

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        daoFacade.getMyBondedReputations().addListener(myBondedReputationsChangeListener);
        bsqWalletService.addBsqBalanceListener(this);

        lockupButton.setOnAction((event) -> {
            Coin lockupAmount = ParsingUtils.parseToCoin(amountInputTextField.getText(), bsqFormatter);
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


        amountInputTextField.resetValidation();
        timeInputTextField.resetValidation();

        setNewRandomSalt();

        updateList();
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 30);
    }

    @Override
    protected void deactivate() {
        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        timeInputTextField.textProperty().removeListener(timeInputTextFieldListener);
        timeInputTextField.focusedProperty().removeListener(timeFocusOutListener);

        saltInputTextField.textProperty().removeListener(saltInputTextFieldListener);
        saltInputTextField.focusedProperty().removeListener(saltFocusOutListener);

        daoFacade.getMyBondedReputations().removeListener(myBondedReputationsChangeListener);
        bsqWalletService.removeBsqBalanceListener(this);

        sortedList.comparatorProperty().unbind();

        lockupButton.setOnAction(null);
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
        bsqValidator.setAvailableBalance(availableBalance);
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
        timeFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
            }
        };
        saltFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
            }
        };

        amountInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        timeInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        saltInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();

        myBondedReputationsChangeListener = c -> updateList();
    }

    private void updateList() {
        observableList.setAll(daoFacade.getMyBondedReputations().stream()
                .map(myBondedReputation -> new MyReputationListItem(myBondedReputation, bsqFormatter))
                .sorted(Comparator.comparing(MyReputationListItem::getLockupDateString).reversed())
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 30);
    }

    private void setNewRandomSalt() {
        byte[] randomBytes = UUID.randomUUID().toString().getBytes(Charsets.UTF_8);
        // We want to limit it to 20 bytes
        byte[] hashOfRandomBytes = Hash.getSha256Ripemd160hash(randomBytes);
        // bytesAsHexString results in 40 chars
        String bytesAsHexString = Utilities.bytesAsHexString(hashOfRandomBytes);
        saltInputTextField.setText(bytesAsHexString);
        saltInputTextField.resetValidation();
    }

    private void updateButtonState() {
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid &&
                timeInputTextFieldValidator.validate(timeInputTextField.getText()).isValid &&
                hexStringValidator.validate(saltInputTextField.getText()).isValid;
        lockupButton.setDisable(!isValid);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<MyReputationListItem, MyReputationListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(120);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                    MyReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyReputationListItem item, boolean empty) {
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockTime"));
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                    MyReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyReputationListItem item, boolean empty) {
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.bondState"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(120);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                            MyReputationListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(MyReputationListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getBondStateString());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockupDate"));
        column.setMinWidth(140);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                    MyReputationListItem> column) {
                return new TableCell<>() {

                    @Override
                    public void updateItem(final MyReputationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getLockupDateString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.table.column.lockupTxId"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(80);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                            MyReputationListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final MyReputationListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                //noinspection Duplicates
                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new ExternalHyperlink(transactionId);
                                    hyperlinkWithIcon.setOnAction(event -> GUIUtil.openTxInBsqBlockExplorer(item.getTxId(), preferences));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.reputation.salt"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                    MyReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyReputationListItem item, boolean empty) {
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.bond.reputation.hash"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                    MyReputationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final MyReputationListItem item, boolean empty) {
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
        column.getStyleClass().add("last-column");
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MyReputationListItem, MyReputationListItem> call(TableColumn<MyReputationListItem,
                            MyReputationListItem> column) {
                        return new TableCell<>() {
                            AutoTooltipButton button;

                            @Override
                            public void updateItem(final MyReputationListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty && item.isShowButton()) {
                                    button = new AutoTooltipButton(item.getButtonText());
                                    button.setOnAction(e -> {
                                        if (item.getBondState() == BondState.LOCKUP_TX_CONFIRMED) {
                                            bondingViewUtils.unLock(item.getLockupTxId(),
                                                    txId -> {
                                                    });
                                        }
                                    });
                                    setGraphic(button);
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
        tableView.getColumns().add(column);
    }
}
