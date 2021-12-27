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
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.BlindVote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.VoteWithProposalTxId;
import bisq.core.dao.governance.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.governance.merit.MeritConsensus;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.voteresult.issuance.IssuanceService;
import bisq.core.dao.governance.votereveal.VoteRevealConsensus;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BallotList;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.MeritList;
import bisq.core.dao.state.model.governance.ParamChange;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.CurrencyUtil;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.util.MathUtils;
import bisq.common.util.PermutationUtil;
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
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Calculates the result of the voting at the VoteResult period.
 * We take all data from the bitcoin domain and additionally the blindVote list which we received from the p2p network.
 * Due to eventual consistency we use the hash of the data view of the voters (majority by merit+stake). If our local
 * blindVote list contains the blindVotes used by the voters we can calculate the result, otherwise we need to request
 * the missing blindVotes from the network.
 */
@Slf4j
public class VoteResultService implements DaoStateListener, DaoSetupService {
    private final ProposalListPresentation proposalListPresentation;
    private final DaoStateService daoStateService;
    private final PeriodService periodService;
    private final BallotListService ballotListService;
    private final BlindVoteListService blindVoteListService;
    private final IssuanceService issuanceService;
    private final MissingDataRequestService missingDataRequestService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();
    @Getter
    private Set<DecryptedBallotsWithMerits> invalidDecryptedBallotsWithMeritItems = new HashSet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(ProposalListPresentation proposalListPresentation,
                             DaoStateService daoStateService,
                             PeriodService periodService,
                             BallotListService ballotListService,
                             BlindVoteListService blindVoteListService,
                             IssuanceService issuanceService,
                             MissingDataRequestService missingDataRequestService) {
        this.proposalListPresentation = proposalListPresentation;
        this.daoStateService = daoStateService;
        this.periodService = periodService;
        this.ballotListService = ballotListService;
        this.blindVoteListService = blindVoteListService;
        this.issuanceService = issuanceService;
        this.missingDataRequestService = missingDataRequestService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockComplete(Block block) {
        maybeCalculateVoteResult(block.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeCalculateVoteResult(int chainHeight) {
        if (isInVoteResultPhase(chainHeight)) {
            log.info("CalculateVoteResult at chainHeight={}", chainHeight);
            Cycle currentCycle = periodService.getCurrentCycle();
            checkNotNull(currentCycle, "currentCycle must not be null");
            long startTs = System.currentTimeMillis();

            Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet = getDecryptedBallotsWithMeritsSet(chainHeight);
            if (!decryptedBallotsWithMeritsSet.isEmpty()) {
                // From the decryptedBallotsWithMeritsSet we create a map with the hash of the blind vote list as key and the
                // aggregated stake as value (no merit as that is part of the P2P network data and might lead to inconsistency).
                // That map is used for calculating the majority of the blind vote lists.
                // There might be conflicting versions due the eventually consistency of the P2P network (if some blind
                // vote payloads do not arrive at all voters) which would lead to consensus failure in the result calculation.
                // To solve that problem we will only consider the blind votes valid which are matching the majority hash.
                // If multiple data views would have the same stake we sort additionally by the hex value of the
                // blind vote hash and use the first one in the sorted list as winner.
                // A node which has a local blindVote list which does not match the winner data view will try
                // permutations of his local list and if that does not succeed he need to recover it's
                // local blindVote list by requesting the correct list from other peers.
                Map<P2PDataStorage.ByteArray, Long> stakeByHashOfBlindVoteListMap = getStakeByHashOfBlindVoteListMap(decryptedBallotsWithMeritsSet);

                try {
                    // Get majority hash
                    byte[] majorityBlindVoteListHash = calculateMajorityBlindVoteListHash(stakeByHashOfBlindVoteListMap);

                    // Is our local list matching the majority data view?
                    Optional<List<BlindVote>> optionalBlindVoteListMatchingMajorityHash = findBlindVoteListMatchingMajorityHash(majorityBlindVoteListHash);
                    if (optionalBlindVoteListMatchingMajorityHash.isPresent()) {
                        List<BlindVote> blindVoteList = optionalBlindVoteListMatchingMajorityHash.get();
                        log.debug("blindVoteListMatchingMajorityHash: {}",
                                blindVoteList.stream()
                                        .map(e -> "blindVoteTxId=" + e.getTxId() + ", Stake=" + e.getStake())
                                        .collect(Collectors.toList()));

                        Set<String> blindVoteTxIdSet = blindVoteList.stream().map(BlindVote::getTxId).collect(Collectors.toSet());
                        // We need to filter out result list according to the majority hash list
                        Set<DecryptedBallotsWithMerits> filteredDecryptedBallotsWithMeritsSet = decryptedBallotsWithMeritsSet.stream()
                                .filter(decryptedBallotsWithMerits -> {
                                    boolean contains = blindVoteTxIdSet.contains(decryptedBallotsWithMerits.getBlindVoteTxId());
                                    if (!contains) {
                                        invalidDecryptedBallotsWithMeritItems.add(decryptedBallotsWithMerits);
                                    }
                                    return contains;
                                })
                                .collect(Collectors.toSet());

                        // Only if we have all blind vote payloads and know the right list matching the majority we add
                        // it to our state. Otherwise we are not in consensus with the network.
                        daoStateService.addDecryptedBallotsWithMeritsSet(filteredDecryptedBallotsWithMeritsSet);

                        Set<EvaluatedProposal> evaluatedProposals = getEvaluatedProposals(filteredDecryptedBallotsWithMeritsSet, chainHeight);
                        daoStateService.addEvaluatedProposalSet(evaluatedProposals);
                        Set<EvaluatedProposal> acceptedEvaluatedProposals = getAcceptedEvaluatedProposals(evaluatedProposals);
                        applyAcceptedProposals(acceptedEvaluatedProposals, chainHeight);
                        log.info("processAllVoteResults completed");
                    } else {
                        String msg = "We could not find a list which matches the majority so we cannot calculate the vote result. Please restart and resync the DAO state.";
                        log.warn(msg);
                        voteResultExceptions.add(new VoteResultException(currentCycle, new Exception(msg)));
                    }
                } catch (Throwable e) {
                    log.warn(e.toString());
                    log.warn("decryptedBallotsWithMeritsSet " + decryptedBallotsWithMeritsSet);
                    e.printStackTrace();
                    voteResultExceptions.add(new VoteResultException(currentCycle, e));
                }
            } else {
                log.info("There have not been any votes in that cycle. chainHeight={}", chainHeight);
            }
            log.info("Evaluating vote result took {} ms", System.currentTimeMillis() - startTs);
        }
    }

    private Set<DecryptedBallotsWithMerits> getDecryptedBallotsWithMeritsSet(int chainHeight) {
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        return daoStateService.getVoteRevealOpReturnTxOutputs().stream()
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .filter(this::isInVoteRevealPhase)
                .map(txOutputToDecryptedBallotsWithMerits(chainHeight))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean isInVoteRevealPhase(TxOutput txOutput) {
        String voteRevealTx = txOutput.getTxId();
        boolean txInPhase = periodService.isTxInPhase(voteRevealTx, DaoPhase.Phase.VOTE_REVEAL);
        if (!txInPhase)
            log.warn("We got a vote reveal tx with was not in the correct phase of that cycle. voteRevealTxId={}", voteRevealTx);

        return txInPhase;
    }

    @NotNull
    private Function<TxOutput, DecryptedBallotsWithMerits> txOutputToDecryptedBallotsWithMerits(int chainHeight) {
        return voteRevealTxOutput -> {
            String voteRevealTxId = voteRevealTxOutput.getTxId();
            Cycle currentCycle = periodService.getCurrentCycle();
            checkNotNull(currentCycle, "currentCycle must not be null");
            try {
                byte[] voteRevealOpReturnData = voteRevealTxOutput.getOpReturnData();
                Optional<Tx> optionalVoteRevealTx = daoStateService.getTx(voteRevealTxId);
                checkArgument(optionalVoteRevealTx.isPresent(), "optionalVoteRevealTx must be present. voteRevealTxId=" + voteRevealTxId);
                Tx voteRevealTx = optionalVoteRevealTx.get();

                // Here we use only blockchain tx data so far so we don't have risks with missing P2P network data.
                // We work back from the voteRealTx to the blindVoteTx to calculate the majority hash. From that we
                // will derive the blind vote list we will use for result calculation and as it was based on
                // blockchain data it will be consistent for all peers independent on their P2P network data state.
                TxOutput blindVoteStakeOutput = VoteResultConsensus.getConnectedBlindVoteStakeOutput(voteRevealTx, daoStateService);
                String blindVoteTxId = blindVoteStakeOutput.getTxId();

                // If we get a blind vote tx which was published too late we ignore it.
                if (!periodService.isTxInPhaseAndCycle(blindVoteTxId, DaoPhase.Phase.BLIND_VOTE, chainHeight)) {
                    log.warn("We got a blind vote tx with was not in the correct phase and/or cycle. " +
                                    "We ignore that vote reveal and blind vote tx. voteRevealTx={}, blindVoteTxId={}",
                            voteRevealTx, blindVoteTxId);
                    return null;
                }

                VoteResultConsensus.validateBlindVoteTx(blindVoteTxId, daoStateService, periodService, chainHeight);

                byte[] hashOfBlindVoteList = VoteResultConsensus.getHashOfBlindVoteList(voteRevealOpReturnData);
                long blindVoteStake = blindVoteStakeOutput.getValue();

                List<BlindVote> blindVoteList = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
                Optional<BlindVote> optionalBlindVote = blindVoteList.stream()
                        .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                        .findAny();
                if (optionalBlindVote.isPresent()) {
                    return getDecryptedBallotsWithMerits(voteRevealTxId, currentCycle, voteRevealOpReturnData,
                            blindVoteTxId, hashOfBlindVoteList, blindVoteStake, optionalBlindVote.get());
                }

                // We are missing P2P network data
                return getEmptyDecryptedBallotsWithMerits(voteRevealTxId, blindVoteTxId, hashOfBlindVoteList,
                        blindVoteStake);
            } catch (Throwable e) {
                log.error("Could not create DecryptedBallotsWithMerits from voteRevealTxId {} because of " +
                        "exception: {}", voteRevealTxId, e.toString());
                voteResultExceptions.add(new VoteResultException(currentCycle, e));
                return null;
            }
        };
    }

    @NotNull
    private DecryptedBallotsWithMerits getEmptyDecryptedBallotsWithMerits(
            String voteRevealTxId, String blindVoteTxId, byte[] hashOfBlindVoteList, long blindVoteStake) {
        log.warn("We have a blindVoteTx but we do not have the corresponding blindVote payload.\n" +
                "That can happen if the blindVote item was not properly broadcast. " +
                "We still add it to our result collection because it might be relevant for the majority " +
                "hash by stake calculation. blindVoteTxId={}", blindVoteTxId);

        missingDataRequestService.sendRepublishRequest();

        // We prefer to use an empty list here instead a null or optional value to avoid that
        // client code need to handle nullable or optional values.
        BallotList emptyBallotList = new BallotList(new ArrayList<>());
        MeritList emptyMeritList = new MeritList(new ArrayList<>());
        log.debug("Add entry to decryptedBallotsWithMeritsSet: blindVoteTxId={}, voteRevealTxId={}, " +
                        "blindVoteStake={}, ballotList={}",
                blindVoteTxId, voteRevealTxId, blindVoteStake, emptyBallotList);
        return new DecryptedBallotsWithMerits(hashOfBlindVoteList, blindVoteTxId, voteRevealTxId,
                blindVoteStake, emptyBallotList, emptyMeritList);
    }

    @Nullable
    private DecryptedBallotsWithMerits getDecryptedBallotsWithMerits(
            String voteRevealTxId, Cycle currentCycle, byte[] voteRevealOpReturnData, String blindVoteTxId,
            byte[] hashOfBlindVoteList, long blindVoteStake, BlindVote blindVote)
            throws VoteResultException.MissingBallotException {
        SecretKey secretKey = VoteResultConsensus.getSecretKey(voteRevealOpReturnData);
        try {
            VoteWithProposalTxIdList voteWithProposalTxIdList = VoteResultConsensus.decryptVotes(blindVote.getEncryptedVotes(), secretKey);
            MeritList meritList = MeritConsensus.decryptMeritList(blindVote.getEncryptedMeritList(), secretKey);
            // We lookup for the proposals we have in our local list which match the txId from the
            // voteWithProposalTxIdList and create a ballot list with the proposal and the vote from
            // the voteWithProposalTxIdList
            BallotList ballotList = createBallotList(voteWithProposalTxIdList);
            log.debug("Add entry to decryptedBallotsWithMeritsSet: blindVoteTxId={}, voteRevealTxId={}, blindVoteStake={}, ballotList={}",
                    blindVoteTxId, voteRevealTxId, blindVoteStake, ballotList);
            return new DecryptedBallotsWithMerits(hashOfBlindVoteList, blindVoteTxId, voteRevealTxId, blindVoteStake, ballotList, meritList);
        } catch (VoteResultException.DecryptionException decryptionException) {
            // We don't consider such vote reveal txs valid for the majority hash
            // calculation and don't add it to our result collection
            log.error("Could not decrypt blind vote. This vote reveal and blind vote will be ignored. " +
                    "VoteRevealTxId={}. DecryptionException={}", voteRevealTxId, decryptionException.toString());
            voteResultExceptions.add(new VoteResultException(currentCycle, decryptionException));
            return null;
        }
    }

    private BallotList createBallotList(VoteWithProposalTxIdList voteWithProposalTxIdList)
            throws VoteResultException.MissingBallotException {
        // voteWithProposalTxIdList is the list of ProposalTxId + vote from the blind vote (decrypted vote data)

        // We convert the list to a map with proposalTxId as key and the vote as value. As the vote can be null we
        // wrap it into an optional.
        Map<String, Optional<Vote>> voteByTxIdMap = voteWithProposalTxIdList.getList().stream()
                .collect(Collectors.toMap(VoteWithProposalTxId::getProposalTxId, e -> Optional.ofNullable(e.getVote())));

        // We make a map with proposalTxId as key and the ballot as value out of our stored ballot list.
        // This can contain ballots which have been added later and have a null value for the vote.
        Map<String, Ballot> ballotByTxIdMap = ballotListService.getValidBallotsOfCycle().stream()
                .collect(Collectors.toMap(Ballot::getTxId, ballot -> ballot));

        // It could be that we missed some proposalPayloads.
        // If we have votes with proposals which are not found in our ballots we add it to missingBallots.
        List<String> missingBallots = new ArrayList<>();
        List<Ballot> ballots = voteByTxIdMap.entrySet().stream()
                .map(entry -> {
                    String txId = entry.getKey();
                    if (ballotByTxIdMap.containsKey(txId)) {
                        Ballot ballot = ballotByTxIdMap.get(txId);
                        // We create a new Ballot with the proposal from the ballot list and the vote from our decrypted votes
                        // We clone the ballot instead applying the vote to the existing ballot from ballotListService
                        // The items from ballotListService.getBallotList() contains my votes.

                        if (ballot.getVote() != null) {
                            // If we had set a vote it was an own active vote
                            if (!entry.getValue().isPresent()) {
                                log.warn("We found a local vote but don't have that vote in the data from the " +
                                        "blind vote. ballot={}", ballot);
                            } else if (!ballot.getVote().equals(entry.getValue().get())) {
                                log.warn("We found a local vote but the vote from the " +
                                                "blind vote does not match. ballot={}, vote from blindVote data={}",
                                        ballot, entry.getValue().get());
                            }
                        }

                        // We only return accepted or rejected votes
                        return entry.getValue().map(vote -> new Ballot(ballot.getProposal(), vote)).orElse(null);
                    } else {
                        // We got a vote but we don't have the ballot (which includes the proposal)
                        // We add it to the missing list to handle it as exception later. We want all missing data so we
                        // do not throw here.
                        log.warn("missingBallot for proposal with txId={}. Optional tx={}", txId, daoStateService.getTx(txId));
                        missingBallots.add(txId);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!missingBallots.isEmpty())
            throw new VoteResultException.MissingBallotException(ballots, missingBallots);

        // If we received a proposal after we had already voted we consider it as a proposal withhold attack and
        // treat the proposal as it was voted with a rejected vote.
        ballotByTxIdMap.entrySet().stream()
                .filter(e -> !voteByTxIdMap.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .forEach(ballot -> {
                    log.warn("We have a proposal which was not part of our blind vote and reject it. " +
                            "Proposal={}", ballot.getProposal());
                    ballots.add(new Ballot(ballot.getProposal(), new Vote(false)));
                });

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
            // We must not use the merit(stake) as that is from the P2P network data and it is not guaranteed that we
            // have received it. We must rely only on blockchain data. The stake is from the vote reveal tx input.
            long aggregatedStake = map.get(hash);
            long stake = decryptedBallotsWithMerits.getStake();
            aggregatedStake += stake;
            map.put(hash, aggregatedStake);

            log.debug("blindVoteTxId={}, stake={}",
                    decryptedBallotsWithMerits.getBlindVoteTxId(), stake);
        });
        return map;
    }

    private byte[] calculateMajorityBlindVoteListHash(Map<P2PDataStorage.ByteArray, Long> stakes)
            throws VoteResultException.ValidationException, VoteResultException.ConsensusException {
        List<HashWithStake> stakeList = stakes.entrySet().stream()
                .map(entry -> new HashWithStake(entry.getKey().bytes, entry.getValue()))
                .collect(Collectors.toList());
        return VoteResultConsensus.getMajorityHash(stakeList);
    }

    // Deal with eventually consistency of P2P network
    private Optional<List<BlindVote>> findBlindVoteListMatchingMajorityHash(byte[] majorityVoteListHash) {
        // We reuse the method at voteReveal domain used when creating the hash
        List<BlindVote> blindVotes = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
        if (isListMatchingMajority(majorityVoteListHash, blindVotes, true)) {
            // Out local list is matching the majority hash
            return Optional.of(blindVotes);
        } else {
            log.warn("Our local list of blind vote payloads does not match the majorityVoteListHash. " +
                    "We try permuting our list to find a matching variant");
            // Each voter has re-published his blind vote list when broadcasting the reveal tx so there should have a very
            // high chance that we have received all blind votes which have been used by the majority of the
            // voters (majority by stake).
            // It still could be that we have additional blind votes so our hash does not match. We can try to permute
            // our list with excluding items to see if we get a matching list. If not last resort is to request the
            // missing items from the network.
            Optional<List<BlindVote>> permutatedList = findPermutatedListMatchingMajority(majorityVoteListHash);
            if (permutatedList.isPresent()) {
                return permutatedList;
            } else {
                log.warn("We did not find a permutation of our blindVote list which matches the majority view. " +
                        "We will request the blindVote data from the peers.");
                // This is async operation. We will restart the whole verification process once we received the data.
                missingDataRequestService.sendRepublishRequest();
                return Optional.empty();
            }
        }
    }

    private Optional<List<BlindVote>> findPermutatedListMatchingMajority(byte[] majorityVoteListHash) {
        List<BlindVote> list = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteListService);
        long ts = System.currentTimeMillis();

        BiPredicate<byte[], List<BlindVote>> predicate = (hash, variation) ->
                isListMatchingMajority(hash, variation, false);

        List<BlindVote> result = PermutationUtil.findMatchingPermutation(majorityVoteListHash, list, predicate, 1000000);
        log.info("findPermutatedListMatchingMajority for {} items took {} ms.",
                list.size(), (System.currentTimeMillis() - ts));
        if (result.isEmpty()) {
            log.info("We did not find a variation of the blind vote list which matches the majority hash.");
            return Optional.empty();
        } else {
            log.info("We found a variation of the blind vote list which matches the majority hash. variation={}", result);
            return Optional.of(result);
        }
    }

    private boolean isListMatchingMajority(byte[] majorityVoteListHash, List<BlindVote> list, boolean doLog) {
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(list);
        if (doLog) {
            log.debug("majorityVoteListHash {}", Utilities.bytesAsHexString(majorityVoteListHash));
            log.debug("hashOfBlindVoteList {}", Utilities.bytesAsHexString(hashOfBlindVoteList));
            log.debug("List of blindVoteTxIds {}", list.stream().map(BlindVote::getTxId)
                    .collect(Collectors.joining(", ")));
        }
        return Arrays.equals(majorityVoteListHash, hashOfBlindVoteList);
    }

    private Set<EvaluatedProposal> getEvaluatedProposals(Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet,
                                                         int chainHeight) {
        // We reorganize the data structure to have a map of proposals with a list of VoteWithStake objects
        Map<Proposal, List<VoteWithStake>> resultListByProposalMap = getVoteWithStakeListByProposalMap(decryptedBallotsWithMeritsSet);

        Set<EvaluatedProposal> evaluatedProposals = new HashSet<>();
        resultListByProposalMap.forEach((proposal, voteWithStakeList) -> {
            long requiredQuorum = daoStateService.getParamValueAsCoin(proposal.getQuorumParam(), chainHeight).value;
            long requiredVoteThreshold = getRequiredVoteThreshold(chainHeight, proposal);
            checkArgument(requiredVoteThreshold >= 5000,
                    "requiredVoteThreshold must be not be less then 50% otherwise we could have conflicting results.");

            // move to consensus class
            ProposalVoteResult proposalVoteResult = getResultPerProposal(voteWithStakeList, proposal);
            // Quorum is min. required BSQ stake to be considered valid
            long reachedQuorum = proposalVoteResult.getQuorum();
            log.debug("proposalTxId: {}, required requiredQuorum: {}, requiredVoteThreshold: {}",
                    proposal.getTxId(), requiredVoteThreshold / 100D, requiredQuorum);
            if (reachedQuorum >= requiredQuorum) {
                // We multiply by 10000 as we use a long for reachedThreshold and we want precision of 2 with
                // a % value. E.g. 50% is 5000.
                // Threshold is percentage of accepted to total stake
                long reachedThreshold = proposalVoteResult.getThreshold();

                log.debug("reached threshold: {} %, required threshold: {} %",
                        reachedThreshold / 100D,
                        requiredVoteThreshold / 100D);
                // We need to exceed requiredVoteThreshold e.g. 50% is not enough but 50.01%.
                // Otherwise we could have 50% vs 50%
                if (reachedThreshold > requiredVoteThreshold) {
                    evaluatedProposals.add(new EvaluatedProposal(true, proposalVoteResult));
                } else {
                    evaluatedProposals.add(new EvaluatedProposal(false, proposalVoteResult));
                    log.debug("Proposal did not reach the requiredVoteThreshold. reachedThreshold={} %, " +
                            "requiredVoteThreshold={} %", reachedThreshold / 100D, requiredVoteThreshold / 100D);
                }
            } else {
                evaluatedProposals.add(new EvaluatedProposal(false, proposalVoteResult));
                log.debug("Proposal did not reach the requiredQuorum. reachedQuorum={}, requiredQuorum={}",
                        reachedQuorum, requiredQuorum);
            }
        });

        Map<String, EvaluatedProposal> evaluatedProposalsByTxIdMap = new HashMap<>();
        evaluatedProposals.forEach(evaluatedProposal -> evaluatedProposalsByTxIdMap.put(evaluatedProposal.getProposalTxId(), evaluatedProposal));

        // Proposals which did not get any vote need to be set as failed.
        // TODO We should not use proposalListPresentation here
        proposalListPresentation.getActiveOrMyUnconfirmedProposals().stream()
                .filter(proposal -> periodService.isTxInCorrectCycle(proposal.getTxId(), chainHeight))
                .filter(proposal -> !evaluatedProposalsByTxIdMap.containsKey(proposal.getTxId()))
                .forEach(proposal -> {
                    ProposalVoteResult proposalVoteResult = new ProposalVoteResult(proposal, 0,
                            0, 0, 0, decryptedBallotsWithMeritsSet.size());
                    EvaluatedProposal evaluatedProposal = new EvaluatedProposal(false, proposalVoteResult);
                    evaluatedProposals.add(evaluatedProposal);
                    log.info("Proposal ignored by all voters: {}", evaluatedProposal);
                });

        // Check if our issuance sum is not exceeding the limit
        long sumIssuance = evaluatedProposals.stream()
                .filter(EvaluatedProposal::isAccepted)
                .map(EvaluatedProposal::getProposal)
                .filter(proposal -> proposal instanceof IssuanceProposal)
                .map(proposal -> (IssuanceProposal) proposal)
                .mapToLong(proposal -> proposal.getRequestedBsq().value)
                .sum();
        long limit = daoStateService.getParamValueAsCoin(Param.ISSUANCE_LIMIT, chainHeight).value;
        if (sumIssuance > limit) {
            Set<EvaluatedProposal> evaluatedProposals2 = new HashSet<>();
            evaluatedProposals.stream().filter(EvaluatedProposal::isAccepted)
                    .forEach(e -> evaluatedProposals2.add(new EvaluatedProposal(false, e.getProposalVoteResult())));
            String msg = "We have a total issuance amount of " + sumIssuance / 100 + " BSQ but our limit for a cycle is " + limit / 100 + " BSQ. " +
                    "We consider that cycle as invalid and have set all proposals as rejected.";
            log.warn(msg);

            checkNotNull(daoStateService.getCurrentCycle(), "daoStateService.getCurrentCycle() must not be null");
            voteResultExceptions.add(new VoteResultException(daoStateService.getCurrentCycle(), new VoteResultException.ConsensusException(msg)));
            return evaluatedProposals2;
        }

        return evaluatedProposals;
    }

    // We use long for calculation to avoid issues with rounding. So we multiply the % value as double (e.g. 0.5 = 50%)
    // by 100 to get the percentage value and again by 100 to get 2 decimal -> 5000 = 50.00%
    private long getRequiredVoteThreshold(int chainHeight, Proposal proposal) {
        double paramValueAsPercentDouble = daoStateService.getParamValueAsPercentDouble(proposal.getThresholdParam(), chainHeight);
        return MathUtils.roundDoubleToLong(paramValueAsPercentDouble * 10000);
    }

    private Map<Proposal, List<VoteWithStake>> getVoteWithStakeListByProposalMap(Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet) {
        Map<Proposal, List<VoteWithStake>> voteWithStakeByProposalMap = new HashMap<>();
        decryptedBallotsWithMeritsSet.forEach(decryptedBallotsWithMerits -> decryptedBallotsWithMerits.getBallotList()
                .forEach(ballot -> {
                    Proposal proposal = ballot.getProposal();
                    voteWithStakeByProposalMap.putIfAbsent(proposal, new ArrayList<>());
                    List<VoteWithStake> voteWithStakeList = voteWithStakeByProposalMap.get(proposal);
                    long sumOfAllMerits = MeritConsensus.getMeritStake(decryptedBallotsWithMerits.getBlindVoteTxId(),
                            decryptedBallotsWithMerits.getMeritList(), daoStateService);
                    VoteWithStake voteWithStake = new VoteWithStake(ballot.getVote(), decryptedBallotsWithMerits.getStake(), sumOfAllMerits);
                    voteWithStakeList.add(voteWithStake);
                    log.debug("Add entry to voteWithStakeListByProposalMap: proposalTxId={}, voteWithStake={} ", proposal.getTxId(), voteWithStake);
                }));
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
            log.debug("proposalTxId={}, stake={}, sumOfAllMerits={}, combinedStake={}",
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
        applyBondedRole(acceptedEvaluatedProposals);
        applyConfiscateBond(acceptedEvaluatedProposals);
        applyRemoveAsset(acceptedEvaluatedProposals);
    }

    private void applyIssuance(Set<EvaluatedProposal> acceptedEvaluatedProposals, int chainHeight) {
        acceptedEvaluatedProposals.stream()
                .map(EvaluatedProposal::getProposal)
                .filter(proposal -> proposal instanceof IssuanceProposal)
                .forEach(proposal -> issuanceService.issueBsq((IssuanceProposal) proposal, chainHeight));
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
            }
        });
    }

    private void applyAcceptedChangeParamProposal(ChangeParamProposal changeParamProposal, int chainHeight) {
        @SuppressWarnings("StringBufferReplaceableByString")
        StringBuilder sb = new StringBuilder();
        sb.append("\n################################################################################\n");
        sb.append("We changed a parameter. ProposalTxId=").append(changeParamProposal.getTxId())
                .append("\nParam: ").append(changeParamProposal.getParam().name())
                .append(" new value: ").append(changeParamProposal.getParamValue())
                .append("\n################################################################################\n");
        log.info(sb.toString());

        daoStateService.setNewParam(chainHeight, changeParamProposal.getParam(), changeParamProposal.getParamValue());
    }

    private ParamChange getParamChange(ChangeParamProposal changeParamProposal, int chainHeight) {
        return daoStateService.getStartHeightOfNextCycle(chainHeight)
                .map(heightOfNewCycle -> new ParamChange(changeParamProposal.getParam().name(),
                        changeParamProposal.getParamValue(),
                        heightOfNewCycle))
                .orElse(null);
    }

    private void applyBondedRole(Set<EvaluatedProposal> acceptedEvaluatedProposals) {
        acceptedEvaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof RoleProposal) {
                RoleProposal roleProposal = (RoleProposal) evaluatedProposal.getProposal();
                Role role = roleProposal.getRole();
                @SuppressWarnings("StringBufferReplaceableByString")
                StringBuilder sb = new StringBuilder();
                sb.append("\n################################################################################\n");
                sb.append("We added a bonded role. ProposalTxId=").append(roleProposal.getTxId())
                        .append("\nRole: ").append(role.getDisplayString())
                        .append("\n################################################################################\n");
                log.info(sb.toString());
            }
        });
    }

    private void applyConfiscateBond(Set<EvaluatedProposal> acceptedEvaluatedProposals) {
        acceptedEvaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof ConfiscateBondProposal) {
                ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) evaluatedProposal.getProposal();
                daoStateService.confiscateBond(confiscateBondProposal.getLockupTxId());

                @SuppressWarnings("StringBufferReplaceableByString")
                StringBuilder sb = new StringBuilder();
                sb.append("\n################################################################################\n");
                sb.append("We confiscated a bond. ProposalTxId=").append(confiscateBondProposal.getTxId())
                        .append("\nLockupTxId: ").append(confiscateBondProposal.getLockupTxId())
                        .append("\n################################################################################\n");
                log.info(sb.toString());
            }
        });
    }

    private void applyRemoveAsset(Set<EvaluatedProposal> acceptedEvaluatedProposals) {
        acceptedEvaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof RemoveAssetProposal) {
                RemoveAssetProposal removeAssetProposal = (RemoveAssetProposal) evaluatedProposal.getProposal();
                String tickerSymbol = removeAssetProposal.getTickerSymbol();
                @SuppressWarnings("StringBufferReplaceableByString")
                StringBuilder sb = new StringBuilder();
                sb.append("\n################################################################################\n");
                sb.append("We removed an asset. ProposalTxId=").append(removeAssetProposal.getTxId())
                        .append("\nAsset: ").append(CurrencyUtil.getNameByCode(tickerSymbol))
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

    private boolean isInVoteResultPhase(int chainHeight) {
        return periodService.getFirstBlockOfPhase(chainHeight, DaoPhase.Phase.RESULT) == chainHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    public static class HashWithStake {
        private final byte[] hash;
        private final long stake;

        HashWithStake(byte[] hash, long stake) {
            this.hash = hash;
            this.stake = stake;
        }

        @Override
        public String toString() {
            return "HashWithStake{" +
                    "\n     hash=" + Utilities.bytesAsHexString(hash) +
                    ",\n     stake=" + stake +
                    "\n}";
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

        @Override
        public String toString() {
            return "VoteWithStake{" +
                    "\n     vote=" + vote +
                    ",\n     stake=" + stake +
                    ",\n     sumOfAllMerits=" + sumOfAllMerits +
                    "\n}";
        }
    }
}
