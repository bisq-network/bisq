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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

@FxmlView
public class ClosedProposalsView extends BaseProposalView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ClosedProposalsView(ProposalService proposalService,
                                PeriodService periodService,
                                BsqWalletService bsqWalletService,
                                ReadableBsqBlockChain readableBsqBlockChain,
                                BsqFormatter bsqFormatter) {
        super(proposalService, bsqWalletService, readableBsqBlockChain, periodService, bsqFormatter);
    }

    @Override
    public void initialize() {
        super.initialize();

        createProposalsTableView();
        createProposalDisplay();
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
    protected void updateProposalList() {
        doUpdateProposalList(proposalService.getClosedProposals());
    }
}

