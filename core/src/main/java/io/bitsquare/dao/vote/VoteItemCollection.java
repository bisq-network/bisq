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

import com.google.inject.Inject;
import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public final class VoteItemCollection implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(VoteItemCollection.class);

    private List<VoteItem> voteItems;

    @Inject
    public VoteItemCollection() {
        voteItems = Arrays.asList(
                new VoteItem(VotingCodes.CREATE_OFFER_FEE, "Create offer fee", 1),
                new VoteItem(VotingCodes.TAKE_OFFER_FEE, "Take offer fee", 1),
                new ProposalVoteItemCollection(VotingCodes.PROPOSAL_MAP, "Proposals")
        );
    }


    public List<VoteItem> getVoteItems() {
        return voteItems;
    }

    @Override
    public String toString() {
        return "VoteItemCollection{" +
                "voteItems=" + voteItems +
                '}';
    }
}
