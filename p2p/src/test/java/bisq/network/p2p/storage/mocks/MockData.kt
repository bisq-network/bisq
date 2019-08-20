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

package bisq.network.p2p.storage.mocks

import bisq.network.p2p.storage.payload.ExpirablePayload
import bisq.network.p2p.storage.payload.ProtectedStoragePayload

import org.apache.commons.lang3.NotImplementedException

import java.security.PublicKey

data class MockData(val msg: String?, val publicKey: PublicKey) : ProtectedStoragePayload, ExpirablePayload {
    var ttl: Long = 0
    override fun getOwnerPubKey(): PublicKey = publicKey


    override fun getExtraDataMap(): Map<String, String>? = null

    override fun getTTL(): Long = ttl

    override fun toProtoMessage(): protobuf.ProtectedMailboxStorageEntry {
        throw NotImplementedException("toProtoMessage not impl.")
    }
}
