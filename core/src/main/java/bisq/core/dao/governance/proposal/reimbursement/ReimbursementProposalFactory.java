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

package bisq.core.dao.governance.proposal.reimbursement;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.proposal.BaseProposalFactory;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import bisq.common.app.Version;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the ReimbursementProposal and the transaction.
 */
@Slf4j
public class ReimbursementProposalFactory extends BaseProposalFactory<ReimbursementProposal> {

    private Coin requestedBsq;
    private String bsqAddress;

    @Inject
    public ReimbursementProposalFactory(BsqWalletService bsqWalletService,
                                        BtcWalletService btcWalletService,
                                        DaoStateService daoStateService,
                                        ReimbursementValidator proposalValidator) {
        super(bsqWalletService,
                btcWalletService,
                daoStateService,
                proposalValidator);
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String link,
                                                                 Coin requestedBsq)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        this.requestedBsq = requestedBsq;
        this.bsqAddress = bsqWalletService.getUnusedBsqAddressAsString();

        return super.createProposalWithTransaction(name, link);
    }

    @Override
    protected ReimbursementProposal createProposalWithoutTxId() {
        return new ReimbursementProposal(
                name,
                link,
                requestedBsq,
                bsqAddress,
                new HashMap<>());
    }

    @Override
    protected byte[] getOpReturnData(byte[] hashOfPayload) {
        return ProposalConsensus.getOpReturnData(hashOfPayload,
                OpReturnType.REIMBURSEMENT_REQUEST.getType(),
                Version.REIMBURSEMENT_REQUEST);
    }

    @Override
    protected Transaction completeTx(Transaction preparedBurnFeeTx, byte[] opReturnData, Proposal proposal)
            throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        ReimbursementProposal reimbursementProposal = (ReimbursementProposal) proposal;
        return btcWalletService.completePreparedReimbursementRequestTx(
                reimbursementProposal.getRequestedBsq(),
                reimbursementProposal.getAddress(),
                preparedBurnFeeTx,
                opReturnData);
    }
}
