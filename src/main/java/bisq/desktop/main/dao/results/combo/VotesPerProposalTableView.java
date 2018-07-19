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

package bisq.desktop.main.dao.results.combo;

import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.main.dao.results.BaseResultsTableView;
import bisq.desktop.main.dao.results.ProposalResultsDetailsWindow;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VotesPerProposalTableView extends BaseResultsTableView<VotesPerProposalListItem> {

    private final BsqStateService bsqStateService;

    public VotesPerProposalTableView(GridPane gridPane, BsqWalletService bsqWalletService, DaoFacade daoFacade,
                                     BsqStateService bsqStateService, BsqFormatter bsqFormatter) {
        super(gridPane, bsqWalletService, daoFacade, bsqFormatter);
        this.bsqStateService = bsqStateService;
    }

    @Override
    protected String getTitle() {
        return Res.get("dao.results.combo.header");
    }

    @Override
    protected void fillList() {
        Map<String, EvaluatedProposalWithDecryptedVote> map = resultsOfCycle.getEvaluatedProposals().stream()
                .collect(Collectors.toMap(EvaluatedProposal::getProposalTxId,
                        EvaluatedProposalWithDecryptedVote::new));

        resultsOfCycle.getDecryptedVotesForCycle()
                .forEach(decryptedVote -> {
                    decryptedVote.getBallotList().stream().forEach(ballot -> {
                        EvaluatedProposalWithDecryptedVote evaluatedProposalWithDecryptedVote = map.get(ballot.getProposalTxId());
                        evaluatedProposalWithDecryptedVote.addDecryptedVote(decryptedVote);
                    });
                });

        itemList.setAll(map.values().stream()
                .map(VotesPerProposalListItem::new)
                .collect(Collectors.toList()));

        itemList.sort(Comparator.comparing(votesPerProposalListItem -> votesPerProposalListItem.getProposal().getCreationDate()));
    }


    public int createAllFields(int gridRowStartIndex, ResultsOfCycle resultsOfCycle) {
        super.createAllFields(gridRowStartIndex, resultsOfCycle);

        createColumnsFromData(tableView);

        GUIUtil.setFitToRowsForTableView(tableView, 30, 28, 80);

        return gridRow;
    }

    private void createColumnsFromData(TableView<VotesPerProposalListItem> tableView) {
        TableColumn<VotesPerProposalListItem, VotesPerProposalListItem> votesColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.combo.table.proposals"));
        votesColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        votesColumn.setSortable(false);
        votesColumn.setMinWidth(150);
        votesColumn.setCellFactory(
                new Callback<TableColumn<VotesPerProposalListItem, VotesPerProposalListItem>, TableCell<VotesPerProposalListItem,
                        VotesPerProposalListItem>>() {

                    @Override
                    public TableCell<VotesPerProposalListItem, VotesPerProposalListItem> call(
                            TableColumn<VotesPerProposalListItem, VotesPerProposalListItem> column) {
                        return new TableCell<VotesPerProposalListItem, VotesPerProposalListItem>() {
                            private Hyperlink hyperlinkWithIcon;

                            @Override
                            public void updateItem(final VotesPerProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    hyperlinkWithIcon = new Hyperlink(item.getProposalInfo());
                                    hyperlinkWithIcon.setOnAction(event -> new ProposalResultsDetailsWindow(bsqFormatter,
                                            item.getEvaluatedProposalWithDecryptedVote().getEvaluatedProposal()).show());
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));

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
        tableView.getColumns().add(votesColumn);

        List<DecryptedVote> list = new ArrayList<>(resultsOfCycle.getDecryptedVotesForCycle());
        list.sort(Comparator.comparing(DecryptedVote::getBlindVoteTxId));
        AtomicInteger index = new AtomicInteger();
        list.forEach(decryptedVote -> {
            index.getAndIncrement();
            String stake = bsqFormatter.formatCoinWithCode(Coin.valueOf(decryptedVote.getStake() + decryptedVote.getMerit(bsqStateService)));
            String header = "Vote " + index.get() + " (" + stake + ")";
            TableColumn<VotesPerProposalListItem, VotesPerProposalListItem> column = new AutoTooltipTableColumn<>(header);
            column.setSortable(false);
            column.setMinWidth(150);
            column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
            column.setCellFactory(
                    new Callback<TableColumn<VotesPerProposalListItem, VotesPerProposalListItem>, TableCell<VotesPerProposalListItem,
                            VotesPerProposalListItem>>() {

                        @Override
                        public TableCell<VotesPerProposalListItem, VotesPerProposalListItem> call(
                                TableColumn<VotesPerProposalListItem, VotesPerProposalListItem> column) {
                            return new TableCell<VotesPerProposalListItem, VotesPerProposalListItem>() {
                                private Label icon;

                                @Override
                                public void updateItem(final VotesPerProposalListItem item, boolean empty) {
                                    super.updateItem(item, empty);

                                    if (item != null && !empty) {
                                        String blindVoteTxId = decryptedVote.getBlindVoteTxId();
                                        Tuple2<AwesomeIcon, String> iconStyleTuple = item.getIconStyleTuple(blindVoteTxId);
                                        icon = new Label();
                                        AwesomeDude.setIcon(icon, iconStyleTuple.first);
                                        icon.getStyleClass().add(iconStyleTuple.second);
                                        setGraphic(icon);
                                    } else {
                                        setGraphic(null);
                                    }
                                }
                            };
                        }
                    });
            tableView.getColumns().add(column);
        });
    }

    @Override
    protected void createColumns(TableView<VotesPerProposalListItem> tableView) {
        // do nothing as we create the columns dynamically
    }
}
