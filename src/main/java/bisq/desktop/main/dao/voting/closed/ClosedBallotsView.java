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

package bisq.desktop.main.dao.voting.closed;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.BaseProposalListItem;
import bisq.desktop.main.dao.BaseProposalView;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ListChangeListener;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class ClosedBallotsView extends BaseProposalView {
    private ListChangeListener<Ballot> listChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ClosedBallotsView(DaoFacade daoFacade,
                              BsqWalletService bsqWalletService,
                              BsqFormatter bsqFormatter,
                              BSFormatter btcFormatter) {

        super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
    }

    @Override
    public void initialize() {
        super.initialize();

        createProposalsTableView();
        createEmptyProposalDisplay();

        listChangeListener = c -> updateListItems();
    }

    @Override
    protected void activate() {
        super.activate();

        daoFacade.getClosedBallots().addListener(listChangeListener);
    }


    @Override
    protected void deactivate() {
        super.deactivate();

        daoFacade.getClosedBallots().removeListener(listChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void fillListItems() {
        List<Ballot> list = daoFacade.getClosedBallots();
        proposalBaseProposalListItems.setAll(list.stream()
                .map(ballot -> new ClosedBallotListItem(ballot, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


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
                    ImageView imageView;

                    @Override
                    public void updateItem(final BaseProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            ClosedBallotListItem closedBallotListItem = (ClosedBallotListItem) item;
                            if (imageView == null) {
                                imageView = closedBallotListItem.getImageView();
                                setGraphic(imageView);
                            }
                            closedBallotListItem.onPhaseChanged(currentPhase);
                        } else {
                            setGraphic(null);
                            if (imageView != null)
                                imageView = null;
                        }
                    }
                };
            }
        });
        actionColumn.setComparator(Comparator.comparing(BaseProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}
