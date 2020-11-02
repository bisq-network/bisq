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

package bisq.core.dao.governance.proposal;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Publishes proposal tx and proposalPayload to p2p network.
 * Allow removal of proposal if in proposal phase.
 * Maintains myProposalList for own proposals.
 * Triggers republishing of my proposals at startup.
 */
@Slf4j
public class MyProposalListService implements PersistedDataHost, DaoStateListener {
    public interface Listener {
        void onListChanged(List<Proposal> list);
    }

    private final P2PService p2PService;
    private final DaoStateService daoStateService;
    private final PeriodService periodService;
    private final WalletsManager walletsManager;
    private final PersistenceManager<MyProposalList> persistenceManager;
    private final PublicKey signaturePubKey;

    private final MyProposalList myProposalList = new MyProposalList();
    private final ChangeListener<Number> numConnectedPeersListener;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyProposalListService(P2PService p2PService,
                                 DaoStateService daoStateService,
                                 PeriodService periodService,
                                 WalletsManager walletsManager,
                                 PersistenceManager<MyProposalList> persistenceManager,
                                 PubKeyRing pubKeyRing) {
        this.p2PService = p2PService;
        this.daoStateService = daoStateService;
        this.periodService = periodService;
        this.walletsManager = walletsManager;
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(myProposalList, PersistenceManager.Source.PRIVATE);

        signaturePubKey = pubKeyRing.getSignaturePubKey();

        numConnectedPeersListener = (observable, oldValue, newValue) -> rePublishMyProposalsOnceWellConnected();
        daoStateService.addDaoStateListener(this);
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        if (DevEnv.isDaoActivated()) {
            persistenceManager.readPersisted(persisted -> {
                        myProposalList.setAll(persisted.getList());
                        listeners.forEach(l -> l.onListChanged(getList()));
                        completeHandler.run();
                    },
                    completeHandler);
        } else {
            completeHandler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockChainComplete() {
        rePublishMyProposalsOnceWellConnected();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Broadcast tx and publish proposal to P2P network
    public void publishTxAndPayload(Proposal proposal, Transaction transaction, ResultHandler resultHandler,
                                    ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, proposal.getTxType(), new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("Proposal tx has been published. TxId={}", transaction.getTxId().toString());
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });

        // We prefer to not wait for the tx broadcast as if the tx broadcast would fail we still prefer to have our
        // proposal stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
        // in worst case if it does not succeed the proposal will be ignored anyway.
        // Inconsistently propagated payloads in the p2p network could have potentially worse effects.
        addToP2PNetworkAsProtectedData(proposal, errorMessageHandler);

        // Add to list
        if (!getList().contains(proposal)) {
            myProposalList.add(proposal);
            listeners.forEach(l -> l.onListChanged(getList()));
            requestPersistence();
        }
    }

    public boolean remove(Proposal proposal) {
        if (canRemoveProposal(proposal, daoStateService, periodService)) {
            boolean success = p2PService.removeData(new TempProposalPayload(proposal, signaturePubKey));
            if (!success)
                log.warn("Removal of proposal from p2p network failed. proposal={}", proposal);

            if (myProposalList.remove(proposal)) {
                listeners.forEach(l -> l.onListChanged(getList()));
                requestPersistence();
            } else {
                log.warn("We called remove at a proposal which was not in our list");
            }
            return success;
        } else {
            final String msg = "remove called with a proposal which is outside of the proposal phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public boolean isMine(Proposal proposal) {
        return getList().contains(proposal);
    }

    public List<Proposal> getList() {
        return myProposalList.getList();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addToP2PNetworkAsProtectedData(Proposal proposal, ErrorMessageHandler errorMessageHandler) {
        final boolean success = addToP2PNetworkAsProtectedData(proposal);
        if (success) {
            log.info("TempProposalPayload has been added to P2P network. ProposalTxId={}", proposal.getTxId());
        } else {
            final String msg = "Adding of proposal to P2P network failed. proposal=" + proposal;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
    }

    private boolean addToP2PNetworkAsProtectedData(Proposal proposal) {
        return p2PService.addProtectedStorageEntry(new TempProposalPayload(proposal, signaturePubKey));
    }

    private void rePublishMyProposalsOnceWellConnected() {
        // We republish at each startup at any block during the cycle. We filter anyway for valid blind votes
        // of that cycle so it is 1 blind vote getting rebroadcast at each startup to my neighbors.
        int minPeers = Config.baseCurrencyNetwork().isMainnet() ? 4 : 1;
        if ((p2PService.getNumConnectedPeers().get() >= minPeers && p2PService.isBootstrapped()) ||
                Config.baseCurrencyNetwork().isRegtest()) {
            myProposalList.stream()
                    .filter(proposal -> periodService.isTxInPhaseAndCycle(proposal.getTxId(),
                            DaoPhase.Phase.PROPOSAL,
                            periodService.getChainHeight()))
                    .forEach(this::addToP2PNetworkAsProtectedData);

            // We delay removal of listener as we call that inside listener itself.
            UserThread.execute(() -> p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener));
        }
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }

    private boolean canRemoveProposal(Proposal proposal, DaoStateService daoStateService, PeriodService periodService) {
        boolean inProposalPhase = periodService.isInPhase(daoStateService.getChainHeight(), DaoPhase.Phase.PROPOSAL);
        return isMine(proposal) && inProposalPhase;

    }
}
