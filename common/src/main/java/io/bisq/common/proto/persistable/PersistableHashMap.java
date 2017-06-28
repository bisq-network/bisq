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

package io.bisq.common.proto.persistable;

import com.google.protobuf.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.util.HashMap;
import java.util.function.Function;

public class PersistableHashMap<K extends PersistablePayload, V extends PersistablePayload> implements PersistableEnvelope {
    @Delegate
    @Getter
    private HashMap<K, V> hashMap = new HashMap<>();
    @Setter
    private Function<HashMap<K, V>, Message> toProto;

    public PersistableHashMap(HashMap<K, V> hashMap) {
        this.hashMap = hashMap;
    }

    public PersistableHashMap(HashMap<K, V> hashMap, Function<HashMap<K, V>, Message> toProto) {
        this(hashMap);
        this.toProto = toProto;
    }

    @Override
    public Message toProtoMessage() {
        return toProto.apply(hashMap);
    }
}
