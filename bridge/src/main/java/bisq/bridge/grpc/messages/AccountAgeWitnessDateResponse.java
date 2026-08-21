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
public final class AccountAgeWitnessDateResponse implements Payload {
    private final long date;

    public AccountAgeWitnessDateResponse(long date) {
        this.date = date;
    }

    @Override
    public bisq.bridge.protobuf.AccountAgeWitnessDateResponse toProtoMessage() {
        return bisq.bridge.protobuf.AccountAgeWitnessDateResponse.newBuilder()
                .setDate(date)
                .build();
    }

    public static AccountAgeWitnessDateResponse fromProto(bisq.bridge.protobuf.AccountAgeWitnessDateResponse proto) {
        return new AccountAgeWitnessDateResponse(proto.getDate());
    }
}
