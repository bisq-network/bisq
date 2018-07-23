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

package bisq.desktop.main.dao.results.proposals;

import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple2;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoteResultsForProposalWindow extends Overlay<VoteResultsForProposalWindow> {
    private Proposal proposal;
    private BsqStateService bsqStateService;
    private final BsqFormatter bsqFormatter;

    private TableView<VoteResultsForProposalListItem> tableView;
    private int gridRow = 0;
    private final ObservableList<VoteResultsForProposalListItem> itemList = FXCollections.observableArrayList();
    private final SortedList<VoteResultsForProposalListItem> sortedList = new SortedList<>(itemList);
    protected ResultsOfCycle resultsOfCycle;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public VoteResultsForProposalWindow(ResultsOfCycle resultsOfCycle,
                                        Proposal proposal,
                                        BsqStateService bsqStateService,
                                        BsqFormatter bsqFormatter) {
        this.resultsOfCycle = resultsOfCycle;
        this.proposal = proposal;
        this.bsqStateService = bsqStateService;
        this.bsqFormatter = bsqFormatter;
        type = Type.Confirmation;
    }

    @Override
    public void show() {
        width = MainView.getRootContainer().getWidth() - 20;

        createGridPane();
        addContent();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();

        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.results.proposals.voting.detail.header"));
        GridPane.setRowIndex(headline, gridRow);
        GridPane.setMargin(headline, new Insets(0, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        gridPane.getChildren().add(headline);

        // For some weird reason the stage key handler (ESC, ENTER) does not work as soon a tableView gets added...
        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(tableView);
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(20, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        gridPane.getChildren().add(tableView);

        tableView.setItems(sortedList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        fillList();

        GUIUtil.setFitToRowsForTableView(tableView, 24, 28, 150);

        // Somehow setting the margin for closeButton does not work here as expected. adding a row does give us the desired layout...
        ++rowIndex;
        Button closeButton = FormBuilder.addButton(gridPane, ++rowIndex, Res.get("shared.close"));
        GridPane.setHalignment(closeButton, HPos.RIGHT);
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }

    private void fillList() {
        itemList.clear();

        resultsOfCycle.getEvaluatedProposals().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal().equals(proposal))
                .forEach(evaluatedProposal -> {
                    resultsOfCycle.getDecryptedVotesForCycle().forEach(decryptedVote -> {
                        itemList.add(new VoteResultsForProposalListItem(evaluatedProposal.getProposal(), decryptedVote,
                                bsqStateService, bsqFormatter));
                    });
                });

        itemList.sort(Comparator.comparing(item -> item.getBlindVoteTxId()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns(TableView<VoteResultsForProposalListItem> tableView) {
        TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> blindVoteTxIdColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.blindVoteTxId"));
        blindVoteTxIdColumn.setSortable(false);
        blindVoteTxIdColumn.setMinWidth(150);
        blindVoteTxIdColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        blindVoteTxIdColumn.setCellFactory(
                new Callback<TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem>, TableCell<VoteResultsForProposalListItem,
                        VoteResultsForProposalListItem>>() {
                    @Override
                    public TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem> call(
                            TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> column) {
                        return new TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem>() {
                            @Override
                            public void updateItem(final VoteResultsForProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getBlindVoteTxId());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(blindVoteTxIdColumn);

        TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> voteColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.vote"));
        voteColumn.setSortable(false);
        voteColumn.setMinWidth(150);
        voteColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        voteColumn.setCellFactory(
                new Callback<TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem>, TableCell<VoteResultsForProposalListItem,
                        VoteResultsForProposalListItem>>() {
                    @Override
                    public TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem> call(
                            TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> column) {
                        return new TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem>() {
                            private Label icon;

                            @Override
                            public void updateItem(final VoteResultsForProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    Tuple2<AwesomeIcon, String> iconStyleTuple = item.getIconStyleTuple();
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
        tableView.getColumns().add(voteColumn);

        TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> meritColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.merit"));
        meritColumn.setSortable(false);
        meritColumn.setMinWidth(150);
        meritColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        meritColumn.setCellFactory(
                new Callback<TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem>, TableCell<VoteResultsForProposalListItem,
                        VoteResultsForProposalListItem>>() {
                    @Override
                    public TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem> call(
                            TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> column) {
                        return new TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem>() {
                            @Override
                            public void updateItem(final VoteResultsForProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMerit());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(meritColumn);

        TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> stakeColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.stake"));
        stakeColumn.setSortable(false);
        stakeColumn.setMinWidth(150);
        stakeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        stakeColumn.setCellFactory(
                new Callback<TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem>, TableCell<VoteResultsForProposalListItem,
                        VoteResultsForProposalListItem>>() {
                    @Override
                    public TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem> call(
                            TableColumn<VoteResultsForProposalListItem, VoteResultsForProposalListItem> column) {
                        return new TableCell<VoteResultsForProposalListItem, VoteResultsForProposalListItem>() {
                            @Override
                            public void updateItem(final VoteResultsForProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(stakeColumn);
    }
}
