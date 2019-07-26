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

package bisq.network.p2p.storage.mocks;

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import org.apache.commons.lang3.NotImplementedException;

import java.security.PublicKey;

import java.util.Map;

import javax.annotation.Nullable;

@SuppressWarnings("ALL")
public class MockData implements ProtectedStoragePayload, ExpirablePayload {
    public final String msg;
    public final PublicKey publicKey;
    public long ttl;

    @Nullable
    private Map<String, String> extraDataMap;

    public MockData(String msg, PublicKey publicKey) {
        this.msg = msg;
        this.publicKey = publicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockData)) return false;

        MockData that = (MockData) o;

        return !(msg != null ? !msg.equals(that.msg) : that.msg != null);

    }

    @Override
    public int hashCode() {
        return msg != null ? msg.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MockData{" +
                "msg='" + msg + '\'' +
                '}';
    }

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    @Override
    public long getTTL() {
        return ttl;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return publicKey;
    }

    @Override
    public protobuf.ProtectedMailboxStorageEntry toProtoMessage() {
        throw new NotImplementedException("toProtoMessage not impl.");
    }
}
