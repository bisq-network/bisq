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

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.core.dao.compensation.CompensationRequest;

public final class CompensationRequestVoteItem implements PersistablePayload {
    public final CompensationRequest compensationRequest;
    private boolean declineVote;
    private boolean acceptedVote;
    private boolean hasVoted;

    public CompensationRequestVoteItem(CompensationRequest compensationRequest) {
        this.compensationRequest = compensationRequest;
    }

    public void setDeclineVote(boolean declineVote) {
        this.declineVote = declineVote;
        this.hasVoted = true;
    }

    public boolean isDeclineVote() {
        return declineVote;
    }

    public void setAcceptedVote(boolean acceptedVote) {
        this.acceptedVote = acceptedVote;
        this.hasVoted = true;
    }

    public boolean isAcceptedVote() {
        return acceptedVote;
    }

    public boolean isHasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    @Override
    public String toString() {
        return "CompensationRequestVoteItem{" +
                "compensationRequest=" + compensationRequest +
                ", declineVote=" + declineVote +
                ", acceptedVote=" + acceptedVote +
                ", hasVoted=" + hasVoted +
                '}';
    }

    // TODO not impl yet
    @Override
    public Message toProtoMessage() {
        return null;
    }
}
