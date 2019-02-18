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

package bisq.common.proto;

import bisq.common.Proto;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import com.google.common.base.Enums;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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

    /**
     * Get a Java enum from a Protobuf enum in a safe way.
     *
     * @param enumType the class of the enum, e.g: BlaEnum.class
     * @param name     the name of the enum entry, e.g: proto.getWinner().name()
     * @param <E>      the enum Type
     * @return an enum
     */
    @Nullable
    public static <E extends Enum<E>> E enumFromProto(Class<E> enumType, String name) {
        E result = Enums.getIfPresent(enumType, name).orNull();
        if (result == null) {
            log.error("Invalid value for enum " + enumType.getSimpleName() + ": " + name);
            result = Enums.getIfPresent(enumType, "UNDEFINED").orNull();
            log.error("We try to lookup for an enum entry with name 'UNDEFINED' and use that if available, " +
                    "otherwise the enum is null. enum={}", result);
            return result;
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
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static <T> Iterable<T> collectionToProto(Collection<? extends Proto> collection, Function<? super Message, T> extra) {
        return collection.stream().map(o -> extra.apply(o.toProtoMessage())).collect(Collectors.toList());
    }
}
