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

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.Proto;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ProtoUtil {

    public static Set<byte[]> byteSetFromProtoByteStringList(List<ByteString> byteStringList) {
        return byteStringList.stream().map(ByteString::toByteArray).collect(Collectors.toSet());
    }

    /**
     * Returns the input String, except when it's the empty string: "", then null is returned.
     * Note: "" is the default value for a protobuffer string, so this means it's not filled in.
     */
    @Nullable
    public static String stringOrNullFromProto(String proto) {
        return "".equals(proto) ? null : proto;
    }

    @Nullable
    public static byte[] byteArrayOrNullFromProto(ByteString proto) {
        return proto.isEmpty() ? null : proto.toByteArray();
    }

    public static <E extends Enum<E>> E enumFromProto(Class<E> e, String id) {
        E result = null;
        try {
            result = Enum.valueOf(e, id);
        } catch (Throwable err) {
            log.error("Invalid value for enum " + e.getSimpleName() + ": " + id, err);
        }

        return result;
    }

    public static <T extends Message> Iterable<T> collectionToProto(Collection<? extends Proto> collection) {
        return collection.stream()
                .map(e -> {
                    final Message message = e.toProtoMessage();
                    try {
                        //noinspection unchecked
                        return (T) message;
                    } catch (Throwable t) {
                        log.error("message could not be casted. message=" + message);
                        return null;
                    }
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());
    }

    public static <T> Iterable<T> collectionToProto(Collection<? extends Proto> collection, Function<? super Message, T> extra) {
        return collection.stream().map(o -> extra.apply(o.toProtoMessage())).collect(Collectors.toList());
    }
}
