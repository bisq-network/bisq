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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.network.RepublishGovernanceDataHandler;
import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;

import javax.inject.Inject;

import javafx.collections.ObservableList;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MissingDataRequestService implements DaoSetupService {
    private final RepublishGovernanceDataHandler republishGovernanceDataHandler;
    private final BlindVoteListService blindVoteListService;
    private final ProposalService proposalService;
    private final P2PService p2PService;
    private boolean reRepublishAllGovernanceDataDone;

    @Inject
    public MissingDataRequestService(RepublishGovernanceDataHandler republishGovernanceDataHandler,
                                     BlindVoteListService blindVoteListService,
                                     ProposalService proposalService,
                                     P2PService p2PService) {
        this.republishGovernanceDataHandler = republishGovernanceDataHandler;
        this.blindVoteListService = blindVoteListService;
        this.proposalService = proposalService;
        this.p2PService = p2PService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendRepublishRequest() {
        republishGovernanceDataHandler.sendRepublishRequest();
    }

    // Can be triggered with shortcut ctrl+h, cmd+h or alt+h
    public void reRepublishAllGovernanceData() {
        // We only want to do it once in case we would get flooded with requests.
        if (!reRepublishAllGovernanceDataDone) {
            reRepublishAllGovernanceDataDone = true;
            ObservableList<ProposalPayload> proposalPayloads = proposalService.getProposalPayloads();
            proposalPayloads.forEach(proposalPayload -> {
                // We want a random delay between 0.1 and 300 sec. depending on the number of items.
                // We send all proposals including those from old cycles.
                int delay = Math.max(100, Math.min(300_000, new Random().nextInt(proposalPayloads.size() * 1000)));
                UserThread.runAfter(() -> {
                    boolean success = p2PService.addPersistableNetworkPayload(proposalPayload, true);
                    String txId = proposalPayload.getProposal().getTxId();
                    if (success) {
                        log.debug("We received a RepublishGovernanceDataRequest and re-published a proposalPayload to " +
                                "the P2P network as append only data. proposalTxId={}", txId);
                    } else {
                        log.error("Adding of proposalPayload to P2P network failed. proposalTxId={}", txId);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            });

            ObservableList<BlindVotePayload> blindVotePayloads = blindVoteListService.getBlindVotePayloads();
            blindVotePayloads.forEach(blindVotePayload -> {
                // We want a random delay between 0.1 and 300 sec. depending on the number of items.
                // We send all blindVotes including those from old cycles.
                int delay = Math.max(100, Math.min(300_000, new Random().nextInt(blindVotePayloads.size() * 1000)));
                UserThread.runAfter(() -> {
                    boolean success = p2PService.addPersistableNetworkPayload(blindVotePayload, true);
                    String txId = blindVotePayload.getBlindVote().getTxId();
                    if (success) {
                        log.debug("We received a RepublishGovernanceDataRequest and re-published a blindVotePayload to " +
                                "the P2P network as append only data. blindVoteTxId={}", txId);
                    } else {
                        log.error("Adding of blindVotePayload to P2P network failed. blindVoteTxId={}", txId);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            });
        }
    }
}
