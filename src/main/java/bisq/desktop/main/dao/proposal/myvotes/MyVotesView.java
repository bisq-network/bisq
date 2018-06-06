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

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.proposal.BaseProposalView;
import bisq.desktop.main.dao.proposal.ProposalListItem;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.myvote.MyVoteService;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.dao.vote.result.BooleanVoteResult;
import bisq.core.dao.vote.result.VoteResult;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class MyVotesView extends BaseProposalView {
    private final MyVoteService myVoteService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final Preferences preferences;

    private final ObservableList<VoteListItem> voteListItems = FXCollections.observableArrayList();
    private SortedList<VoteListItem> sortedList = new SortedList<>(voteListItems);
    private TableView<VoteListItem> votesTableView;
    private VoteListItem selectedVoteListItem;
    private Subscription selectedVoteSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MyVotesView(ProposalService voteRequestManger,
                        PeriodService periodService,
                        MyVoteService myVoteService,
                        BsqWalletService bsqWalletService,
                        ReadableBsqBlockChain readableBsqBlockChain,
                        Preferences preferences,
                        BsqFormatter bsqFormatter) {
        super(voteRequestManger, bsqWalletService, readableBsqBlockChain, periodService,
                bsqFormatter);
        this.myVoteService = myVoteService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.preferences = preferences;
    }

    @Override
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
        List<VoteListItem> items = myVoteService.getMyVoteList().stream()
                .map(vote -> new VoteListItem(vote, bsqWalletService, readableBsqBlockChain, bsqFormatter))
                .collect(Collectors.toList());
        voteListItems.addAll(items);
    }

    private void onSelectVote(VoteListItem voteListItem) {
        selectedVoteListItem = voteListItem;
        changeProposalViewItemsVisibility(selectedVoteListItem != null);
        updateProposalList();
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

    private void onShowProposalList(ProposalList proposalList) {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void updateProposalList() {
        if (selectedVoteListItem != null)
            doUpdateProposalList(selectedVoteListItem.getMyVote().getProposalList().getList());
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
                                    ProposalList proposalList = item.getMyVote().getProposalList();
                                    HyperlinkWithIcon field = new HyperlinkWithIcon(Res.get("dao.proposal.myVotes.showProposalList"), AwesomeIcon.INFO_SIGN);
                                    field.setOnAction(event -> onShowProposalList(proposalList));
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
    protected void createProposalColumns(TableView<ProposalListItem> tableView) {
        super.createProposalColumns(tableView);

        TableColumn<ProposalListItem, ProposalListItem> actionColumn = new TableColumn<>(Res.get("dao.proposal.votes.header"));
        actionColumn.setMinWidth(50);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<ProposalListItem, ProposalListItem>,
                TableCell<ProposalListItem, ProposalListItem>>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<ProposalListItem, ProposalListItem>() {
                    ImageView actionButtonIconView;

                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            actionButtonIconView = new ImageView();
                            VoteResult voteResult = item.getProposal().getVoteResult();
                            if (voteResult instanceof BooleanVoteResult) {
                                if (((BooleanVoteResult) voteResult).isAccepted()) {
                                    actionButtonIconView.setId("accepted");
                                } else {
                                    actionButtonIconView.setId("rejected");
                                }
                            } else {
                                //TODO
                            }

                            setGraphic(actionButtonIconView);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        actionColumn.setComparator(Comparator.comparing(ProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}
