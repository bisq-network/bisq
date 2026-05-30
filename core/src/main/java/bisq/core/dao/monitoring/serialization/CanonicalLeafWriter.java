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

package bisq.core.dao.monitoring.serialization;

import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.ScriptType;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Merit;
import bisq.core.dao.state.model.governance.ParamChange;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;

/**
 * Hand-rolled canonical byte encoders for every DAO state leaf class
 * reachable from the hash chain. No protobuf, no Java HashMap — every byte
 * comes out of {@link CanonicalWriter} primitives whose encoding is fully
 * specified in this codebase.
 *
 * <p><strong>Enum encoding.</strong> All enums on the hash path are written
 * via a stable numeric code returned by an explicit switch. The code values
 * mirror the proto enum field numbers from {@code pb.proto}. Encoding via a
 * switch — not via {@link Enum#name()} or {@link Enum#ordinal()} — means a
 * rename or reorder of a model enum is a compile-time visible change at the
 * call site here, not a silent consensus fork. {@code Proposal} subtype
 * discriminator and a few enums whose proto wire form is already a string
 * (Param, BondedRoleType) are written as length-prefixed strings.
 *
 * <p>This file is the single source of truth for the canonical hash format.
 * Any change to a {@code writeXxx} method or to a {@code codeOf} switch is
 * a hard fork.
 */
final class CanonicalLeafWriter {

    private CanonicalLeafWriter() {}

    // ---------- blockchain ----------

    static void writeBlock(CanonicalWriter w, Block block) {
        w.writeI32(block.getHeight());
        w.writeI64(block.getTime());
        w.writeString(block.getHash());
        w.writeOptionalString(block.getPreviousBlockHash());
        w.writeList(block.getTxs(), CanonicalLeafWriter::writeTx);
    }

    static void writeTx(CanonicalWriter w, Tx tx) {
        // BaseTx fields, in proto declaration order
        w.writeString(tx.getTxVersion());
        w.writeString(tx.getId());
        w.writeI32(tx.getBlockHeight());
        w.writeString(tx.getBlockHash());
        w.writeI64(tx.getTime());
        w.writeList(tx.getTxInputs(), CanonicalLeafWriter::writeTxInput);
        // Tx-specific fields
        w.writeList(tx.getTxOutputs(), CanonicalLeafWriter::writeTxOutput);
        w.writeOptional(tx.getTxType(), (cw, t) -> cw.writeEnumCode(codeOf(t)));
        w.writeI64(tx.getBurntBsq());
    }

    static void writeTxInput(CanonicalWriter w, TxInput input) {
        w.writeString(input.getConnectedTxOutputTxId());
        w.writeI32(input.getConnectedTxOutputIndex());
        w.writeOptionalString(input.getPubKey());
    }

    static void writeTxOutput(CanonicalWriter w, TxOutput txOutput) {
        // BaseTxOutput fields first
        writeBaseTxOutputFields(w, txOutput);
        // TxOutput-specific fields
        w.writeEnumCode(codeOf(txOutput.getTxOutputType()));
        w.writeI32(txOutput.getLockTime());
        w.writeI32(txOutput.getUnlockBlockHeight());
    }

    private static void writeBaseTxOutputFields(CanonicalWriter w, BaseTxOutput bto) {
        w.writeI32(bto.getIndex());
        w.writeI64(bto.getValue());
        w.writeString(bto.getTxId());
        w.writeOptional(bto.getPubKeyScript(), CanonicalLeafWriter::writePubKeyScript);
        w.writeOptionalString(bto.getAddress());
        w.writeOptionalBytes(bto.getOpReturnData());
        w.writeI32(bto.getBlockHeight());
    }

    static void writePubKeyScript(CanonicalWriter w, PubKeyScript script) {
        w.writeI32(script.getReqSigs());
        w.writeEnumCode(codeOf(script.getScriptType()));
        w.writeStringList(script.getAddresses());
        w.writeString(script.getAsm());
        w.writeString(script.getHex());
    }

    static void writeTxOutputKey(CanonicalWriter w, TxOutputKey key) {
        w.writeString(key.getTxId());
        w.writeI32(key.getIndex());
    }

    static void writeSpentInfo(CanonicalWriter w, SpentInfo info) {
        w.writeI64(info.getBlockHeight());
        w.writeString(info.getTxId());
        w.writeI32(info.getInputIndex());
    }

    // ---------- governance ----------

    static void writeCycle(CanonicalWriter w, Cycle cycle) {
        w.writeI32(cycle.getHeightOfFirstBlock());
        w.writeList(cycle.getDaoPhaseList(), CanonicalLeafWriter::writeDaoPhase);
    }

    static void writeDaoPhase(CanonicalWriter w, DaoPhase phase) {
        w.writeEnumCode(codeOf(phase.getPhase()));
        w.writeI32(phase.getDuration());
    }

    static void writeIssuance(CanonicalWriter w, Issuance issuance) {
        w.writeString(issuance.getTxId());
        w.writeI32(issuance.getChainHeight());
        w.writeI64(issuance.getAmount());
        w.writeOptionalString(issuance.getPubKey());
        w.writeEnumCode(codeOf(issuance.getIssuanceType()));
    }

    static void writeParamChange(CanonicalWriter w, ParamChange paramChange) {
        w.writeString(paramChange.getParamName());
        w.writeString(paramChange.getValue());
        w.writeI32(paramChange.getActivationHeight());
    }

    static void writeEvaluatedProposal(CanonicalWriter w, EvaluatedProposal ep) {
        w.writeBool(ep.isAccepted());
        writeProposalVoteResult(w, ep.getProposalVoteResult());
    }

    static void writeProposalVoteResult(CanonicalWriter w, ProposalVoteResult r) {
        writeProposal(w, r.getProposal());
        w.writeI64(r.getStakeOfAcceptedVotes());
        w.writeI64(r.getStakeOfRejectedVotes());
        w.writeI32(r.getNumAcceptedVotes());
        w.writeI32(r.getNumRejectedVotes());
        w.writeI32(r.getNumIgnoredVotes());
    }

    static void writeDecryptedBallotsWithMerits(CanonicalWriter w, DecryptedBallotsWithMerits d) {
        w.writeBytes(d.getHashOfBlindVoteList());
        w.writeString(d.getBlindVoteTxId());
        w.writeString(d.getVoteRevealTxId());
        w.writeI64(d.getStake());
        w.writeList(d.getBallotList().getList(), CanonicalLeafWriter::writeBallot);
        w.writeList(d.getMeritList().getList(), CanonicalLeafWriter::writeMerit);
    }

    static void writeBallot(CanonicalWriter w, Ballot ballot) {
        writeProposal(w, ballot.getProposal());
        w.writeOptional(ballot.getVote(), CanonicalLeafWriter::writeVote);
    }

    static void writeVote(CanonicalWriter w, Vote vote) {
        w.writeBool(vote.isAccepted());
    }

    static void writeMerit(CanonicalWriter w, Merit merit) {
        writeIssuance(w, merit.getIssuance());
        w.writeBytes(merit.getSignature());
    }

    static void writeRole(CanonicalWriter w, Role role) {
        w.writeString(role.getUid());
        w.writeString(role.getName());
        w.writeString(role.getLink());
        // BondedRoleType is stored on the wire as a string (proto field type
        // is string bonded_role_type), so we mirror that with a length-
        // prefixed UTF-8 of the enum name. A rename of a BondedRoleType
        // constant is consensus-breaking either way.
        w.writeString(role.getBondedRoleType().name());
    }

    // ---------- proposal hierarchy ----------

    static void writeProposal(CanonicalWriter w, Proposal proposal) {
        // Common base fields in proto declaration order
        w.writeString(proposal.getName());
        w.writeString(proposal.getLink());
        // version is a Java byte storing a uint32; emit unsigned
        w.writeVarint(proposal.getVersion() & 0xFF);
        w.writeI64(proposal.getCreationDate());
        w.writeOptionalString(proposal.getTxId());
        // extraDataMap is TreeMap<String, String>, iterates sorted
        w.writeOptional(proposal.getExtraDataMap(),
                (cw, m) -> cw.writeSortedMap(m, CanonicalWriter::writeString, CanonicalWriter::writeString));
        // Discriminated subtype: stable 1-byte tag (proto oneof field numbers)
        // + subtype-specific payload.
        if (proposal instanceof CompensationProposal) {
            w.writeRawByte(6);
            writeCompensationProposalFields(w, (CompensationProposal) proposal);
        } else if (proposal instanceof ReimbursementProposal) {
            w.writeRawByte(7);
            writeReimbursementProposalFields(w, (ReimbursementProposal) proposal);
        } else if (proposal instanceof ChangeParamProposal) {
            w.writeRawByte(8);
            writeChangeParamProposalFields(w, (ChangeParamProposal) proposal);
        } else if (proposal instanceof RoleProposal) {
            w.writeRawByte(9);
            writeRoleProposalFields(w, (RoleProposal) proposal);
        } else if (proposal instanceof ConfiscateBondProposal) {
            w.writeRawByte(10);
            writeConfiscateBondProposalFields(w, (ConfiscateBondProposal) proposal);
        } else if (proposal instanceof GenericProposal) {
            w.writeRawByte(11);
            // no fields
        } else if (proposal instanceof RemoveAssetProposal) {
            w.writeRawByte(12);
            writeRemoveAssetProposalFields(w, (RemoveAssetProposal) proposal);
        } else {
            throw new IllegalStateException("Unknown Proposal subtype: " + proposal.getClass().getName());
        }
    }

    private static void writeCompensationProposalFields(CanonicalWriter w, CompensationProposal p) {
        w.writeI64(p.getRequestedBsq().value);
        w.writeString(p.getBsqAddress());
    }

    private static void writeReimbursementProposalFields(CanonicalWriter w, ReimbursementProposal p) {
        w.writeI64(p.getRequestedBsq().value);
        w.writeString(p.getBsqAddress());
    }

    private static void writeChangeParamProposalFields(CanonicalWriter w, ChangeParamProposal p) {
        // Proto field `param` is string; mirror that and let Param renames be
        // a consensus-relevant change (governance code reviews catch this).
        w.writeString(p.getParam().name());
        w.writeString(p.getParamValue());
    }

    private static void writeRoleProposalFields(CanonicalWriter w, RoleProposal p) {
        writeRole(w, p.getRole());
        w.writeI64(p.getRequiredBondUnit());
        w.writeI32(p.getUnlockTime());
    }

    private static void writeConfiscateBondProposalFields(CanonicalWriter w, ConfiscateBondProposal p) {
        w.writeString(p.getLockupTxId());
    }

    private static void writeRemoveAssetProposalFields(CanonicalWriter w, RemoveAssetProposal p) {
        w.writeString(p.getTickerSymbol());
    }

    // ---------- stable enum codes ----------
    //
    // The integer codes below are CONSENSUS-CRITICAL. They mirror the proto
    // enum field numbers from proto/src/main/proto/pb.proto. Java values
    // whose name differs from the proto enum (e.g. TxType.UNDEFINED is a
    // local fallback with no proto counterpart) get a code reserved at the
    // far end of the proto value space, marked with "// LOCAL". Any new
    // enum constant added to one of these enums must be assigned a code
    // here; the default branch throws so the compiler does not — but the
    // runtime does — catch the omission.

    static int codeOf(TxType t) {
        switch (t) {
            case UNDEFINED:            return 0;   // matches proto PB_ERROR_TX_TYPE
            case UNDEFINED_TX_TYPE:    return 1;
            case UNVERIFIED:           return 2;
            case INVALID:              return 3;
            case GENESIS:              return 4;
            case TRANSFER_BSQ:         return 5;
            case PAY_TRADE_FEE:        return 6;
            case PROPOSAL:             return 7;
            case COMPENSATION_REQUEST: return 8;
            case REIMBURSEMENT_REQUEST: return 9;
            case BLIND_VOTE:           return 10;
            case VOTE_REVEAL:          return 11;
            case LOCKUP:               return 12;
            case UNLOCK:               return 13;
            case ASSET_LISTING_FEE:    return 14;
            case PROOF_OF_BURN:        return 15;
            case IRREGULAR:            return 16;
            default:
                throw new IllegalStateException("Unmapped TxType: " + t);
        }
    }

    static int codeOf(TxOutputType t) {
        switch (t) {
            case UNDEFINED:                          return 0;   // matches proto PB_ERROR_TX_OUTPUT_TYPE
            case UNDEFINED_OUTPUT:                   return 1;
            case GENESIS_OUTPUT:                     return 2;
            case BSQ_OUTPUT:                         return 3;
            case BTC_OUTPUT:                         return 4;
            case PROPOSAL_OP_RETURN_OUTPUT:          return 5;
            case COMP_REQ_OP_RETURN_OUTPUT:          return 6;
            case REIMBURSEMENT_OP_RETURN_OUTPUT:     return 7;
            case CONFISCATE_BOND_OP_RETURN_OUTPUT:   return 8;
            case ISSUANCE_CANDIDATE_OUTPUT:          return 9;
            case BLIND_VOTE_LOCK_STAKE_OUTPUT:       return 10;
            case BLIND_VOTE_OP_RETURN_OUTPUT:        return 11;
            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:    return 12;
            case VOTE_REVEAL_OP_RETURN_OUTPUT:       return 13;
            case ASSET_LISTING_FEE_OP_RETURN_OUTPUT: return 14;
            case PROOF_OF_BURN_OP_RETURN_OUTPUT:     return 15;
            case LOCKUP_OUTPUT:                      return 16;
            case LOCKUP_OP_RETURN_OUTPUT:            return 17;
            case UNLOCK_OUTPUT:                      return 18;
            case INVALID_OUTPUT:                     return 19;
            default:
                throw new IllegalStateException("Unmapped TxOutputType: " + t);
        }
    }

    static int codeOf(ScriptType t) {
        switch (t) {
            case UNDEFINED:           return 0;   // matches proto PB_ERROR_SCRIPT_TYPES
            case PUB_KEY:             return 1;
            case PUB_KEY_HASH:        return 2;
            case SCRIPT_HASH:         return 3;
            case MULTISIG:            return 4;
            case NULL_DATA:           return 5;
            case WITNESS_V0_KEYHASH:  return 6;
            case WITNESS_V0_SCRIPTHASH: return 7;
            case NONSTANDARD:         return 8;
            case WITNESS_UNKNOWN:     return 9;
            case WITNESS_V1_TAPROOT:  return 10;
            default:
                throw new IllegalStateException("Unmapped ScriptType: " + t);
        }
    }

    static int codeOf(IssuanceType t) {
        // No proto enum exists for IssuanceType (the proto stores it as a
        // string field). We lock the codes here ourselves; any reorder/
        // rename is caught by the golden vector tests.
        switch (t) {
            case UNDEFINED:    return 0;
            case COMPENSATION: return 1;
            case REIMBURSEMENT: return 2;
            default:
                throw new IllegalStateException("Unmapped IssuanceType: " + t);
        }
    }

    static int codeOf(DaoPhase.Phase phase) {
        switch (phase) {
            case UNDEFINED:   return 0;
            case PROPOSAL:    return 1;
            case BREAK1:      return 2;
            case BLIND_VOTE:  return 3;
            case BREAK2:      return 4;
            case VOTE_REVEAL: return 5;
            case BREAK3:      return 6;
            case RESULT:      return 7;
            default:
                throw new IllegalStateException("Unmapped DaoPhase.Phase: " + phase);
        }
    }
}
