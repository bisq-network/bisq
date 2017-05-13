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

package io.bisq.common.proto;

import com.google.protobuf.Message;
import io.bisq.common.Payload;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProtoCollectionUtil {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Convenience
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T extends Message> Iterable<T> collectionToProto(Collection<? extends Payload> collection) {
        return collection.stream().map(e -> (T) e.toProtoMessage()).collect(Collectors.toList());
    }

    public static <T> Iterable<T> collectionToProto(Collection<? extends Payload> collection, Function<? super Message, T> extra) {
        return collection.stream().map(o -> {
            return extra.apply(o.toProtoMessage());
        }).collect(Collectors.toList());
    }
}
