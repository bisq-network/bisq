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
public final class SignedWitnessDateRequest implements Payload {
    private final String hashAsHex;

    public SignedWitnessDateRequest(String hashAsHex) {
        this.hashAsHex = hashAsHex;
    }

    @Override
    public bisq.bridge.protobuf.SignedWitnessDateRequest toProtoMessage() {
        return bisq.bridge.protobuf.SignedWitnessDateRequest.newBuilder()
                .setHashAsHex(hashAsHex)
                .build();
    }

    public static SignedWitnessDateRequest fromProto(bisq.bridge.protobuf.SignedWitnessDateRequest proto) {
        return new SignedWitnessDateRequest(proto.getHashAsHex());
    }
}
