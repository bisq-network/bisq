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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Proposal;

import org.bitcoinj.core.TransactionConfidence;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides filtered observableLists of the Proposals from proposalService and myProposalListService.
 * We want to show the own proposals in unconfirmed state (validation of phase and cycle cannot be done but as it is
 * our own proposal that is not critical). Foreign proposals are only shown if confirmed and fully validated.
 */
@Slf4j
public class ProposalListPresentation implements DaoStateListener, MyProposalListService.Listener {
    private final ProposalService proposalService;
    private final DaoStateService daoStateService;
    private final MyProposalListService myProposalListService;
    private final BsqWalletService bsqWalletService;
    private final ProposalValidator proposalValidator;
    private final ObservableList<Proposal> allProposals = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<Proposal> activeOrMyUnconfirmedProposals = new FilteredList<>(allProposals);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalListPresentation(ProposalService proposalService,
                                    DaoStateService daoStateService,
                                    MyProposalListService myProposalListService,
                                    BsqWalletService bsqWalletService,
                                    ProposalValidator proposalValidator) {
        this.proposalService = proposalService;
        this.daoStateService = daoStateService;
        this.myProposalListService = myProposalListService;
        this.bsqWalletService = bsqWalletService;
        this.proposalValidator = proposalValidator;

        daoStateService.addBsqStateListener(this);
        myProposalListService.addListener(this);

        proposalService.getTempProposals().addListener((ListChangeListener<Proposal>) c -> {
            updateLists();
        });
        proposalService.getProposalPayloads().addListener((ListChangeListener<ProposalPayload>) c -> {
            updateLists();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseTxsCompleteAfterBatchProcessing(Block block) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MyProposalListService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onListChanged(List<Proposal> list) {
        updateLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateLists() {
        List<Proposal> tempProposals = proposalService.getTempProposals();
        Set<Proposal> verifiedProposals = proposalService.getProposalPayloads().stream()
                .map(ProposalPayload::getProposal)
                .filter(proposalValidator::isValidAndConfirmed)
                .collect(Collectors.toSet());
        Set<Proposal> set = new HashSet<>(tempProposals);
        set.addAll(verifiedProposals);

        // We want to show our own unconfirmed proposals. Unconfirmed proposals from other users are not included
        // in the list.
        // If a tx is not found in the daoStateService it can be that it is either unconfirmed or invalid.
        // To avoid inclusion of invalid txs we add a check for the confidence type PENDING from the bsqWalletService.
        // So we only add proposals if they are unconfirmed and therefor not yet parsed. Once confirmed they have to be
        // found in the daoStateService.
        List<Proposal> myUnconfirmedProposals = myProposalListService.getList().stream()
                .filter(p -> !daoStateService.getTx(p.getTxId()).isPresent()) // Tx is still not in our bsq blocks
                .filter(p -> {
                    TransactionConfidence confidenceForTxId = bsqWalletService.getConfidenceForTxId(p.getTxId());
                    return confidenceForTxId != null &&
                            confidenceForTxId.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                })
                .collect(Collectors.toList());
        set.addAll(myUnconfirmedProposals);

        allProposals.clear();
        allProposals.addAll(set);

        activeOrMyUnconfirmedProposals.setPredicate(proposal -> proposalValidator.isValidAndConfirmed(proposal) ||
                myUnconfirmedProposals.contains(proposal));
    }
}
