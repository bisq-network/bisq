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

package bisq.core.dao.governance.proposal.compensation;

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
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.common.app.Version;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Creates the CompensationProposal and the transaction.
 */
@Slf4j
public class CompensationProposalFactory extends BaseProposalFactory<CompensationProposal> {
    private Coin requestedBsq;
    private String bsqAddress;
    @Nullable
    private String btcFeeReceiverAddress;
    private boolean isReducedIssuanceAmount;

    @Inject
    public CompensationProposalFactory(BsqWalletService bsqWalletService,
                                       BtcWalletService btcWalletService,
                                       DaoStateService daoStateService,
                                       CompensationValidator proposalValidator) {
        super(bsqWalletService,
                btcWalletService,
                daoStateService,
                proposalValidator);
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String link,
                                                                 Coin requestedBsq,
                                                                 @Nullable String btcFeeReceiverAddress,
                                                                 boolean isReducedIssuanceAmount)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        this.requestedBsq = requestedBsq;
        this.btcFeeReceiverAddress = btcFeeReceiverAddress;
        this.isReducedIssuanceAmount = isReducedIssuanceAmount;
        this.bsqAddress = bsqWalletService.getUnusedBsqAddressAsString();

        return createProposalWithTransaction(name, link);
    }

    @Override
    protected CompensationProposal createProposalWithoutTxId() {
        Map<String, String> extraDataMap = btcFeeReceiverAddress != null || isReducedIssuanceAmount ?
                new HashMap<>() : null;
        if (btcFeeReceiverAddress != null) {
            extraDataMap.put("btcFeeReceiverAddress", btcFeeReceiverAddress);
        }
        if (isReducedIssuanceAmount) {
            extraDataMap.put("isReducedIssuanceAmount", "1");
        }
        return new CompensationProposal(
                name,
                link,
                requestedBsq,
                bsqAddress,
                extraDataMap);
    }

    @Override
    protected byte[] getOpReturnData(byte[] hashOfPayload) {
        return ProposalConsensus.getOpReturnData(hashOfPayload,
                OpReturnType.COMPENSATION_REQUEST.getType(),
                Version.COMPENSATION_REQUEST);
    }

    @Override
    protected Transaction completeTx(Transaction preparedBurnFeeTx, byte[] opReturnData, Proposal proposal)
            throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        CompensationProposal compensationProposal = (CompensationProposal) proposal;
        return btcWalletService.completePreparedCompensationRequestTx(
                compensationProposal.getRequestedBsq(),
                compensationProposal.getAddress(),
                preparedBurnFeeTx,
                opReturnData);
    }
}
