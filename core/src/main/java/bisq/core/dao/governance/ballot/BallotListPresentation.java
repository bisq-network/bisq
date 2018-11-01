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

package bisq.core.dao.governance.ballot;

import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Ballot;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides the ballots as observableList for presentation classes.
 */
@Slf4j
public class BallotListPresentation implements BallotListService.BallotListChangeListener, DaoStateListener {
    private final BallotListService ballotListService;
    private final PeriodService periodService;

    @Getter
    private final ObservableList<Ballot> ballots = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<Ballot> ballotsOfCycle = new FilteredList<>(ballots);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BallotListPresentation(BallotListService ballotListService,
                                  PeriodService periodService,
                                  DaoStateService daoStateService,
                                  ProposalValidator proposalValidator) {
        this.ballotListService = ballotListService;
        this.periodService = periodService;

        daoStateService.addBsqStateListener(this);
        ballotListService.addListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        ballotsOfCycle.setPredicate(ballot -> periodService.isTxInCorrectCycle(ballot.getTxId(), blockHeight));
    }

    @Override
    public void onParseTxsComplete(Block block) {
        onListChanged(ballotListService.getValidatedBallotList());
    }

    @Override
    public void onParseBlockChainComplete() {
        // As we update the list in ProposalService.onParseBlockChainComplete we need to update here as well.
        onListChanged(ballotListService.getValidatedBallotList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BallotListService.BallotListChangeListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onListChanged(List<Ballot> list) {
        ballots.clear();
        ballots.addAll(list);
    }
}
