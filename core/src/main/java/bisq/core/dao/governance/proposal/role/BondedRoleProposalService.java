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
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.proposal.BaseProposalService;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.state.DaoStateService;

import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates BondedRoleProposal and transaction.
 */
@Slf4j
public class BondedRoleProposalService extends BaseProposalService<BondedRoleProposal> {
    private BondedRole bondedRole;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRoleProposalService(BsqWalletService bsqWalletService,
                                     BtcWalletService btcWalletService,
                                     DaoStateService daoStateService,
                                     BondedRoleValidator proposalValidator) {
        super(bsqWalletService,
                btcWalletService,
                daoStateService,
                proposalValidator);
    }

    public ProposalWithTransaction createProposalWithTransaction(BondedRole bondedRole)
            throws ValidationException, InsufficientMoneyException, TxException {
        this.bondedRole = bondedRole;

        return super.createProposalWithTransaction(bondedRole.getName(), bondedRole.getLink());
    }

    @Override
    protected BondedRoleProposal createProposalWithoutTxId() {
        return new BondedRoleProposal(bondedRole);
    }
}
