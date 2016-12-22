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

public final class VoteItemCollection extends ArrayList<VoteItem> implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(VoteItemCollection.class);

    private boolean isMyVote;

    public VoteItemCollection() {
        add(new VoteItem(VotingParameters.Code.CREATE_OFFER_FEE_IN_BTC, "Create offer fee", (byte) 0));
        add(new VoteItem(VotingParameters.Code.TAKE_OFFER_FEE_IN_BTC, "Take offer fee", (byte) 0));
        add(new VoteItem(VotingParameters.Code.COMPENSATION_REQUEST_PERIOD_IN_BLOCKS, "Period until next voting", (byte) 0));
        add(new CompensationRequestVoteItemCollection(VotingParameters.Code.COMP_REQUEST_MAPS, "CompensationRequest"));
    }

    public void setIsMyVote(boolean isMyVote) {
        this.isMyVote = isMyVote;
    }

    public boolean isMyVote() {
        return isMyVote;
    }
}
