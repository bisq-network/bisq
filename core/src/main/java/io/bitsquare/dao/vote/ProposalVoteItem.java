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
import io.bitsquare.dao.proposals.Proposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProposalVoteItem implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(ProposalVoteItem.class);

    public final Proposal proposal;
    private boolean declineVote;
    private boolean acceptedVote;

    public ProposalVoteItem(Proposal proposal) {
        this.proposal = proposal;
    }

    public void setDeclineVote(boolean declineVote) {
        this.declineVote = declineVote;
    }

    public boolean isDeclineVote() {
        return declineVote;
    }

    public void setAcceptedVote(boolean acceptedVote) {
        this.acceptedVote = acceptedVote;
    }

    public boolean isAcceptedVote() {
        return acceptedVote;
    }

    @Override
    public String toString() {
        return "ProposalVoteItem{" +
                "proposal=" + proposal +
                ", declineVote=" + declineVote +
                ", acceptedVote=" + acceptedVote +
                '}';
    }
}
