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

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.ListItem;
import bisq.desktop.main.dao.proposal.ProposalItemsView;
import bisq.desktop.main.dao.proposal.ProposalListItem;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ObservableList;

import javafx.util.Callback;

import java.util.Comparator;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class ActiveProposalsView extends ProposalItemsView {

    private Button removeButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveProposalsView(DaoFacade daoFacade,
                                BsqWalletService bsqWalletService,
                                BsqFormatter bsqFormatter,
                                BSFormatter btcFormatter) {

        super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
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

        removeButton = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.active.remove"));
        removeButton.setOnAction(event -> onRemove());
        removeButton.setDisable(daoFacade.phaseProperty().get() != DaoPhase.Phase.PROPOSAL);

    }

    @Override
    protected void hideProposalDisplay() {
        super.hideProposalDisplay();

        if (removeButton != null) {
            removeButton.setManaged(false);
            removeButton.setVisible(false);
        }
    }

    protected void onRemove() {
        final Proposal proposal = selectedListItem.getProposal();
        if (daoFacade.removeMyProposal(proposal)) {
            hideProposalDisplay();
        } else {
            new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();
        }
        proposalTableView.getSelectionModel().clearSelection();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createProposalColumns(TableView<ListItem> tableView) {
        super.createProposalColumns(tableView);
        createConfidenceColumn(tableView);

        TableColumn<ListItem, ListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<ListItem, ListItem>,
                TableCell<ListItem, ListItem>>() {

            @Override
            public TableCell<ListItem, ListItem> call(TableColumn<ListItem,
                    ListItem> column) {
                return new TableCell<ListItem, ListItem>() {
                    Button button;

                    @Override
                    public void updateItem(final ListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            ProposalListItem proposalListItem = (ProposalListItem) item;

                            proposalListItem.applyState(currentPhase);
                            if (button == null) {
                                button = proposalListItem.getActionButton();
                                setGraphic(button);
                                button.setOnAction(e -> {
                                    ActiveProposalsView.this.selectedListItem = item;
                                    ActiveProposalsView.this.onRemove();
                                });
                            }
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
        actionColumn.setComparator(Comparator.comparing(ListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}

