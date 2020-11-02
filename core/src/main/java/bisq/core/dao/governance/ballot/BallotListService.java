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

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.ProposalValidatorProvider;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Vote;

import bisq.common.app.DevEnv;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import javax.inject.Inject;

import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Takes the proposals from the append only store and makes Ballots out of it (vote is null).
 * Applies voting on individual ballots and persist the list.
 * The BallotList contains all ballots of all cycles.
 */
@Slf4j
public class BallotListService implements PersistedDataHost, DaoSetupService {
    public interface BallotListChangeListener {
        void onListChanged(List<Ballot> list);
    }

    private final ProposalService proposalService;
    private final PeriodService periodService;
    private final ProposalValidatorProvider validatorProvider;
    private final PersistenceManager<BallotList> persistenceManager;

    private final BallotList ballotList = new BallotList();
    private final List<BallotListChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public BallotListService(ProposalService proposalService,
                             PeriodService periodService,
                             ProposalValidatorProvider validatorProvider,
                             PersistenceManager<BallotList> persistenceManager) {
        this.proposalService = proposalService;
        this.periodService = periodService;
        this.validatorProvider = validatorProvider;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(ballotList, PersistenceManager.Source.NETWORK);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public final void addListeners() {
        ObservableList<ProposalPayload> payloads = proposalService.getProposalPayloads();
        payloads.addListener(this::onChanged);
    }

    private void onChanged(Change<? extends ProposalPayload> change) {
        change.next();
        if (change.wasAdded()) {
            List<? extends ProposalPayload> addedPayloads = change.getAddedSubList();
            addedPayloads.stream()
                    .map(ProposalPayload::getProposal)
                    .filter(this::isNewProposal)
                    .forEach(this::registerProposalAsBallot);
            requestPersistence();
        }
    }

    private boolean isNewProposal(Proposal proposal) {
        return ballotList.stream()
                .map(Ballot::getProposal)
                .noneMatch(proposal::equals);
    }

    private void registerProposalAsBallot(Proposal proposal) {
        Ballot ballot = new Ballot(proposal); // vote is null
        if (log.isInfoEnabled()) {
            log.debug("We create a new ballot with a proposal and add it to our list. " +
                    "Vote is null at that moment. proposalTxId={}", proposal.getTxId());
        }
        if (ballotList.contains(ballot)) {
            log.warn("Ballot {} already exists on our ballotList", ballot);
        } else {
            ballotList.add(ballot);
            listeners.forEach(listener -> listener.onListChanged(ballotList.getList()));
        }
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        if (DevEnv.isDaoActivated()) {
            persistenceManager.readPersisted(persisted -> {
                        ballotList.setAll(persisted.getList());
                        listeners.forEach(l -> l.onListChanged(ballotList.getList()));
                        completeHandler.run();
                    },
                    completeHandler);
        } else {
            completeHandler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVote(Ballot ballot, @Nullable Vote vote) {
        ballot.setVote(vote);
        requestPersistence();
    }

    public void addListener(BallotListChangeListener listener) {
        listeners.add(listener);
    }

    public List<Ballot> getValidatedBallotList() {
        return ballotList.stream()
                .filter(ballot -> validatorProvider.getValidator(ballot.getProposal()).isTxTypeValid(ballot.getProposal()))
                .collect(Collectors.toList());
    }

    public List<Ballot> getValidBallotsOfCycle() {
        return ballotList.stream()
                .filter(ballot -> validatorProvider.getValidator(ballot.getProposal()).isTxTypeValid(ballot.getProposal()))
                .filter(ballot -> periodService.isTxInCorrectCycle(ballot.getTxId(), periodService.getChainHeight()))
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
