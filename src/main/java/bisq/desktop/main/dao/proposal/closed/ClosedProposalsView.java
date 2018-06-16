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

package bisq.desktop.main.dao.proposal.closed;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.proposal.ProposalItemsView;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.proposal.Proposal;

import javax.inject.Inject;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class ClosedProposalsView extends ProposalItemsView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ClosedProposalsView(DaoFacade daoFacade,
                                BsqWalletService bsqWalletService,
                                BsqFormatter bsqFormatter,
                                BSFormatter btcFormatter) {

        super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
    }

    @Override
    protected ObservableList<Proposal> getProposals() {
        return daoFacade.getClosedProposals();
    }

    @Override
    protected void fillListItems() {
        List<Proposal> list = getProposals();
        proposalBaseProposalListItems.setAll(list.stream()
                .map(proposal -> new ClosedProposalListItem(proposal, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }
}

