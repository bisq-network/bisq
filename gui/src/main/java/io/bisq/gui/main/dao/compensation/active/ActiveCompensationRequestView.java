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

import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
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

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class ActiveCompensationRequestView extends ActivatableView<SplitPane, Void> {

    TableView<CompensationRequest> tableView;
    private final CompensationRequestManager compensationRequestManger;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private final Navigation navigation;
    private final BsqFormatter bsqFormatter;
    private SortedList<CompensationRequest> sortedList;
    private Subscription selectedCompensationRequestSubscription, phaseSubscription;
    private CompensationRequestDisplay compensationRequestDisplay;
    private int gridRow = 0;
    private Label phaseInfoLabel;
    private GridPane detailsGridPane;
    private GridPane gridPane;
    private DaoPeriodService.Phase currentPhase;
    private CompensationRequest selectedCompensationRequest;
    private Button removeButton;
    private Button voteButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveCompensationRequestView(CompensationRequestManager compensationRequestManger,
                                          DaoPeriodService daoPeriodService,
                                          BsqWalletService bsqWalletService,
                                          Navigation navigation,
                                          BsqFormatter bsqFormatter) {
        this.compensationRequestManger = compensationRequestManger;
        this.daoPeriodService = daoPeriodService;
        this.bsqWalletService = bsqWalletService;
        this.navigation = navigation;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        root.setDividerPositions(0.3, 0.7);
        root.setStyle("-fx-background-insets: 0, 0 0 0 0");
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
        phaseInfoLabel = addLabel(gridPane, gridRow, "", Layout.FIRST_ROW_DISTANCE);

        TableGroupHeadline header = new TableGroupHeadline(Res.get("dao.compensation.active.header"));
        GridPane.setRowIndex(header, ++gridRow);
        GridPane.setMargin(header, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        gridPane.getChildren().add(header);
        header.setMinHeight(20);
        header.setMaxHeight(20);

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Res.get("table.placeholder.noData")));
        tableView.setMinHeight(70);
        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setMargin(tableView, new Insets(5, -15, -10, -10));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        gridPane.getChildren().add(tableView);

        createColumns();

        sortedList = new SortedList<>(compensationRequestManger.getObservableList());
        tableView.setItems(sortedList);

    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        selectedCompensationRequestSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectCompensationRequest);

        phaseSubscription = EasyBind.subscribe(daoPeriodService.getPhaseProperty(), phase -> {
            phaseInfoLabel.setText(Res.get("dao.compensation.active.phase.info", Res.get("dao.phase." + phase)));
            if (!phase.equals(this.currentPhase)) {
                this.currentPhase = phase;
                onSelectCompensationRequest(selectedCompensationRequest);
            }
        });
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        selectedCompensationRequestSubscription.unsubscribe();
        phaseSubscription.unsubscribe();
    }

    private void onSelectCompensationRequest(CompensationRequest compensationRequest) {
        selectedCompensationRequest = compensationRequest;
        if (compensationRequest != null) {
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

                compensationRequestDisplay = new CompensationRequestDisplay(detailsGridPane, bsqFormatter, bsqWalletService);
            }
            compensationRequestDisplay.removeAllFields();
            compensationRequestDisplay.createAllFields(Res.get("dao.compensation.active.selectedRequest"), Layout.GROUP_DISTANCE);
            compensationRequestDisplay.setAllFieldsEditable(false);
            compensationRequestDisplay.fillWithData(compensationRequest.getCompensationRequestPayload());

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

            switch (daoPeriodService.getPhaseProperty().get()) {
                case OPEN_FOR_COMPENSATION_REQUESTS:
                    if (compensationRequestManger.isMyCompensationRequest(compensationRequest)) {
                        if (removeButton == null) {
                            removeButton = addButtonAfterGroup(detailsGridPane, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.active.remove"));
                            removeButton.setOnAction(event -> {
                                if (!compensationRequestManger.removeCompensationRequest(compensationRequest))
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
                            compensationRequestManger.setSelectedCompensationRequest(compensationRequest);
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

    private void createColumns() {
        TableColumn<CompensationRequest, CompensationRequest> dateColumn = new TableColumn<CompensationRequest, CompensationRequest>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequest, CompensationRequest>, TableCell<CompensationRequest,
                        CompensationRequest>>() {
                    @Override
                    public TableCell<CompensationRequest, CompensationRequest> call(
                            TableColumn<CompensationRequest, CompensationRequest> column) {
                        return new TableCell<CompensationRequest, CompensationRequest>() {
                            @Override
                            public void updateItem(final CompensationRequest item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getCompensationRequestPayload().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator((o1, o2) -> o1.getCompensationRequestPayload().getCreationDate().compareTo(o2.getCompensationRequestPayload().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<CompensationRequest, CompensationRequest> nameColumn = new TableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequest, CompensationRequest>, TableCell<CompensationRequest,
                        CompensationRequest>>() {
                    @Override
                    public TableCell<CompensationRequest, CompensationRequest> call(
                            TableColumn<CompensationRequest, CompensationRequest> column) {
                        return new TableCell<CompensationRequest, CompensationRequest>() {
                            @Override
                            public void updateItem(final CompensationRequest item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCompensationRequestPayload().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator((o1, o2) -> o1.getCompensationRequestPayload().getName().compareTo(o2.getCompensationRequestPayload().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<CompensationRequest, CompensationRequest> uidColumn = new TableColumn<>(Res.get("shared.id"));
        uidColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<CompensationRequest, CompensationRequest>, TableCell<CompensationRequest,
                        CompensationRequest>>() {
                    @Override
                    public TableCell<CompensationRequest, CompensationRequest> call(
                            TableColumn<CompensationRequest, CompensationRequest> column) {
                        return new TableCell<CompensationRequest, CompensationRequest>() {
                            @Override
                            public void updateItem(final CompensationRequest item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCompensationRequestPayload().getUid());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        uidColumn.setComparator((o1, o2) -> o1.getCompensationRequestPayload().getUid().compareTo(o2.getCompensationRequestPayload().getUid()));
        tableView.getColumns().add(uidColumn);
    }
}

