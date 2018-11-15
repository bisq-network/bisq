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

package bisq.core.dao.node.parser;

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.governance.voteresult.VoteResultConsensus;
import bisq.core.dao.node.parser.exceptions.InvalidParsingConditionException;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Processes OpReturn output if valid and delegates validation to specific validators.
 */
@Slf4j
class OpReturnParser {

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    static Optional<OpReturnType> getOptionalOpReturnTypeCandidate(TempTxOutput tempTxOutput) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        byte[] opReturnData = tempTxOutput.getOpReturnData();
        if (tempTxOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 22)
            return OpReturnType.getOpReturnType(opReturnData[0]);

        return Optional.empty();
    }

    /**
     * Parse the type of OP_RETURN data and validate it.
     *
     * @param tempTxOutput      The temporary transaction output to parse.
     * @return The type of the transaction output, which will be either one of the
     *                          {@code *_OP_RETURN_OUTPUT} values, or {@code UNDEFINED} in case of
     *                          unexpected state.
     */
    static TxOutputType getTxOutputType(TempTxOutput tempTxOutput) {
        boolean nonZeroOutput = tempTxOutput.getValue() != 0;
        byte[] opReturnData = tempTxOutput.getOpReturnData();
        checkNotNull(opReturnData, "opReturnData must not be null");

        if (nonZeroOutput || opReturnData.length < 22) {
            log.warn("OP_RETURN data does not match our rules. opReturnData={}",
                    Utils.HEX.encode(opReturnData));
            return TxOutputType.INVALID_OUTPUT;
        }
        Optional<OpReturnType> optionalOpReturnType = OpReturnType.getOpReturnType(opReturnData[0]);
        if (!optionalOpReturnType.isPresent()) {
            log.warn("OP_RETURN data does not match our defined types. opReturnData={}",
                    Utils.HEX.encode(opReturnData));
            return TxOutputType.INVALID_OUTPUT;
        }

        switch (optionalOpReturnType.get()) {
            case PROPOSAL:
                if (ProposalConsensus.hasOpReturnDataValidLength(opReturnData))
                    return TxOutputType.PROPOSAL_OP_RETURN_OUTPUT;
                else
                    break;
            case COMPENSATION_REQUEST:
                if (ProposalConsensus.hasOpReturnDataValidLength(opReturnData))
                    return TxOutputType.COMP_REQ_OP_RETURN_OUTPUT;
                else
                    break;
            case REIMBURSEMENT_REQUEST:
                if (ProposalConsensus.hasOpReturnDataValidLength(opReturnData))
                    return TxOutputType.REIMBURSEMENT_OP_RETURN_OUTPUT;
                else
                    break;
            case BLIND_VOTE:
                if (BlindVoteConsensus.hasOpReturnDataValidLength(opReturnData))
                    return TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT;
                else
                    break;
            case VOTE_REVEAL:
                if (VoteResultConsensus.hasOpReturnDataValidLength(opReturnData))
                    return TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT;
                else
                    break;
            case LOCKUP:
                if (!BondingConsensus.hasOpReturnDataValidLength(opReturnData))
                    return TxOutputType.INVALID_OUTPUT;
                Optional<LockupType> lockupType = BondingConsensus.getLockupType(opReturnData);
                if (!lockupType.isPresent()) {
                    log.warn("No lockupType found for lockup tx, opReturnData=" + Utilities.encodeToHex(opReturnData));
                    return TxOutputType.INVALID_OUTPUT;
                }

                int lockTime = BondingConsensus.getLockTime(opReturnData);
                if (BondingConsensus.isLockTimeInValidRange(lockTime)) {
                    return TxOutputType.LOCKUP_OP_RETURN_OUTPUT;
                } else {
                    break;
                }
            default:
                throw new InvalidParsingConditionException("We must have a defined opReturnType as it was checked earlier in the caller.");
        }

        log.info("We expected a compensation request op_return data but it did not " +
                "match our rules.");
        return TxOutputType.INVALID_OUTPUT;
    }
}
