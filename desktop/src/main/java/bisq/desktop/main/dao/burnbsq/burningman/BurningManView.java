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

package bisq.desktop.main.dao.burnbsq.burningman;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipRadioButton;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.balance.BalanceEntry;
import bisq.core.dao.burningman.accounting.balance.BalanceModel;
import bisq.core.dao.burningman.accounting.balance.BaseBalanceEntry;
import bisq.core.dao.burningman.accounting.balance.BurnedBsqBalanceEntry;
import bisq.core.dao.burningman.accounting.balance.ReceivedBtcBalanceEntry;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.burningman.model.LegacyBurningMan;
import bisq.core.dao.governance.proofofburn.ProofOfBurnService;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.app.DevEnv;
import bisq.common.util.DateUtil;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class BurningManView extends ActivatableView<ScrollPane, Void> implements DaoStateListener, BsqBalanceListener {
    private final DaoFacade daoFacade;
    private final BurningManPresentationService burningManPresentationService;
    private final BurningManAccountingService burningManAccountingService;
    private final ProofOfBurnService proofOfBurnService;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final BsqValidator bsqValidator;

    private InputTextField amountInputTextField, burningmenFilterField;
    private ComboBox<BurningManListItem> contributorComboBox;
    private Button burnButton, exportBalanceEntriesButton;
    private TitledGroupBg burnOutputsTitledGroupBg, compensationsTitledGroupBg, selectedContributorTitledGroupBg;
    private AutoTooltipSlideToggleButton showOnlyActiveBurningmenToggle, showMonthlyBalanceEntryToggle;
    private TextField expectedRevenueField, daoBalanceTotalBurnedField, daoBalanceTotalDistributedField, selectedContributorNameField, selectedContributorAddressField, burnTargetField;
    private ToggleGroup balanceEntryToggleGroup;
    private HBox balanceEntryHBox;
    private VBox selectedContributorNameBox, selectedContributorAddressBox;
    private TableView<BurningManListItem> burningManTableView;
    private TableView<BalanceEntryItem> balanceEntryTableView;
    private TableView<BurnOutputListItem> burnOutputsTableView;
    private TableView<CompensationListItem> compensationsTableView;
    private TableView<ReimbursementListItem> reimbursementsTableView;


    private final ObservableList<BurningManListItem> burningManObservableList = FXCollections.observableArrayList();
    private final FilteredList<BurningManListItem> burningManFilteredList = new FilteredList<>(burningManObservableList);
    private final SortedList<BurningManListItem> burningManSortedList = new SortedList<>(burningManFilteredList);
    private final ObservableList<BurnOutputListItem> burnOutputsObservableList = FXCollections.observableArrayList();
    private final SortedList<BurnOutputListItem> burnOutputsSortedList = new SortedList<>(burnOutputsObservableList);
    private final ObservableList<CompensationListItem> compensationObservableList = FXCollections.observableArrayList();
    private final SortedList<CompensationListItem> compensationSortedList = new SortedList<>(compensationObservableList);
    private final ObservableList<ReimbursementListItem> reimbursementObservableList = FXCollections.observableArrayList();
    private final SortedList<ReimbursementListItem> reimbursementSortedList = new SortedList<>(reimbursementObservableList);
    private final ObservableList<BalanceEntryItem> balanceEntryObservableList = FXCollections.observableArrayList();
    private final FilteredList<BalanceEntryItem> balanceEntryFilteredList = new FilteredList<>(balanceEntryObservableList);
    private final SortedList<BalanceEntryItem> balanceEntrySortedList = new SortedList<>(balanceEntryFilteredList);

    private final ChangeListener<Boolean> amountFocusOutListener;
    private final ChangeListener<String> amountInputTextFieldListener;
    private final ChangeListener<BurningManListItem> burningmenSelectionListener;
    private final ChangeListener<String> filterListener;
    private final ChangeListener<BurningManListItem> contributorsListener;
    private final ChangeListener<Toggle> balanceEntryToggleListener;

    private int gridRow = 0;
    private boolean showMonthlyBalanceEntries = true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BurningManView(DaoFacade daoFacade,
                           BurningManPresentationService burningManPresentationService,
                           BurningManAccountingService burningManAccountingService,
                           ProofOfBurnService proofOfBurnService,
                           BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter,
                           @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                           BsqValidator bsqValidator) {
        this.daoFacade = daoFacade;
        this.burningManPresentationService = burningManPresentationService;
        this.burningManAccountingService = burningManAccountingService;
        this.proofOfBurnService = proofOfBurnService;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.bsqValidator = bsqValidator;

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
            balanceEntryTableView.setVisible(isValueSet);
            balanceEntryTableView.setManaged(isValueSet);
            balanceEntryHBox.setVisible(isValueSet);
            balanceEntryHBox.setManaged(isValueSet);
            exportBalanceEntriesButton.setVisible(isValueSet);
            exportBalanceEntriesButton.setManaged(isValueSet);

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
                bsqValidator.setMaxValue(Coin.valueOf(newValue.getMaxBurnTarget()));
                amountInputTextField.clear();
                amountInputTextField.resetValidation();
                String burnTarget = bsqFormatter.formatCoin(newValue.getBurnTarget());
                String maxBurnTarget = bsqFormatter.formatCoin(newValue.getMaxBurnTarget());
                amountInputTextField.setPromptText(Res.get("dao.burningman.amount.prompt.max", burnTarget, maxBurnTarget));
                updateButtonState();
            }
        };
        balanceEntryToggleListener = (observable, oldValue, newValue) -> onTypeChanged();
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
        TitledGroupBg targetTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 2, Res.get("dao.burningman.target.header"));
        GridPane.setColumnSpan(targetTitledGroupBg, 2);
        burnTargetField = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("dao.burningman.burnTarget.label"), "", Layout.FLOATING_LABEL_DISTANCE).second;
        Tuple3<Label, TextField, VBox> currentBlockHeightTuple = addCompactTopLabelTextField(gridPane, gridRow,
                Res.get("dao.burningman.expectedRevenue"), "", Layout.FLOATING_LABEL_DISTANCE);
        expectedRevenueField = currentBlockHeightTuple.second;
        GridPane.setColumnIndex(currentBlockHeightTuple.third, 1);

        // Burn inputs
        addTitledGroupBg(gridPane, ++gridRow, 4, Res.get("dao.burningman.burn.header"), Layout.COMPACT_GROUP_DISTANCE);
        contributorComboBox = addComboBox(gridPane, gridRow, Res.get("dao.burningman.contributorsComboBox.prompt"), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);
        contributorComboBox.setMaxWidth(300);
        contributorComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BurningManListItem item) {
                return item.getName();
            }

            @Override
            public BurningManListItem fromString(String string) {
                return null;
            }
        });
        amountInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("dao.burningman.amount.prompt"));
        burnButton = addButtonAfterGroup(gridPane, ++gridRow, Res.get("dao.proofOfBurn.burn"));

        // Burningmen candidates
        Tuple3<InputTextField, TableView<BurningManListItem>, HBox> burningmenTuple = addTableViewWithHeaderAndFilterField(gridPane,
                ++gridRow,
                Res.get("dao.burningman.candidates.table.header"),
                Res.get("dao.burningman.filter"),
                30);
        burningmenFilterField = burningmenTuple.first;
        burningManTableView = burningmenTuple.second;
        GridPane.setColumnSpan(burningManTableView, 2);
        createBurningmenColumns();
        burningManTableView.setItems(burningManSortedList);
        burningManTableView.setTableMenuButtonVisible(true);
        HBox hBox = burningmenTuple.third;
        GridPane.setColumnSpan(hBox, 2);
        showOnlyActiveBurningmenToggle = new AutoTooltipSlideToggleButton();
        showOnlyActiveBurningmenToggle.setText(Res.get("dao.burningman.toggle"));
        HBox.setMargin(showOnlyActiveBurningmenToggle, new Insets(-21, 0, 0, 0));
        hBox.getChildren().add(2, showOnlyActiveBurningmenToggle);

        // DAO balance
        addTitledGroupBg(gridPane, ++gridRow, 4,
                Res.get("dao.burningman.daoBalance"), Layout.COMPACT_GROUP_DISTANCE);
        Tuple3<Label, TextField, VBox> daoBalanceTotalBurnedTuple = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("dao.burningman.daoBalanceTotalBurned"), "",
                Layout.COMPACT_GROUP_DISTANCE + Layout.FLOATING_LABEL_DISTANCE);
        daoBalanceTotalBurnedField =daoBalanceTotalBurnedTuple.second;
        Tuple3<Label, TextField, VBox> daoBalanceTotalDistributedTuple = addCompactTopLabelTextField(gridPane, gridRow,
                Res.get("dao.burningman.daoBalanceTotalDistributed"), "",
                Layout.COMPACT_GROUP_DISTANCE + Layout.FLOATING_LABEL_DISTANCE);
        daoBalanceTotalDistributedField = daoBalanceTotalDistributedTuple.second;
        VBox daoBalanceTotalDistributedBox = daoBalanceTotalDistributedTuple.third;
        daoBalanceTotalDistributedBox.getChildren().remove(daoBalanceTotalDistributedField);
        Button reportButton = new AutoTooltipButton(Res.get("support.reportButton.label"));
        reportButton.setStyle("-fx-pref-width: 66; -fx-pref-height: 26; -fx-padding: 3 3 3 3;");
        reportButton.setOnAction(e -> writeReport());
        HBox hBox1 = new HBox();
        hBox1.getChildren().addAll(daoBalanceTotalDistributedField, reportButton);
        HBox.setHgrow(daoBalanceTotalDistributedField, Priority.ALWAYS);
        HBox.setMargin(reportButton, new Insets(0, 0, 0, 5.0));
        daoBalanceTotalDistributedBox.getChildren().add(hBox1);
        GridPane.setColumnSpan(daoBalanceTotalDistributedBox, 2);
        GridPane.setColumnIndex(daoBalanceTotalDistributedBox, 1);

        // Selected contributor
        selectedContributorTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 4,
                Res.get("dao.burningman.selectedContributor"), Layout.COMPACT_GROUP_DISTANCE);
        selectedContributorTitledGroupBg.setManaged(false);
        selectedContributorTitledGroupBg.setVisible(false);
        Tuple3<Label, TextField, VBox> nameTuple = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("dao.burningman.selectedContributorName"), "",
                Layout.COMPACT_GROUP_DISTANCE + Layout.FLOATING_LABEL_DISTANCE);
        selectedContributorNameField = nameTuple.second;
        selectedContributorNameBox = nameTuple.third;
        selectedContributorNameBox.setManaged(false);
        selectedContributorNameBox.setVisible(false);

        Tuple3<Label, TextField, VBox> addressTuple = addCompactTopLabelTextField(gridPane, gridRow,
                Res.get("dao.burningman.selectedContributorAddress"), "",
                Layout.COMPACT_GROUP_DISTANCE + Layout.FLOATING_LABEL_DISTANCE);
        selectedContributorAddressField = addressTuple.second;
        selectedContributorAddressBox = addressTuple.third;
        GridPane.setColumnSpan(selectedContributorAddressBox, 2);
        GridPane.setColumnIndex(selectedContributorAddressBox, 1);
        selectedContributorAddressBox.setManaged(false);
        selectedContributorAddressBox.setVisible(false);

        // BalanceEntry
        TitledGroupBg balanceEntryTitledGroupBg = new TitledGroupBg();
        balanceEntryTitledGroupBg.setText(Res.get("dao.burningman.balanceEntry.table.header"));

        showMonthlyBalanceEntryToggle = new AutoTooltipSlideToggleButton();
        showMonthlyBalanceEntryToggle.setText(Res.get("dao.burningman.balanceEntry.table.showMonthlyToggle"));
        HBox.setMargin(showMonthlyBalanceEntryToggle, new Insets(-21, 0, 0, 0));
        showMonthlyBalanceEntryToggle.setSelected(true);

        balanceEntryToggleGroup = new ToggleGroup();
        RadioButton balanceEntryShowAllRadioButton = getRadioButton(Res.get("dao.burningman.balanceEntry.table.radio.all"), null);
        RadioButton balanceEntryShowFeeRadioButton = getRadioButton(Res.get("dao.burningman.balanceEntry.table.radio.fee"), BalanceEntry.Type.BTC_TRADE_FEE_TX);
        RadioButton balanceEntryShowDptRadioButton = getRadioButton(Res.get("dao.burningman.balanceEntry.table.radio.dpt"), BalanceEntry.Type.DPT_TX);
        RadioButton balanceEntryShowBurnRadioButton = getRadioButton(Res.get("dao.burningman.balanceEntry.table.radio.burn"), BalanceEntry.Type.BURN_TX);
        balanceEntryToggleGroup.selectToggle(balanceEntryShowAllRadioButton);

        Region spacer = new Region();
        balanceEntryHBox = new HBox(20,
                balanceEntryTitledGroupBg,
                spacer,
                showMonthlyBalanceEntryToggle,
                balanceEntryShowAllRadioButton,
                balanceEntryShowFeeRadioButton,
                balanceEntryShowDptRadioButton,
                balanceEntryShowBurnRadioButton);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        balanceEntryHBox.setVisible(false);
        balanceEntryHBox.setManaged(false);
        balanceEntryHBox.prefWidthProperty().bind(gridPane.widthProperty());
        GridPane.setRowIndex(balanceEntryHBox, ++gridRow);
        GridPane.setColumnSpan(balanceEntryHBox, 2);
        GridPane.setMargin(balanceEntryHBox, new Insets(38, -10, -12, -10));
        gridPane.getChildren().add(balanceEntryHBox);

        balanceEntryTableView = new TableView<>();
        GridPane.setColumnSpan(balanceEntryTableView, 2);
        GridPane.setRowIndex(balanceEntryTableView, gridRow);
        GridPane.setMargin(balanceEntryTableView, new Insets(60, -10, 5, -10));
        gridPane.getChildren().add(balanceEntryTableView);
        balanceEntryTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        balanceEntryTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        createBalanceEntryColumns();
        balanceEntryTableView.setItems(balanceEntrySortedList);
        balanceEntryTableView.setVisible(false);
        balanceEntryTableView.setManaged(false);

        exportBalanceEntriesButton = addButton(gridPane, ++gridRow, Res.get("shared.exportCSV"));
        GridPane.setColumnIndex(exportBalanceEntriesButton, 1);
        GridPane.setHalignment(exportBalanceEntriesButton, HPos.RIGHT);
        exportBalanceEntriesButton.setVisible(false);
        exportBalanceEntriesButton.setManaged(false);

        // BurnOutputs
        Tuple2<TableView<BurnOutputListItem>, TitledGroupBg> burnOutputTuple = addTableViewWithHeader(gridPane, ++gridRow,
                Res.get("dao.burningman.burnOutput.table.header"), 30);
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
                Res.get("dao.burningman.compensations.table.header"), 30);
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
                Res.get("dao.burningman.reimbursement.table.header"), 30).first;
        GridPane.setColumnSpan(reimbursementsTableView, 2);
        createReimbursementColumns();
        reimbursementsTableView.setItems(reimbursementSortedList);
    }

    private void writeReport() {
        List<String> reportList = new ArrayList<>();
        String separator = "~";
        String tableColumns = "Month~Fees~DPT~Total";
        CSVEntryConverter<String> headerConverter = item -> tableColumns.split(separator);
        CSVEntryConverter<String> contentConverter = item -> item.split(separator);
        int year = 2022;
        int month = 11;
        while(true) {
            Date date = DateUtil.getStartOfMonth(year, month);
            long feeAmount = burningManAccountingService.getDistributedBtcBalanceByMonth(date)
                    .filter(ee -> ee.getType() == BalanceEntry.Type.BTC_TRADE_FEE_TX).mapToLong(BaseBalanceEntry::getAmount).sum();
            long dptAmount = burningManAccountingService.getDistributedBtcBalanceByMonth(date)
                    .filter(ee -> ee.getType() == BalanceEntry.Type.DPT_TX).mapToLong(BaseBalanceEntry::getAmount).sum();
            String reportLine = new SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(date)
                    + separator + Coin.valueOf(feeAmount).toPlainString()
                    + separator + Coin.valueOf(dptAmount).toPlainString()
                    + separator + Coin.valueOf(feeAmount + dptAmount).toPlainString();
            reportList.add(reportLine);
            if (++month > 11) {
                month = 0;
                ++year;
            }
            if (date.after(new Date())) {
                break;
            }
        }
        GUIUtil.exportCSV("DAOreport.csv", headerConverter, contentConverter, "", reportList,
                (Stage) root.getScene().getWindow());
    }

    private RadioButton getRadioButton(String title, @Nullable BalanceEntry.Type type) {
        AutoTooltipRadioButton radioButton = new AutoTooltipRadioButton(title);
        radioButton.setToggleGroup(balanceEntryToggleGroup);
        radioButton.setUserData(type);
        HBox.setMargin(radioButton, new Insets(-12, 2, 0, 0));
        return radioButton;
    }

    @Override
    protected void activate() {
        GUIUtil.setFitToRowsForTableView(burningManTableView, 36, 28, 5, 5);
        GUIUtil.setFitToRowsForTableView(reimbursementsTableView, 36, 28, 3, 6);

        daoFacade.addBsqStateListener(this);
        bsqWalletService.addBsqBalanceListener(this);

        amountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        burningmenFilterField.textProperty().addListener(filterListener);
        burningManTableView.getSelectionModel().selectedItemProperty().addListener(burningmenSelectionListener);

        contributorComboBox.getSelectionModel().selectedItemProperty().addListener(contributorsListener);

        balanceEntryToggleGroup.selectedToggleProperty().addListener(balanceEntryToggleListener);

        burningManSortedList.comparatorProperty().bind(burningManTableView.comparatorProperty());
        burnOutputsSortedList.comparatorProperty().bind(burnOutputsTableView.comparatorProperty());
        balanceEntrySortedList.comparatorProperty().bind(balanceEntryTableView.comparatorProperty());
        compensationSortedList.comparatorProperty().bind(compensationsTableView.comparatorProperty());
        reimbursementSortedList.comparatorProperty().bind(reimbursementsTableView.comparatorProperty());

        burnButton.setOnAction(e -> onBurn());

        exportBalanceEntriesButton.setOnAction(event -> onExportBalanceEntries());
        showOnlyActiveBurningmenToggle.setOnAction(e -> updateBurningmenPredicate());
        showMonthlyBalanceEntryToggle.setOnAction(e -> onShowMonthly(showMonthlyBalanceEntryToggle.isSelected()));

        amountInputTextField.setValidator(bsqValidator);

        if (daoFacade.isParseBlockChainComplete()) {
            updateData();
        }

        onUpdateAvailableBalance(bsqWalletService.getAvailableBalance());

        updateButtonState();
        updateBurningmenPredicate();
    }

    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);
        bsqWalletService.removeBsqBalanceListener(this);

        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        burningmenFilterField.textProperty().removeListener(filterListener);
        burningManTableView.getSelectionModel().selectedItemProperty().removeListener(burningmenSelectionListener);

        contributorComboBox.getSelectionModel().selectedItemProperty().removeListener(contributorsListener);

        balanceEntryToggleGroup.selectedToggleProperty().removeListener(balanceEntryToggleListener);

        burningManSortedList.comparatorProperty().unbind();
        burnOutputsSortedList.comparatorProperty().unbind();
        balanceEntrySortedList.comparatorProperty().unbind();
        compensationSortedList.comparatorProperty().unbind();
        reimbursementSortedList.comparatorProperty().unbind();

        burnButton.setOnAction(null);
        exportBalanceEntriesButton.setOnAction(null);
        showOnlyActiveBurningmenToggle.setOnAction(null);
        showMonthlyBalanceEntryToggle.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateData();
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

    private void onUpdateAvailableBalance(Coin availableBalance) {
        bsqValidator.setAvailableBalance(availableBalance);
        updateButtonState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateData() {
        burningManObservableList.setAll(burningManPresentationService.getBurningManCandidatesByName().entrySet().stream()
                .map(entry -> new BurningManListItem(burningManPresentationService, entry.getKey(), entry.getValue(), bsqFormatter))
                .collect(Collectors.toList()));
        burningManObservableList.add(new BurningManListItem(burningManPresentationService,
                BurningManPresentationService.LEGACY_BURNING_MAN_DPT_NAME,
                burningManPresentationService.getLegacyBurningManForDPT(),
                bsqFormatter));
        burningManObservableList.add(new BurningManListItem(burningManPresentationService,
                BurningManPresentationService.LEGACY_BURNING_MAN_BTC_FEES_NAME,
                burningManPresentationService.getLegacyBurningManForBtcFees(),
                bsqFormatter));
        reimbursementObservableList.setAll(burningManPresentationService.getReimbursements().stream()
                .map(reimbursementModel -> new ReimbursementListItem(reimbursementModel, bsqFormatter))
                .collect(Collectors.toList()));

        expectedRevenueField.setText(bsqFormatter.formatCoinWithCode(burningManPresentationService.getAverageDistributionPerCycle()));

        String burnTarget = bsqFormatter.formatCoin(burningManPresentationService.getBurnTarget());
        String boostedBurnTarget = bsqFormatter.formatCoin(burningManPresentationService.getBoostedBurnTarget());
        burnTargetField.setText(Res.get("dao.burningman.burnTarget.fromTo", burnTarget, boostedBurnTarget));

        if (daoFacade.isParseBlockChainComplete()) {
            Set<String> myContributorNames = burningManPresentationService.getMyCompensationRequestNames();
            burningManPresentationService.findMyGenesisOutputNames().ifPresent(myContributorNames::addAll);
            Map<String, BurningManListItem> burningmenListItemByName = burningManObservableList.stream()
                    .collect(Collectors.toMap(BurningManListItem::getName, e -> e));
            List<BurningManListItem> myBurningManListItems = myContributorNames.stream()
                    .filter(burningmenListItemByName::containsKey)
                    .map(burningmenListItemByName::get)
                    .sorted(Comparator.comparing(BurningManListItem::getName))
                    .collect(Collectors.toList());
            contributorComboBox.setItems(FXCollections.observableArrayList(myBurningManListItems));

            daoBalanceTotalBurnedField.setText(bsqFormatter.formatCoinWithCode(burningManPresentationService.getTotalAmountOfBurnedBsq()));
            daoBalanceTotalDistributedField.setText(btcFormatter.formatCoinWithCode(burningManAccountingService.getTotalAmountOfDistributedBtc()) + " / " +
                    bsqFormatter.formatCoinWithCode(burningManAccountingService.getTotalAmountOfDistributedBsq()));
        }
    }

    private void onShowMonthly(boolean value) {
        showMonthlyBalanceEntries = value;
        BurningManListItem selectedItem = burningManTableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            onBurningManSelected(selectedItem);
        }
    }

    private void updateBurningmenPredicate() {
        burningManFilteredList.setPredicate(burningManListItem -> {
            boolean showOnlyActiveBurningmen = showOnlyActiveBurningmenToggle.isSelected();
            String filterText = burningmenFilterField.getText();
            boolean activeBurnerOrShowAll = !showOnlyActiveBurningmen || burningManListItem.getCappedBurnAmountShare() > 0;
            if (filterText == null || filterText.trim().isEmpty()) {
                return activeBurnerOrShowAll;
            } else {
                return activeBurnerOrShowAll && burningManListItem.getName().toLowerCase().contains(filterText.toLowerCase());
            }
        });
    }

    private void onTypeChanged() {
        if (showMonthlyBalanceEntries) {
            BurningManListItem selectedItem = burningManTableView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                onBurningManSelected(selectedItem);
            }
        }
        balanceEntryFilteredList.setPredicate(balanceEntryItem -> {
            BalanceEntry.Type userData = (BalanceEntry.Type) balanceEntryToggleGroup.getSelectedToggle().getUserData();
            return showMonthlyBalanceEntries || userData == null || balanceEntryItem.getType().orElse(null) == userData;
        });
    }

    private void onBurningManSelected(BurningManListItem burningManListItem) {
        String name = burningManListItem.getName();
        selectedContributorNameField.setText(name);
        selectedContributorAddressField.setText(burningManListItem.getAddress());
        BurningManCandidate burningManCandidate = burningManListItem.getBurningManCandidate();

        boolean isLegacyBurningMan = burningManCandidate instanceof LegacyBurningMan;
        burnOutputsObservableList.setAll(burningManCandidate.getBurnOutputModels().stream()
                .map(burnOutputModel -> new BurnOutputListItem(burnOutputModel, bsqFormatter, isLegacyBurningMan))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(burnOutputsTableView, 36, 28, 4, 6);

        if (burningManAccountingService.getBalanceModelByBurningManName().containsKey(name)) {
            BalanceModel balanceModel = burningManAccountingService.getBalanceModelByBurningManName().get(name);
            List<? extends BalanceEntry> balanceEntries;
            if (showMonthlyBalanceEntries) {
                Predicate<BaseBalanceEntry> predicate = balanceEntry -> {
                    BalanceEntry.Type selectedType = (BalanceEntry.Type) balanceEntryToggleGroup.getSelectedToggle().getUserData();
                    return selectedType == null ||
                            selectedType.equals(balanceEntry.getType());
                };
                balanceEntries = balanceModel.getMonthlyBalanceEntries(burningManCandidate, predicate);
            } else {
                Stream<ReceivedBtcBalanceEntry> receivedBtcBalanceEntries = balanceModel.getReceivedBtcBalanceEntries().stream();
                Stream<BurnedBsqBalanceEntry> burnedBsqBalanceEntries = balanceModel.getBurnedBsqBalanceEntries(burningManCandidate.getBurnOutputModels());
                balanceEntries = Stream.concat(receivedBtcBalanceEntries, burnedBsqBalanceEntries).collect(Collectors.toList());
            }

            Map<Date, Price> averageBsqPriceByMonth = burningManAccountingService.getAverageBsqPriceByMonth();
            balanceEntryObservableList.setAll(balanceEntries.stream()
                    .map(balanceEntry -> new BalanceEntryItem(balanceEntry, averageBsqPriceByMonth, bsqFormatter, btcFormatter))
                    .collect(Collectors.toList()));
        } else {
            balanceEntryObservableList.clear();
        }
        GUIUtil.setFitToRowsForTableView(balanceEntryTableView, 36, 28, 4, 6);

        compensationObservableList.setAll(burningManCandidate.getCompensationModels().stream()
                .map(compensationModel -> new CompensationListItem(compensationModel, bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(compensationsTableView, 36, 28, 4, 6);
    }

    private void updateButtonState() {
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid &&
                contributorComboBox.getSelectionModel().getSelectedItem() != null;
        burnButton.setDisable(!isValid);
    }

    private void onBurn() {
        BurningManListItem selectedItem = contributorComboBox.getSelectionModel().getSelectedItem();
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
        amountInputTextField.setPromptText(Res.get("dao.burningman.amount.prompt"));
        amountInputTextField.resetValidation();
        contributorComboBox.getSelectionModel().clearSelection();
    }

    private void onExportBalanceEntries() {
        CSVEntryConverter<BalanceEntryItem> headerConverter = item -> {
            ObservableList<TableColumn<BalanceEntryItem, ?>> tableColumns = balanceEntryTableView.getColumns();
            String[] columns = new String[tableColumns.size()];
            for (int i = 0; i < tableColumns.size(); i++) {
                columns[i] = ((AutoTooltipLabel) tableColumns.get(i).getGraphic()).getText();
            }
            return columns;
        };
        CSVEntryConverter<BalanceEntryItem> contentConverter = item -> {
            String[] columns = new String[7];
            columns[0] = item.getDateAsString();
            columns[1] = item.getReceivedBtcAsString();
            columns[2] = item.getPriceAsString();
            columns[3] = item.getReceivedBtcAsBsqAsString();
            columns[4] = item.getBurnedBsqAsString();
            columns[5] = item.getRevenueAsString();
            columns[6] = item.getTypeAsString();
            return columns;
        };

        GUIUtil.exportCSV("burningman_revenue.csv", headerConverter, contentConverter,
                new BalanceEntryItem(), balanceEntryObservableList, (Stage) root.getScene().getWindow());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createBurningmenColumns() {
        TableColumn<BurningManListItem, BurningManListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.name"));
        column.setMinWidth(190);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.getStyleClass().add("first-column");
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
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
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getName().toLowerCase()));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.burnTarget"), Res.get("dao.burningman.table.burnTarget.help"));
        column.setMinWidth(200);
        column.getGraphic().setStyle("-fx-alignment: center-right; -fx-padding: 2 10 2 0");
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getBurnTargetAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getBurnTarget));

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.expectedRevenue"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getExpectedRevenueAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getExpectedRevenue));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.burnAmountShare.label"));
        column.setMinWidth(230);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getCappedBurnAmountShareAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.setComparator(Comparator.comparing(BurningManListItem::getCappedBurnAmountShare));
        burningManTableView.getSortOrder().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.decayedBurnAmount"));
        column.setMinWidth(160);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedDecayedBurnAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getAccumulatedDecayedBurnAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.burnAmount"));
        column.setMinWidth(130);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedBurnAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getAccumulatedBurnAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

       /* column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.numBurnOutputs"));
        column.setMinWidth(90);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getNumBurnOutputs()));
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getNumBurnOutputs));
        column.setSortType(TableColumn.SortType.DESCENDING);*/

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.issuanceShare"));
        column.setMinWidth(110);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getCompensationShareAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getCompensationShare));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.decayedIssuanceAmount"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedDecayedCompensationAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getAccumulatedDecayedCompensationAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.issuanceAmount"));
        column.setMinWidth(120);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getAccumulatedCompensationAmountAsBsq());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getAccumulatedCompensationAmount));
        column.setSortType(TableColumn.SortType.DESCENDING);

       /* column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.numIssuances"));
        column.setMinWidth(110);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BurningManListItem, BurningManListItem> call(TableColumn<BurningManListItem,
                    BurningManListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BurningManListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getNumIssuancesAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        burningManTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BurningManListItem::getNumIssuances));
        column.setSortType(TableColumn.SortType.DESCENDING);*/
    }

    private void createBurnOutputsColumns() {
        TableColumn<BurnOutputListItem, BurnOutputListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.date"));
        column.setMinWidth(160);
        column.setMaxWidth(column.getMinWidth());
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.cycle"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.height"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.decayedBurnAmount"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.burnAmount"));
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

    private void createBalanceEntryColumns() {
        TableColumn<BalanceEntryItem, BalanceEntryItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.date"));
        column.setMinWidth(160);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setMinHeight(36);
                            setText(showMonthlyBalanceEntries ? item.getMonthAsString() : item.getDateAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setSortType(TableColumn.SortType.DESCENDING);
        column.setComparator(Comparator.comparing(BalanceEntryItem::getDate));
        balanceEntryTableView.getSortOrder().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.receivedBtc"));
        column.setMinWidth(100);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getReceivedBtcAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getReceivedBtc().orElse(null)));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.price"));
        column.setMinWidth(160);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getPriceAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getPrice().orElse(null)));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.receivedBtcAsBsq"));
        column.setMinWidth(100);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getReceivedBtcAsBsqAsString()));
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getReceivedBtcAsBsq().orElse(null)));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.burnedBsq"));
        column.setMinWidth(100);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getBurnedBsqAsString()));
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getBurnedBsq().orElse(null)));
        column.setSortType(TableColumn.SortType.DESCENDING);


        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.revenue"));
        column.setMinWidth(100);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(String.valueOf(item.getRevenueAsString()));
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getRevenue().orElse(null)));
        column.setSortType(TableColumn.SortType.DESCENDING);

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.balanceEntry.type"));
        column.setMinWidth(140);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BalanceEntryItem, BalanceEntryItem> call(TableColumn<BalanceEntryItem,
                    BalanceEntryItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final BalanceEntryItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            setText(item.getTypeAsString());
                        } else
                            setText("");
                    }
                };
            }
        });
        balanceEntryTableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(e -> e.getType().orElse(null)));
        column.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void createCompensationColumns() {
        TableColumn<CompensationListItem, CompensationListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.date"));
        column.setMinWidth(160);
        column.setMaxWidth(column.getMinWidth());
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.cycle"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.height"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.decayedIssuanceAmount"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.issuanceAmount"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.date"));
        column.setMinWidth(160);
        column.setMaxWidth(column.getMinWidth());
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.height"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.shared.table.cycle"));
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

        column = new AutoTooltipTableColumn<>(Res.get("dao.burningman.table.reimbursedAmount"));
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
