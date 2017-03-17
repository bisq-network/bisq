/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.vote;

import io.bisq.common.app.Version;
import io.bisq.common.persistance.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VoteItemsList extends ArrayList<VoteItem> implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(VoteItemsList.class);
    private final CompensationRequestVoteItemCollection compensationRequest;

    private boolean isMyVote;
    private byte[] hashOfCompensationRequestsCollection;

    public VoteItemsList(VotingDefaultValues votingDefaultValues) {
        add(new VoteItem(VotingType.CREATE_OFFER_FEE_IN_BTC, "Create offer fee (in BTC Satoshi)", votingDefaultValues));
        add(new VoteItem(VotingType.TAKE_OFFER_FEE_IN_BTC, "Take offer fee (in BTC Satoshi)", votingDefaultValues));
        add(new VoteItem(VotingType.CREATE_OFFER_FEE_IN_BSQ, "Create offer fee (in BSQ)", votingDefaultValues));
        add(new VoteItem(VotingType.TAKE_OFFER_FEE_IN_BSQ, "Take offer fee (in BSQ)", votingDefaultValues));
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

    public CompensationRequestVoteItemCollection getCompensationRequestVoteItemCollection() {
        return compensationRequest;
    }

    public List<VoteItem> getVoteItemList() {
        return this.stream()
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
        return getVoteItemList().stream()
                .filter(VoteItem::hasVoted)
                .findAny()
                .isPresent() || compensationRequest.hasVotedOnAnyItem();
    }

    public Optional<VoteItem> getVoteItemByVotingType(VotingType votingType) {
        return getVoteItemList().stream().filter(e -> e.votingType == votingType).findAny();
    }
}
