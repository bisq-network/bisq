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

package bisq.desktop.main.dao.burnbsq.burningmen;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BurningManService;
import bisq.core.dao.governance.proofofburn.ProofOfBurnService;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class BurningmenView extends ActivatableView<ScrollPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final BurningManService burningManService;
    private final ProofOfBurnService proofOfBurnService;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final BsqValidator burnAmountValidator;

    private InputTextField amountInputTextField, burningmenFilterField;
    private ComboBox<BurningmenListItem> contributorComboBox;
    private Button burnButton;
    private TitledGroupBg burnOutputsTitledGroupBg, compensationsTitledGroupBg, selectedContributorTitledGroupBg;
    private AutoTooltipSlideToggleButton showOnlyActiveBurningmenToggle;
    private TextField expectedRevenueField, selectedContributorNameField, selectedContributorAddressField, burnTargetField;
    private VBox selectedContributorNameBox, selectedContributorAddressBox;
    private TableView<BurningmenListItem> burningmenTableView;
    private TableView<BurnOutputListItem> burnOutputsTableView;
    private TableView<CompensationListItem> compensationsTableView;
    private TableView<ReimbursementListItem> reimbursementsTableView;

    private final ObservableList<BurningmenListItem> burningmenObservableList = FXCollections.observableArrayList();
    private final FilteredList<BurningmenListItem> burningmenFilteredList = new FilteredList<>(burningmenObservableList);
    private final SortedList<BurningmenListItem> burningmenSortedList = new SortedList<>(burningmenFilteredList);
    private final ObservableList<BurnOutputListItem> burnOutputsObservableList = FXCollections.observableArrayList();
    private final SortedList<BurnOutputListItem> burnOutputsSortedList = new SortedList<>(burnOutputsObservableList);
    private final ObservableList<CompensationListItem> compensationObservableList = FXCollections.observableArrayList();
    private final SortedList<CompensationListItem> compensationSortedList = new SortedList<>(compensationObservableList);
    private final ObservableList<ReimbursementListItem> reimbursementObservableList = FXCollections.observableArrayList();
    private final SortedList<ReimbursementListItem> reimbursementSortedList = new SortedList<>(reimbursementObservableList);

    private final ChangeListener<Boolean> amountFocusOutListener;
    private final ChangeListener<String> amountInputTextFieldListener;
    private final ChangeListener<BurningmenListItem> burningmenSelectionListener;
    private final ChangeListener<String> filterListener;
    private final ChangeListener<BurningmenListItem> contributorsListener;

    private int gridRow = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BurningmenView(DaoFacade daoFacade,
                           BurningManService burningManService,
                           ProofOfBurnService proofOfBurnService,
                           BsqFormatter bsqFormatter,
                           @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                           BsqValidator bsqValidator) {
        this.daoFacade = daoFacade;
        this.burningManService = burningManService;
        this.proofOfBurnService = proofOfBurnService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.burnAmountValidator = bsqValidator;

        amountFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
            }
        };

        amountInputTextFieldListener = (observable, oldValue, newValue) -> {
            updateButtonState();
        };

        burningmenSelectionListener = (observable, oldValue, newValue) -> {
            boolean isValueSet = newValue != null;
            burnOutputsTableView.setVisible(isValueSet);
            burnOutputsTableView.setManaged(isValueSet);
            burnOutputsTitledGroupBg.setVisible(isValueSet);
            burnOutputsTitledGroupBg.setManaged(isValueSet);
            compensationsTableView.setVisible(isValueSet);
            compensationsTableView.setManaged(isValueSet);
            compensationsTitledGroupBg.setVisible(isValueSet);
            compensationsTitledGroupBg.setManaged(isValueSet);
            selectedContributorTitledGroupBg.setManaged(isValueSet);
            selectedContributorTitledGroupBg.setVisible(isValueSet);
            selectedContributorNameBox.setManaged(isValueSet);
            selectedContributorNameBox.setVisible(isValueSet);
            selectedContributorAddressBox.setManaged(isValueSet);
            selectedContributorAddressBox.setVisible(isValueSet);
            if (isValueSet) {
                onBurningManSelected(newValue);
            } else {
                selectedContributorNameField.clear();
                selectedContributorAddressField.clear();
            }
        };

        filterListener = (observable, oldValue, newValue) -> updateBurningmenPredicate();

        contributorsListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                bsqValidator.setMaxValue(Coin.valueOf(newValue.getAllowedBurnAmount()));
                amountInputTextField.clear();
                amountInputTextField.resetValidation();
                amountInputTextField.setPromptText(Res.get("dao.burningmen.amount.prompt.max",
                        bsqFormatter.formatCoinWithCode(newValue.getAllowedBurnAmount())));
                updateButtonState();
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(0, 20, 0, 10));
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setPercentWidth(50);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setPercentWidth(50);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);

        root.setContent(gridPane);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setFitToWidth(true);

        // Burn target
        TitledGroupBg targetTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 2, Res.get("dao.burningmen.target.header"));
        GridPane.setColumnSpan(targetTitledGroupBg, 2);
        burnTargetField = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("dao.burningmen.burnTarget"), "", Layout.FLOATING_LABEL_DISTANCE).second;
        Tuple3<Label, TextField, VBox> currentBlockHeightTuple = addCompactTopLabelTextField(gridPane, gridRow,
                Res.get("dao.burningmen.expectedRevenue"), "", Layout.FLOATING_LABEL_DISTANCE);
        expectedRevenueField = currentBlockHeightTuple.second;
        GridPane.setColumnIndex(currentBlockHeightTuple.third, 1);

        // Burn inputs
        addTitledGroupBg(gridPane, ++gridRow, 4, Res.get("dao.burningmen.burn.header"), Layout.COMPACT_GROUP_DISTANCE);
        contributorComboBox = addComboBox(gridPane, gridRow, Res.get("dao.burningmen.contributorsComboBox.prompt"), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);
        contributorComboBox.setMaxWidth(300);
        contributorComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BurningmenListItem item) {
                return item.getName();
            }

            @Override
            public BurningmenListItem fromString(String string) {
                return null;
            }
        });
        amountInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("dao.burningmen.amount.prompt"));
        burnButton = addButtonAfterGroup(gridPane, ++gridRow, Res.get("dao.proofOfBurn.burn"));

        // Burningmen candidates
        Tuple3<InputTextField, TableView<BurningmenListItem>, HBox> burningmenTuple = addTableViewWithHeaderAndFilterField(gridPane,
                ++gridRow,
                Res.get("dao.burningmen.candidates.table.header"),
                Res.get("dao.burningmen.filter"),
                30);
        burningmenFilterField = burningmenTuple.first;
        burningmenTableView = burningmenTuple.second;
        GridPane.setColumnSpan(burningmenTableView, 2);
        createBurningmenColumns();
        burningmenTableView.setItems(burningmenSortedList);
        HBox hBox = burningmenTuple.third;
        GridPane.setColumnSpan(hBox, 2);
        showOnlyActiveBurningmenToggle = new AutoTooltipSlideToggleButton();
        showOnlyActiveBurningmenToggle.setText(Res.get("dao.burningmen.toggle"));
        HBox.setMargin(showOnlyActiveBurningmenToggle, new Insets(-21, 0, 0, 0));
        hBox.getChildren().add(2, showOnlyActiveBurningmenToggle);

        // Selected contributor
        selectedContributorTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 3,
                Res.get("dao.burningmen.selectedContributor"), Layout.COMPACT_GROUP_DISTANCE);
        selectedContributorTitledGroupBg.setManaged(false);
        selectedContributorTitledGroupBg.setVisible(false);
        Tuple3<Label, TextField, VBox> nameTuple = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("dao.burningmen.selectedContributorName"), "",
                Layout.COMPACT_GROUP_DISTANCE + Layout.FLOATING_LABEL_DISTANCE);
        selectedContributorNameField = nameTuple.second;
        selectedContributorNameBox = nameTuple.third;
        selectedContributorNameBox.setManaged(false);
        selectedContributorNameBox.setVisible(false);

        Tuple3<Label, TextField, VBox> addressTuple = addCompactTopLabelTextField(gridPane, gridRow,
                Res.get("dao.burningmen.selectedContributorAddress"), "",
                Layout.COMPACT_GROUP_DISTANCE + Layout.FLOATING_LABEL_DISTANCE);
        selectedContributorAddressField = addressTuple.second;
        selectedContributorAddressBox = addressTuple.third;
        GridPane.setColumnSpan(selectedContributorAddressBox, 2);
        GridPane.setColumnIndex(selectedContributorAddressBox, 1);
        selectedContributorAddressBox.setManaged(false);
        selectedContributorAddressBox.setVisible(false);

        // BurnOutputs
        Tuple2<TableView<BurnOutputListItem>, TitledGroupBg> burnOutputTuple = addTableViewWithHeader(gridPane, ++gridRow,
                Res.get("dao.burningmen.burnOutput.table.header"), 30);
        burnOutputsTableView = burnOutputTuple.first;
        GridPane.setMargin(burnOutputsTableView, new Insets(60, 0, 5, -10));
        createBurnOutputsColumns();
        burnOutputsTableView.setItems(burnOutputsSortedList);
        burnOutputsTableView.setVisible(false);
        burnOutputsTableView.setManaged(false);
        burnOutputsTitledGroupBg = burnOutputTuple.second;
        burnOutputsTitledGroupBg.setVisible(false);
        burnOutputsTitledGroupBg.setManaged(false);

        // Compensations
        Tuple2<TableView<CompensationListItem>, TitledGroupBg> compensationTuple = addTableViewWithHeader(gridPane, gridRow,
                Res.get("dao.burningmen.compensations.table.header"), 30);
        compensationsTableView = compensationTuple.first;
        GridPane.setMargin(compensationsTableView, new Insets(60, -10, 5, 0));
        GridPane.setColumnIndex(compensationsTableView, 1);
        createCompensationColumns();
        compensationsTableView.setItems(compensationSortedList);
        compensationsTableView.setVisible(false);
        compensationsTableView.setManaged(false);
        compensationsTitledGroupBg = compensationTuple.second;
        GridPane.setColumnIndex(compensationsTitledGroupBg, 1);
        compensationsTitledGroupBg.setVisible(false);
        compensationsTitledGroupBg.setManaged(false);

        // Reimbursements
        reimbursementsTableView = FormBuilder.<ReimbursementListItem>addTableViewWithHeader(gridPane, ++gridRow,
                Res.get("dao.burningmen.reimbursement.table.header"), 30).first;
        GridPane.setColumnSpan(reimbursementsTableView, 2);
        createReimbursementColumns();
        reimbursementsTableView.setItems(reimbursementSortedList);
    }

    @Override
    protected void activate() {
        GUIUtil.setFitToRowsForTableView(burningmenTableView, 36, 28, 15, 30);
        GUIUtil.setFitToRowsForTableView(reimbursementsTableView, 36, 28, 3, 6);

        daoFacade.addBsqStateListener(this);

        amountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        burningmenFilterField.textProperty().addListener(filterListener);
        burningmenTableView.getSelectionModel().selectedItemProperty().addListener(burningmenSelectionListener);

        contributorComboBox.getSelectionModel().selectedItemProperty().addListener(contributorsListener);

        burningmenSortedList.comparatorProperty().bind(burningmenTableView.comparatorProperty());
        burnOutputsSortedList.comparatorProperty().bind(burnOutputsTableView.comparatorProperty());
        compensationSortedList.comparatorProperty().bind(compensationsTableView.comparatorProperty());
        reimbursementSortedList.comparatorProperty().bind(reimbursementsTableView.comparatorProperty());

        burnButton.setOnAction((event) -> {
            BurningmenListItem selectedItem = contributorComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                Coin amount = getAmountFee();
                String name = selectedItem.getName();
                try {
                    Transaction transaction = proofOfBurnService.burn(name, amount.value);
                    Coin miningFee = transaction.getFee();
                    int txVsize = transaction.getVsize();

                    if (!DevEnv.isDevMode()) {
                        GUIUtil.showBsqFeeInfoPopup(amount, miningFee, txVsize, bsqFormatter, btcFormatter,
                                Res.get("dao.proofOfBurn.header"), () -> doPublishFeeTx(transaction, name));
                    } else {
                        doPublishFeeTx(transaction, name);
                    }
                } catch (InsufficientMoneyException | TxException e) {
                    e.printStackTrace();
                    new Popup().error(e.toString()).show();
                }
            }
        });

        showOnlyActiveBurningmenToggle.setOnAction(e -> updateBurningmenPredicate());

        amountInputTextField.setValidator(burnAmountValidator);

        if (daoFacade.isParseBlockChainComplete()) {
            updateData();
        }

        updateButtonState();
        updateBurningmenPredicate();
    }

    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);

        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        burningmenFilterField.textProperty().removeListener(filterListener);
        burningmenTableView.getSelectionModel().selectedItemProperty().removeListener(burningmenSelectionListener);

        contributorComboBox.getSelectionModel().selectedItemProperty().removeListener(contributorsListener);

        burningmenSortedList.comparatorProperty().unbind();
        burnOutputsSortedList.comparatorProperty().unbind();
        compensationSortedList.comparatorProperty().unbind();
        reimbursementSortedList.comparatorProperty().unbind();

        burnButton.setOnAction(null);
        showOnlyActiveBurningmenToggle.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateData() {
        burningmenObservableList.setAll(burningManService.getCurrentBurningManCandidatesByName().entrySet().stream()
                .map(entry -> new BurningmenListItem(entry.getKey(), entry.getValue(), bsqFormatter))
                .collect(Collectors.toList()));
        reimbursementObservableList.setAll(burningManService.getCurrentReimbursements().stream()
                .map(reimbursementModel -> new ReimbursementListItem(reimbursementModel, bsqFormatter))
                .collect(Collectors.toList()));

        expectedRevenueField.setText(bsqFormatter.formatCoinWithCode(burningManService.getAverageDistributionPerCycle()));
        burnTargetField.setText(bsqFormatter.formatCoinWithCode(burningManService.getCurrentBurnTarget()));

        if (daoFacade.isParseBlockChainComplete()) {
            Set<String> myContributorNames = burningManService.getMyCompensationRequestNames();
            burningManService.findMyGenesisOutputNames().ifPresent(myContributorNames::addAll);

            Map<String, BurningmenListItem> burningmenListItemByName = burningmenObservableList.stream()
                    .collect(Collectors.toMap(BurningmenListItem::getName, e -> e));
            List<BurningmenListItem> myBurningmenListItems = myContributorNames.stream()
                    .filter(burningmenListItemByName::containsKey)
                    .map(burningmenListItemByName::get)
                    .sorted(Comparator.comparing(BurningmenListItem::getName))
                    .collect(Collectors.toList());
            contributorComboBox.setItems(FXCollections.observableArrayList(myBurningmenListItems));
        }
    }

    private void updateBurningmenPredicate() {
        burningmenFilteredList.setPredicate(burningmenListItem -> {
            boolean showOnlyActiveBurningmen = showOnlyActiveBurningmenToggle.isSelected();
            String filterText = burningmenFilterField.getText();
            boolean activeBurnerOrShowAll = !showOnlyActiveBurningmen || burningmenListItem.getEffectiveBurnOutputShare() > 0;
            if (filterText == null || filterText.trim().isEmpty()) {
                return activeBurnerOrShowAll;
            } else {
                return activeBurnerOrShowAll && burningmenListItem.getName().toLowerCase().contains(filterText.toLowerCase());
            }
        });
    }

    private void onBurningManSelected(BurningmenListItem burningmenListItem) {
        selectedContributorNameField.setText(burningmenListItem.getName());
        selectedContributorAddressField.setText(burningmenListItem.getAddress());
        burnOutputsObservableList.setAll(burningmenListItem.getBurningManCandidate().getBurnOutputModels().stream()
                .map(burnOutputModel -> new BurnOutputListItem(burnOutputModel, bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(burnOutputsTableView, 36, 28, 4, 6);
        compensationObservableList.setAll(burningmenListItem.getBurningManCandidate().getCompensationModels().stream()
                .map(compensationModel -> new CompensationListItem(compensationModel, bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(compensationsTableView, 36, 28, 4, 6);
    }

    private void updateButtonState() {
        boolean isValid = burnAmountValidator.validate(amountInputTextField.getText()).isValid &&
                contributorComboBox.getSelectionModel().getSelectedItem() != null;
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
        amountInputTextField.setPromptText(Res.get("dao.burningmen.amount.prompt"));
        amountInputTextField.resetValidation();
        contributorComboBox.getSelectionModel().clearSelection();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createBurningmenColumns() {
        TableColumn<BurningmenListItem, BurningmenListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.name"));
        column.setMinWidth(140);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.getStyleClass().add("first-column");
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setMinHeight(36);
                            setText(item.getName());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getName().toLowerCase()));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.allowedBurnAmount"));
        column.setMinWidth(110);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAllowedBurnAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getAllowedBurnAmount));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.expectedRevenue"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getExpectedRevenueAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getExpectedRevenue));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.effectiveBurnOutputShare"));
        column.setMinWidth(110);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getEffectiveBurnOutputShareAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.setComparator(Comparator.comparing(BurningmenListItem::getEffectiveBurnOutputShare));
        burningmenTableView.getSortOrder().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.decayedBurnAmount"));
        column.setMinWidth(160);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedDecayedBurnAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getAccumulatedDecayedBurnAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.burnAmount"));
        column.setMinWidth(110);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedBurnAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getAccumulatedBurnAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.numBurnOutputs"));
        column.setMinWidth(90);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getNumBurnOutputs()));
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getNumBurnOutputs));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.issuanceShare"));
        column.setMinWidth(110);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getIssuanceShareAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getIssuanceShare));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.decayedIssuanceAmount"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedDecayedCompensationAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getAccumulatedDecayedCompensationAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.issuanceAmount"));
        column.setMinWidth(120);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedCompensationAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getAccumulatedCompensationAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.numIssuances"));
        column.setMinWidth(110);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningmenListItem, BurningmenListItem> call(TableColumn<BurningmenListItem,
                    BurningmenListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningmenListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getNumIssuances()));
                        } else
                            setText("");
                    }
                };
            }
        });
        burningmenTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningmenListItem::getNumIssuances));
        column.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void createBurnOutputsColumns() {
        TableColumn<BurnOutputListItem, BurnOutputListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.date"));
        column.setMinWidth(160);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurnOutputListItem, BurnOutputListItem> call(TableColumn<BurnOutputListItem,
                    BurnOutputListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurnOutputListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setMinHeight(36);
                            setText(item.getDateAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burnOutputsTableView.getColumns().add(column);
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.setComparator(Comparator.comparing(BurnOutputListItem::getDate));
        burnOutputsTableView.getSortOrder().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.cycle"));
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurnOutputListItem, BurnOutputListItem> call(TableColumn<BurnOutputListItem,
                    BurnOutputListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurnOutputListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getCycleIndex() + 1));
                        } else
                            setText("");
                    }
                };
            }
        });
        burnOutputsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurnOutputListItem::getCycleIndex));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.height"));
        column.setMinWidth(90);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurnOutputListItem, BurnOutputListItem> call(TableColumn<BurnOutputListItem,
                    BurnOutputListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurnOutputListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getHeight()));
                        } else
                            setText("");
                    }
                };
            }
        });
        burnOutputsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurnOutputListItem::getHeight));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.decayedBurnAmount"));
        column.setMinWidth(160);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurnOutputListItem, BurnOutputListItem> call(TableColumn<BurnOutputListItem,
                    BurnOutputListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurnOutputListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getDecayedAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burnOutputsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurnOutputListItem::getDecayedAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.burnAmount"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurnOutputListItem, BurnOutputListItem> call(TableColumn<BurnOutputListItem,
                    BurnOutputListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurnOutputListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burnOutputsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurnOutputListItem::getAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void createCompensationColumns() {
        TableColumn<CompensationListItem, CompensationListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.date"));
        column.setMinWidth(160);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<CompensationListItem, CompensationListItem> call(TableColumn<CompensationListItem,
                    CompensationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final CompensationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setMinHeight(36);
                            setText(item.getDateAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        compensationsTableView.getColumns().add(column);
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.setComparator(Comparator.comparing(CompensationListItem::getDate));
        compensationsTableView.getSortOrder().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.cycle"));
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<CompensationListItem, CompensationListItem> call(TableColumn<CompensationListItem,
                    CompensationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final CompensationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getCycleIndex() + 1));
                        } else
                            setText("");
                    }
                };
            }
        });
        compensationsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(CompensationListItem::getCycleIndex));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.height"));
        column.setMinWidth(90);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<CompensationListItem, CompensationListItem> call(TableColumn<CompensationListItem,
                    CompensationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final CompensationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getHeight()));
                        } else
                            setText("");
                    }
                };
            }
        });
        compensationsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(CompensationListItem::getHeight));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.decayedIssuanceAmount"));
        column.setMinWidth(160);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<CompensationListItem, CompensationListItem> call(TableColumn<CompensationListItem,
                    CompensationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final CompensationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getDecayedAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        compensationsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(CompensationListItem::getDecayedAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.issuanceAmount"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<CompensationListItem, CompensationListItem> call(TableColumn<CompensationListItem,
                    CompensationListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final CompensationListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        compensationsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(CompensationListItem::getAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void createReimbursementColumns() {
        TableColumn<ReimbursementListItem, ReimbursementListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.date"));
        column.setMinWidth(160);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ReimbursementListItem, ReimbursementListItem> call(TableColumn<ReimbursementListItem,
                    ReimbursementListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ReimbursementListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setMinHeight(36);
                            setText(item.getDateAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        reimbursementsTableView.getColumns().add(column);
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.setComparator(Comparator.comparing(ReimbursementListItem::getDate));
        reimbursementsTableView.getSortOrder().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.height"));
        column.setMinWidth(90);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ReimbursementListItem, ReimbursementListItem> call(TableColumn<ReimbursementListItem,
                    ReimbursementListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ReimbursementListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getHeight()));
                        } else
                            setText("");
                    }
                };
            }
        });
        reimbursementsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ReimbursementListItem::getHeight));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.shared.table.cycle"));
        column.setMinWidth(60);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ReimbursementListItem, ReimbursementListItem> call(TableColumn<ReimbursementListItem,
                    ReimbursementListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ReimbursementListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getCycleIndex() + 1));
                        } else
                            setText("");
                    }
                };
            }
        });
        reimbursementsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ReimbursementListItem::getCycleIndex));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningmen.table.reimbursedAmount"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ReimbursementListItem, ReimbursementListItem> call(TableColumn<ReimbursementListItem,
                    ReimbursementListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ReimbursementListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAmountAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        reimbursementsTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(ReimbursementListItem::getAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);
    }
}
