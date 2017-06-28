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
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VoteItemsList implements PersistablePayload {
    private final CompensationRequestVoteItemCollection compensationRequest;

    private boolean isMyVote;
    private byte[] hashOfCompensationRequestsCollection;
    @Getter
    private List<VoteItem> allVoteItemList = new ArrayList<>();


    private boolean add(VoteItem voteItem) {
        return allVoteItemList.add(voteItem);
    }

    private boolean remove(VoteItem voteItem) {
        return allVoteItemList.remove(voteItem);
    }

    // TODO no translations yet
    public VoteItemsList(VotingDefaultValues votingDefaultValues) {
        add(new VoteItem(VotingType.MAKER_FEE_IN_BTC, "Maker fee (in BTC Satoshi)", votingDefaultValues));
        add(new VoteItem(VotingType.TAKER_FEE_IN_BTC, "Taker fee (in BTC Satoshi)", votingDefaultValues));
        add(new VoteItem(VotingType.MAKER_FEE_IN_BSQ, "Maker fee (in BSQ)", votingDefaultValues));
        add(new VoteItem(VotingType.TAKER_FEE_IN_BSQ, "Taker fee (in BSQ)", votingDefaultValues));
        add(new VoteItem(VotingType.CREATE_COMPENSATION_REQUEST_FEE_IN_BSQ, "Compensation request fee (in BSQ)", votingDefaultValues));
        add(new VoteItem(VotingType.VOTING_FEE_IN_BSQ, "Voting fee (in BSQ)", votingDefaultValues));

        add(new VoteItem(VotingType.COMPENSATION_REQUEST_PERIOD_IN_BLOCKS, "Compensation request period (in blocks)", votingDefaultValues));
        add(new VoteItem(VotingType.VOTING_PERIOD_IN_BLOCKS, "Voting period (in blocks)", votingDefaultValues));
        add(new VoteItem(VotingType.FUNDING_PERIOD_IN_BLOCKS, "Funding period (in blocks)", votingDefaultValues));
        add(new VoteItem(VotingType.BREAK_BETWEEN_PERIODS_IN_BLOCKS, "Break between periods (in blocks)", votingDefaultValues));

        add(new VoteItem(VotingType.QUORUM_FOR_COMPENSATION_REQUEST_VOTING, "Quorum for compensation request (in %)", votingDefaultValues));
        add(new VoteItem(VotingType.QUORUM_FOR_PARAMETER_VOTING, "Quorum for parameter vote (in %)", votingDefaultValues));

        add(new VoteItem(VotingType.MIN_BTC_AMOUNT_COMPENSATION_REQUEST, "Min. amount for compensation request (in BTC)", votingDefaultValues));
        add(new VoteItem(VotingType.MAX_BTC_AMOUNT_COMPENSATION_REQUEST, "Max. amount for compensation request (in BTC)", votingDefaultValues));

        add(new VoteItem(VotingType.CONVERSION_RATE, "BSQ/BTC conversion rate", votingDefaultValues));

        compensationRequest = new CompensationRequestVoteItemCollection(VotingType.COMP_REQUEST_MAPS);
        add(compensationRequest);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO not impl yet
    @Override
    public Message toProtoMessage() {
        return null;
    }

    public static PersistableEnvelope fromProto(PB.VoteItemsList voteItemsList) {
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CompensationRequestVoteItemCollection getCompensationRequestVoteItemCollection() {
        return compensationRequest;
    }

    public List<VoteItem> getVoteItemList() {
        return allVoteItemList.stream()
                .filter(e -> !(e instanceof CompensationRequestVoteItemCollection))
                .collect(Collectors.toList());
    }

    public void setIsMyVote(boolean isMyVote) {
        this.isMyVote = isMyVote;
    }

    public boolean isMyVote() {
        return isMyVote;
    }

    public void setHashOfCompensationRequestsCollection(byte[] hashOfCompensationRequestsCollection) {
        this.hashOfCompensationRequestsCollection = hashOfCompensationRequestsCollection;
    }

    public boolean hasVotedOnAnyItem() {
        return allVoteItemList.stream()
                .filter(VoteItem::isHasVoted)
                .findAny()
                .isPresent() || compensationRequest.hasVotedOnAnyItem();
    }

    public Optional<VoteItem> getVoteItemByVotingType(VotingType votingType) {
        return allVoteItemList.stream().filter(e -> e.getVotingType() == votingType).findAny();
    }
}
