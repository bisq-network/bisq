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

package bisq.desktop.main.dao.burnbsq.assetfee;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.dao.governance.asset.StatefulAsset;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class AssetFeeView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, DaoStateListener {
    private ComboBox<StatefulAsset> assetComboBox;
    private InputTextField feeAmountInputTextField;
    private TextField trialPeriodTextField;
    private Button payFeeButton;
    private TableView<AssetListItem> tableView;

    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final BsqValidator bsqValidator;
    private final AssetService assetService;
    private final DaoStateService daoStateService;
    private final CoinFormatter btcFormatter;

    private final ObservableList<AssetListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<AssetListItem> sortedList = new SortedList<>(observableList);

    private int gridRow = 0;

    private ChangeListener<Boolean> amountFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener;
    @Nullable
    private StatefulAsset selectedAsset;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AssetFeeView(BsqFormatter bsqFormatter,
                        BsqWalletService bsqWalletService,
                        BsqValidator bsqValidator,
                        AssetService assetService,
                        DaoStateService daoStateService,
                        @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.bsqValidator = bsqValidator;
        this.assetService = assetService;
        this.daoStateService = daoStateService;
        this.btcFormatter = btcFormatter;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 3, Res.get("dao.burnBsq.header"));

        assetComboBox = FormBuilder.addComboBox(root, gridRow,
                Res.get("dao.burnBsq.selectAsset"), Layout.FIRST_ROW_DISTANCE);
        assetComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatefulAsset statefulAsset) {
                return CurrencyUtil.getNameAndCode(statefulAsset.getAsset().getTickerSymbol());
            }

            @Override
            public StatefulAsset fromString(String string) {
                return null;
            }
        });

        feeAmountInputTextField = addInputTextField(root, ++gridRow, Res.get("dao.burnBsq.fee"));
        feeAmountInputTextField.setValidator(bsqValidator);

        trialPeriodTextField = FormBuilder.addTopLabelTextField(root, ++gridRow, Res.get("dao.burnBsq.trialPeriod")).second;

        payFeeButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.burnBsq.payFee"));

        tableView = FormBuilder.addTableViewWithHeader(root, ++gridRow, Res.get("dao.burnBsq.allAssets"), 20, "last");
        createColumns();
        tableView.setItems(sortedList);

        createListeners();
    }

    @Override
    protected void activate() {
        assetComboBox.setOnAction(e -> {
            selectedAsset = assetComboBox.getSelectionModel().getSelectedItem();
        });

        feeAmountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        feeAmountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        daoStateService.addDaoStateListener(this);

        bsqWalletService.addBsqBalanceListener(this);

        assetService.updateAssetStates();
        updateList();

        onUpdateAvailableBalance(bsqWalletService.getAvailableBalance());

        payFeeButton.setOnAction((event) -> {
            Coin listingFee = getListingFee();
            long days = getDays();
            // We don't allow shorter periods as it would allow an attacker to try to deactivate other coins by making a
            // small fee payment to reduce the trial period and look back period.
            // Still not a perfect solution but should be good enough for now.
            long minDays = 30;
            if (days >= minDays) {
                try {
                    Transaction transaction = assetService.payFee(selectedAsset, listingFee.value);
                    Coin miningFee = transaction.getFee();
                    int txVsize = transaction.getVsize();

                    if (!DevEnv.isDevMode()) {
                        GUIUtil.showBsqFeeInfoPopup(listingFee, miningFee, txVsize, bsqFormatter, btcFormatter,
                                Res.get("dao.burnBsq.assetFee"), () -> doPublishFeeTx(transaction));
                    } else {
                        doPublishFeeTx(transaction);
                    }
                } catch (InsufficientMoneyException | TxException e) {
                    e.printStackTrace();
                    new Popup().error(e.toString()).show();
                }
            } else {
                new Popup().warning(Res.get("dao.burnBsq.assets.toFewDays", minDays)).show();
            }
        });


        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 100);
        updateButtonState();

        feeAmountInputTextField.resetValidation();
    }

    @Override
    protected void deactivate() {
        assetComboBox.setOnAction(null);

        feeAmountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        feeAmountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        daoStateService.removeDaoStateListener(this);

        bsqWalletService.removeBsqBalanceListener(this);

        sortedList.comparatorProperty().unbind();

        payFeeButton.setOnAction(null);
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
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        // Delay a bit to reduce load at onParseBlockCompleteAfterBatchProcessing event
        UserThread.runAfter(() -> {
            assetService.updateAssetStates();
            updateList();
        }, 300, TimeUnit.MILLISECONDS);
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
            trialPeriodTextField.setText(Res.get("dao.burnBsq.assets.days", getDays()));
            updateButtonState();
        };
    }

    private void onUpdateAvailableBalance(Coin availableBalance) {
        bsqValidator.setAvailableBalance(availableBalance);
        updateButtonState();
    }

    private long getDays() {
        return getListingFee().value / assetService.getFeePerDay().value;
    }

    // We only update on new BSQ blocks and at view activation. We do not update at each trade statistics change as
    // that would cause too much CPU load. The assetService.updateAssetStates() call takes about 22 ms.
    private void updateList() {
        // Here we exclude the assets which have been removed by voting. Paying a fee would not change the state.
        List<StatefulAsset> statefulAssets = assetService.getStatefulAssets();
        ObservableList<StatefulAsset> nonRemovedStatefulAssets = FXCollections.observableArrayList(statefulAssets.stream()
                .filter(e -> !e.wasRemovedByVoting())
                .collect(Collectors.toList()));
        assetComboBox.setItems(nonRemovedStatefulAssets);

        // In the table we want to show all including removed assets.
        observableList.setAll(statefulAssets.stream()
                .map(statefulAsset -> new AssetListItem(statefulAsset, bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 100);
    }

    private void updateButtonState() {
        boolean isValid = bsqValidator.validate(feeAmountInputTextField.getText()).isValid &&
                selectedAsset != null;
        payFeeButton.setDisable(!isValid);
    }

    private Coin getListingFee() {
        return ParsingUtils.parseToCoin(feeAmountInputTextField.getText(), bsqFormatter);
    }

    private void doPublishFeeTx(Transaction transaction) {
        assetService.publishTransaction(transaction,
                () -> {
                    assetComboBox.getSelectionModel().clearSelection();
                    if (!DevEnv.isDevMode())
                        new Popup().confirmation(Res.get("dao.tx.published.success")).show();
                },
                errorMessage -> new Popup().warning(errorMessage).show());

        feeAmountInputTextField.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<AssetListItem, AssetListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.nameAndCode"));
        column.setMinWidth(120);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                    AssetListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final AssetListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getNameAndCode());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(AssetListItem::getNameAndCode));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.state"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                    AssetListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final AssetListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAssetStateString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(AssetListItem::getAssetStateString));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.tradeVolume"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                    AssetListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final AssetListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getTradedVolumeAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(AssetListItem::getTradedVolume));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.lookBackPeriod"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                    AssetListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final AssetListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getLookBackPeriodInDays());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(AssetListItem::getLookBackPeriodInDays));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.trialFee"));
        column.setMinWidth(120);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                    AssetListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final AssetListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getFeeOfTrialPeriodAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(AssetListItem::getFeeOfTrialPeriod));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.totalFee"));
        column.setMinWidth(120);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                    AssetListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final AssetListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getTotalFeesPaidAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(AssetListItem::getTotalFeesPaid));
    }
}
