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
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.BaseProposalListItem;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.proposal.ProposalItemsView;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ObservableList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class ActiveProposalsView extends ProposalItemsView {
    private final Navigation navigation;

    private Button button;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveProposalsView(DaoFacade daoFacade,
                                BsqWalletService bsqWalletService,
                                BsqFormatter bsqFormatter,
                                BSFormatter btcFormatter,
                                Navigation navigation) {

        super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected ObservableList<Proposal> getProposals() {
        return daoFacade.getActiveOrMyUnconfirmedProposals();
    }


    @Override
    protected void createAllFieldsOnProposalDisplay(Proposal proposal) {
        super.createAllFieldsOnProposalDisplay(proposal);

        button = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), "");
        button.setOnAction(event -> onButtonClick());
        onPhaseChanged(daoFacade.phaseProperty().get());
    }

    @Override
    protected void hideProposalDisplay() {
        super.hideProposalDisplay();

        if (button != null) {
            button.setManaged(false);
            button.setVisible(false);
        }
    }

    @Override
    protected void fillListItems() {
        List<Proposal> list = getProposals();
        proposalBaseProposalListItems.setAll(list.stream()
                .map(proposal -> new ActiveProposalListItem(proposal, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }


    @Override
    protected void onPhaseChanged(DaoPhase.Phase phase) {
        super.onPhaseChanged(phase);

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createProposalColumns(TableView<BaseProposalListItem> tableView) {
        super.createProposalColumns(tableView);
        createConfidenceColumn(tableView);

        TableColumn<BaseProposalListItem, BaseProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>,
                TableCell<BaseProposalListItem, BaseProposalListItem>>() {

            @Override
            public TableCell<BaseProposalListItem, BaseProposalListItem> call(TableColumn<BaseProposalListItem,
                    BaseProposalListItem> column) {
                return new TableCell<BaseProposalListItem, BaseProposalListItem>() {
                    Button button;

                    @Override
                    public void updateItem(final BaseProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            ActiveProposalListItem activeProposalListItem = (ActiveProposalListItem) item;
                            if (button == null) {
                                button = activeProposalListItem.getButton();
                                button.setOnAction(e -> {
                                    ActiveProposalsView.this.selectedBaseProposalListItem = item;
                                    ActiveProposalsView.this.onButtonClick();
                                });
                                setGraphic(button);
                            }
                            activeProposalListItem.onPhaseChanged(currentPhase);
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
        actionColumn.setComparator(Comparator.comparing(BaseProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}

