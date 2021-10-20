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

package bisq.core.dao.governance.proposal;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.common.app.Version;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Base class for proposalFactory classes. Provides creation of a transaction. Proposal creation is delegated to
 * concrete classes.
 */
@Slf4j
public abstract class BaseProposalFactory<R extends Proposal> {
    protected final BsqWalletService bsqWalletService;
    protected final BtcWalletService btcWalletService;
    private final DaoStateService daoStateService;
    private final ProposalValidator proposalValidator;
    @Nullable
    protected String name;
    @Nullable
    protected String link;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected BaseProposalFactory(BsqWalletService bsqWalletService,
                                  BtcWalletService btcWalletService,
                                  DaoStateService daoStateService,
                                  ProposalValidator proposalValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.daoStateService = daoStateService;
        this.proposalValidator = proposalValidator;
    }

    protected ProposalWithTransaction createProposalWithTransaction(String name,
                                                                    String link)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        this.name = name;
        this.link = link;
        // As we don't know the txId yet we create a temp proposal with txId set to an empty string.
        R proposal = createProposalWithoutTxId();
        proposalValidator.validateDataFields(proposal);
        Transaction transaction = createTransaction(proposal);
        Proposal proposalWithTxId = proposal.cloneProposalAndAddTxId(transaction.getTxId().toString());
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    protected abstract R createProposalWithoutTxId();

    // We have txId set to null in proposal as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction createTransaction(R proposal) throws InsufficientMoneyException, TxException {
        try {
            Coin fee = ProposalConsensus.getFee(daoStateService, daoStateService.getChainHeight());
            // We create a prepared Bsq Tx for the proposal fee.
            Transaction preparedBurnFeeTx = proposal instanceof IssuanceProposal ?
                    bsqWalletService.getPreparedIssuanceTx(fee) :
                    bsqWalletService.getPreparedProposalTx(fee);

            // payload does not have txId at that moment
            byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal);
            byte[] opReturnData = getOpReturnData(hashOfPayload);

            // We add the BTC inputs for the miner fee.
            Transaction txWithBtcFee = completeTx(preparedBurnFeeTx, opReturnData, proposal);

            // We sign the BSQ inputs of the final tx.
            Transaction transaction = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
            log.info("Proposal tx: " + transaction);
            return transaction;
        } catch (WalletException | TransactionVerificationException e) {
            throw new TxException(e);
        }
    }

    protected byte[] getOpReturnData(byte[] hashOfPayload) {
        return ProposalConsensus.getOpReturnData(hashOfPayload, OpReturnType.PROPOSAL.getType(), Version.PROPOSAL);
    }

    protected Transaction completeTx(Transaction preparedBurnFeeTx, byte[] opReturnData, Proposal proposal)
            throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        return btcWalletService.completePreparedBurnBsqTx(preparedBurnFeeTx, opReturnData);
    }
}
