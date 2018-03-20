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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.blockchain.BsqBlockChainListener;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsManager;
import bisq.core.dao.proposal.ProposalPayload;
import bisq.core.locale.Res;

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
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.stream.Collectors;

@FxmlView
public abstract class BaseProposalView extends ActivatableView<GridPane, Void> implements BsqBlockChainListener {

    protected final ProposalCollectionsManager proposalCollectionsManager;
    protected final BsqBlockChain bsqBlockChain;
    protected final ObservableList<ProposalListItem> proposalListItems = FXCollections.observableArrayList();
    protected TableView<ProposalListItem> tableView;
    protected final BsqWalletService bsqWalletService;
    protected final BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher;
    protected final BsqFormatter bsqFormatter;
    protected SortedList<ProposalListItem> sortedList = new SortedList<>(proposalListItems);
    protected Subscription selectedProposalSubscription;
    protected ProposalDisplay proposalDisplay;
    protected int gridRow = 0;
    protected GridPane detailsGridPane, gridPane;
    protected ProposalListItem selectedProposalListItem;
    protected ListChangeListener<Proposal> proposalListChangeListener;
    protected ChangeListener<DaoPeriodService.Phase> phaseChangeListener;
    protected final DaoPeriodService daoPeriodService;
    protected DaoPeriodService.Phase currentPhase;
    protected Subscription phaseSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    protected BaseProposalView(ProposalCollectionsManager proposalCollectionsManager,
                               BsqWalletService bsqWalletService,
                               BsqBlockChain bsqBlockChain,
                               BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                               DaoPeriodService daoPeriodService,
                               BsqFormatter bsqFormatter) {
        this.proposalCollectionsManager = proposalCollectionsManager;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockChain = bsqBlockChain;
        this.bsqBlockChainChangeDispatcher = bsqBlockChainChangeDispatcher;
        this.daoPeriodService = daoPeriodService;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        super.initialize();
        root.getStyleClass().add("vote-root");

        proposalListChangeListener = c -> updateList();
        phaseChangeListener = (observable, oldValue, newValue) -> onPhaseChanged(newValue);
    }

    protected void createTableView() {
        TableGroupHeadline proposalsHeadline = new TableGroupHeadline(Res.get("dao.proposal.active.header"));
        GridPane.setRowIndex(proposalsHeadline, ++gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(-10, -10, -10, -10));
        GridPane.setColumnSpan(proposalsHeadline, 2);
        root.getChildren().add(proposalsHeadline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(tableView);
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(10, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    protected void createProposalDisplay() {
        detailsGridPane = new GridPane();
        proposalDisplay = new ProposalDisplay(detailsGridPane, bsqFormatter, bsqWalletService, null);
        final ScrollPane proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(10, -10, 0, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setColumnSpan(proposalDisplayView, 2);
        root.getChildren().add(proposalDisplayView);
    }


    @Override
    protected void activate() {
        phaseSubscription = EasyBind.subscribe(daoPeriodService.getPhaseProperty(), phase -> {
            if (!phase.equals(this.currentPhase)) {
                this.currentPhase = phase;
                onSelectProposal(selectedProposalListItem);
            }
        });

        daoPeriodService.getPhaseProperty().addListener(phaseChangeListener);
        onPhaseChanged(daoPeriodService.getPhaseProperty().get());

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        selectedProposalSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        bsqBlockChainChangeDispatcher.addBsqBlockChainListener(this);
        proposalCollectionsManager.getAllProposals().addListener(proposalListChangeListener);
        updateList();
    }

    @Override
    protected void deactivate() {
        phaseSubscription.unsubscribe();
        daoPeriodService.getPhaseProperty().removeListener(phaseChangeListener);

        sortedList.comparatorProperty().unbind();

        selectedProposalSubscription.unsubscribe();

        bsqBlockChainChangeDispatcher.removeBsqBlockChainListener(this);
        proposalCollectionsManager.getAllProposals().removeListener(proposalListChangeListener);

        proposalListItems.forEach(ProposalListItem::cleanup);

        tableView.getSelectionModel().clearSelection();
        removeProposalDisplay();
        selectedProposalListItem = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBsqBlockChainChanged() {
        // Need delay otherwise we modify list while dispatching  and cause a ConcurrentModificationException
        //UserThread.execute(this::updateList);
    }

    abstract protected void updateList();

    protected void onPhaseChanged(DaoPeriodService.Phase phase) {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void doUpdateList(FilteredList<Proposal> list) {
        proposalListItems.forEach(ProposalListItem::cleanup);

        proposalListItems.setAll(list.stream()
                .map(proposal -> new ProposalListItem(proposal,
                        proposalCollectionsManager,
                        daoPeriodService,
                        bsqWalletService,
                        bsqBlockChain,
                        bsqBlockChainChangeDispatcher,
                        bsqFormatter))
                .collect(Collectors.toSet()));

        if (list.isEmpty() && proposalDisplay != null)
            proposalDisplay.removeAllFields();
    }

    protected void onSelectProposal(ProposalListItem item) {
        selectedProposalListItem = item;
        if (item != null) {
            final Proposal proposal = item.getProposal();

            removeProposalDisplay();

            //TODO
            proposalDisplay = new ProposalDisplay(detailsGridPane, bsqFormatter, bsqWalletService, null);
            proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0, proposal.getType(),
                    false, false);
            proposalDisplay.setAllFieldsEditable(false);
            proposalDisplay.fillWithData(proposal.getProposalPayload());
        }
    }

    protected void removeProposalDisplay() {
        if (proposalDisplay != null) {
            proposalDisplay.removeAllFields();
            proposalDisplay = null;
        }
    }

    protected void createColumns(TableView<ProposalListItem> tableView) {
        TableColumn<ProposalListItem, ProposalListItem> dateColumn = new AutoTooltipTableColumn<ProposalListItem, ProposalListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getProposal().getProposalPayload().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(o3 -> o3.getProposal().getProposalPayload().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<ProposalListItem, ProposalListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getProposalPayload().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator(Comparator.comparing(o2 -> o2.getProposal().getProposalPayload().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<ProposalListItem, ProposalListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));

        uidColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<ProposalListItem, ProposalListItem>, TableCell<ProposalListItem,
                        ProposalListItem>>() {

                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                            ProposalListItem> column) {
                        return new TableCell<ProposalListItem, ProposalListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final Proposal proposal = item.getProposal();
                                    final ProposalPayload proposalPayload = proposal.getProposalPayload();
                                    field = new HyperlinkWithIcon(proposalPayload.getShortId());
                                    field.setOnAction(event -> {
                                        new ProposalDetailsWindow(bsqFormatter, bsqWalletService, proposalPayload).show();
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
        uidColumn.setComparator(Comparator.comparing(o -> o.getProposal().getProposalPayload().getUid()));
        tableView.getColumns().add(uidColumn);

        TableColumn<ProposalListItem, ProposalListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<ProposalListItem, ProposalListItem>,
                TableCell<ProposalListItem, ProposalListItem>>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<ProposalListItem, ProposalListItem>() {

                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
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
        confidenceColumn.setComparator(Comparator.comparing(ProposalListItem::getConfirmations));
        tableView.getColumns().add(confidenceColumn);
    }
}

