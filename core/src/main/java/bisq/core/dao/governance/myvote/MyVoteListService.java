/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.myvote;

import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;

import bisq.common.app.DevEnv;
import bisq.common.crypto.Encryption;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;

import javax.inject.Inject;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates and stores myVote items. Persist in MyVoteList.
 */
@Slf4j
public class MyVoteListService implements PersistedDataHost {
    private final DaoStateService daoStateService;
    private final Storage<MyVoteList> storage;
    private final MyVoteList myVoteList = new MyVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyVoteListService(DaoStateService daoStateService,
                             Storage<MyVoteList> storage) {
        this.daoStateService = daoStateService;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (DevEnv.isDaoActivated()) {
            MyVoteList persisted = storage.initAndGetPersisted(myVoteList, 100);
            if (persisted != null) {
                this.myVoteList.clear();
                this.myVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void createAndAddMyVote(BallotList sortedBallotListForCycle, SecretKey secretKey, BlindVote blindVote) {
        final byte[] secretKeyBytes = Encryption.getSecretKeyBytes(secretKey);
        MyVote myVote = new MyVote(daoStateService.getChainHeight(), sortedBallotListForCycle, secretKeyBytes, blindVote);
        log.info("Add new MyVote to myVotesList list.\nMyVote=" + myVote);
        myVoteList.add(myVote);
        persist();
    }

    public void applyRevealTxId(MyVote myVote, String voteRevealTxId) {
        myVote.setRevealTxId(voteRevealTxId);
        log.info("Applied revealTxId to myVote.\nmyVote={}\nvoteRevealTxId={}", myVote, voteRevealTxId);
        persist();
    }

    public Tuple2<Long, Long> getMeritAndStakeForProposal(String proposalTxId, MyBlindVoteListService myBlindVoteListService) {
        long merit = 0;
        long stake = 0;
        List<MyVote> list = new ArrayList<>(myVoteList.getList());
        list.sort(Comparator.comparing(MyVote::getDate));
        for (MyVote myVote : list) {
            for (Ballot ballot : myVote.getBallotList()) {
                if (ballot.getTxId().equals(proposalTxId)) {
                    merit = myVote.getMerit(myBlindVoteListService, daoStateService);
                    stake = myVote.getBlindVote().getStake();
                    break;
                }
            }
        }
        return new Tuple2<>(merit, stake);
    }

    public MyVoteList getMyVoteList() {
        return myVoteList;
    }

    public List<MyVote> getMyVoteListForCycle() {
        return myVoteList.getList().stream()
                .filter(e -> daoStateService.getCurrentCycle() != null)
                .filter(e -> daoStateService.getCurrentCycle().isInCycle(e.getHeight()))
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persist() {
        storage.queueUpForSave();
    }
}
