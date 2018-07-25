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

package bisq.desktop.main.dao.proposal.open;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.proposal.ProposalDisplay;
import bisq.desktop.main.dao.proposal.ProposalWindow;
import bisq.desktop.main.dao.voting.VotingView;
import bisq.desktop.main.dao.voting.active.ActiveBallotsView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.control.Button;
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class OpenProposalsView extends ActivatableView<GridPane, Void> {

    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BSFormatter btcFormatter;

    private final ObservableList<OpenProposalListItem> proposalBaseProposalListItems = FXCollections.observableArrayList();
    private final SortedList<OpenProposalListItem> sortedList = new SortedList<>(proposalBaseProposalListItems);
    private TableView<OpenProposalListItem> proposalTableView;
    private Subscription selectedProposalSubscription;
    private ProposalDisplay proposalDisplay;
    private int gridRow = 0;
    private GridPane detailsGridPane, gridPane;
    private OpenProposalListItem selectedBaseProposalListItem;

    private DaoPhase.Phase currentPhase;
    private Subscription phaseSubscription;
    private ScrollPane proposalDisplayView;
    private boolean proposalDisplayInitialized;


    private ListChangeListener<Proposal> listChangeListener;
    private final Navigation navigation;

    private Button button;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private OpenProposalsView(DaoFacade daoFacade,
                              BsqWalletService bsqWalletService,
                              BsqFormatter bsqFormatter,
                              BSFormatter btcFormatter,
                              Navigation navigation) {

        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        super.initialize();
        root.getStyleClass().add("vote-root");

        detailsGridPane = new GridPane();
        createProposalsTableView();
        createEmptyProposalDisplay();

        listChangeListener = c -> updateListItems();
    }

    @Override
    protected void activate() {
        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), this::onPhaseChanged);
        selectedProposalSubscription = EasyBind.subscribe(proposalTableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        sortedList.comparatorProperty().bind(proposalTableView.comparatorProperty());

        updateListItems();

        getProposals().addListener(listChangeListener);
    }

    @Override
    protected void deactivate() {
        phaseSubscription.unsubscribe();
        selectedProposalSubscription.unsubscribe();

        sortedList.comparatorProperty().unbind();

        proposalBaseProposalListItems.forEach(OpenProposalListItem::cleanup);
        proposalTableView.getSelectionModel().clearSelection();
        selectedBaseProposalListItem = null;

        getProposals().removeListener(listChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ObservableList<Proposal> getProposals() {
        return daoFacade.getActiveOrMyUnconfirmedProposals();
    }

    private void updateListItems() {
        proposalBaseProposalListItems.forEach(OpenProposalListItem::cleanup);
        proposalBaseProposalListItems.clear();

        fillListItems();

        if (proposalBaseProposalListItems.isEmpty())
            hideProposalDisplay();
    }

    private void createAllFieldsOnProposalDisplay(Proposal proposal) {
        proposalDisplayView.setVisible(true);
        proposalDisplayView.setManaged(true);

        proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0, proposal.getType(),
                false);
        proposalDisplay.setEditable(false);
        proposalDisplay.applyProposalPayload(proposal);
        proposalDisplayInitialized = true;

        button = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), "");
        button.setOnAction(event -> onButtonClick());
        onPhaseChanged(daoFacade.phaseProperty().get());
    }

    private void hideProposalDisplay() {
        if (proposalDisplayInitialized) {
            proposalDisplay.removeAllFields();
            proposalDisplayView.setVisible(false);
            proposalDisplayView.setManaged(false);
        }
        if (button != null) {
            button.setManaged(false);
            button.setVisible(false);
        }
    }

    private void fillListItems() {
        List<Proposal> list = getProposals();
        proposalBaseProposalListItems.setAll(list.stream()
                .map(proposal -> new OpenProposalListItem(proposal, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }


    private void onPhaseChanged(DaoPhase.Phase phase) {
        if (phase != null && !phase.equals(currentPhase)) {
            currentPhase = phase;
            onSelectProposal(selectedBaseProposalListItem);
        }

        if (button != null) {
            //noinspection IfCanBeSwitch,IfCanBeSwitch,IfCanBeSwitch
            if (phase == DaoPhase.Phase.PROPOSAL) {
                if (selectedBaseProposalListItem != null && selectedBaseProposalListItem.getProposal() != null) {
                    button.setText(Res.get("shared.remove"));
                    final boolean isMyProposal = daoFacade.isMyProposal(selectedBaseProposalListItem.getProposal());
                    button.setVisible(isMyProposal);
                    button.setManaged(isMyProposal);
                }
            } else if (phase == DaoPhase.Phase.BLIND_VOTE) {
                button.setText(Res.get("dao.proposal.active.vote"));
                button.setVisible(true);
                button.setManaged(true);
            } else {
                button.setVisible(false);
                button.setManaged(false);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onButtonClick() {
        if (daoFacade.phaseProperty().get() == DaoPhase.Phase.PROPOSAL) {
            final Proposal proposal = selectedBaseProposalListItem.getProposal();
            if (daoFacade.removeMyProposal(proposal)) {
                hideProposalDisplay();
            } else {
                new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();
            }
            proposalTableView.getSelectionModel().clearSelection();
        } else if (daoFacade.phaseProperty().get() == DaoPhase.Phase.BLIND_VOTE) {
            navigation.navigateTo(MainView.class, DaoView.class, VotingView.class, ActiveBallotsView.class);
        }
    }

    private void onSelectProposal(OpenProposalListItem item) {
        selectedBaseProposalListItem = item;
        if (selectedBaseProposalListItem != null)
            createAllFieldsOnProposalDisplay(selectedBaseProposalListItem.getProposal());
        else
            hideProposalDisplay();

        onPhaseChanged(daoFacade.phaseProperty().get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsTableView() {
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

    private void createEmptyProposalDisplay() {
        proposalDisplay = new ProposalDisplay(detailsGridPane, bsqFormatter, bsqWalletService, daoFacade);
        proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(10, -10, 0, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setColumnSpan(proposalDisplayView, 2);
        root.getChildren().add(proposalDisplayView);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalColumns(TableView<OpenProposalListItem> tableView) {
        TableColumn<OpenProposalListItem, OpenProposalListItem> dateColumn = new AutoTooltipTableColumn<OpenProposalListItem, OpenProposalListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>, TableCell<OpenProposalListItem,
                        OpenProposalListItem>>() {
                    @Override
                    public TableCell<OpenProposalListItem, OpenProposalListItem> call(
                            TableColumn<OpenProposalListItem, OpenProposalListItem> column) {
                        return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                            @Override
                            public void updateItem(final OpenProposalListItem item, boolean empty) {
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

        TableColumn<OpenProposalListItem, OpenProposalListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>, TableCell<OpenProposalListItem,
                        OpenProposalListItem>>() {
                    @Override
                    public TableCell<OpenProposalListItem, OpenProposalListItem> call(
                            TableColumn<OpenProposalListItem, OpenProposalListItem> column) {
                        return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                            @Override
                            public void updateItem(final OpenProposalListItem item, boolean empty) {
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

        TableColumn<OpenProposalListItem, OpenProposalListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));
        uidColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>, TableCell<OpenProposalListItem,
                        OpenProposalListItem>>() {

                    @Override
                    public TableCell<OpenProposalListItem, OpenProposalListItem> call(TableColumn<OpenProposalListItem,
                            OpenProposalListItem> column) {
                        return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OpenProposalListItem item, boolean empty) {
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


        TableColumn<OpenProposalListItem, OpenProposalListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>,
                TableCell<OpenProposalListItem, OpenProposalListItem>>() {

            @Override
            public TableCell<OpenProposalListItem, OpenProposalListItem> call(TableColumn<OpenProposalListItem,
                    OpenProposalListItem> column) {
                return new TableCell<OpenProposalListItem, OpenProposalListItem>() {

                    @Override
                    public void updateItem(final OpenProposalListItem item, boolean empty) {
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
        confidenceColumn.setComparator(Comparator.comparing(OpenProposalListItem::getConfirmations));
        tableView.getColumns().add(confidenceColumn);

        TableColumn<OpenProposalListItem, OpenProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>,
                TableCell<OpenProposalListItem, OpenProposalListItem>>() {

            @Override
            public TableCell<OpenProposalListItem, OpenProposalListItem> call(TableColumn<OpenProposalListItem,
                    OpenProposalListItem> column) {
                return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                    Button button;

                    @Override
                    public void updateItem(final OpenProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            OpenProposalListItem openProposalListItem = item;
                            if (button == null) {
                                button = openProposalListItem.getButton();
                                button.setOnAction(e -> {
                                    OpenProposalsView.this.selectedBaseProposalListItem = item;
                                    OpenProposalsView.this.onButtonClick();
                                });
                                setGraphic(button);
                            }
                            openProposalListItem.onPhaseChanged(currentPhase);
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
        actionColumn.setComparator(Comparator.comparing(OpenProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}

