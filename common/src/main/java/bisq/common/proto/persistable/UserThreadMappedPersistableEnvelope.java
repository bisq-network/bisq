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

import bisq.common.UserThread;

import com.google.protobuf.Message;

import com.google.common.util.concurrent.Futures;

import java.util.concurrent.FutureTask;

/**
 * Interface for the outer envelope object persisted to disk, where its serialization
 * during persistence is forced to take place on the user thread.
 * <p>
 * To avoid jitter, this should be only be used for small, safely critical stores. Larger
 * or frequently written stores should either implement {@link PersistableEnvelope}
 * directly (where thread-safety isn't needed) or use {@link ThreadedPersistableEnvelope}.
 */
public interface UserThreadMappedPersistableEnvelope extends PersistableEnvelope {

    @Override
    default Message toPersistableMessage() {
        FutureTask<Message> toProtoOnUserThread = new FutureTask<>(this::toProtoMessage);
        UserThread.execute(toProtoOnUserThread);
        return Futures.getUnchecked(toProtoOnUserThread);
    }
}
