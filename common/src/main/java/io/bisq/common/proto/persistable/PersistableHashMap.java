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
import java.util.Map;
import java.util.function.Function;

public class PersistableHashMap<K, V extends PersistablePayload> implements PersistableEnvelope {
    @Delegate
    @Getter
    private Map<K, V> map = new HashMap<>();
    @Setter
    private Function<Map<K, V>, Message> toProto;

    public PersistableHashMap(Map<K, V> map) {
        this.map = map;
    }

    public PersistableHashMap(Map<K, V> map, Function<Map<K, V>, Message> toProto) {
        this(map);
        this.toProto = toProto;
    }

    @Override
    public Message toProtoMessage() {
        return toProto.apply(map);
    }
}
