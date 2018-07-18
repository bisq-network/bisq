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

package bisq.desktop.main.dao.voting;

import bisq.desktop.main.dao.BaseProposalListItem;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.util.BsqFormatter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class BaseBallotListItem extends BaseProposalListItem {
    @Getter
    private final Ballot ballot;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected BaseBallotListItem(Ballot ballot,
                                 DaoFacade daoFacade,
                                 BsqWalletService bsqWalletService,
                                 BsqFormatter bsqFormatter) {
        super(daoFacade,
                bsqWalletService,
                bsqFormatter);

        this.ballot = ballot;

        init();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void onPhaseChanged(DaoPhase.Phase phase) {
        super.onPhaseChanged(phase);

        final Vote vote = ballot.getVote();
        if (vote != null) {
            imageView.setVisible(true);
            if (vote instanceof BooleanVote) {
                if (((BooleanVote) vote).isAccepted()) {
                    imageView.setId("accepted");
                } else {
                    imageView.setId("rejected");
                }
            }/* else {
                // not impl.
            }*/
        } else {
            imageView.setVisible(false);
        }
    }

    @Override
    public Proposal getProposal() {
        return ballot.getProposal();
    }
}
