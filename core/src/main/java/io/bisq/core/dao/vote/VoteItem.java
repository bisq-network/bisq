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

package io.bisq.core.dao.vote;

import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

//TODO if sent over wire make final
@Slf4j
@Getter
@ToString
@EqualsAndHashCode
public class VoteItem implements PersistablePayload {
    private final VotingType votingType;
    @Nullable
    private final String name;
    private final long defaultValue;

    private byte value;
    private boolean hasVoted;

    public VoteItem(VotingType votingType, String name, VotingDefaultValues votingDefaultValues) {
        this(votingType, name, (byte) 0x00, votingDefaultValues);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private VoteItem(VotingType votingType, @Nullable String name, byte value, @Nullable VotingDefaultValues votingDefaultValues) {
        this.votingType = votingType;
        this.name = name;
        this.value = value;
        this.defaultValue = votingDefaultValues != null ? votingDefaultValues.getValueByVotingType(votingType) : 0;
    }

    @Override
    public PB.VoteItem toProtoMessage() {
        PB.VoteItem.Builder builder = PB.VoteItem.newBuilder()
                .setVotingType(PB.VoteItem.VotingType.valueOf(votingType.name()))
                .setDefaultValue(defaultValue)
                .setHasVoted(hasVoted)
                .setValue(value);
        Optional.ofNullable(name).ifPresent(builder::setName);
        return builder.build();
    }

    public static VoteItem fromProto(PB.VoteItem voteItem) {
        VotingDefaultValues defaultValues = new VotingDefaultValues();
        VotingType votingType = ProtoUtil.enumFromProto(VotingType.class, voteItem.getVotingType().name());
        defaultValues.setValueByVotingType(votingType, voteItem.getValue());
        return new VoteItem(votingType,
                voteItem.getName(),
                (byte) voteItem.getValue(),
                defaultValues);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getAdjustedValue(long originalValue, int change) {
        checkArgument(change < 255 && change > -1,
                "Range for change can be 0 to 254. 255 is not supported as we want a 0 value in the middle");
        double fact = (change - 127) / 127d;
        return (long) (originalValue * Math.pow(10, fact));
    }

    // We return the change parameter (0-254)
    public int getChange(long originalValue, long newValue) {
        return (int) Math.round(Math.log10((double) newValue / (double) originalValue) * 127 + 127);
    }

    public void setValue(byte value) {
        this.value = value;
        this.hasVoted = true;
    }
}
