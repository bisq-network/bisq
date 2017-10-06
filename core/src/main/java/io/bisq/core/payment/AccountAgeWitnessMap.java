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

package io.bisq.core.payment;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableHashMap;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.P2PDataStorage;

import java.util.HashMap;

// key in PB map cannot be byte array, so we use a hex string of the bytes array
public class AccountAgeWitnessMap extends PersistableHashMap<P2PDataStorage.ByteArray, AccountAgeWitness> {

    public AccountAgeWitnessMap(HashMap<P2PDataStorage.ByteArray, AccountAgeWitness> hashMap) {
        super(hashMap);
    }

    @Override
    public Message toProtoMessage() {
        PB.PersistableEnvelope.Builder builder = PB.PersistableEnvelope.newBuilder()
                .setAccountAgeWitnessMap(PB.AccountAgeWitnessMap.newBuilder());
       
    /*    .putAllAccountAgeWitnessMap(getHashMap().stream()
                .map(TradeStatistics::toProtoTradeStatistics)
                .collect(Collectors.toList())))*/
        return builder.build();
    }

    public static PersistableEnvelope fromProto(PB.AccountAgeWitnessMap proto) {
        return null; /*new AccountAgeWitnessMap(new ArrayList<>(proto.getAccountAgeWitnessMap().stream()
                .map(TradeStatistics::fromProto)
                .collect(Collectors.toList())));*/
    }
}
