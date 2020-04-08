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

package bisq.common.proto.persistable;

import com.google.protobuf.Message;

/**
 * Interface for the outer envelope object persisted to disk, where its serialization
 * during persistence takes place on a separate thread (for performance).
 * <p>
 * To make the serialization thread-safe, all modifications of the object must be
 * synchronized with it. This may be achieved by wrapping such modifications with the
 * provided {@link ThreadedPersistableEnvelope#modifySynchronized(Runnable)} method.
 */
public interface ThreadedPersistableEnvelope extends PersistableEnvelope {

    @Override
    default Message toPersistableMessage() {
        synchronized (this) {
            return toProtoMessage();
        }
    }

    default void modifySynchronized(Runnable modifyTask) {
        synchronized (this) {
            modifyTask.run();
        }
    }
}
