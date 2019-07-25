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

package bisq.core.dao.governance.proofofburn;

import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import com.google.common.base.Charsets;

import java.util.Objects;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * MyProofOfBurn is persisted locally and holds the preImage and txId.
 */
@Immutable
@Value
@Slf4j
public final class MyProofOfBurn implements PersistablePayload, NetworkPayload {
    private final String txId;
    private final String preImage;
    private final transient byte[] hash; // Not persisted as it is derived from preImage. Stored for caching purpose only.

    public MyProofOfBurn(String txId, String preImage) {
        this.txId = txId;
        this.preImage = preImage;
        this.hash = Hash.getSha256Ripemd160hash(preImage.getBytes(Charsets.UTF_8));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.MyProofOfBurn toProtoMessage() {
        return protobuf.MyProofOfBurn.newBuilder()
                .setTxId(txId)
                .setPreImage(preImage)
                .build();
    }

    public static MyProofOfBurn fromProto(protobuf.MyProofOfBurn proto) {
        return new MyProofOfBurn(proto.getTxId(), proto.getPreImage());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MyProofOfBurn)) return false;
        if (!super.equals(o)) return false;
        MyProofOfBurn that = (MyProofOfBurn) o;
        return Objects.equals(txId, that.txId) &&
                Objects.equals(preImage, that.preImage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txId, preImage);
    }

    @Override
    public String toString() {
        return "MyProofOfBurn{" +
                "\n     txId='" + txId + '\'' +
                ",\n     preImage=" + preImage +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
