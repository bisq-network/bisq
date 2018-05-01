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

package bisq.desktop.main.dao.proposal.myvotes;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.user.Preferences;

import javax.inject.Inject;

import javafx.scene.layout.GridPane;

@FxmlView
public class MyVotesView extends ActivatableView<GridPane, Void> /*extends BaseProposalView*/ {
    private final Preferences preferences;
   /*

    private final ObservableList<VoteListItem> voteListItems = FXCollections.observableArrayList();
    private SortedList<VoteListItem> sortedList = new SortedList<>(voteListItems);
    private TableView<VoteListItem> votesTableView;
    private VoteListItem selectedVoteListItem;
    private Subscription selectedVoteSubscription;
*/

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MyVotesView(DaoFacade daoFacade,
                        BsqWalletService bsqWalletService,
                        BsqFormatter bsqFormatter,
                        BSFormatter btcFormatter,
                        Preferences preferences) {

        // super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
        this.preferences = preferences;
    }

   /* @Override
    public void initialize() {
        super.initialize();

        createVoteTableView();
        createProposalsTableView(Res.get("dao.proposal.myVotes.proposals.header"), Layout.GROUP_DISTANCE - 20);
        createProposalDisplay();

        changeProposalViewItemsVisibility(false);
    }

    @Override
    protected void activate() {
        super.activate();

        selectedVoteSubscription = EasyBind.subscribe(votesTableView.getSelectionModel().selectedItemProperty(),
                this::onSelectVote);

        sortedList.comparatorProperty().bind(votesTableView.comparatorProperty());

        voteListItems.clear();
        List<VoteListItem> items = daoFacade.getMyVoteList().stream()
                .map(vote -> new VoteListItem(vote, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toList());
        voteListItems.addAll(items);
    }

    private void onSelectVote(VoteListItem voteListItem) {
        selectedVoteListItem = voteListItem;
        changeProposalViewItemsVisibility(selectedVoteListItem != null);
        updateListItems();
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        selectedVoteSubscription.unsubscribe();

        changeProposalViewItemsVisibility(false);
        votesTableView.getSelectionModel().clearSelection();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createVoteTableView() {
        TableGroupHeadline proposalsHeadline = new TableGroupHeadline(Res.get("dao.proposal.myVotes.header"));
        GridPane.setRowIndex(proposalsHeadline, ++gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(-10, -10, -10, -10));
        GridPane.setColumnSpan(proposalsHeadline, 2);
        root.getChildren().add(proposalsHeadline);

        votesTableView = new TableView<>();
        votesTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        votesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createVoteColumns(votesTableView);
        GridPane.setRowIndex(votesTableView, gridRow);
        GridPane.setMargin(votesTableView, new Insets(10, -10, 5, -10));
        GridPane.setColumnSpan(votesTableView, 2);
        GridPane.setHgrow(votesTableView, Priority.ALWAYS);
        root.getChildren().add(votesTableView);

        votesTableView.setItems(sortedList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onShowProposalList(BallotList ballotList) {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    *//*  @Override
      protected void updateProposalList() {
          //TODO
          //if (selectedVoteListItem != null)
          //  doUpdateProposalList(selectedVoteListItem.getMyVote().getBallotList().getList());
      }*//*
    @Override
    protected List<Proposal> getProposalList() {
        //TODO
        return null;// daoFacade.getActiveOrMyUnconfirmedProposals();
    }

    @Override
    protected BaseProposalListItem getListItem(Proposal proposal) {
        return new ActiveProposalListItem(proposal, daoFacade, bsqWalletService, bsqFormatter);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createVoteColumns(TableView<VoteListItem> tableView) {
        TableColumn<VoteListItem, VoteListItem> dateColumn = new AutoTooltipTableColumn<VoteListItem, VoteListItem>(Res.get("shared.dateTime")) {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<VoteListItem, VoteListItem>, TableCell<VoteListItem,
                        VoteListItem>>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<VoteListItem, VoteListItem>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(new Date(item.getMyVote()
                                            .getDate())));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(o3 -> o3.getMyVote().getDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);


        TableColumn<VoteListItem, VoteListItem> proposalListColumn = new AutoTooltipTableColumn<>(Res.get("dao.proposal.myVotes.proposalList"));
        proposalListColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        proposalListColumn.setCellFactory(
                new Callback<TableColumn<VoteListItem, VoteListItem>, TableCell<VoteListItem,
                        VoteListItem>>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<VoteListItem, VoteListItem>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    BallotList ballotList = item.getMyVote().getBallotList();
                                    HyperlinkWithIcon field = new HyperlinkWithIcon(Res.get("dao.proposal.myVotes.showProposalList"), AwesomeIcon.INFO_SIGN);
                                    field.setOnAction(event -> onShowProposalList(ballotList));
                                    field.setTooltip(new Tooltip(Res.get("dao.proposal.myVotes.tooltip.showProposalList")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        proposalListColumn.setSortable(false);
        tableView.getColumns().add(proposalListColumn);

        TableColumn<VoteListItem, VoteListItem> stakeColumn = new AutoTooltipTableColumn<>(Res.get("dao.proposal.votes.stake"));
        stakeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        stakeColumn.setCellFactory(
                new Callback<TableColumn<VoteListItem, VoteListItem>, TableCell<VoteListItem,
                        VoteListItem>>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<VoteListItem, VoteListItem>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    textProperty().bind(item.getStakeAsStringProperty());
                                } else {
                                    textProperty().unbind();
                                    setText("");
                                }
                            }
                        };
                    }
                });
        stakeColumn.setComparator(Comparator.comparing(VoteListItem::getStake));
        tableView.getColumns().add(stakeColumn);

        TableColumn<VoteListItem, VoteListItem> txColumn = new AutoTooltipTableColumn<>(Res.get("dao.proposal.myVotes.tx"));
        txColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        txColumn.setCellFactory(
                new Callback<TableColumn<VoteListItem, VoteListItem>, TableCell<VoteListItem,
                        VoteListItem>>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<VoteListItem, VoteListItem>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String txId = item.getMyVote().getTxId();
                                    HyperlinkWithIcon hyperlinkWithIcon = new HyperlinkWithIcon(txId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> {
                                        if (txId != null)
                                            GUIUtil.openWebPage(preferences.getBlockChainExplorer().txUrl + txId);
                                    });
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", txId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        txColumn.setComparator(Comparator.comparing(o2 -> o2.getMyVote().getBlindVote().getTxId()));
        tableView.getColumns().add(txColumn);

        TableColumn<VoteListItem, VoteListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());

        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        confidenceColumn.setCellFactory(new Callback<TableColumn<VoteListItem, VoteListItem>,
                TableCell<VoteListItem, VoteListItem>>() {

            @Override
            public TableCell<VoteListItem, VoteListItem> call(TableColumn<VoteListItem,
                    VoteListItem> column) {
                return new TableCell<VoteListItem, VoteListItem>() {

                    @Override
                    public void updateItem(final VoteListItem item, boolean empty) {
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
        confidenceColumn.setComparator(Comparator.comparing(VoteListItem::getConfirmations));
        tableView.getColumns().add(confidenceColumn);
    }


    @Override
    protected void createProposalColumns(TableView<BaseProposalListItem> tableView) {
        super.createProposalColumns(tableView);

        TableColumn<BaseProposalListItem, BaseProposalListItem> actionColumn = new TableColumn<>(Res.get("dao.proposal.votes.header"));
        actionColumn.setMinWidth(50);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>,
                TableCell<BaseProposalListItem, BaseProposalListItem>>() {

            @Override
            public TableCell<BaseProposalListItem, BaseProposalListItem> call(TableColumn<BaseProposalListItem,
                    BaseProposalListItem> column) {
                return new TableCell<BaseProposalListItem, BaseProposalListItem>() {
                    ImageView actionButtonIconView;

                    @Override
                    public void updateItem(final BaseProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            actionButtonIconView = new ImageView();
                            //TODO
                         *//*   Vote vote = item.getProposal().getVote();
                            if (vote instanceof BooleanVote) {
                                if (((BooleanVote) vote).isAccepted()) {
                                    actionButtonIconView.setId("accepted");
                                } else {
                                    actionButtonIconView.setId("rejected");
                                }
                            } else {
                                //TODO
                            }*//*

                            setGraphic(actionButtonIconView);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        actionColumn.setComparator(Comparator.comparing(BaseProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }*/
}
