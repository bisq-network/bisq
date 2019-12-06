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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.DaoPhase;

import bisq.common.crypto.Encryption;
import bisq.common.util.Utilities;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class VoteResultConsensus {
    public static boolean hasOpReturnDataValidLength(byte[] opReturnData) {
        return opReturnData.length == 38;
    }

    // Hash of the list of Blind votes is 20 bytes after version and type bytes
    public static byte[] getHashOfBlindVoteList(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 2, 22);
    }

    public static VoteWithProposalTxIdList decryptVotes(byte[] encryptedVotes, SecretKey secretKey)
            throws VoteResultException.DecryptionException {
        try {
            byte[] decrypted = Encryption.decrypt(encryptedVotes, secretKey);
            return VoteWithProposalTxIdList.getVoteWithProposalTxIdListFromBytes(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException.DecryptionException(t);
        }
    }

    // We compare first by stake and in case we have multiple entries with same stake we use the
    // hex encoded hashOfProposalList for comparison
    @Nullable
    public static byte[] getMajorityHash(List<VoteResultService.HashWithStake> hashWithStakeList)
            throws VoteResultException.ValidationException, VoteResultException.ConsensusException {
        try {
            checkArgument(!hashWithStakeList.isEmpty(), "hashWithStakeList must not be empty");
        } catch (Throwable t) {
            throw new VoteResultException.ValidationException(t);
        }

        hashWithStakeList.sort(Comparator.comparingLong(VoteResultService.HashWithStake::getStake).reversed()
                .thenComparing(hashWithStake -> Utilities.encodeToHex(hashWithStake.getHash())));

        // If there are conflicting data views (multiple hashes) we only consider the voting round as valid if
        // the majority is a super majority with > 80%.
        if (hashWithStakeList.size() > 1) {
            long stakeOfAll = hashWithStakeList.stream().mapToLong(VoteResultService.HashWithStake::getStake).sum();
            long stakeOfFirst = hashWithStakeList.get(0).getStake();
            if ((double) stakeOfFirst / (double) stakeOfAll < 0.8) {
                log.warn("The winning data view has less then 80% of the " +
                        "total stake of all data views. We consider the voting cycle as invalid if the " +
                        "winning data view does not reach a super majority. hashWithStakeList={}", hashWithStakeList);
                throw new VoteResultException.ConsensusException("The winning data view has less then 80% of the " +
                        "total stake of all data views. We consider the voting cycle as invalid if the " +
                        "winning data view does not reach a super majority.");
            }
        }
        return hashWithStakeList.get(0).getHash();
    }

    // Key is stored after version and type bytes and list of Blind votes. It has 16 bytes
    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 38);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }

    public static TxOutput getConnectedBlindVoteStakeOutput(Tx voteRevealTx, DaoStateService daoStateService)
            throws VoteResultException.ValidationException {
        try {
            // We use the stake output of the blind vote tx as first input
            TxInput stakeTxInput = voteRevealTx.getTxInputs().get(0);
            Optional<TxOutput> optionalBlindVoteStakeOutput = daoStateService.getConnectedTxOutput(stakeTxInput);
            checkArgument(optionalBlindVoteStakeOutput.isPresent(), "blindVoteStakeOutput must be present");
            TxOutput blindVoteStakeOutput = optionalBlindVoteStakeOutput.get();
            if (blindVoteStakeOutput.getTxOutputType() != TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT) {
                String message = "blindVoteStakeOutput must be of type BLIND_VOTE_LOCK_STAKE_OUTPUT but is " +
                        blindVoteStakeOutput.getTxOutputType();
                log.warn(message + ". VoteRevealTx=" + voteRevealTx);
                throw new VoteResultException.ValidationException(message + ". VoteRevealTxId=" + voteRevealTx.getId());
            }
            return blindVoteStakeOutput;
        } catch (VoteResultException.ValidationException t) {
            throw t;
        } catch (Throwable t) {
            throw new VoteResultException.ValidationException(t);
        }
    }

    public static void validateBlindVoteTx(String blindVoteTxId, DaoStateService daoStateService,
                                           PeriodService periodService, int chainHeight)
            throws VoteResultException.ValidationException {
        try {
            Optional<Tx> optionalBlindVoteTx = daoStateService.getTx(blindVoteTxId);

            checkArgument(optionalBlindVoteTx.isPresent(), "blindVoteTx with txId " +
                    blindVoteTxId + " not found.");

            Tx blindVoteTx = optionalBlindVoteTx.get();
            Optional<TxType> optionalTxType = daoStateService.getOptionalTxType(blindVoteTx.getId());

            checkArgument(optionalTxType.isPresent(), "optionalTxType must be present" +
                    ". blindVoteTxId=" + blindVoteTx.getId());

            checkArgument(optionalTxType.get() == TxType.BLIND_VOTE,
                    "blindVoteTx must have type BLIND_VOTE but is " + optionalTxType.get() +
                            ". blindVoteTxId=" + blindVoteTx.getId());

            checkArgument(periodService.isTxInCorrectCycle(blindVoteTx.getBlockHeight(), chainHeight),
                    "blindVoteTx is not in correct cycle. blindVoteTx.getBlockHeight()="
                            + blindVoteTx.getBlockHeight() + ". chainHeight=" + chainHeight +
                            ". blindVoteTxId=" + blindVoteTx.getId());

            checkArgument(periodService.isInPhase(blindVoteTx.getBlockHeight(), DaoPhase.Phase.BLIND_VOTE),
                    "blindVoteTx is not in BLIND_VOTE phase. blindVoteTx.getBlockHeight()="
                            + blindVoteTx.getBlockHeight() + ". blindVoteTxId=" + blindVoteTx.getId());
        } catch (Throwable t) {
            throw new VoteResultException.ValidationException(t);
        }
    }
}
