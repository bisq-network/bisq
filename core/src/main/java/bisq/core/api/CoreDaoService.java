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

package bisq.core.api;

import bisq.asset.Asset;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.dao.governance.asset.StatefulAsset;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import bisq.common.handlers.ErrorMessageHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

/**
 * Headless API facade for DAO governance operations. Thin pass-through to
 * {@link DaoFacade} and the supporting period / state services. Adds blocking
 * adapters around the async publish flows so a gRPC call only returns once the
 * underlying tx has been broadcast (or the publish error surfaced).
 */
@Singleton
@Slf4j
public class CoreDaoService {

    private final DaoFacade daoFacade;
    private final PeriodService periodService;
    private final CycleService cycleService;
    private final DaoStateService daoStateService;
    private final MyProposalListService myProposalListService;
    private final AssetService assetService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final bisq.core.dao.governance.blindvote.BlindVoteListService blindVoteListService;

    @Inject
    public CoreDaoService(DaoFacade daoFacade,
                          PeriodService periodService,
                          CycleService cycleService,
                          DaoStateService daoStateService,
                          MyProposalListService myProposalListService,
                          AssetService assetService,
                          BsqWalletService bsqWalletService,
                          BtcWalletService btcWalletService,
                          bisq.core.dao.governance.blindvote.BlindVoteListService blindVoteListService) {
        this.daoFacade = daoFacade;
        this.periodService = periodService;
        this.cycleService = cycleService;
        this.daoStateService = daoStateService;
        this.myProposalListService = myProposalListService;
        this.assetService = assetService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.blindVoteListService = blindVoteListService;
    }

    /**
     * Returns the hex-encoded serialized transaction for a tx known to the local wallets,
     * or empty string if not found. Used by tests to re-broadcast via bitcoind's
     * sendrawtransaction RPC for deterministic confirmation timing (bitcoinj P2P relay
     * via INV/getdata can stall for minutes against bitcoind v29).
     */
    public String getRawTransactionHex(String txId) {
        Sha256Hash hash = Sha256Hash.wrap(txId);
        Transaction tx = bsqWalletService.getTransaction(hash);
        if (tx == null) {
            tx = btcWalletService.getTransaction(hash);
        }
        if (tx == null) {
            return "";
        }
        return Utils.HEX.encode(tx.bitcoinSerialize());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cycle / phase reads
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CycleSnapshot getCycleSnapshot() {
        Cycle current = periodService.getCurrentCycle();
        int chainHeight = daoStateService.getChainHeight();
        if (current == null) {
            return new CycleSnapshot(-1, chainHeight, DaoPhase.Phase.UNDEFINED, 0, 0, 0, 0, 0, 0,
                    daoFacade.isDaoStateReadyAndInSync(), 0, 0);
        }
        DaoPhase.Phase phase = current.getPhaseForHeight(chainHeight).orElse(DaoPhase.Phase.UNDEFINED);
        int firstBlockOfPhase = phase == DaoPhase.Phase.UNDEFINED ? 0 : current.getFirstBlockOfPhase(phase);
        int lastBlockOfPhase = phase == DaoPhase.Phase.UNDEFINED ? 0 : current.getLastBlockOfPhase(phase);
        int durationOfPhase = phase == DaoPhase.Phase.UNDEFINED ? 0 : current.getDurationOfPhase(phase);
        int blocksRemaining = phase == DaoPhase.Phase.UNDEFINED ? 0 : Math.max(0, lastBlockOfPhase - chainHeight);
        return new CycleSnapshot(
                cycleService.getCycleIndex(current),
                chainHeight,
                phase,
                current.getHeightOfFirstBlock(),
                current.getHeightOfLastBlock(),
                firstBlockOfPhase,
                lastBlockOfPhase,
                durationOfPhase,
                blocksRemaining,
                daoFacade.isDaoStateReadyAndInSync(),
                blindVoteListService.getBlindVotesInPhaseAndCycle().size(),
                daoFacade.getActiveOrMyUnconfirmedProposals().size());
    }

    public List<Cycle> getCycles() {
        return new ArrayList<>(daoStateService.getCycles());
    }

    public int getCycleIndex(Cycle cycle) {
        return cycleService.getCycleIndex(cycle);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Proposal reads
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Proposal> getActiveOrMyUnconfirmedProposals() {
        return new ArrayList<>(daoFacade.getActiveOrMyUnconfirmedProposals());
    }

    public List<Proposal> getMyProposals() {
        return new ArrayList<>(myProposalListService.getList());
    }

    /**
     * Returns all <strong>evaluated</strong> proposals across past cycles. An
     * {@link EvaluatedProposal} only materializes after its cycle reaches the RESULT
     * phase; proposals in cycles still in PROPOSAL/BLIND_VOTE/VOTE_REVEAL are not
     * included here — use {@link #getActiveOrMyUnconfirmedProposals()} for those.
     */
    public List<Proposal> getAllValidatedProposals() {
        return new ArrayList<>(daoStateService.getEvaluatedProposalList()).stream()
                .map(EvaluatedProposal::getProposal)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns evaluated proposals in the cycle at {@code cycleIndex}. Returns an
     * empty list for the current cycle until it reaches the RESULT phase. See
     * {@link #getAllValidatedProposals()} for the evaluation lifecycle.
     */
    public List<Proposal> getProposalsForCycle(int cycleIndex) {
        List<Cycle> cycles = daoStateService.getCycles();
        if (cycleIndex < 0 || cycleIndex >= cycles.size()) {
            throw new IllegalArgumentException(format("invalid cycle index %d (have %d cycles)",
                    cycleIndex, cycles.size()));
        }
        Cycle cycle = cycles.get(cycleIndex);
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(ep -> cycleService.isTxInCycle(cycle, ep.getProposal().getTxId()))
                .map(EvaluatedProposal::getProposal)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Ballot> getBallots() {
        return new ArrayList<>(daoFacade.getBallotsOfCycle());
    }

    public List<MyVote> getMyVotes() {
        return daoFacade.getMyVoteListForCycle();
    }

    public List<EvaluatedProposal> getVoteResults(int cycleIndex) {
        List<EvaluatedProposal> all = daoStateService.getEvaluatedProposalList();
        List<Cycle> cycles = daoStateService.getCycles();
        // Negative cycleIndex → return all evaluated proposals across all cycles. Callers
        // filter by tx id; this avoids guessing which cycle is "the relevant one" in a
        // test that has already advanced past the proposal's RESULT phase into the next.
        if (cycleIndex < 0) {
            return new ArrayList<>(all);
        }
        if (cycleIndex >= cycles.size()) {
            throw new IllegalArgumentException(format("invalid cycle index %d (have %d cycles)",
                    cycleIndex, cycles.size()));
        }
        Cycle target = cycles.get(cycleIndex);
        return all.stream()
                .filter(ep -> cycleService.isTxInCycle(target, ep.getProposal().getTxId()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<BondedRole> getBondedRoles() {
        return new ArrayList<>(daoFacade.getBondedRoles());
    }

    public long getRequiredBond(BondedRoleType bondedRoleType) {
        return daoFacade.getRequiredBond(bondedRoleType);
    }

    public String getParamValue(String paramName) {
        Param param = parseParam(paramName);
        return daoFacade.getParamValue(param);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Proposal create + publish (async — fires result/error handlers when broadcast
    // completes). We MUST NOT block here: gRPC runs on Bisq's UserThread executor and
    // TxBroadcaster's onSuccess/onTimeout also dispatch via UserThread — a blocking
    // wait on this thread would deadlock (the broadcast callback never runs).
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void createCompensationProposal(String name, String link, long requestedBsqSats,
                                           String burningManReceiverAddress,
                                           Consumer<Proposal> resultHandler,
                                           ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            pwt = daoFacade.getCompensationProposalWithTransaction(
                    name, link, Coin.valueOf(requestedBsqSats),
                    burningManReceiverAddress == null || burningManReceiverAddress.isEmpty()
                            ? Optional.empty() : Optional.of(burningManReceiverAddress));
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage(e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    public void createReimbursementProposal(String name, String link, long requestedBsqSats,
                                            Consumer<Proposal> resultHandler,
                                            ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            pwt = daoFacade.getReimbursementProposalWithTransaction(
                    name, link, Coin.valueOf(requestedBsqSats));
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage(e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    public void createChangeParamProposal(String name, String link, String paramName, String paramValue,
                                          Consumer<Proposal> resultHandler,
                                          ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            Param param = parseParam(paramName);
            pwt = daoFacade.getParamProposalWithTransaction(name, link, param, paramValue);
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage(e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    public void createBondedRoleProposal(String bondedRoleTypeName, String name, String link,
                                         Consumer<Proposal> resultHandler,
                                         ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            BondedRoleType type = BondedRoleType.valueOf(bondedRoleTypeName);
            Role role = new Role(name, link, type);
            pwt = daoFacade.getBondedRoleProposalWithTransaction(role);
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage("unknown bondedRoleType or factory error: " + e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    public void createConfiscateBondProposal(String name, String link, String lockupTxId,
                                             Consumer<Proposal> resultHandler,
                                             ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            pwt = daoFacade.getConfiscateBondProposalWithTransaction(name, link, lockupTxId);
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage(e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    public void createGenericProposal(String name, String link,
                                      Consumer<Proposal> resultHandler,
                                      ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            pwt = daoFacade.getGenericProposalWithTransaction(name, link);
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage(e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    public void createRemoveAssetProposal(String name, String link, String assetCode,
                                          Consumer<Proposal> resultHandler,
                                          ErrorMessageHandler errorMessageHandler) {
        ProposalWithTransaction pwt;
        try {
            StatefulAsset stateful = assetService.getStatefulAssets().stream()
                    .filter(a -> a.getTickerSymbol().equalsIgnoreCase(assetCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown asset code: " + assetCode));
            pwt = daoFacade.getRemoveAssetProposalWithTransaction(name, link, stateful.getAsset());
        } catch (Exception e) {
            errorMessageHandler.handleErrorMessage(e.getMessage());
            return;
        }
        publishAsync(pwt, resultHandler, errorMessageHandler);
    }

    private void publishAsync(ProposalWithTransaction pwt,
                              Consumer<Proposal> resultHandler,
                              ErrorMessageHandler errorMessageHandler) {
        String txId = pwt.getTransaction().getTxId().toString();
        daoFacade.publishMyProposal(pwt.getProposal(), pwt.getTransaction(),
                () -> {
                    Proposal published = ((List<Proposal>) myProposalListService.getList()).stream()
                            .filter(p -> txId.equals(p.getTxId()))
                            .findFirst()
                            .orElseGet(() -> pwt.getProposal().cloneProposalAndAddTxId(txId));
                    resultHandler.accept(published);
                },
                errorMessageHandler);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Voting / blind vote
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setVote(String proposalTxId, String voteOpt) {
        Ballot target = daoFacade.getBallotsOfCycle().stream()
                .filter(b -> b.getTxId().equals(proposalTxId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no ballot in current cycle for proposal tx id: " + proposalTxId));
        Vote vote;
        switch (voteOpt.toLowerCase()) {
            case "accept":
                vote = new Vote(true);
                break;
            case "reject":
                vote = new Vote(false);
                break;
            case "ignore":
            case "abstain":
            case "":
            case "null":
                vote = null;
                break;
            default:
                throw new IllegalArgumentException("vote must be one of accept|reject|ignore: " + voteOpt);
        }
        daoFacade.setVote(target, vote);
    }

    public void publishBlindVote(long stakeSats,
                                 Consumer<String> resultHandler,
                                 ErrorMessageHandler errorMessageHandler) {
        if (stakeSats <= 0) {
            errorMessageHandler.handleErrorMessage("blind vote stake must be > 0 sats");
            return;
        }
        daoFacade.publishBlindVote(Coin.valueOf(stakeSats),
                () -> {
                    List<MyVote> myVotes = daoFacade.getMyVoteListForCycle();
                    String txId = myVotes.isEmpty() ? "" : myVotes.get(myVotes.size() - 1).getBlindVoteTxId();
                    resultHandler.accept(txId);
                },
                ex -> errorMessageHandler.handleErrorMessage(ex.getMessage()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static Param parseParam(String paramName) {
        try {
            return Param.valueOf(paramName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown DAO param: " + paramName, ex);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DTO record types (kept package-private, mapped by gRPC layer)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final class CycleSnapshot {
        public final int cycleIndex;
        public final int chainHeight;
        public final DaoPhase.Phase phase;
        public final int firstBlockOfCycle;
        public final int lastBlockOfCycle;
        public final int firstBlockOfPhase;
        public final int lastBlockOfPhase;
        public final int durationOfPhase;
        public final int blocksRemainingInPhase;
        public final boolean isDaoStateInSync;
        public final int numBlindVotesInCurrentCycle;
        public final int numProposalsInCurrentCycle;

        public CycleSnapshot(int cycleIndex,
                             int chainHeight,
                             DaoPhase.Phase phase,
                             int firstBlockOfCycle,
                             int lastBlockOfCycle,
                             int firstBlockOfPhase,
                             int lastBlockOfPhase,
                             int durationOfPhase,
                             int blocksRemainingInPhase,
                             boolean isDaoStateInSync,
                             int numBlindVotesInCurrentCycle,
                             int numProposalsInCurrentCycle) {
            this.cycleIndex = cycleIndex;
            this.chainHeight = chainHeight;
            this.phase = phase;
            this.firstBlockOfCycle = firstBlockOfCycle;
            this.lastBlockOfCycle = lastBlockOfCycle;
            this.firstBlockOfPhase = firstBlockOfPhase;
            this.lastBlockOfPhase = lastBlockOfPhase;
            this.durationOfPhase = durationOfPhase;
            this.blocksRemainingInPhase = blocksRemainingInPhase;
            this.isDaoStateInSync = isDaoStateInSync;
            this.numBlindVotesInCurrentCycle = numBlindVotesInCurrentCycle;
            this.numProposalsInCurrentCycle = numProposalsInCurrentCycle;
        }
    }

    @SuppressWarnings("unused")
    static String proposalKindOf(Proposal proposal) {
        if (proposal instanceof CompensationProposal) return "COMPENSATION_REQUEST";
        if (proposal instanceof ReimbursementProposal) return "REIMBURSEMENT_REQUEST";
        if (proposal instanceof ChangeParamProposal) return "CHANGE_PARAM";
        if (proposal instanceof RoleProposal) return "BONDED_ROLE";
        if (proposal instanceof ConfiscateBondProposal) return "CONFISCATE_BOND";
        if (proposal instanceof RemoveAssetProposal) return "REMOVE_ASSET";
        return "GENERIC";
    }
}
