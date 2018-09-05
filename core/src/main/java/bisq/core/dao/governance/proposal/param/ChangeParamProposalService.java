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

package bisq.core.dao.governance.proposal.param;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.proposal.BaseProposalService;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.governance.Param;

import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates ChangeParamProposal and transaction.
 */
@Slf4j
public class ChangeParamProposalService extends BaseProposalService<ChangeParamProposal> {
    private Param param;
    private long paramValue;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ChangeParamProposalService(BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      BsqStateService bsqStateService,
                                      ProposalConsensus proposalConsensus,
                                      ChangeParamValidator proposalValidator) {
        super(bsqWalletService,
                btcWalletService,
                bsqStateService,
                proposalConsensus,
                proposalValidator);
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String link,
                                                                 Param param,
                                                                 long paramValue)
            throws ValidationException, InsufficientMoneyException, TxException {
        this.param = param;
        this.paramValue = paramValue;

        return super.createProposalWithTransaction(name, link);
    }

    @Override
    protected ChangeParamProposal createProposalWithoutTxId() {
        return new ChangeParamProposal(
                name,
                link,
                param,
                paramValue);
    }
}
