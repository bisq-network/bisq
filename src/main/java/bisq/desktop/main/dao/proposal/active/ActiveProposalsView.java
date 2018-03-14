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

package bisq.desktop.main.dao.proposal.active;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.proposal.BaseProposalView;
import bisq.desktop.main.dao.proposal.ProposalListItem;
import bisq.desktop.main.dao.voting.VotingView;
import bisq.desktop.main.dao.voting.vote.VoteView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsManager;

import bisq.common.locale.Res;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.util.Callback;

import java.util.Comparator;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class ActiveProposalsView extends BaseProposalView {

    private Button removeButton, voteButton;
    private final Navigation navigation;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveProposalsView(ProposalCollectionsManager voteRequestManger,
                                DaoPeriodService daoPeriodService,
                                BsqWalletService bsqWalletService,
                                BsqBlockChain bsqBlockChain,
                                BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                Navigation navigation,
                                BsqFormatter bsqFormatter) {
        super(voteRequestManger, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, daoPeriodService,
                bsqFormatter);
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();
    }

    @Override
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    protected void updateList() {
        doUpdateList(proposalCollectionsManager.getActiveProposals());
    }

    protected void onSelectProposal(ProposalListItem item) {
        super.onSelectProposal(item);
        if (item != null) {
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
            onPhaseChanged(daoPeriodService.getPhaseProperty().get());
        }
    }

    private void onVote() {
        //noinspection unchecked
        navigation.navigateTo(MainView.class, DaoView.class, VotingView.class, VoteView.class);
    }

    private void onRemove(Proposal proposal) {
        if (proposalCollectionsManager.removeProposal(proposal))
            proposalDisplay.removeAllFields();
        else
            new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();
    }


    @Override
    protected void onPhaseChanged(DaoPeriodService.Phase phase) {
        if (removeButton != null) {
            removeButton.setManaged(false);
            removeButton.setVisible(false);
            removeButton = null;
        }
        if (selectedProposalListItem != null && proposalDisplay != null && !selectedProposalListItem.getProposal().isClosed()) {
            final Proposal proposal = selectedProposalListItem.getProposal();
            switch (phase) {
                case COMPENSATION_REQUESTS:
                    if (proposalCollectionsManager.isMine(proposal)) {
                        if (removeButton == null) {
                            removeButton = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.active.remove"));
                            removeButton.setOnAction(event -> onRemove(proposal));
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
                        voteButton = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.active.vote"));
                        voteButton.setOnAction(event -> onVote());
                    } else {
                        voteButton.setManaged(true);
                        voteButton.setVisible(true);
                    }
                    break;
                case BREAK2:
                    break;
                case VOTE_REVEAL:
                    break;
                case BREAK3:
                    break;
                case UNDEFINED:
                default:
                    log.warn("Undefined phase: " + phase);
                    break;
            }
        }
    }

    @Override
    protected void createColumns(TableView<ProposalListItem> tableView) {
        super.createColumns(tableView);

        TableColumn<ProposalListItem, ProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<ProposalListItem, ProposalListItem>,
                TableCell<ProposalListItem, ProposalListItem>>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<ProposalListItem, ProposalListItem>() {
                    final ImageView iconView = new ImageView();
                    Button button;

                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            final Proposal proposal = item.getProposal();
                            if (button == null) {
                                button = new AutoTooltipButton(getActionButtonText());
                                button.setMinWidth(70);
                                iconView.setId(getActionButtonIconStyle());
                                button.setGraphic(iconView);
                                button.setVisible(getActionButtonVisibility(proposal));
                                setGraphic(button);
                            }
                            button.setOnAction(event -> onActionButton(proposal));
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
        actionColumn.setComparator(Comparator.comparing(ProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }

    private void onActionButton(Proposal proposal) {
        if (showVoteButton())
            onVote();
        else if (showRemoveButton())
            onRemove(proposal);
    }

    private boolean getActionButtonVisibility(Proposal proposal) {
        return !proposal.isClosed() && (showRemoveButton() || showVoteButton());
    }

    private String getActionButtonIconStyle() {
        // TODO find better icon
        return showRemoveButton() ? "image-remove" : "image-tick";
    }

    private String getActionButtonText() {
        return showRemoveButton() ? Res.get("shared.remove") : Res.get("shared.vote");
    }

    private boolean showVoteButton() {
        return isTxInVotePhase();
    }

    private boolean showRemoveButton() {
        return isTxInRequestPhase() && selectedProposalListItem != null && proposalCollectionsManager.isMine(selectedProposalListItem.getProposal());
    }

    private boolean isTxInRequestPhase() {
        return daoPeriodService.getPhaseProperty().get().equals(DaoPeriodService.Phase.COMPENSATION_REQUESTS);
    }

    private boolean isTxInVotePhase() {
        return daoPeriodService.getPhaseProperty().get().equals(DaoPeriodService.Phase.OPEN_FOR_VOTING);
    }

}

