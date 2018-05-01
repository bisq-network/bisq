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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.ItemsView;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.proposal.Proposal;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public abstract class ProposalItemsView extends ItemsView {
    protected ListChangeListener<Proposal> listChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    protected ProposalItemsView(DaoFacade daoFacade,
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

        getProposals().addListener(listChangeListener);
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        getProposals().removeListener(listChangeListener);
    }

    @Override
    protected void fillListItems() {
        List<Proposal> list = getProposals();
        proposalListItems.setAll(list.stream()
                .map(proposal -> new ProposalListItem(proposal, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }

    abstract protected ObservableList<Proposal> getProposals();

}

