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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PersistableList<T extends PersistablePayload> implements PersistableEnvelope {
    @Delegate
    @Getter
    @Setter
    private List<T> list;
    @Setter
    private Function<List<T>, Message> toProto;

    public PersistableList() {
        list = new ArrayList<>();
    }

    public PersistableList(List<T> list) {
        this.list = list;
    }

    public PersistableList(List<T> list, Function<List<T>, Message> toProto) {
        this(list);
        this.toProto = toProto;
    }

    public PersistableList(HashSet<T> set) {
        this(set.stream().collect(Collectors.toList()));
    }

    public PersistableList(HashSet<T> set, Function<List<T>, Message> toProto) {
        this(set);
        this.toProto = toProto;
    }

    @Override
    public Message toProtoMessage() {
        return toProto.apply(list);
    }
}
