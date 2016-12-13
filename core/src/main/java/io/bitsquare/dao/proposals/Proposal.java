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

package io.bitsquare.dao.proposals;

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Proposal implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(Proposal.class);

    private final ProposalPayload proposalPayload;

    private boolean accepted;
    private Coin fundsReceived;
    private boolean inVotePeriod;
    private boolean inFundingPeriod;
    private boolean closed;
    private boolean waitingForVotingPeriod;

    public Proposal(ProposalPayload proposalPayload) {
        this.proposalPayload = proposalPayload;
    }

    public ProposalPayload getProposalPayload() {
        return proposalPayload;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public Coin getFundsReceived() {
        return fundsReceived;
    }

    public void setFundsReceived(Coin fundsReceived) {
        this.fundsReceived = fundsReceived;
    }

    public boolean isInVotePeriod() {
        return inVotePeriod;
    }

    public void setInVotePeriod(boolean inVotePeriod) {
        this.inVotePeriod = inVotePeriod;
    }

    public boolean isInFundingPeriod() {
        return inFundingPeriod;
    }

    public void setInFundingPeriod(boolean inFundingPeriod) {
        this.inFundingPeriod = inFundingPeriod;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean isWaitingForVotingPeriod() {
        return waitingForVotingPeriod;
    }

    public void setWaitingForVotingPeriod(boolean waitingForVotingPeriod) {
        this.waitingForVotingPeriod = waitingForVotingPeriod;
    }
}
