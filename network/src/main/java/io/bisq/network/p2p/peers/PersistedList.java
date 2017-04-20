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

package io.bisq.network.p2p.peers;

import com.google.protobuf.Message;
import io.bisq.common.Marshaller;
import io.bisq.common.persistence.Persistable;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PersistedList<T extends Marshaller> implements Persistable {
    @Getter
    @Setter
    private List<T> list;
    @Setter
    private Function<List<T>, Message> toProto;

    public PersistedList(List<T> list) {
        this.list = list;
    }

    /** convenience ctor */
    public PersistedList(HashSet<T> set) {
        this(set.stream().collect(Collectors.toList()));
    }

    @Override
    public Message toProto() {
        if(Objects.isNull(toProto())) {
            throw new NotImplementedException();
        }
        return toProto.apply(list);
    }
}
