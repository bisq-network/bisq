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
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
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
 * comes out of a {@link CanonicalWriter} primitive whose encoding is fully
 * specified in this codebase.
 *
 * <p>Field ordering within each {@code writeXxx} method matches the proto
 * declaration order from {@code proto/src/main/proto/pb.proto}, so the
 * canonical bytes parallel (but are not byte-identical to) the protobuf
 * wire format. Discriminated unions ({@link Proposal} subtypes) use a
 * fixed type-tag byte chosen to match the proto oneof tag for clarity:
 * Compensation=6, Reimbursement=7, ChangeParam=8, Role=9,
 * ConfiscateBond=10, Generic=11, RemoveAsset=12.
 *
 * <p>Enums are written as length-prefixed UTF-8 of {@link Enum#name()};
 * this is stable across enum-ordinal reorderings within a Java release.
 *
 * <p>This file is the single source of truth for the canonical hash format.
 * Any change to a {@code writeXxx} method is a hard fork.
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
        w.writeOptionalEnum(tx.getTxType());
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
        w.writeEnum(txOutput.getTxOutputType());
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
        w.writeEnum(script.getScriptType());
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
        w.writeEnum(phase.getPhase());
        w.writeI32(phase.getDuration());
    }

    static void writeIssuance(CanonicalWriter w, Issuance issuance) {
        w.writeString(issuance.getTxId());
        w.writeI32(issuance.getChainHeight());
        w.writeI64(issuance.getAmount());
        w.writeOptionalString(issuance.getPubKey());
        w.writeEnum(issuance.getIssuanceType());
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
        w.writeEnum(role.getBondedRoleType());
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
        // Discriminated subtype: 1-byte tag + subtype-specific payload.
        // Tag values match the proto oneof field numbers so they are stable.
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
        // Proto stores `param` as the enum name string; mirror that semantics.
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
}
