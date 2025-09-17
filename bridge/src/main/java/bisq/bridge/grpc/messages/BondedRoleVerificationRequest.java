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

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public final class BondedRoleVerificationRequest implements Payload {
    private final String bondUserName;
    private final String roleType;
    private final String profileId;
    private final String signatureBase64;

    public BondedRoleVerificationRequest(String bondUserName,
                                         String roleType,
                                         String profileId,
                                         String signatureBase64) {
        this.bondUserName = bondUserName;
        this.roleType = roleType;
        this.profileId = profileId;
        this.signatureBase64 = signatureBase64;
    }

    @Override
    public bisq.bridge.protobuf.BondedRoleVerificationRequest toProtoMessage() {
        return bisq.bridge.protobuf.BondedRoleVerificationRequest.newBuilder()
                .setBondUserName(bondUserName)
                .setRoleType(roleType)
                .setProfileId(profileId)
                .setSignatureBase64(signatureBase64)
                .build();
    }

    public static BondedRoleVerificationRequest fromProto(bisq.bridge.protobuf.BondedRoleVerificationRequest proto) {
        return new BondedRoleVerificationRequest(proto.getBondUserName(),
                proto.getRoleType(),
                proto.getProfileId(),
                proto.getSignatureBase64()
        );
    }
}
