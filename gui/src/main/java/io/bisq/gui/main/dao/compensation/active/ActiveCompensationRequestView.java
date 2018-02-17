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

package io.bisq.gui.main.dao.compensation.active;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.AutoTooltipLabel;
import io.bisq.gui.components.AutoTooltipTableColumn;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.components.SeparatedPhaseBars;
import io.bisq.gui.components.TableGroupHeadline;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.dao.DaoView;
import io.bisq.gui.main.dao.compensation.CompensationRequestDisplay;
import io.bisq.gui.main.dao.voting.VotingView;
import io.bisq.gui.main.dao.voting.vote.VoteView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.Layout;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.bisq.gui.util.FormBuilder.addButtonAfterGroup;
import static io.bisq.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ActiveCompensationRequestView extends ActivatableView<SplitPane, Void> implements BsqBlockChainListener {

    TableView<CompensationRequestListItem> tableView;
    private final CompensationRequestManager compensationRequestManger;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private final BsqBlockChain bsqBlockChain;
    private final BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher;
    private final Navigation navigation;
    private final BsqFormatter bsqFormatter;
    private final ObservableList<CompensationRequestListItem> observableList = FXCollections.observableArrayList();
    private SortedList<CompensationRequestListItem> sortedList = new SortedList<>(observableList);
    private Subscription selectedCompensationRequestSubscription, phaseSubscription;
    private CompensationRequestDisplay compensationRequestDisplay;
    private int gridRow = 0;
    private GridPane detailsGridPane, gridPane;
    private DaoPeriodService.Phase currentPhase;
    private CompensationRequestListItem selectedCompensationRequest;
    private Button removeButton, voteButton;
    private TextField cycleTextField;
    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;
    private ChangeListener<Number> chainHeightChangeListener;
    private ListChangeListener<CompensationRequest> compensationRequestListChangeListener;
    private ChangeListener<DaoPeriodService.Phase> phaseChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveCompensationRequestView(CompensationRequestManager compensationRequestManger,
                                          DaoPeriodService daoPeriodService,
                                          BsqWalletService bsqWalletService,
                                          BsqBlockChain bsqBlockChain,
                                          BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                          Navigation navigation,
                                          BsqFormatter bsqFormatter) {
        this.compensationRequestManger = compensationRequestManger;
        this.daoPeriodService = daoPeriodService;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockChain = bsqBlockChain;
        this.bsqBlockChainChangeDispatcher = bsqBlockChainChangeDispatcher;
        this.navigation = navigation;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        root.setDividerPositions(0.3, 0.7);
        root.getStyleClass().add("compensation-root");
        AnchorPane topAnchorPane = new AnchorPane();
        root.getItems().add(topAnchorPane);

        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        AnchorPane.setBottomAnchor(gridPane, 10d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 10d);
        topAnchorPane.getChildren().add(gridPane);

        addTitledGroupBg(gridPane, gridRow, 1, Res.get("dao.compensation.active.phase.header"));
        phaseBarsItems = Arrays.asList(
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.COMPENSATION_REQUESTS, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.BREAK1, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.OPEN_FOR_VOTING, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.BREAK2, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.VOTE_CONFIRMATION, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.BREAK3, false));
        SeparatedPhaseBars separatedPhaseBars = new SeparatedPhaseBars(phaseBarsItems);
        GridPane.setRowIndex(separatedPhaseBars, gridRow);
        GridPane.setColumnSpan(separatedPhaseBars, 2);
        GridPane.setColumnIndex(separatedPhaseBars, 0);
        GridPane.setMargin(separatedPhaseBars, new Insets(Layout.FIRST_ROW_DISTANCE - 6, 0, 0, 0));
        gridPane.getChildren().add(separatedPhaseBars);

     /*   final Tuple2<Label, TextField> tuple2 = addLabelTextField(gridPane, ++gridRow, Res.get("dao.compensation.active.cycle"));
        final Label label = tuple2.first;
        GridPane.setHalignment(label, HPos.RIGHT);
        cycleTextField = tuple2.second;*/

        TableGroupHeadline header = new TableGroupHeadline(Res.get("dao.compensation.active.header"));
        GridPane.setRowIndex(header, ++gridRow);
        GridPane.setMargin(header, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        gridPane.getChildren().add(header);
        header.setMinHeight(20);
        header.setMaxHeight(20);

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setMinHeight(90);
        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(5, -15, -10, -10));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        gridPane.getChildren().add(tableView);

        createColumns();

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        chainHeightChangeListener = (observable, oldValue, newValue) -> {
            onChainHeightChanged((int) newValue);
        };

        compensationRequestListChangeListener = c -> updateList();
        phaseChangeListener = (observable, oldValue, newValue) -> onPhaseChanged(newValue);
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        selectedCompensationRequestSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectCompensationRequest);
        phaseSubscription = EasyBind.subscribe(daoPeriodService.getPhaseProperty(), phase -> {
            if (!phase.equals(this.currentPhase)) {
                this.currentPhase = phase;
                onSelectCompensationRequest(selectedCompensationRequest);
            }

            phaseBarsItems.stream().forEach(item -> {
                if (item.getPhase() == phase) {
                    item.setActive();
                } else {
                    item.setInActive();
                }
            });
        });

        bsqWalletService.getChainHeightProperty().addListener(chainHeightChangeListener);
        bsqBlockChainChangeDispatcher.addBsqBlockChainListener(this);
        compensationRequestManger.getAllRequests().addListener(compensationRequestListChangeListener);
        daoPeriodService.getPhaseProperty().addListener(phaseChangeListener);

        onChainHeightChanged(bsqWalletService.getChainHeightProperty().get());
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();

        selectedCompensationRequestSubscription.unsubscribe();
        phaseSubscription.unsubscribe();

        bsqWalletService.getChainHeightProperty().removeListener(chainHeightChangeListener);
        bsqBlockChainChangeDispatcher.removeBsqBlockChainListener(this);
        compensationRequestManger.getAllRequests().removeListener(compensationRequestListChangeListener);
        daoPeriodService.getPhaseProperty().removeListener(phaseChangeListener);

        observableList.forEach(CompensationRequestListItem::cleanup);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBsqBlockChainChanged() {
        // Need delay otherwise we modify list while dispatching  and cause a ConcurrentModificationException
        UserThread.execute(this::updateList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        observableList.forEach(CompensationRequestListItem::cleanup);

        final FilteredList<CompensationRequest> activeRequests = compensationRequestManger.getActiveRequests();
        observableList.setAll(activeRequests.stream()
                .map(e -> new CompensationRequestListItem(e, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, bsqFormatter))
                .collect(Collectors.toSet()));

        if (activeRequests.isEmpty() && compensationRequestDisplay != null)
            compensationRequestDisplay.removeAllFields();
    }

    private void onChainHeightChanged(int height) {
        //cycleTextField.setText(String.valueOf(daoPeriodService.getNumOfStartedCycles(height)));

        phaseBarsItems.stream().forEach(item -> {
            int startBlock = daoPeriodService.getAbsoluteStartBlockOfPhase(height, item.getPhase());
            int endBlock = daoPeriodService.getAbsoluteEndBlockOfPhase(height, item.getPhase());
            item.setStartAndEnd(startBlock, endBlock);
            double progress = 0;
            if (height >= startBlock && height <= endBlock) {
                progress = (double) (height - startBlock + 1) / (double) item.getPhase().getDurationInBlocks();
            } else if (height < startBlock) {
                progress = 0;
            } else if (height > endBlock) {
                progress = 1;
            }
            item.getProgressProperty().set(progress);
        });

        updateList();
    }

    private void onSelectCompensationRequest(CompensationRequestListItem item) {
        selectedCompensationRequest = item;
        if (item != null) {
            final CompensationRequest compensationRequest = item.getCompensationRequest();
            if (compensationRequestDisplay == null) {
                ScrollPane scrollPane = new ScrollPane();
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                scrollPane.setMinHeight(100);
                root.getItems().add(scrollPane);

                AnchorPane bottomAnchorPane = new AnchorPane();
                scrollPane.setContent(bottomAnchorPane);

                detailsGridPane = new GridPane();
                detailsGridPane.setHgap(5);
                detailsGridPane.setVgap(5);
                ColumnConstraints columnConstraints1 = new ColumnConstraints();
                columnConstraints1.setHalignment(HPos.RIGHT);
                columnConstraints1.setHgrow(Priority.SOMETIMES);
                columnConstraints1.setMinWidth(140);
                ColumnConstraints columnConstraints2 = new ColumnConstraints();
                columnConstraints2.setHgrow(Priority.ALWAYS);
                columnConstraints2.setMinWidth(300);
                detailsGridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
                AnchorPane.setBottomAnchor(detailsGridPane, 20d);
                AnchorPane.setRightAnchor(detailsGridPane, 10d);
                AnchorPane.setLeftAnchor(detailsGridPane, 10d);
                AnchorPane.setTopAnchor(detailsGridPane, -20d);
                bottomAnchorPane.getChildren().add(detailsGridPane);

                compensationRequestDisplay = new CompensationRequestDisplay(detailsGridPane, bsqFormatter, bsqWalletService, null);
            }
            compensationRequestDisplay.removeAllFields();
            compensationRequestDisplay.createAllFields(Res.get("dao.compensation.active.selectedRequest"), Layout.GROUP_DISTANCE);
            compensationRequestDisplay.setAllFieldsEditable(false);
            compensationRequestDisplay.fillWithData(compensationRequest.getPayload());

            if (removeButton != null) {
                removeButton.setManaged(false);
                removeButton.setVisible(false);
                removeButton = null;
            }
            if (voteButton != null) {
                voteButton.setManaged(false);
                voteButton.setVisible(false);
                voteButton = null;
            }
            onPhaseChanged(daoPeriodService.getPhaseProperty().get());
        }
    }

    private void onPhaseChanged(DaoPeriodService.Phase phase) {
        if (removeButton != null) {
            removeButton.setManaged(false);
            removeButton.setVisible(false);
            removeButton = null;
        }
        if (selectedCompensationRequest != null && compensationRequestDisplay != null) {
            final CompensationRequest compensationRequest = selectedCompensationRequest.getCompensationRequest();
            switch (phase) {
                case COMPENSATION_REQUESTS:
                    if (compensationRequestManger.isMine(compensationRequest)) {
                        if (removeButton == null) {
                            removeButton = addButtonAfterGroup(detailsGridPane, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.active.remove"));
                            removeButton.setOnAction(event -> {
                                if (compensationRequestManger.removeCompensationRequest(compensationRequest))
                                    compensationRequestDisplay.removeAllFields();
                                else
                                    new Popup<>().warning(Res.get("dao.compensation.active.remove.failed")).show();
                            });
                        } else {
                            removeButton.setManaged(true);
                            removeButton.setVisible(true);
                        }
                    }
                    break;
                case BREAK1:
                    break;
                case OPEN_FOR_VOTING:
                    if (voteButton == null) {
                        voteButton = addButtonAfterGroup(detailsGridPane, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.active.vote"));
                        voteButton.setOnAction(event -> {
                            //noinspection unchecked
                            navigation.navigateTo(MainView.class, DaoView.class, VotingView.class, VoteView.class);
                        });
                    } else {
                        voteButton.setManaged(true);
                        voteButton.setVisible(true);
                    }
                    break;
                case BREAK2:
                    break;
                case VOTE_CONFIRMATION:
                    //TODO
                    log.warn("VOTE_CONFIRMATION");
                    break;
                case BREAK3:
                    break;
                case UNDEFINED:
                default:
                    log.warn("Undefined phase: " + daoPeriodService.getPhaseProperty());
                    break;
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {
        TableColumn<CompensationRequestListItem, CompensationRequestListItem> dateColumn = new AutoTooltipTableColumn<CompensationRequestListItem, CompensationRequestListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>, TableCell<CompensationRequestListItem,
                        CompensationRequestListItem>>() {
                    @Override
                    public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(
                            TableColumn<CompensationRequestListItem, CompensationRequestListItem> column) {
                        return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {
                            @Override
                            public void updateItem(final CompensationRequestListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getCompensationRequest().getPayload().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator((o1, o2) -> o1.getCompensationRequest().getPayload().getCreationDate().compareTo(o2.getCompensationRequest().getPayload().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<CompensationRequestListItem, CompensationRequestListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>, TableCell<CompensationRequestListItem,
                        CompensationRequestListItem>>() {
                    @Override
                    public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(
                            TableColumn<CompensationRequestListItem, CompensationRequestListItem> column) {
                        return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {
                            @Override
                            public void updateItem(final CompensationRequestListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCompensationRequest().getPayload().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator((o1, o2) -> o1.getCompensationRequest().getPayload().getName().compareTo(o2.getCompensationRequest().getPayload().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<CompensationRequestListItem, CompensationRequestListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));
        uidColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>, TableCell<CompensationRequestListItem,
                        CompensationRequestListItem>>() {
                    @Override
                    public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(
                            TableColumn<CompensationRequestListItem, CompensationRequestListItem> column) {
                        return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {
                            @Override
                            public void updateItem(final CompensationRequestListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCompensationRequest().getPayload().getUid());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        uidColumn.setComparator((o1, o2) -> o1.getCompensationRequest().getPayload().getUid().compareTo(o2.getCompensationRequest().getPayload().getUid()));
        tableView.getColumns().add(uidColumn);

        TableColumn<CompensationRequestListItem, CompensationRequestListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<CompensationRequestListItem, CompensationRequestListItem>,
                TableCell<CompensationRequestListItem, CompensationRequestListItem>>() {

            @Override
            public TableCell<CompensationRequestListItem, CompensationRequestListItem> call(TableColumn<CompensationRequestListItem,
                    CompensationRequestListItem> column) {
                return new TableCell<CompensationRequestListItem, CompensationRequestListItem>() {

                    @Override
                    public void updateItem(final CompensationRequestListItem item, boolean empty) {
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
        confidenceColumn.setComparator((o1, o2) -> o1.getConfirmations().compareTo(o2.getConfirmations()));
        tableView.getColumns().add(confidenceColumn);
    }
}

