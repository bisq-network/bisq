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

import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

// Represents the state of the CompensationRequest data
// TODO cleanup
@Getter
@EqualsAndHashCode
@Slf4j
public final class CompensationRequest implements PersistablePayload {

    private final CompensationRequestPayload payload;

    @Setter //TODO
    private boolean accepted;
    @Setter //TODO
    private long fundsReceived;
    //TODO
    @Setter
    private boolean inVotePeriod = true;
    @Setter //TODO
    private boolean inFundingPeriod;
    @Setter //TODO
    private boolean closed;
    @Setter //TODO
    private boolean waitingForVotingPeriod;

    @Nullable
    private Map<String, String> extraDataMap;

    public CompensationRequest(CompensationRequestPayload payload) {
        this.payload = payload;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationRequest(CompensationRequestPayload payload,
                                boolean accepted,
                                long fundsReceived,
                                boolean inVotePeriod,
                                boolean inFundingPeriod,
                                boolean closed,
                                boolean waitingForVotingPeriod,
                                @Nullable Map<String, String> extraDataMap) {
        this.payload = payload;
        this.accepted = accepted;
        this.fundsReceived = fundsReceived;
        this.inVotePeriod = inVotePeriod;
        this.inFundingPeriod = inFundingPeriod;
        this.closed = closed;
        this.waitingForVotingPeriod = waitingForVotingPeriod;
        this.extraDataMap = extraDataMap;
    }

    @Override
    public PB.CompensationRequest toProtoMessage() {
        final PB.CompensationRequest.Builder builder = PB.CompensationRequest.newBuilder()
                .setCompensationRequestPayload(payload.getCompensationRequestPayloadBuilder())
                .setAccepted(accepted)
                .setFundsReceived(fundsReceived)
                .setInVotePeriod(isInVotePeriod())
                .setInFundingPeriod(isInFundingPeriod())
                .setClosed(closed)
                .setWaitingForVotingPeriod(waitingForVotingPeriod);

        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder.build();
    }

    public static CompensationRequest fromProto(PB.CompensationRequest proto) {
        return new CompensationRequest(
                CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload()),
                proto.getAccepted(),
                proto.getFundsReceived(),
                proto.getInVotePeriod(),
                proto.getInFundingPeriod(),
                proto.getClosed(),
                proto.getWaitingForVotingPeriod(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }
}
