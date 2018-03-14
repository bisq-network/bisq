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
import bisq.desktop.main.dao.proposal.BaseProposalView;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.proposal.ProposalCollectionsManager;

import javax.inject.Inject;

@FxmlView
public class ClosedProposalsView extends BaseProposalView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ClosedProposalsView(ProposalCollectionsManager proposalCollectionsManager,
                                DaoPeriodService daoPeriodService,
                                BsqWalletService bsqWalletService,
                                BsqBlockChain bsqBlockChain,
                                BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                BsqFormatter bsqFormatter) {
        super(proposalCollectionsManager, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, daoPeriodService,
                bsqFormatter);
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
        doUpdateList(proposalCollectionsManager.getClosedProposals());
    }
}

