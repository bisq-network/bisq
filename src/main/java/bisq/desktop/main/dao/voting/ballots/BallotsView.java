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

package bisq.desktop.main.dao.voting.ballots;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.ActiveView;
import bisq.desktop.main.dao.voting.BallotListItem;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.ballot.Ballot;

import javax.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class BallotsView extends ActiveView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BallotsView(DaoFacade daoFacade,
                        BsqWalletService bsqWalletService,
                        BsqFormatter bsqFormatter,
                        BSFormatter btcFormatter) {

        super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void fillListItems() {
        List<Ballot> list = daoFacade.getActiveOrMyUnconfirmedBallots();
        proposalListItems.setAll(list.stream()
                .map(ballot -> new BallotListItem(ballot, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }
}

