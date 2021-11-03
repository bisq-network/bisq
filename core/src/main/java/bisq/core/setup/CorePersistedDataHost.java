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

package bisq.core.setup;

import bisq.core.btc.model.AddressEntryList;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.bond.reputation.MyReputationListService;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.proofofburn.MyProofOfBurnListService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.support.dispute.arbitration.ArbitrationDisputeListService;
import bisq.core.support.dispute.mediation.MediationDisputeListService;
import bisq.core.support.dispute.refund.RefundDisputeListService;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.mailbox.IgnoredMailboxService;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.RemovedPayloadsService;

import bisq.common.config.Config;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.inject.Injector;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CorePersistedDataHost {

    // All classes which are persisting objects need to be added here
    public static List<PersistedDataHost> getPersistedDataHosts(Injector injector) {
        List<PersistedDataHost> persistedDataHosts = new ArrayList<>();
        persistedDataHosts.add(injector.getInstance(Preferences.class));
        persistedDataHosts.add(injector.getInstance(User.class));
        persistedDataHosts.add(injector.getInstance(AddressEntryList.class));
        persistedDataHosts.add(injector.getInstance(OpenOfferManager.class));
        persistedDataHosts.add(injector.getInstance(TradeManager.class));
        persistedDataHosts.add(injector.getInstance(ClosedTradableManager.class));
        persistedDataHosts.add(injector.getInstance(BsqSwapTradeManager.class));
        persistedDataHosts.add(injector.getInstance(FailedTradesManager.class));
        persistedDataHosts.add(injector.getInstance(ArbitrationDisputeListService.class));
        persistedDataHosts.add(injector.getInstance(MediationDisputeListService.class));
        persistedDataHosts.add(injector.getInstance(RefundDisputeListService.class));
        persistedDataHosts.add(injector.getInstance(P2PDataStorage.class));
        persistedDataHosts.add(injector.getInstance(PeerManager.class));
        persistedDataHosts.add(injector.getInstance(MailboxMessageService.class));
        persistedDataHosts.add(injector.getInstance(IgnoredMailboxService.class));
        persistedDataHosts.add(injector.getInstance(RemovedPayloadsService.class));

        if (injector.getInstance(Config.class).daoActivated) {
            persistedDataHosts.add(injector.getInstance(BallotListService.class));
            persistedDataHosts.add(injector.getInstance(MyBlindVoteListService.class));
            persistedDataHosts.add(injector.getInstance(MyVoteListService.class));
            persistedDataHosts.add(injector.getInstance(MyProposalListService.class));
            persistedDataHosts.add(injector.getInstance(MyReputationListService.class));
            persistedDataHosts.add(injector.getInstance(MyProofOfBurnListService.class));
            persistedDataHosts.add(injector.getInstance(UnconfirmedBsqChangeOutputListService.class));
        }
        return persistedDataHosts;
    }
}
