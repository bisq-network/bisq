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

package bisq.common;


import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.io.IOException;
import java.io.OutputStream;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.common.util.Utilities.snakeToCamel;

/**
 * Borrowed from Bisq2, but as we want to reduce risk of breaking things, specially for the DAO we pack
 * the support for ExcludeForHash annotations into a dedicated interface.
 * Classes which use ExcludeForHash need to implement that interface.
 */
public interface ExcludeForHashAwareProto extends Proto {

    ////////////////////////////////////////////////////////////////////////////////
    // Override Proto methods
    ////////////////////////////////////////////////////////////////////////////////

    default Message toProtoMessage() {
        return completeProto();
    }

    default byte[] serialize() {
        return resolveProto(false).toByteArray();
    }

    default byte[] serializeForHash() {
        return resolveProto(true).toByteArray();
    }


    ////////////////////////////////////////////////////////////////////////////////
    // API
    ////////////////////////////////////////////////////////////////////////////////

    Message.Builder getBuilder(boolean serializeForHash);

    Message toProto(boolean serializeForHash);

    default Message completeProto() {
        return toProto(false);
    }

    default <T extends Message> T resolveProto(boolean serializeForHash) {
        //noinspection unchecked
        return (T) resolveBuilder(getBuilder(serializeForHash), serializeForHash).build();
    }

    default <B extends Message.Builder> B resolveBuilder(B builder, boolean serializeForHash) {
        return serializeForHash ? clearAnnotatedFields(builder) : builder;
    }

    default int getSerializedSize() {
        return resolveProto(false).getSerializedSize();
    }

    default void writeDelimitedTo(OutputStream outputStream) throws IOException {
        completeProto().writeDelimitedTo(outputStream);
    }

    default Set<String> getExcludedFields() {
        return Arrays.stream(getClass().getDeclaredFields())
                .peek(field -> field.setAccessible(true))
                .filter(field -> field.isAnnotationPresent(ExcludeForHash.class))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Requires that the name of the java fields is the same as the name of the proto definition.
     *
     * @param builder The builder we transform by clearing the ExcludeForHash annotated fields.
     * @return Builder with the fields annotated with ExcludeForHash cleared.
     */
    default <B extends Message.Builder> B clearAnnotatedFields(B builder) {
        Set<String> excludedFields = getExcludedFields();
        if (!excludedFields.isEmpty()) {
            getLogger().debug("Clear fields in builder annotated with @ExcludeForHash: {}", excludedFields);
        }

        for (Descriptors.FieldDescriptor fieldDesc : builder.getAllFields().keySet()) {
            if (
                    excludedFields.contains(fieldDesc.getName())
                    || excludedFields.contains(snakeToCamel(fieldDesc.getName()))
            ) {
                builder.clearField(fieldDesc);
            }
        }
        return builder;
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass().getSimpleName());
    }
}
