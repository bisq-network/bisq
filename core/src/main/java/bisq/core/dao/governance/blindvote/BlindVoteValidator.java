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
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.DaoPhase;

import bisq.common.util.ExtraDataMapValidator;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BlindVoteValidator {
    private static final Set<String> INVALID_BLIND_VOTE_TX_IDS = new HashSet<>();

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
        Optional<Tx> optionalTx = daoStateService.getTx(blindVote.getTxId());
        if (daoStateService.isParseBlockChainComplete() && optionalTx.isEmpty())
            log.warn("blindVoteTx is not confirmed. blindVoteTxId={}", blindVote.getTxId());

        return optionalTx
                .filter(tx -> isBlindVoteTxType(blindVote, tx))
                .filter(tx -> isOpReturnDataMatchingPayload(blindVote, tx))
                .isPresent();
    }

    public boolean isTxInPhaseAndCycle(BlindVote blindVote) {
        if (!areDataFieldsValid(blindVote)) {
            log.warn("blindVote is invalid. blindVote={}", blindVote);
            return false;
        }

        String txId = blindVote.getTxId();
        Optional<Tx> optionalTx = daoStateService.getTx(txId);
        if (optionalTx.isEmpty()) {
            log.debug("Tx is not in daoStateService. blindVoteTxId={}", txId);
            return false;
        }

        Tx tx = optionalTx.get();
        int txHeight = tx.getBlockHeight();
        if (!periodService.isTxInCorrectCycle(txHeight, daoStateService.getChainHeight())) {
            log.debug("Tx is not in current cycle. blindVote={}", blindVote);
            return false;
        }
        if (!periodService.isTxInPhase(txId, DaoPhase.Phase.BLIND_VOTE)) {
            log.debug("Tx is not in BLIND_VOTE phase. blindVote={}", blindVote);
            return false;
        }
        return isBlindVoteTxType(blindVote, tx) && isOpReturnDataMatchingPayload(blindVote, tx);
    }

    private boolean isBlindVoteTxType(BlindVote blindVote, Tx tx) {
        boolean txTypeMatches = tx.getTxType() == TxType.BLIND_VOTE;
        // We get called many times and want to avoid to spam the logs, thus we use a cache
        if (!txTypeMatches && !INVALID_BLIND_VOTE_TX_IDS.contains(blindVote.getTxId())) {
            INVALID_BLIND_VOTE_TX_IDS.add(blindVote.getTxId());
            log.warn("blindVoteTx must have type BLIND_VOTE but is {}. blindVoteTxId={}",
                    tx.getTxType(),
                    blindVote.getTxId());
        }
        return txTypeMatches;
    }

    @VisibleForTesting
    boolean isOpReturnDataMatchingPayload(BlindVote blindVote, Tx tx) {
        try {
            byte[] opReturnData = tx.getLastTxOutput().getOpReturnData();
            byte[] hashOfEncryptedVotes = BlindVoteConsensus.getHashOfEncryptedVotes(blindVote.getEncryptedVotes());
            byte[] expectedOpReturnData = BlindVoteConsensus.getOpReturnData(hashOfEncryptedVotes);
            boolean opReturnMatchesPayload = Arrays.equals(expectedOpReturnData, opReturnData);
            if (!opReturnMatchesPayload) {
                log.warn("Blind vote payload does not match the OP_RETURN commitment. blindVoteTxId={}",
                        blindVote.getTxId());
            }
            return opReturnMatchesPayload;
        } catch (Exception e) {
            log.warn("Could not validate blind vote OP_RETURN commitment. blindVoteTxId={}. error={}",
                    blindVote.getTxId(),
                    e.toString());
            return false;
        }
    }
}
