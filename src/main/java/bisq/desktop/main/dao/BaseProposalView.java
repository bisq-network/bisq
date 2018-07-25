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

package bisq.desktop.main.dao;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.proposal.ProposalDisplay;
import bisq.desktop.main.dao.proposal.ProposalWindow;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

@FxmlView
public abstract class BaseProposalView extends ActivatableView<GridPane, Void> {

    protected final DaoFacade daoFacade;
    protected final BsqWalletService bsqWalletService;
    protected final BsqFormatter bsqFormatter;
    protected final BSFormatter btcFormatter;

    protected final ObservableList<BaseProposalListItem> proposalBaseProposalListItems = FXCollections.observableArrayList();
    private final SortedList<BaseProposalListItem> sortedList = new SortedList<>(proposalBaseProposalListItems);
    protected TableView<BaseProposalListItem> proposalTableView;
    private Subscription selectedProposalSubscription;
    protected ProposalDisplay proposalDisplay;
    protected int gridRow = 0;
    protected GridPane detailsGridPane, gridPane;
    protected BaseProposalListItem selectedBaseProposalListItem;

    protected DaoPhase.Phase currentPhase;
    private Subscription phaseSubscription;
    private ScrollPane proposalDisplayView;
    private boolean proposalDisplayInitialized;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    protected BaseProposalView(DaoFacade daoFacade,
                               BsqWalletService bsqWalletService,
                               BsqFormatter bsqFormatter,
                               BSFormatter btcFormatter) {
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    @Override
    public void initialize() {
        super.initialize();
        root.getStyleClass().add("vote-root");

        detailsGridPane = new GridPane();
    }

    @Override
    protected void activate() {
        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), this::onPhaseChanged);
        selectedProposalSubscription = EasyBind.subscribe(proposalTableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        sortedList.comparatorProperty().bind(proposalTableView.comparatorProperty());

        updateListItems();
    }

    @Override
    protected void deactivate() {
        phaseSubscription.unsubscribe();
        selectedProposalSubscription.unsubscribe();

        sortedList.comparatorProperty().unbind();

        proposalBaseProposalListItems.forEach(BaseProposalListItem::cleanup);
        proposalTableView.getSelectionModel().clearSelection();
        selectedBaseProposalListItem = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////


    protected void createProposalsTableView() {
        createProposalsTableView(Res.get("dao.proposal.active.header"), -10);
    }

    private void createProposalsTableView(String header, double top) {
        TableGroupHeadline proposalsHeadline = new TableGroupHeadline(header);
        GridPane.setRowIndex(proposalsHeadline, ++gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(top, -10, -10, -10));
        GridPane.setColumnSpan(proposalsHeadline, 2);
        root.getChildren().add(proposalsHeadline);

        proposalTableView = new TableView<>();
        proposalTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        proposalTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        proposalTableView.setPrefHeight(200);

        createProposalColumns(proposalTableView);
        GridPane.setRowIndex(proposalTableView, gridRow);
        GridPane.setMargin(proposalTableView, new Insets(top + 20, -10, 5, -10));
        GridPane.setColumnSpan(proposalTableView, 2);
        GridPane.setHgrow(proposalTableView, Priority.ALWAYS);
        root.getChildren().add(proposalTableView);

        proposalTableView.setItems(sortedList);
    }

    protected void createEmptyProposalDisplay() {
        proposalDisplay = new ProposalDisplay(detailsGridPane, bsqFormatter, bsqWalletService, daoFacade);
        proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(10, -10, 0, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setColumnSpan(proposalDisplayView, 2);
        root.getChildren().add(proposalDisplayView);
    }

    protected void hideProposalDisplay() {
        if (proposalDisplayInitialized) {
            proposalDisplay.removeAllFields();
            proposalDisplayView.setVisible(false);
            proposalDisplayView.setManaged(false);
        }
    }

    protected void createAllFieldsOnProposalDisplay(Proposal proposal) {
        proposalDisplayView.setVisible(true);
        proposalDisplayView.setManaged(true);

        proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0, proposal.getType(),
                false);
        proposalDisplay.setEditable(false);
        proposalDisplay.applyProposalPayload(proposal);
        proposalDisplayInitialized = true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onSelectProposal(BaseProposalListItem item) {
        selectedBaseProposalListItem = item;
        if (selectedBaseProposalListItem != null)
            createAllFieldsOnProposalDisplay(selectedBaseProposalListItem.getProposal());
        else
            hideProposalDisplay();

        onPhaseChanged(daoFacade.phaseProperty().get());
    }

    protected void onPhaseChanged(DaoPhase.Phase phase) {
        if (phase != null && !phase.equals(currentPhase)) {
            currentPhase = phase;
            onSelectProposal(selectedBaseProposalListItem);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void updateListItems() {
        proposalBaseProposalListItems.forEach(BaseProposalListItem::cleanup);
        proposalBaseProposalListItems.clear();

        fillListItems();

        if (proposalBaseProposalListItems.isEmpty())
            hideProposalDisplay();
    }

    abstract protected void fillListItems();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createProposalColumns(TableView<BaseProposalListItem> tableView) {
        TableColumn<BaseProposalListItem, BaseProposalListItem> dateColumn = new AutoTooltipTableColumn<BaseProposalListItem, BaseProposalListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>, TableCell<BaseProposalListItem,
                        BaseProposalListItem>>() {
                    @Override
                    public TableCell<BaseProposalListItem, BaseProposalListItem> call(
                            TableColumn<BaseProposalListItem, BaseProposalListItem> column) {
                        return new TableCell<BaseProposalListItem, BaseProposalListItem>() {
                            @Override
                            public void updateItem(final BaseProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getProposal().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(o3 -> o3.getProposal().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<BaseProposalListItem, BaseProposalListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>, TableCell<BaseProposalListItem,
                        BaseProposalListItem>>() {
                    @Override
                    public TableCell<BaseProposalListItem, BaseProposalListItem> call(
                            TableColumn<BaseProposalListItem, BaseProposalListItem> column) {
                        return new TableCell<BaseProposalListItem, BaseProposalListItem>() {
                            @Override
                            public void updateItem(final BaseProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator(Comparator.comparing(o2 -> o2.getProposal().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<BaseProposalListItem, BaseProposalListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));
        uidColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>, TableCell<BaseProposalListItem,
                        BaseProposalListItem>>() {

                    @Override
                    public TableCell<BaseProposalListItem, BaseProposalListItem> call(TableColumn<BaseProposalListItem,
                            BaseProposalListItem> column) {
                        return new TableCell<BaseProposalListItem, BaseProposalListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final BaseProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final Proposal proposal = item.getProposal();
                                    field = new HyperlinkWithIcon(proposal.getShortId());
                                    field.setOnAction(event -> {
                                        new ProposalWindow(bsqFormatter, bsqWalletService, proposal, daoFacade).show();
                                    });
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        uidColumn.setComparator(Comparator.comparing(o -> o.getProposal().getUid()));
        tableView.getColumns().add(uidColumn);
    }

    protected void createConfidenceColumn(TableView<BaseProposalListItem> tableView) {
        TableColumn<BaseProposalListItem, BaseProposalListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>,
                TableCell<BaseProposalListItem, BaseProposalListItem>>() {

            @Override
            public TableCell<BaseProposalListItem, BaseProposalListItem> call(TableColumn<BaseProposalListItem,
                    BaseProposalListItem> column) {
                return new TableCell<BaseProposalListItem, BaseProposalListItem>() {

                    @Override
                    public void updateItem(final BaseProposalListItem item, boolean empty) {
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
        confidenceColumn.setComparator(Comparator.comparing(BaseProposalListItem::getConfirmations));
        tableView.getColumns().add(confidenceColumn);
    }
}

