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

import java.util.Map;
import java.util.stream.Collectors;

// Key in PB map cannot be byte array, so we use a hex string of the bytes array
public class AccountAgeWitnessMap extends PersistableHashMap<String, AccountAgeWitness> {

    public AccountAgeWitnessMap(Map<String, AccountAgeWitness> map) {
        super(map);
    }

    @Override
    public Message toProtoMessage() {
        Map<String, PB.AccountAgeWitness> protoMap = getMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    return e.getValue().toProtoAccountAgeWitness();
                }));

        final PB.AccountAgeWitnessMap.Builder builder = PB.AccountAgeWitnessMap.newBuilder();
        builder.putAllAccountAgeWitnessMap(protoMap);

        return PB.PersistableEnvelope.newBuilder()
                .setAccountAgeWitnessMap(builder).build();
    }

    public static PersistableEnvelope fromProto(PB.AccountAgeWitnessMap proto) {
        Map<String, AccountAgeWitness> map = proto.getAccountAgeWitnessMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    return AccountAgeWitness.fromProto(e.getValue());
                }));
        return new AccountAgeWitnessMap(map);
    }
}
