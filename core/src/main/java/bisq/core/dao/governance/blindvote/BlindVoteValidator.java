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

package bisq.core.dao.governance.blindvote;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.DaoPhase;

import bisq.common.util.ExtraDataMapValidator;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BlindVoteValidator {

    private final DaoStateService daoStateService;
    private final PeriodService periodService;

    @Inject
    public BlindVoteValidator(DaoStateService daoStateService, PeriodService periodService) {
        this.daoStateService = daoStateService;
        this.periodService = periodService;
    }

    public boolean areDataFieldsValid(BlindVote blindVote) {
        try {
            validateDataFields(blindVote);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void validateDataFields(BlindVote blindVote) throws ProposalValidationException {
        try {
            checkNotNull(blindVote.getEncryptedVotes(), "encryptedProposalList must not be null");
            checkArgument(blindVote.getEncryptedVotes().length > 0,
                    "encryptedProposalList must not be empty");
            checkArgument(blindVote.getEncryptedVotes().length <= 100000,
                    "encryptedProposalList must not exceed 100kb");

            checkNotNull(blindVote.getTxId(), "Tx ID must not be null");
            checkArgument(blindVote.getTxId().length() == 64, "Tx ID must be 64 chars");
            checkArgument(blindVote.getStake() >= Restrictions.getMinNonDustOutput().value, "Stake must be at least MinNonDustOutput");

            checkNotNull(blindVote.getEncryptedMeritList(), "getEncryptedMeritList must not be null");
            checkArgument(blindVote.getEncryptedMeritList().length > 0,
                    "getEncryptedMeritList must not be empty");
            checkArgument(blindVote.getEncryptedMeritList().length <= 100000,
                    "getEncryptedMeritList must not exceed 100kb");

            ExtraDataMapValidator.validate(blindVote.getExtraDataMap());
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ProposalValidationException(e);
        }
    }

    public boolean areDataFieldsValidAndTxConfirmed(BlindVote blindVote) {
        if (!areDataFieldsValid(blindVote)) {
            log.warn("blindVote is invalid. blindVote={}", blindVote);
            return false;
        }

        // Check if tx is already confirmed and in DaoState
        boolean isConfirmed = daoStateService.getTx(blindVote.getTxId()).isPresent();
        if (daoStateService.isParseBlockChainComplete() && !isConfirmed)
            log.warn("blindVoteTx is not confirmed. blindVoteTxId={}", blindVote.getTxId());

        return isConfirmed;
    }

    public boolean isTxInPhaseAndCycle(BlindVote blindVote) {
        String txId = blindVote.getTxId();
        Optional<Tx> optionalTx = daoStateService.getTx(txId);
        if (!optionalTx.isPresent()) {
            log.debug("Tx is not in daoStateService. blindVoteTxId={}", txId);
            return false;
        }

        int txHeight = optionalTx.get().getBlockHeight();
        if (!periodService.isTxInCorrectCycle(txHeight, daoStateService.getChainHeight())) {
            log.debug("Tx is not in current cycle. blindVote={}", blindVote);
            return false;
        }
        if (!periodService.isTxInPhase(txId, DaoPhase.Phase.BLIND_VOTE)) {
            log.debug("Tx is not in BLIND_VOTE phase. blindVote={}", blindVote);
            return false;
        }
        return true;
    }
}
