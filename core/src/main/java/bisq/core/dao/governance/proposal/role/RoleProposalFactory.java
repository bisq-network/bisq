/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.proposal.role;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.proposal.BaseProposalFactory;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates RoleProposal and transaction.
 */
@Slf4j
public class RoleProposalFactory extends BaseProposalFactory<RoleProposal> {
    private Role role;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RoleProposalFactory(BsqWalletService bsqWalletService,
                               BtcWalletService btcWalletService,
                               DaoStateService daoStateService,
                               RoleValidator proposalValidator) {
        super(bsqWalletService,
                btcWalletService,
                daoStateService,
                proposalValidator);
    }

    public ProposalWithTransaction createProposalWithTransaction(Role role)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        this.role = role;

        return super.createProposalWithTransaction(role.getName(), role.getLink());
    }

    @Override
    protected RoleProposal createProposalWithoutTxId() {
        return new RoleProposal(role, new HashMap<>());
    }
}
