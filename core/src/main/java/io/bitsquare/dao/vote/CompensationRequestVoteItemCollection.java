/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.vote;

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class CompensationRequestVoteItemCollection extends VoteItem implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(CompensationRequestVoteItemCollection.class);

    private List<CompensationRequestVoteItem> compensationRequestVoteItems = new ArrayList<>();

    public List<CompensationRequestVoteItem> getCompensationRequestVoteItems() {
        return compensationRequestVoteItems;
    }

    public List<CompensationRequestVoteItem> getCompensationRequestVoteItemsSortedByTxId() {
        ArrayList<CompensationRequestVoteItem> list = new ArrayList<>(compensationRequestVoteItems);
        list.sort((o1, o2) -> o2.compensationRequest.getCompensationRequestPayload().feeTxId.compareTo(o1.compensationRequest.getCompensationRequestPayload().feeTxId));
        return list;
    }


    public CompensationRequestVoteItemCollection(VotingParameters.Code code, String name) {
        super(code, name);
    }

    public void addCompensationRequestVoteItem(CompensationRequestVoteItem compensationRequestVoteItem) {
        compensationRequestVoteItems.add(compensationRequestVoteItem);
    }

    @Override
    public String toString() {
        return "CompensationRequestVoteItemCollection{" +
                "compensationRequestVoteItems=" + compensationRequestVoteItems +
                '}';
    }

    public boolean hasAnyVoted() {
        return compensationRequestVoteItems.stream().filter(CompensationRequestVoteItem::isHasVoted).findAny().isPresent();
    }
}
