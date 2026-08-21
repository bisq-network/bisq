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

package bisq.bridge.grpc.messages;

import bisq.core.dao.governance.bond.role.BondedRoleRegistration;

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class BondedRoleVerificationRequest implements Payload {
    private final BondedRoleRegistration registration;

    public BondedRoleVerificationRequest(BondedRoleRegistration registration) {
        this.registration = registration;
    }

    @Override
    public bisq.bridge.protobuf.BondedRoleVerificationRequest toProtoMessage() {
        return bisq.bridge.protobuf.BondedRoleVerificationRequest.newBuilder()
                .setProtocolVersion(registration.protocolVersion())
                .setBondUserName(registration.bondUserName())
                .setRoleType(registration.roleType())
                .setProposalTxId(registration.proposalTxId())
                .setLockupTxId(registration.lockupTxId())
                .setProfileId(registration.profileId())
                .setSignatureBase64(registration.signatureBase64())
                .build();
    }

    public static BondedRoleVerificationRequest fromProto(bisq.bridge.protobuf.BondedRoleVerificationRequest proto) {
        return new BondedRoleVerificationRequest(new BondedRoleRegistration(
                proto.hasProtocolVersion() ?
                        proto.getProtocolVersion() : BondedRoleRegistration.LEGACY_PROTOCOL_VERSION,
                proto.getBondUserName(),
                proto.getRoleType(),
                proto.getProposalTxId(),
                proto.getLockupTxId(),
                proto.getProfileId(),
                proto.getSignatureBase64()));
    }
}
