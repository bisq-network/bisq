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

import io.bisq.common.proto.persistable.PersistablePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@ToString
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class CompensationRequestVoteItemCollection extends VoteItem implements PersistablePayload {
    @Getter
    private final List<CompensationRequestVoteItem> compensationRequestVoteItems = new ArrayList<>();

    /**
     * constructor
     */
    public CompensationRequestVoteItemCollection(@SuppressWarnings("SameParameterValue") VotingType votingType) {
        super(votingType, null, null);
    }

    public List<CompensationRequestVoteItem> getCompensationRequestVoteItemsSortedByTxId() {
        ArrayList<CompensationRequestVoteItem> list = new ArrayList<>(compensationRequestVoteItems);
        list.sort((o1, o2) -> o2.compensationRequest.getCompensationRequestPayload().getFeeTxId().compareTo(o1.compensationRequest.getCompensationRequestPayload().getFeeTxId()));
        return list;
    }

    public void addCompensationRequestVoteItem(CompensationRequestVoteItem compensationRequestVoteItem) {
        compensationRequestVoteItems.add(compensationRequestVoteItem);
    }

    public boolean hasVotedOnAnyItem() {
        return compensationRequestVoteItems.stream().filter(CompensationRequestVoteItem::isHasVoted).findAny().isPresent();
    }
}
