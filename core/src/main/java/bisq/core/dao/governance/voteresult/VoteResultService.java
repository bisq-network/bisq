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
import bisq.core.dao.governance.ballot.Ballot;
import bisq.core.dao.governance.ballot.BallotList;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.VoteWithProposalTxId;
import bisq.core.dao.governance.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.governance.merit.MeritConsensus;
import bisq.core.dao.governance.merit.MeritList;
import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.proposal.compensation.CompensationProposal;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposal;
import bisq.core.dao.governance.proposal.param.ChangeParamProposal;
import bisq.core.dao.governance.proposal.role.BondedRoleProposal;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.governance.role.BondedRolesService;
import bisq.core.dao.governance.voteresult.issuance.IssuanceService;
import bisq.core.dao.governance.votereveal.VoteRevealConsensus;
import bisq.core.dao.governance.votereveal.VoteRevealService;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.governance.ConfiscateBond;
import bisq.core.dao.state.governance.ParamChange;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Calculates the result of the voting at the VoteResult period.
 * We  take all data from the bitcoin domain and additionally the blindVote list which we received from the p2p network.
 * Due to eventual consistency we use the hash of the data view of the voters (majority by merit+stake). If our local
 * blindVote list contains the blindVotes used by the voters we can calculate the result, otherwise we need to request
 * the missing blindVotes from the network.
 */
@Slf4j
public class VoteResultService implements BsqStateListener, DaoSetupService {
    private final VoteRevealService voteRevealService;
    private final ProposalListPresentation proposalListPresentation;
    private final BsqStateService bsqStateService;
    private final PeriodService periodService;
    private final BallotListService ballotListService;
    private final BlindVoteListService blindVoteListService;
    private final BondedRolesService bondedRolesService;
    private final IssuanceService issuanceService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();
    // Use a list to have order by cycle

    // TODO persist, PB
    @Getter
    private final Set<EvaluatedProposal> allEvaluatedProposals = new HashSet<>();
    @Getter
    private final Set<DecryptedBallotsWithMerits> allDecryptedBallotsWithMerits = new HashSet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(VoteRevealService voteRevealService,
                             ProposalListPresentation proposalListPresentation,
                             BsqStateService bsqStateService,
                             PeriodService periodService,
                             BallotListService ballotListService,
                             BlindVoteListService blindVoteListService,
                             BondedRolesService bondedRolesService,
                             IssuanceService issuanceService) {
        this.voteRevealService = voteRevealService;
        this.proposalListPresentation = proposalListPresentation;
        this.bsqStateService = bsqStateService;
        this.periodService = periodService;
        this.ballotListService = ballotListService;
        this.blindVoteListService = blindVoteListService;
        this.bondedRolesService = bondedRolesService;
        this.issuanceService = issuanceService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        bsqStateService.addBsqStateListener(this);
    }

    @Override
    public void start() {
        maybeCalculateVoteResult(bsqStateService.getChainHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<EvaluatedProposal> getAllAcceptedEvaluatedProposals() {
        return getAcceptedEvaluatedProposals(allEvaluatedProposals);
    }

    public Set<EvaluatedProposal> getAllRejectedEvaluatedProposals() {
        return getRejectedEvaluatedProposals(allEvaluatedProposals);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        // TODO check if we should use onParseTxsComplete for calling maybeCalculateVoteResult
        maybeCalculateVoteResult(blockHeight);
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeCalculateVoteResult(int chainHeight) {
        if (isInVoteResultPhase(chainHeight)) {
            Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet = getDecryptedBallotsWithMeritsSet(chainHeight);
            allDecryptedBallotsWithMerits.addAll(decryptedBallotsWithMeritsSet);

            if (!decryptedBallotsWithMeritsSet.isEmpty()) {
                // From the decryptedBallotsWithMerits we create a map with the hash of the blind vote list as key and the
                // aggregated stake+merit as value. That map is used for calculating the majority of the blind vote lists.
                // There might be conflicting versions due the eventually consistency of the P2P network (if some blind
                // votes do not arrive at all voters) which would lead to consensus failure in the result calculation.
                // To solve that problem we will only consider the majority data view as valid.
                // If multiple data views would have the same stake we sort additionally by the hex value of the
                // blind vote hash and use the first one in the sorted list as winner.
                // A node which has a local blindVote list which does not match the winner data view need to recover it's
                // local blindVote list by requesting the correct list from other peers.
                Map<P2PDataStorage.ByteArray, Long> stakeByHashOfBlindVoteListMap = getStakeByHashOfBlindVoteListMap(decryptedBallotsWithMeritsSet);

                try {
                    // Get majority hash
                    byte[] majorityBlindVoteListHash = getMajorityBlindVoteListHash(stakeByHashOfBlindVoteListMap);

                    // Is our local list matching the majority data view?
                    if (isBlindVoteListMatchingMajority(majorityBlindVoteListHash)) {
                        //TODO should we write the decryptedBallotsWithMerits here into the state?

                        //TODO we get duplicated items in evaluatedProposals with diff. merit values
                        Set<EvaluatedProposal> evaluatedProposals = getEvaluatedProposals(decryptedBallotsWithMeritsSet, chainHeight);

                        Set<EvaluatedProposal> acceptedEvaluatedProposals = getAcceptedEvaluatedProposals(evaluatedProposals);
                        applyAcceptedProposals(acceptedEvaluatedProposals, chainHeight);

                        allEvaluatedProposals.addAll(evaluatedProposals);
                        log.info("processAllVoteResults completed");
                    } else {
                        log.warn("Our list of received blind votes do not match the list from the majority of voters.");
                        // TODO request missing blind votes
                    }

                } catch (VoteResultException e) {
                    log.error(e.toString());
                    e.printStackTrace();

                    //TODO notify application of that case (e.g. add error handler)
                    // The vote cycle is invalid as conflicting data views of the blind vote data exist and the winner
                    // did not reach super majority of 80%.
                }
            } else {
                log.info("There have not been any votes in that cycle. chainHeight={}", chainHeight);
            }

            // Those which did not get accepted will be added to the nonBsq map
            bsqStateService.getIssuanceCandidateTxOutputs().stream()
                    .filter(txOutput -> !bsqStateService.isIssuanceTx(txOutput.getTxId()))
                    .forEach(bsqStateService::addNonBsqTxOutput);
        }
    }

    private Set<DecryptedBallotsWithMerits> getDecryptedBallotsWithMeritsSet(int chainHeight) {
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        return bsqStateService.getVoteRevealOpReturnTxOutputs().stream()
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .map(txOutput -> {
                    // TODO make method
                    byte[] opReturnData = txOutput.getOpReturnData();
                    String voteRevealTxId = txOutput.getTxId();
                    Optional<Tx> optionalVoteRevealTx = bsqStateService.getTx(voteRevealTxId);
                    if (!optionalVoteRevealTx.isPresent()) {
                        log.error("optionalVoteRevealTx is not present. voteRevealTxId={}", voteRevealTxId);
                        //TODO throw exception
                        return null;
                    }

                    Tx voteRevealTx = optionalVoteRevealTx.get();
                    try {
                        // TODO maybe verify version in opReturn
                        byte[] hashOfBlindVoteList = VoteResultConsensus.getHashOfBlindVoteList(opReturnData);
                        SecretKey secretKey = VoteResultConsensus.getSecretKey(opReturnData);
                        TxOutput blindVoteStakeOutput = VoteResultConsensus.getConnectedBlindVoteStakeOutput(voteRevealTx, bsqStateService);
                        long blindVoteStake = blindVoteStakeOutput.getValue();
                        Tx blindVoteTx = VoteResultConsensus.getBlindVoteTx(blindVoteStakeOutput, bsqStateService, periodService, chainHeight);
                        String blindVoteTxId = blindVoteTx.getId();

                        // Here we deal with eventual consistency of the p2p network data!
                        // TODO make more clear we are in p2p domain now
                        List<BlindVote> blindVoteList = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
                        Optional<BlindVote> optionalBlindVote = blindVoteList.stream()
                                .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                                .findAny();
                        if (optionalBlindVote.isPresent()) {
                            BlindVote blindVote = optionalBlindVote.get();
                            VoteWithProposalTxIdList voteWithProposalTxIdList = VoteResultConsensus.decryptVotes(blindVote.getEncryptedVotes(), secretKey);
                            MeritList meritList = MeritConsensus.decryptMeritList(blindVote.getEncryptedMeritList(), secretKey);

                            // We lookup for the proposals we have in our local list which match the txId from the
                            // voteWithProposalTxIdList and create a ballot list with the proposal and the vote from
                            // the voteWithProposalTxIdList
                            BallotList ballotList = createBallotList(voteWithProposalTxIdList);
                            return new DecryptedBallotsWithMerits(hashOfBlindVoteList, voteRevealTxId, blindVoteTxId, blindVoteStake, ballotList, meritList);
                        } else {
                            //TODO handle recovering
                            log.warn("We have a blindVoteTx but we do not have the corresponding blindVote in our local list.\n" +
                                    "That can happen if the blindVote item was not properly broadcast. We will go on " +
                                    "and see if that blindVote was part of the majority data view. If so we should " +
                                    "recover the missing blind vote by a request to our peers. blindVoteTxId={}", blindVoteTxId);
                            return null;
                        }
                    } catch (MissingBallotException e) {
                        //TODO handle case that we are missing proposals
                        log.error("We are missing proposals to create the vote result: " + e.toString());
                        return null;
                    } catch (Throwable e) {
                        log.error("Could not create DecryptedBallotsWithMerits: " + e.toString());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private BallotList createBallotList(VoteWithProposalTxIdList voteWithProposalTxIdList) throws MissingBallotException {
        // We convert the list to a map with proposalTxId as key and the vote as value
        Map<String, Vote> voteByTxIdMap = voteWithProposalTxIdList.stream()
                .filter(voteWithProposalTxId -> voteWithProposalTxId.getVote() != null)
                .collect(Collectors.toMap(VoteWithProposalTxId::getProposalTxId, VoteWithProposalTxId::getVote));

        // We make a map with proposalTxId as key and the ballot as value out of our stored ballot list
        Map<String, Ballot> ballotByTxIdMap = ballotListService.getBallotList().stream()
                .collect(Collectors.toMap(Ballot::getTxId, ballot -> ballot));

        List<String> missingBallots = new ArrayList<>();
        List<Ballot> ballots = voteByTxIdMap.entrySet().stream()
                .map(entry -> {
                    String txId = entry.getKey();
                    if (ballotByTxIdMap.containsKey(txId)) {
                        // why not use proposalList?
                        Ballot ballot = ballotByTxIdMap.get(txId);
                        // We create a new Ballot with the proposal from the ballot list and the vote from our decrypted votes
                        Vote vote = entry.getValue();
                        // We clone the ballot instead applying the vote to the existing ballot from ballotListService
                        // The items from ballotListService.getBallotList() contains my votes.
                        // Maybe we should cross verify if the vote we had in our local list matches my own vote we
                        // received from the network?
                        return new Ballot(ballot.getProposal(), vote);
                    } else {
                        // We got a vote but we don't have the ballot (which includes the proposal)
                        // We add it to the missing list to handle it as exception later. We want all missing data so we
                        // do not throw here.
                        missingBallots.add(txId);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!missingBallots.isEmpty())
            throw new MissingBallotException(ballots, missingBallots);

        // Let's keep the data more deterministic by sorting it by txId. Though we are not using the sorting.
        ballots.sort(Comparator.comparing(Ballot::getTxId));
        return new BallotList(ballots);
    }

    private Map<P2PDataStorage.ByteArray, Long> getStakeByHashOfBlindVoteListMap(Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet) {
        // Don't use byte[] as key as byte[] uses object identity for equals and hashCode
        Map<P2PDataStorage.ByteArray, Long> map = new HashMap<>();
        decryptedBallotsWithMeritsSet.forEach(decryptedBallotsWithMerits -> {
            P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(decryptedBallotsWithMerits.getHashOfBlindVoteList());
            map.putIfAbsent(hash, 0L);
            long aggregatedStake = map.get(hash);
            //TODO move to consensus class
            long merit = decryptedBallotsWithMerits.getMerit(bsqStateService);
            long stake = decryptedBallotsWithMerits.getStake();
            long combinedStake = stake + merit;
            aggregatedStake += combinedStake;
            map.put(hash, aggregatedStake);

            log.debug("blindVoteTxId={}, meritStake={}, stake={}, combinedStake={}",
                    decryptedBallotsWithMerits.getBlindVoteTxId(), merit, stake, combinedStake);
        });
        return map;
    }

    private byte[] getMajorityBlindVoteListHash(Map<P2PDataStorage.ByteArray, Long> map) throws VoteResultException {
        List<HashWithStake> list = map.entrySet().stream()
                .map(entry -> new HashWithStake(entry.getKey().bytes, entry.getValue()))
                .collect(Collectors.toList());
        return VoteResultConsensus.getMajorityHash(list);
    }

    // Deal with eventually consistency of P2P network
    private boolean isBlindVoteListMatchingMajority(byte[] majorityVoteListHash) {
        // We reuse the method at voteReveal domain used when creating the hash
        byte[] myBlindVoteListHash = voteRevealService.getHashOfBlindVoteList();
        log.info("majorityVoteListHash " + Utilities.bytesAsHexString(majorityVoteListHash));
        log.info("myBlindVoteListHash " + Utilities.bytesAsHexString(myBlindVoteListHash));
        boolean matches = Arrays.equals(majorityVoteListHash, myBlindVoteListHash);
        // refactor to method
        if (!matches) {
            log.warn("myBlindVoteListHash does not match with majorityVoteListHash. We try permuting our list to " +
                    "find a matching variant");
            // Each voter has re-published his blind vote list when broadcasting the reveal tx so it should have a very
            // high change that we have received all blind votes which have been used by the majority of the
            // voters (e.g. its stake not nr. of voters).
            // It still could be that we have additional blind votes so our hash does not match. We can try to permute
            // our list with excluding items to see if we get a matching list. If not last resort is to request the
            // missing items from the network.
            List<BlindVote> permutatedListMatchingMajority = findPermutatedListMatchingMajority(majorityVoteListHash);
            if (!permutatedListMatchingMajority.isEmpty()) {
                log.info("We found a permutation of our blindVote list which matches the majority view. " +
                        "permutatedListMatchingMajority={}", permutatedListMatchingMajority);
                //TODO do we need to apply/store it for later use?
            } else {
                log.info("We did not find a permutation of our blindVote list which matches the majority view. " +
                        "We will request the blindVote data from the peers.");
                // This is async operation. We will restart the whole verification process once we received the data.
                requestBlindVoteListFromNetwork(majorityVoteListHash);
            }
        }
        return matches;
    }

    private List<BlindVote> findPermutatedListMatchingMajority(byte[] majorityVoteListHash) {
        List<BlindVote> list = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
        while (!list.isEmpty() && !isListMatchingMajority(majorityVoteListHash, list)) {
            // We remove first item as it will be sorted anyway...
            list.remove(0);
        }
        return list;
    }

    private boolean isListMatchingMajority(byte[] majorityVoteListHash, List<BlindVote> list) {
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(list);
        return Arrays.equals(majorityVoteListHash, hashOfBlindVoteList);
    }

    private void requestBlindVoteListFromNetwork(byte[] majorityVoteListHash) {
        //TODO impl
    }

    private Set<EvaluatedProposal> getEvaluatedProposals(Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet, int chainHeight) {
        // We reorganize the data structure to have a map of proposals with a list of VoteWithStake objects
        Map<Proposal, List<VoteWithStake>> resultListByProposalMap = getVoteWithStakeListByProposalMap(decryptedBallotsWithMeritsSet);

        // TODO breakup
        Set<EvaluatedProposal> evaluatedProposals = new HashSet<>();
        resultListByProposalMap.forEach((proposal, voteWithStakeList) -> {
            long requiredQuorum = bsqStateService.getParamValue(proposal.getQuorumParam(), chainHeight);
            long requiredVoteThreshold = bsqStateService.getParamValue(proposal.getThresholdParam(), chainHeight);

            //TODO add checks for param change that input for quorum param of <5000 is not allowed
            checkArgument(requiredVoteThreshold >= 5000,
                    "requiredVoteThreshold must be not be less then 50% otherwise we could have conflicting results.");

            // move to consensus class
            ProposalVoteResult proposalVoteResult = getResultPerProposal(voteWithStakeList, proposal);
            // Quorum is min. required BSQ stake to be considered valid
            long reachedQuorum = proposalVoteResult.getQuorum();
            log.info("proposalTxId: {}, required requiredQuorum: {}, requiredVoteThreshold: {}",
                    proposal.getTxId(), requiredVoteThreshold, requiredQuorum);
            if (reachedQuorum >= requiredQuorum) {
                // We multiply by 10000 as we use a long for reachedThreshold and we want precision of 2 with
                // a % value. E.g. 50% is 5000.
                // Threshold is percentage of accepted to total stake
                long reachedThreshold = proposalVoteResult.getThreshold();

                log.info("reached threshold: {} %, required threshold: {} %",
                        reachedThreshold / 100D,
                        requiredVoteThreshold / 100D);
                // We need to exceed requiredVoteThreshold e.g. 50% is not enough but 50.01%.
                // Otherwise we could have 50% vs 50%
                if (reachedThreshold > requiredVoteThreshold) {
                    evaluatedProposals.add(new EvaluatedProposal(true, proposalVoteResult,
                            requiredQuorum, requiredVoteThreshold));
                } else {
                    evaluatedProposals.add(new EvaluatedProposal(false, proposalVoteResult,
                            requiredQuorum, requiredVoteThreshold));
                    log.warn("Proposal did not reach the requiredVoteThreshold. reachedThreshold={} %, " +
                            "requiredVoteThreshold={} %", reachedThreshold / 100D, requiredVoteThreshold / 100D);
                }
            } else {
                evaluatedProposals.add(new EvaluatedProposal(false, proposalVoteResult,
                        requiredQuorum, requiredVoteThreshold));
                log.warn("Proposal did not reach the requiredQuorum. reachedQuorum={}, requiredQuorum={}",
                        reachedQuorum, requiredQuorum);
            }
        });

        Map<String, EvaluatedProposal> evaluatedProposalsByTxIdMap = new HashMap<>();
        evaluatedProposals.forEach(evaluatedProposal -> evaluatedProposalsByTxIdMap.put(evaluatedProposal.getProposalTxId(), evaluatedProposal));

        // Proposals which did not get any vote need to be set as failed.
        proposalListPresentation.getActiveOrMyUnconfirmedProposals().stream()
                .filter(proposal -> !evaluatedProposalsByTxIdMap.containsKey(proposal.getTxId()))
                .forEach(proposal -> {
                    long requiredQuorum = bsqStateService.getParamValue(proposal.getQuorumParam(), chainHeight);
                    long requiredVoteThreshold = bsqStateService.getParamValue(proposal.getThresholdParam(), chainHeight);
                    ProposalVoteResult proposalVoteResult = new ProposalVoteResult(proposal, 0,
                            0, 0, 0, decryptedBallotsWithMeritsSet.size());
                    EvaluatedProposal evaluatedProposal = new EvaluatedProposal(false,
                            proposalVoteResult,
                            requiredQuorum,
                            requiredVoteThreshold);
                    evaluatedProposals.add(evaluatedProposal);
                    log.info("Proposal ignored by all voters: " + evaluatedProposal);
                });
        return evaluatedProposals;
    }

    private Map<Proposal, List<VoteWithStake>> getVoteWithStakeListByProposalMap(Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet) {
        Map<Proposal, List<VoteWithStake>> voteWithStakeByProposalMap = new HashMap<>();
        decryptedBallotsWithMeritsSet.forEach(decryptedBallotsWithMerits -> {
            decryptedBallotsWithMerits.getBallotList()
                    .forEach(ballot -> {
                        Proposal proposal = ballot.getProposal();
                        voteWithStakeByProposalMap.putIfAbsent(proposal, new ArrayList<>());
                        List<VoteWithStake> voteWithStakeList = voteWithStakeByProposalMap.get(proposal);
                        long sumOfAllMerits = MeritConsensus.getMeritStake(decryptedBallotsWithMerits.getBlindVoteTxId(),
                                decryptedBallotsWithMerits.getMeritList(), bsqStateService);
                        VoteWithStake voteWithStake = new VoteWithStake(ballot.getVote(), decryptedBallotsWithMerits.getStake(), sumOfAllMerits);
                        voteWithStakeList.add(voteWithStake);
                    });
        });
        return voteWithStakeByProposalMap;
    }


    private ProposalVoteResult getResultPerProposal(List<VoteWithStake> voteWithStakeList, Proposal proposal) {
        int numAcceptedVotes = 0;
        int numRejectedVotes = 0;
        int numIgnoredVotes = 0;
        long stakeOfAcceptedVotes = 0;
        long stakeOfRejectedVotes = 0;

        for (VoteWithStake voteWithStake : voteWithStakeList) {
            long sumOfAllMerits = voteWithStake.getSumOfAllMerits();
            long stake = voteWithStake.getStake();
            long combinedStake = stake + sumOfAllMerits;
            log.info("proposalTxId={}, stake={}, sumOfAllMerits={}, combinedStake={}",
                    proposal.getTxId(), stake, sumOfAllMerits, combinedStake);
            Vote vote = voteWithStake.getVote();
            if (vote != null) {
                if (vote.isAccepted()) {
                    stakeOfAcceptedVotes += combinedStake;
                    numAcceptedVotes++;
                } else {
                    stakeOfRejectedVotes += combinedStake;
                    numRejectedVotes++;
                }
            } else {
                numIgnoredVotes++;
                log.debug("Voter ignored proposal");
            }
        }
        return new ProposalVoteResult(proposal, stakeOfAcceptedVotes, stakeOfRejectedVotes, numAcceptedVotes, numRejectedVotes, numIgnoredVotes);
    }

    private void applyAcceptedProposals(Set<EvaluatedProposal> acceptedEvaluatedProposals, int chainHeight) {
        applyIssuance(acceptedEvaluatedProposals, chainHeight);
        applyParamChange(acceptedEvaluatedProposals, chainHeight);
        applyBondedRole(acceptedEvaluatedProposals, chainHeight);
        applyConfiscateBond(acceptedEvaluatedProposals, chainHeight);
    }

    private void applyIssuance(Set<EvaluatedProposal> acceptedEvaluatedProposals, int chainHeight) {
        acceptedEvaluatedProposals.stream()
                .map(EvaluatedProposal::getProposal)
                .filter(proposal -> proposal instanceof CompensationProposal)
                .forEach(proposal -> issuanceService.issueBsq((CompensationProposal) proposal, chainHeight));
    }

    private void applyParamChange(Set<EvaluatedProposal> acceptedEvaluatedProposals, int chainHeight) {
        Map<String, List<EvaluatedProposal>> evaluatedProposalsByParam = new HashMap<>();
        acceptedEvaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof ChangeParamProposal) {
                ChangeParamProposal changeParamProposal = (ChangeParamProposal) evaluatedProposal.getProposal();
                ParamChange paramChange = getParamChange(changeParamProposal, chainHeight);
                if (paramChange != null) {
                    String paramName = paramChange.getParamName();
                    evaluatedProposalsByParam.putIfAbsent(paramName, new ArrayList<>());
                    evaluatedProposalsByParam.get(paramName).add(evaluatedProposal);
                }
            }
        });

        evaluatedProposalsByParam.forEach((key, list) -> {
            if (list.size() == 1) {
                applyAcceptedChangeParamProposal((ChangeParamProposal) list.get(0).getProposal(), chainHeight);
            } else if (list.size() > 1) {
                log.warn("There have been multiple winning param change proposals with the same item. " +
                        "This is a sign of a social consensus failure. " +
                        "We treat all requests as failed in such a case.");

                // TODO remove code once we are 100% sure we stick with the above solution.
                // We got multiple proposals for the same parameter. We check which one got the higher stake and that
                // one will be the winner. If both have same stake none will be the winner.
                /*list.sort(Comparator.comparing(ev -> ev.getProposalVoteResult().getStakeOfAcceptedVotes()));
                Collections.reverse(list);
                EvaluatedProposal first = list.get(0);
                EvaluatedProposal second = list.get(1);
                if (first.getProposalVoteResult().getStakeOfAcceptedVotes() >
                        second.getProposalVoteResult().getStakeOfAcceptedVotes()) {
                    applyAcceptedChangeParamProposal((ChangeParamProposal) first.getProposal(), chainHeight);
                } else {
                    // Rare case that both have the same stake. We don't need to check for a third entry as if 2 have
                    // the same we are already in the abort case to reject all proposals with that param
                    log.warn("We got the rare case that multiple changeParamProposals have received the same stake. " +
                            "None will be accepted in such a case.\n" +
                            "EvaluatedProposal={}", list);
                }*/
            }
        });
    }

    private void applyAcceptedChangeParamProposal(ChangeParamProposal changeParamProposal, int chainHeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n################################################################################\n");
        sb.append("We changed a parameter. ProposalTxId=").append(changeParamProposal.getTxId())
                .append("\nfor changeParamProposal with UID ").append(changeParamProposal.getTxId())
                .append("\nParam: ").append(changeParamProposal.getParam().name())
                .append(" new value: ").append(changeParamProposal.getParamValue())
                .append("\n################################################################################\n");
        log.info(sb.toString());

        bsqStateService.setNewParam(chainHeight, changeParamProposal.getParam(), changeParamProposal.getParamValue());
    }

    private ParamChange getParamChange(ChangeParamProposal changeParamProposal, int chainHeight) {
        return bsqStateService.getStartHeightOfNextCycle(chainHeight)
                .map(heightOfNewCycle -> new ParamChange(changeParamProposal.getParam().name(),
                        changeParamProposal.getParamValue(),
                        heightOfNewCycle))
                .orElse(null);
    }

    private void applyBondedRole(Set<EvaluatedProposal> acceptedEvaluatedProposals, int chainHeight) {
        acceptedEvaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof BondedRoleProposal) {
                BondedRoleProposal bondedRoleProposal = (BondedRoleProposal) evaluatedProposal.getProposal();
                BondedRole bondedRole = bondedRoleProposal.getBondedRole();
                bondedRolesService.addAcceptedBondedRole(bondedRole);
                StringBuilder sb = new StringBuilder();
                sb.append("\n################################################################################\n");
                sb.append("We added a bonded role. ProposalTxId=").append(bondedRoleProposal.getTxId())
                        .append("\nfor bondedRoleProposal with UID ").append(bondedRoleProposal.getTxId())
                        .append("\nBondedRole: ").append(bondedRole.getDisplayString())
                        .append("\n################################################################################\n");
                log.info(sb.toString());
            }
        });
    }

    private void applyConfiscateBond(Set<EvaluatedProposal> acceptedEvaluatedProposals, int chainHeight) {
        acceptedEvaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof ConfiscateBondProposal) {
                ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) evaluatedProposal.getProposal();
                bsqStateService.confiscateBond(new ConfiscateBond(confiscateBondProposal.getHash()));

                StringBuilder sb = new StringBuilder();
                sb.append("\n################################################################################\n");
                sb.append("We confiscated  bond. ProposalTxId=").append(confiscateBondProposal.getTxId())
                        .append("\nfor confiscateBondProposal with UID ").append(confiscateBondProposal.getTxId())
                        .append("\nHashOfBondId: ").append(Utilities.encodeToHex(confiscateBondProposal.getHash()))
                        .append("\n################################################################################\n");
                log.info(sb.toString());
            }
        });
    }

    private Set<EvaluatedProposal> getAcceptedEvaluatedProposals(Set<EvaluatedProposal> evaluatedProposals) {
        return evaluatedProposals.stream()
                .filter(EvaluatedProposal::isAccepted)
                .collect(Collectors.toSet());
    }

    private Set<EvaluatedProposal> getRejectedEvaluatedProposals(Set<EvaluatedProposal> evaluatedProposals) {
        return evaluatedProposals.stream()
                .filter(evaluatedProposal -> !evaluatedProposal.isAccepted())
                .collect(Collectors.toSet());
    }

    private boolean isInVoteResultPhase(int chainHeight) {
        return periodService.getFirstBlockOfPhase(chainHeight, DaoPhase.Phase.RESULT) == chainHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    public class HashWithStake {
        private final byte[] hash;
        private final long stake;

        HashWithStake(byte[] hash, long stake) {
            this.hash = hash;
            this.stake = stake;
        }
    }

    @Value
    private static class VoteWithStake {
        @Nullable
        private final Vote vote;
        private final long stake;
        private final long sumOfAllMerits;

        VoteWithStake(@Nullable Vote vote, long stake, long sumOfAllMerits) {
            this.vote = vote;
            this.stake = stake;
            this.sumOfAllMerits = sumOfAllMerits;
        }
    }
}
