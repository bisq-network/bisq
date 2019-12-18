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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.app.Version;
import bisq.common.util.CollectionUtils;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class RoleProposal extends Proposal implements ImmutableDaoStateModel {
    private final Role role;
    private final long requiredBondUnit;
    private final int unlockTime; // in blocks

    public RoleProposal(Role role, Map<String, String> extraDataMap) {
        this(role.getName(),
                role.getLink(),
                role,
                role.getBondedRoleType().getRequiredBondUnit(),
                role.getBondedRoleType().getUnlockTimeInBlocks(),
                Version.PROPOSAL,
                new Date().getTime(),
                null,
                extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RoleProposal(String name,
                         String link,
                         Role role,
                         long requiredBondUnit,
                         int unlockTime,
                         byte version,
                         long creationDate,
                         String txId,
                         Map<String, String> extraDataMap) {
        super(name,
                link,
                version,
                creationDate,
                txId,
                extraDataMap);

        this.role = role;
        this.requiredBondUnit = requiredBondUnit;
        this.unlockTime = unlockTime;
    }

    @Override
    public protobuf.Proposal.Builder getProposalBuilder() {
        final protobuf.RoleProposal.Builder builder = protobuf.RoleProposal.newBuilder()
                .setRole(role.toProtoMessage())
                .setRequiredBondUnit(requiredBondUnit)
                .setUnlockTime(unlockTime);
        return super.getProposalBuilder().setRoleProposal(builder);
    }

    public static RoleProposal fromProto(protobuf.Proposal proto) {
        final protobuf.RoleProposal proposalProto = proto.getRoleProposal();
        return new RoleProposal(proto.getName(),
                proto.getLink(),
                Role.fromProto(proposalProto.getRole()),
                proposalProto.getRequiredBondUnit(),
                proposalProto.getUnlockTime(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.BONDED_ROLE;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_ROLE;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_ROLE;
    }

    @Override
    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    @Override
    public Proposal cloneProposalAndAddTxId(String txId) {
        return new RoleProposal(name,
                link,
                role,
                requiredBondUnit,
                unlockTime,
                version,
                creationDate,
                txId,
                extraDataMap);
    }

    @Override
    public String toString() {
        return "RoleProposal{" +
                "\n     role=" + role +
                "\n     requiredBondUnit=" + requiredBondUnit +
                "\n     unlockTime=" + unlockTime +
                "\n} " + super.toString();
    }
}
