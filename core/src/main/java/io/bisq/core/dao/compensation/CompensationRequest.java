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

package io.bisq.core.dao.compensation;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistablePayload;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Represents the state of the CompensationRequest data
@Getter

public final class CompensationRequest implements PersistablePayload {
    private static final Logger log = LoggerFactory.getLogger(CompensationRequest.class);

    private final CompensationRequestPayload compensationRequestPayload;

    @Setter
    private boolean accepted;
    @Setter
    private long fundsReceived;
    //TODO
    @Setter
    private boolean inVotePeriod = true;
    @Setter
    private boolean inFundingPeriod;
    @Setter
    private boolean closed;
    @Setter
    private boolean waitingForVotingPeriod;

    public CompensationRequest(CompensationRequestPayload compensationRequestPayload) {
        this.compensationRequestPayload = compensationRequestPayload;
    }

    // TODO not impl yet
    @Override
    public Message toProtoMessage() {
        return null;
    }
}
