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

package bisq.desktop.main.dao.results.votes;

import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.results.BaseResultsTableView;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VotesTableView extends BaseResultsTableView<VotesListItem> {
    private final BsqStateService bsqStateService;
    private final Preferences preferences;

    public VotesTableView(GridPane gridPane, BsqWalletService bsqWalletService, DaoFacade daoFacade,
                          BsqStateService bsqStateService, Preferences preferences, BsqFormatter bsqFormatter) {
        super(gridPane, bsqWalletService, daoFacade, bsqFormatter);

        this.bsqStateService = bsqStateService;
        this.preferences = preferences;
    }

    @Override
    protected String getTitle() {
        return Res.get("dao.results.votes.header");
    }

    @Override
    protected void fillList() {
        List<DecryptedVote> decryptedVotesForCycle = new ArrayList<>(resultsOfCycle.getDecryptedVotesForCycle());
        decryptedVotesForCycle.sort(Comparator.comparing(DecryptedVote::getBlindVoteTxId));
        AtomicInteger index = new AtomicInteger();
        itemList.setAll(decryptedVotesForCycle.stream()
                .map(decryptedVote -> {
                    int id = index.incrementAndGet();
                    return new VotesListItem(id, decryptedVote, bsqStateService, bsqFormatter);
                })
                .collect(Collectors.toList()));
    }

    public int createAllFields(int gridRowStartIndex, ResultsOfCycle resultsOfCycle) {
        super.createAllFields(gridRowStartIndex, resultsOfCycle);

        GUIUtil.setFitToRowsForTableView(tableView, 33, 28, 80);

        return gridRow;
    }

    @Override
    protected void createColumns(TableView<VotesListItem> tableView) {
        TableColumn<VotesListItem, VotesListItem> indexColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.id"));
        indexColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        indexColumn.setMinWidth(70);
        indexColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {
                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getId());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        indexColumn.setComparator(Comparator.comparing(VotesListItem::getStakeAsCoin));
        tableView.getColumns().add(indexColumn);

        TableColumn<VotesListItem, VotesListItem> stakeColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.stake"));
        stakeColumn.setMinWidth(70);
        stakeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        stakeColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {
                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        stakeColumn.setComparator(Comparator.comparing(VotesListItem::getStakeAsCoin));
        tableView.getColumns().add(stakeColumn);

        TableColumn<VotesListItem, VotesListItem> meritColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.merit"));
        meritColumn.setMinWidth(70);
        meritColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        meritColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {
                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMerit());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        meritColumn.setComparator(Comparator.comparing(VotesListItem::getMeritAsCoin));
        tableView.getColumns().add(meritColumn);

        TableColumn<VotesListItem, VotesListItem> acceptedColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.accepted"));
        acceptedColumn.setMinWidth(70);
        acceptedColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        acceptedColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {
                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumAcceptedVotes());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        acceptedColumn.setComparator(Comparator.comparing(VotesListItem::getNumAcceptedVotes));
        tableView.getColumns().add(acceptedColumn);

        TableColumn<VotesListItem, VotesListItem> rejectedColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.rejected"));
        rejectedColumn.setMinWidth(70);
        rejectedColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        rejectedColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {
                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumRejectedVotes());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        rejectedColumn.setComparator(Comparator.comparing(VotesListItem::getNumRejectedVotes));
        tableView.getColumns().add(rejectedColumn);

        TableColumn<VotesListItem, VotesListItem> blindVoteTxIdColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.blindVoteTxId"));
        blindVoteTxIdColumn.setMinWidth(120);
        blindVoteTxIdColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        blindVoteTxIdColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {

                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String txId = item.getBlindVoteTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(txId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(txId));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", txId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        blindVoteTxIdColumn.setComparator(Comparator.comparing(VotesListItem::getBlindVoteTxId));
        tableView.getColumns().add(blindVoteTxIdColumn);

        TableColumn<VotesListItem, VotesListItem> voteRevealTxIdColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.voteRevealTxId"));
        voteRevealTxIdColumn.setMinWidth(120);
        voteRevealTxIdColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        voteRevealTxIdColumn.setCellFactory(
                new Callback<TableColumn<VotesListItem, VotesListItem>, TableCell<VotesListItem,
                        VotesListItem>>() {
                    @Override
                    public TableCell<VotesListItem, VotesListItem> call(
                            TableColumn<VotesListItem, VotesListItem> column) {
                        return new TableCell<VotesListItem, VotesListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final VotesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String txId = item.getVoteRevealTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(txId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(txId));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", txId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        voteRevealTxIdColumn.setComparator(Comparator.comparing(VotesListItem::getVoteRevealTxId));
        tableView.getColumns().add(voteRevealTxIdColumn);
    }

    private void openTxInBlockExplorer(String txId) {
        if (txId != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + txId);
    }
}
