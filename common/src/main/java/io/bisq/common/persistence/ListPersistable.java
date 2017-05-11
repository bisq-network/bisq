/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.common.persistence;

import com.google.protobuf.Message;
import lombok.Getter;
import lombok.Setter;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListPersistable<T extends Persistable> implements Persistable {
    @Getter
    @Setter
    private List<T> list;
    @Setter
    private Function<List<T>, Message> toProto;

    public ListPersistable(List<T> list) {
        this.list = list;
    }

    public ListPersistable(List<T> list,  Function<List<T>, Message> toProto) {
        this(list);
        this.toProto = toProto;
    }

    /** convenience ctor */
    public ListPersistable(HashSet<T> set) {
        this(set.stream().collect(Collectors.toList()));
    }
    /** convenience ctor */
    public ListPersistable(HashSet<T> set,  Function<List<T>, Message> toProto) {
        this(set);
        this.toProto = toProto;
    }

    @Override
    public Message toProtoMessage() {
        if(Objects.isNull(toProto)) {
            throw new NotImplementedException();
        }
        return toProto.apply(list);
    }

}
