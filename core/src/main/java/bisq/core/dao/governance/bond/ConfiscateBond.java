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

package bisq.core.dao.governance.bond;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * Holds the hash of a confiscated bond.
 */
@Immutable
@Value
public class ConfiscateBond implements PersistablePayload {
    private final String lockupTxId;

    public ConfiscateBond(String lockupTxId) {
        this.lockupTxId = lockupTxId;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public PB.ConfiscateBond toProtoMessage() {
        return PB.ConfiscateBond.newBuilder()
                .setLockupTxId(lockupTxId)
                .build();
    }

    public static ConfiscateBond fromProto(PB.ConfiscateBond proto) {
        return new ConfiscateBond(proto.getLockupTxId());
    }


    @Override
    public String toString() {
        return "ConfiscateBond{" +
                "\n     lockupTxId='" + lockupTxId + '\'' +
                "\n}";
    }
}
