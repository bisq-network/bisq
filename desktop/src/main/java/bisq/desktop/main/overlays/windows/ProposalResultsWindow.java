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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.Navigation;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.governance.ProposalDisplay;
import bisq.desktop.main.dao.governance.result.VoteListItem;
import bisq.desktop.main.overlays.TabbedOverlay;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple2;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

@Slf4j
public class ProposalResultsWindow extends TabbedOverlay<ProposalResultsWindow> {

    private final BsqFormatter bsqFormatter;
    private final DaoFacade daoFacade;
    private final Navigation navigation;
    private final Preferences preferences;
    private boolean isVoteIncludedInResult;
    private SortedList<VoteListItem> sortedVotes;
    private Tab proposalTab, votesTab;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalResultsWindow(BsqFormatter bsqFormatter,
                                 DaoFacade daoFacade,
                                 Navigation navigation,
                                 Preferences preferences) {
        this.bsqFormatter = bsqFormatter;
        this.daoFacade = daoFacade;
        this.navigation = navigation;
        this.preferences = preferences;
    }

    public void show(EvaluatedProposal evaluatedProposal, Ballot ballot,
                     boolean isVoteIncludedInResult, SortedList<VoteListItem> sortedVotes) {

        this.isVoteIncludedInResult = isVoteIncludedInResult;
        this.sortedVotes = sortedVotes;

        rowIndex = 0;
        width = 1000;
        createTabPane();
        createGridPane();
        addContent(evaluatedProposal, ballot);

        display();
    }

    private void createTabs() {
        proposalTab = new Tab(Res.get("shared.proposal").toUpperCase());
        votesTab = new Tab(Res.get("shared.votes").toUpperCase());

        tabPane.getTabs().addAll(proposalTab, votesTab);

        tabPane.getSelectionModel().selectedItemProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != oldValue) {
                stage.sizeToScene();
                layout();
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void onShow() {
        super.onShow();

        setupCloseKeyHandler(stage.getScene());
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.getColumnConstraints().remove(1);
    }

    private void addContent(EvaluatedProposal evaluatedProposal, Ballot ballot) {
        Proposal proposal = evaluatedProposal.getProposal();
        ProposalDisplay proposalDisplay = new ProposalDisplay(gridPane, bsqFormatter, daoFacade, null,
                navigation, preferences);
        proposalDisplay.createAllFields("", rowIndex, -Layout.FIRST_ROW_DISTANCE, proposal.getType(),
                false, "last");
        proposalDisplay.setEditable(false);
        proposalDisplay.onNavigate(this::doClose);

        proposalDisplay.applyProposalPayload(proposal);
        proposalDisplay.applyEvaluatedProposal(evaluatedProposal);
        proposalDisplay.setIsVoteIncludedInResult(isVoteIncludedInResult);

        Tuple2<Long, Long> meritAndStakeTuple = daoFacade.getMeritAndStakeForProposal(proposal.getTxId());
        long merit = meritAndStakeTuple.first;
        long stake = meritAndStakeTuple.second;
        proposalDisplay.applyBallotAndVoteWeight(ballot, merit, stake, isVoteIncludedInResult);

        Region spacer = new Region();
        GridPane.setVgrow(spacer, Priority.ALWAYS);
        GridPane.setRowIndex(spacer, proposalDisplay.incrementAndGetGridRow());
        gridPane.getChildren().add(spacer);

        addCloseButton(gridPane, proposalDisplay.incrementAndGetGridRow());

        createTabs();

        gridPane.setPadding(new Insets(0, 15, 15, 15));
        proposalTab.setContent(gridPane);
        votesTab.setContent(createVotesTable());
    }

    private void addCloseButton(GridPane gridPane, int rowIndex) {
        Button closeButton = addButtonAfterGroup(gridPane, rowIndex, Res.get("shared.close"));
        closeButton.setOnAction(event -> doClose());
    }

    private GridPane createVotesTable() {
        GridPane votesGridPane = new GridPane();
        votesGridPane.setHgap(5);
        votesGridPane.setVgap(5);
        votesGridPane.setPadding(new Insets(15));

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.ALWAYS);
        votesGridPane.getColumnConstraints().addAll(columnConstraints1);

        int gridRow = 0;

        TableGroupHeadline votesTableHeader = new TableGroupHeadline(Res.get("dao.results.proposals.voting.detail.header"));
        GridPane.setRowIndex(votesTableHeader, gridRow);
        GridPane.setMargin(votesTableHeader, new Insets(8, 0, 0, 0));
        GridPane.setColumnSpan(votesTableHeader, 2);
        votesGridPane.getChildren().add(votesTableHeader);

        TableView<VoteListItem> votesTableView = new TableView<>();
        votesTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        votesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(votesTableView);
        GridPane.setRowIndex(votesTableView, gridRow);
        GridPane.setMargin(votesTableView, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        GridPane.setColumnSpan(votesTableView, 2);
        GridPane.setVgrow(votesTableView, Priority.ALWAYS);
        votesGridPane.getChildren().add(votesTableView);

        votesTableView.setItems(sortedVotes);

        addCloseButton(votesGridPane, ++gridRow);

        return votesGridPane;
    }

    private void createColumns(TableView<VoteListItem> votesTableView) {
        TableColumn<VoteListItem, VoteListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.dateTime"));
        column.getStyleClass().add("first-column");
        column.setSortable(false);
        column.setMinWidth(180);
        column.setMaxWidth(column.getMinWidth() + 20);

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(DisplayUtils.formatDateTime(item.getBlindVoteDate()));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("shared.blindVoteTxId"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {

                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getBlindVoteTxId();
                                    hyperlinkWithIcon = new ExternalHyperlink(transactionId);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
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
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.merit"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMerit());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.stake"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.stakeAndMerit"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMeritAndStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.vote"));
        column.setSortable(false);
        column.setMinWidth(50);
        column.getStyleClass().add("last-column");
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            private Label icon;

                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
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
        votesTableView.getColumns().add(column);
    }

    private void setupCloseKeyHandler(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.ENTER) {
                e.consume();
                doClose();
            }
        });
    }

    private void openTxInBlockExplorer(VoteListItem item) {
        if (item.getBlindVoteTxId() != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + item.getBlindVoteTxId(), false);
    }
}
