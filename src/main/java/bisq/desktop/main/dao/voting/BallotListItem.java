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

import bisq.desktop.main.dao.ListItem;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.proposal.Proposal;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class BallotListItem extends ListItem {
    private final Ballot ballot;

    public BallotListItem(Ballot ballot,
                          DaoFacade daoFacade,
                          BsqWalletService bsqWalletService,
                          BsqFormatter bsqFormatter) {
        super(daoFacade,
                bsqWalletService,
                bsqFormatter);
        this.ballot = ballot;
        init();
    }

    @Override
    protected Proposal getProposal() {
        return ballot.getProposal();
    }
}
