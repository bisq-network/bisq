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
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.dao.governance.asset.StatefulAsset;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class AssetFeeView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    public ComboBox<StatefulAsset> assetComboBox;
    private InputTextField feeAmountInputTextField;
    private TextField trialPeriodTextField;
    private Button payFeeButton;
    private TableView<AssetListItem> tableView;

    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final BsqValidator bsqValidator;
    private final DaoFacade daoFacade;
    private final AssetService assetService;
    private BSFormatter btcFormatter;
    private final Preferences preferences;

    private final ObservableList<AssetListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<AssetListItem> sortedList = new SortedList<>(observableList);

    private int gridRow = 0;

    private ChangeListener<Boolean> amountFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener;
    private ListChangeListener<StatefulAsset> statefulAssetsChangeListener;
    @Nullable
    private StatefulAsset selectedAsset;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AssetFeeView(BsqFormatter bsqFormatter,
                         BsqWalletService bsqWalletService,
                         BondingViewUtils bondingViewUtils,
                         BsqValidator bsqValidator,
                         DaoFacade daoFacade,
                         AssetService assetService,
                         BSFormatter btcFormatter,
                         Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.bsqValidator = bsqValidator;
        this.daoFacade = daoFacade;
        this.assetService = assetService;
        this.btcFormatter = btcFormatter;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 3, Res.get("dao.burnBsq.header"));

        assetComboBox = FormBuilder.<StatefulAsset>addComboBox(root, gridRow,
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

        tableView = FormBuilder.addTableViewWithHeader(root, ++gridRow, Res.get("dao.burnBsq.allAssets"), 20);
        createColumns();
        tableView.setItems(sortedList);

        createListeners();
    }

    @Override
    protected void activate() {
        assetComboBox.setItems(assetService.getStatefulAssets());
        assetComboBox.setOnAction(e -> {
            selectedAsset = assetComboBox.getSelectionModel().getSelectedItem();
        });

        feeAmountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        feeAmountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        assetService.getStatefulAssets().addListener(statefulAssetsChangeListener);
        bsqWalletService.addBsqBalanceListener(this);

        payFeeButton.setOnAction((event) -> {
            Coin listingFee = bsqFormatter.parseToCoin(feeAmountInputTextField.getText());
            try {
                Transaction transaction = assetService.payFee(selectedAsset, listingFee.value);
                Coin miningFee = transaction.getFee();
                int txSize = transaction.bitcoinSerialize().length;

                if (!DevEnv.isDevMode()) {
                    GUIUtil.showBsqFeeInfoPopup(listingFee, miningFee, txSize, bsqFormatter, btcFormatter,
                            Res.get("dao.burnBsq.assetFee"), () -> doPublishMyProposal(transaction, listingFee));
                } else {
                    doPublishMyProposal(transaction, listingFee);
                }
            } catch (InsufficientMoneyException | TxException e) {
                e.printStackTrace();
                new Popup<>().error(e.toString()).show();
            }

        });

        feeAmountInputTextField.resetValidation();

        updateList();
        updateButtonState();
    }

    private void doPublishMyProposal(Transaction transaction, Coin listingFee) {
        assetService.publishTransaction(selectedAsset, transaction, listingFee.value,
                () -> {
                    assetComboBox.getSelectionModel().clearSelection();
                    if (!DevEnv.isDevMode())
                        new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                },
                errorMessage -> new Popup<>().warning(errorMessage).show());
    }

    @Override
    protected void deactivate() {
        assetComboBox.setOnAction(null);

        feeAmountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        feeAmountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        assetService.getStatefulAssets().removeListener(statefulAssetsChangeListener);
        bsqWalletService.removeBsqBalanceListener(this);

        sortedList.comparatorProperty().unbind();

        payFeeButton.setOnAction(null);
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

        amountInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();

        statefulAssetsChangeListener = c -> updateList();
    }

    private void updateList() {
        observableList.setAll(assetService.getStatefulAssets().stream()
                .map(statefulAsset -> new AssetListItem(statefulAsset, bsqFormatter))
                .sorted(Comparator.comparing(AssetListItem::getNameAndCode))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(tableView, 41, 28, 2, 10);
    }

    private void updateButtonState() {
        boolean isValid = bsqValidator.validate(feeAmountInputTextField.getText()).isValid &&
                selectedAsset != null;
        payFeeButton.setDisable(!isValid);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<AssetListItem, AssetListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.nameAndCode"));
        column.setMinWidth(120);
        column.setMaxWidth(column.getMinWidth());
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.activeFee"));
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
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
                            setText(item.getActiveFeeAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burnBsq.assets.tradedVolume"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(120);
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<AssetListItem, AssetListItem> call(TableColumn<AssetListItem,
                            AssetListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(AssetListItem item, boolean empty) {
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
    }
}
