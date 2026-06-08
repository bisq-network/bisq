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

import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.Message;

/**
 * Base interface for Envelope and Payload.
 */
public interface Proto {
    Message toProtoMessage();

    default byte[] serialize() {
        return toProtoMessage().toByteArray();
    }

    // If the class implements ExcludedFieldsProto this method will be overwritten so that
    // fields annotated with ExcludeForHash will be excluded.
    default byte[] serializeForHash() {
        if (this instanceof Canonical canonical) {
            return canonical.encodeCanonical(CanonicalEncoder.DEFAULT);
        }
        return serialize();
    }
}
