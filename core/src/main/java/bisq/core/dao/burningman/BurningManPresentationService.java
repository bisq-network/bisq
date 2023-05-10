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

package bisq.core.dao.burningman;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.CyclesInDaoStateService;
import bisq.core.dao.burningman.model.BurnOutputModel;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.burningman.model.LegacyBurningMan;
import bisq.core.dao.burningman.model.ReimbursementModel;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.config.Config;
import bisq.common.util.Hex;
import bisq.common.util.Tuple2;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides APIs for burningman data representation in the UI.
 */
@Slf4j
@Singleton
public class BurningManPresentationService implements DaoStateListener {
    // Burn target gets increased by that amount to give more flexibility.
    // Burn target is calculated from reimbursements + estimated BTC fees - burned amounts.
    private static final long BURN_TARGET_BOOST_AMOUNT = 10000000;
    public static final String LEGACY_BURNING_MAN_DPT_NAME = "Legacy Burningman (DPT)";
    public static final String LEGACY_BURNING_MAN_BTC_FEES_NAME = "Legacy Burningman (BTC fees)";
    static final String LEGACY_BURNING_MAN_BTC_FEES_ADDRESS = "38bZBj5peYS3Husdz7AH3gEUiUbYRD951t";
    // Those are the opReturn data used by legacy BM for burning BTC received from DPT.
    // For regtest testing burn bsq and use the pre-image `dpt` which has the hash 14af04ea7e34bd7378b034ddf90da53b7c27a277.
    // The opReturn data gets additionally prefixed with 1701
    static final Set<String> OP_RETURN_DATA_LEGACY_BM_DPT = Config.baseCurrencyNetwork().isRegtest() ?
            Set.of("170114af04ea7e34bd7378b034ddf90da53b7c27a277") :
            Set.of("1701e47e5d8030f444c182b5e243871ebbaeadb5e82f",
                    "1701293c488822f98e70e047012f46f5f1647f37deb7");
    // The opReturn data used by legacy BM for burning BTC received from BTC trade fees.
    // For regtest testing burn bsq and use the pre-image `fee` which has the hash b3253b7b92bb7f0916b05f10d4fa92be8e48f5e6.
    // The opReturn data gets additionally prefixed with 1701
    static final Set<String> OP_RETURN_DATA_LEGACY_BM_FEES = Config.baseCurrencyNetwork().isRegtest() ?
            Set.of("1701b3253b7b92bb7f0916b05f10d4fa92be8e48f5e6") :
            Set.of("1701721206fe6b40777763de1c741f4fd2706d94775d");

    private final DaoStateService daoStateService;
    private final CyclesInDaoStateService cyclesInDaoStateService;
    private final MyProposalListService myProposalListService;
    private final BsqWalletService bsqWalletService;
    private final BurningManService burningManService;
    private final BurnTargetService burnTargetService;

    private int currentChainHeight;
    private Optional<Long> burnTarget = Optional.empty();
    private final Map<String, BurningManCandidate> burningManCandidatesByName = new HashMap<>();
    private Long accumulatedDecayedBurnedAmount;
    private final Set<ReimbursementModel> reimbursements = new HashSet<>();
    private Optional<Long> averageDistributionPerCycle = Optional.empty();
    private Set<String> myCompensationRequestNames = null;
    @SuppressWarnings("OptionalAssignedToNull")
    private Optional<Set<String>> myGenesisOutputNames = null;
    private Optional<LegacyBurningMan> legacyBurningManDPT = Optional.empty();
    private Optional<LegacyBurningMan> legacyBurningManBtcFees = Optional.empty();
    private final Map<P2PDataStorage.ByteArray, Set<TxOutput>> proofOfBurnOpReturnTxOutputByHash = new HashMap<>();
    private final Map<String, String> burningManNameByAddress = new HashMap<>();

    @Inject
    public BurningManPresentationService(DaoStateService daoStateService,
                                         CyclesInDaoStateService cyclesInDaoStateService,
                                         MyProposalListService myProposalListService,
                                         BsqWalletService bsqWalletService,
                                         BurningManService burningManService,
                                         BurnTargetService burnTargetService) {
        this.daoStateService = daoStateService;
        this.cyclesInDaoStateService = cyclesInDaoStateService;
        this.myProposalListService = myProposalListService;
        this.bsqWalletService = bsqWalletService;
        this.burningManService = burningManService;
        this.burnTargetService = burnTargetService;

        daoStateService.addDaoStateListener(this);
        daoStateService.getLastBlock().ifPresent(this::applyBlock);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyBlock(block);
    }

    private void applyBlock(Block block) {
        currentChainHeight = block.getHeight();
        burningManCandidatesByName.clear();
        reimbursements.clear();
        burnTarget = Optional.empty();
        accumulatedDecayedBurnedAmount = null;
        myCompensationRequestNames = null;
        averageDistributionPerCycle = Optional.empty();
        legacyBurningManDPT = Optional.empty();
        legacyBurningManBtcFees = Optional.empty();
        proofOfBurnOpReturnTxOutputByHash.clear();
        burningManNameByAddress.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBurnTarget() {
        if (burnTarget.isPresent()) {
            return burnTarget.get();
        }

        burnTarget = Optional.of(burnTargetService.getBurnTarget(currentChainHeight, getBurningManCandidatesByName().values()));
        return burnTarget.get();
    }

    public long getBoostedBurnTarget() {
        return getBurnTarget() + BURN_TARGET_BOOST_AMOUNT;
    }

    public long getAverageDistributionPerCycle() {
        if (averageDistributionPerCycle.isPresent()) {
            return averageDistributionPerCycle.get();
        }

        averageDistributionPerCycle = Optional.of(burnTargetService.getAverageDistributionPerCycle(currentChainHeight));
        return averageDistributionPerCycle.get();
    }

    public long getExpectedRevenue(BurningManCandidate burningManCandidate) {
        return Math.round(burningManCandidate.getCappedBurnAmountShare() * getAverageDistributionPerCycle());
    }

    // Left side in tuple is the amount to burn to reach the max. burn share based on the total burned amount.
    // This value is safe to not burn more than needed and to avoid to get capped.
    // The right side is the amount to burn to reach the max. burn share based on the boosted burn target.
    // This can lead to burning too much and getting capped.
    public Tuple2<Long, Long> getCandidateBurnTarget(BurningManCandidate burningManCandidate) {
        long burnTarget = getBurnTarget();
        long boostedBurnTarget = burnTarget + BURN_TARGET_BOOST_AMOUNT;
        double compensationShare = burningManCandidate.getCompensationShare();

        if (boostedBurnTarget <= 0 || compensationShare == 0) {
            return new Tuple2<>(0L, 0L);
        }

        double maxCompensationShare = Math.min(BurningManService.MAX_BURN_SHARE, compensationShare);
        long lowerBaseTarget = Math.round(burnTarget * maxCompensationShare);
        double maxBoostedCompensationShare = burningManCandidate.getMaxBoostedCompensationShare();
        long upperBaseTarget = Math.round(boostedBurnTarget * maxBoostedCompensationShare);
        long totalBurnedAmount = getAccumulatedDecayedBurnedAmount();

        if (totalBurnedAmount == 0) {
            // The first BM would reach their max burn share by 5.46 BSQ already. But we suggest the lowerBaseTarget
            // as lower target to speed up the bootstrapping.
            return new Tuple2<>(lowerBaseTarget, upperBaseTarget);
        }

        if (burningManCandidate.getAdjustedBurnAmountShare() < maxBoostedCompensationShare) {
            long candidatesBurnAmount = burningManCandidate.getAccumulatedDecayedBurnAmount();

            // TODO We do not consider adjustedBurnAmountShare. This could lead to slight over burn. Atm we ignore that.
            long myBurnAmount = getMissingAmountToReachTargetShare(totalBurnedAmount, candidatesBurnAmount, maxBoostedCompensationShare);

            // If below dust we set value to 0
            myBurnAmount = myBurnAmount < 546 ? 0 : myBurnAmount;

            // In case the myBurnAmount would be larger than the upperBaseTarget we use the upperBaseTarget.
            myBurnAmount = Math.min(myBurnAmount, upperBaseTarget);

            return new Tuple2<>(myBurnAmount, upperBaseTarget);
        } else {
            // We have reached our cap.
            return new Tuple2<>(0L, upperBaseTarget);
        }
    }

    @VisibleForTesting
    static long getMissingAmountToReachTargetShare(long totalBurnedAmount, long myBurnAmount, double myTargetShare) {
        long others = totalBurnedAmount - myBurnAmount;
        double shareTargetOthers = 1 - myTargetShare;
        double targetAmount = shareTargetOthers > 0 ? myTargetShare / shareTargetOthers * others : 0;
        return Math.round(targetAmount) - myBurnAmount;
    }

    public Set<ReimbursementModel> getReimbursements() {
        if (!reimbursements.isEmpty()) {
            return reimbursements;
        }

        reimbursements.addAll(burnTargetService.getReimbursements(currentChainHeight));
        return reimbursements;
    }

    public Optional<Set<String>> findMyGenesisOutputNames() {
        // Optional.empty is valid case, so we use null to detect if it was set.
        // As it does not change at new blocks its only set once.
        //noinspection OptionalAssignedToNull
        if (myGenesisOutputNames != null) {
            return myGenesisOutputNames;
        }

        myGenesisOutputNames = daoStateService.getGenesisTx()
                .flatMap(tx -> Optional.ofNullable(bsqWalletService.getTransaction(tx.getId()))
                        .map(genesisTransaction -> genesisTransaction.getOutputs().stream()
                                .filter(transactionOutput -> transactionOutput.isMine(bsqWalletService.getWallet()))
                                .map(transactionOutput -> BurningManService.GENESIS_OUTPUT_PREFIX + transactionOutput.getIndex())
                                .collect(Collectors.toSet())
                        )
                );
        return myGenesisOutputNames;
    }

    public Set<String> getMyCompensationRequestNames() {
        // Can be empty, so we compare with null and reset to null at new block
        if (myCompensationRequestNames != null) {
            return myCompensationRequestNames;
        }
        myCompensationRequestNames = myProposalListService.getList().stream()
                .filter(proposal -> proposal instanceof CompensationProposal)
                .map(Proposal::getName)
                .collect(Collectors.toSet());
        return myCompensationRequestNames;
    }

    public Map<String, BurningManCandidate> getBurningManCandidatesByName() {
        // Cached value is only used for currentChainHeight
        if (!burningManCandidatesByName.isEmpty()) {
            return burningManCandidatesByName;
        }

        burningManCandidatesByName.putAll(burningManService.getBurningManCandidatesByName(currentChainHeight));
        return burningManCandidatesByName;
    }

    public LegacyBurningMan getLegacyBurningManForDPT() {
        if (legacyBurningManDPT.isPresent()) {
            return legacyBurningManDPT.get();
        }

        // We do not add the legacy burningman to the list but keep it as class field only to avoid that it
        // interferes with usage of the burningManCandidatesByName map.
        LegacyBurningMan legacyBurningManDPT = getLegacyBurningMan(burningManService.getLegacyBurningManAddress(currentChainHeight), OP_RETURN_DATA_LEGACY_BM_DPT);
        this.legacyBurningManDPT = Optional.of(legacyBurningManDPT);
        return legacyBurningManDPT;
    }

    public LegacyBurningMan getLegacyBurningManForBtcFees() {
        if (legacyBurningManBtcFees.isPresent()) {
            return legacyBurningManBtcFees.get();
        }

        // We do not add the legacy burningman to the list but keep it as class field only to avoid that it
        // interferes with usage of the burningManCandidatesByName map.
        LegacyBurningMan legacyBurningManBtcFees = getLegacyBurningMan(LEGACY_BURNING_MAN_BTC_FEES_ADDRESS, OP_RETURN_DATA_LEGACY_BM_FEES);

        this.legacyBurningManBtcFees = Optional.of(legacyBurningManBtcFees);
        return legacyBurningManBtcFees;
    }

    private LegacyBurningMan getLegacyBurningMan(String address, Set<String> opReturnData) {
        LegacyBurningMan legacyBurningMan = new LegacyBurningMan(address);
        // The opReturnData used by legacy BM at burning BSQ.
        getProofOfBurnOpReturnTxOutputByHash().values().stream()
                .flatMap(txOutputs -> txOutputs.stream()
                        .filter(txOutput -> {
                            String opReturnAsHex = Hex.encode(txOutput.getOpReturnData());
                            return opReturnData.stream().anyMatch(e -> e.equals(opReturnAsHex));
                        }))
                .forEach(burnOutput -> {
                    int burnOutputHeight = burnOutput.getBlockHeight();
                    Optional<Tx> optionalTx = daoStateService.getTx(burnOutput.getTxId());
                    long burnOutputAmount = optionalTx.map(Tx::getBurntBsq).orElse(0L);
                    long date = optionalTx.map(BaseTx::getTime).orElse(0L);
                    int cycleIndex = cyclesInDaoStateService.getCycleIndexAtChainHeight(burnOutputHeight);
                    legacyBurningMan.addBurnOutputModel(new BurnOutputModel(burnOutputAmount,
                            burnOutputAmount,
                            burnOutputHeight,
                            burnOutput.getTxId(),
                            date,
                            cycleIndex));
                });
        // Set remaining share if the sum of all capped shares does not reach 100%.
        double burnAmountShareOfOthers = getBurningManCandidatesByName().values().stream()
                .mapToDouble(BurningManCandidate::getCappedBurnAmountShare)
                .sum();
        legacyBurningMan.applyBurnAmountShare(1 - burnAmountShareOfOthers);
        return legacyBurningMan;
    }

    public Map<String, String> getBurningManNameByAddress() {
        if (!burningManNameByAddress.isEmpty()) {
            return burningManNameByAddress;
        }
        // clone to not alter source map. We do not store legacy BM in the source map.
        Map<String, BurningManCandidate> burningManCandidatesByName = new HashMap<>(getBurningManCandidatesByName());
        burningManCandidatesByName.put(LEGACY_BURNING_MAN_DPT_NAME, getLegacyBurningManForDPT());
        burningManCandidatesByName.put(LEGACY_BURNING_MAN_BTC_FEES_NAME, getLegacyBurningManForBtcFees());

        Map<String, Set<String>> receiverAddressesByBurningManName = new HashMap<>();
        burningManCandidatesByName.forEach((name, burningManCandidate) -> {
            receiverAddressesByBurningManName.putIfAbsent(name, new HashSet<>());
            receiverAddressesByBurningManName.get(name).addAll(burningManCandidate.getAllAddresses());
        });

        Map<String, String> map = new HashMap<>();
        receiverAddressesByBurningManName
                .forEach((name, addresses) -> addresses
                        .forEach(address -> map.putIfAbsent(address, name)));
        burningManNameByAddress.putAll(map);
        return burningManNameByAddress;
    }

    public long getTotalAmountOfBurnedBsq() {
        return getBurningManCandidatesByName().values().stream()
                .mapToLong(BurningManCandidate::getAccumulatedBurnAmount)
                .sum();
    }

    public String getGenesisTxId() {
        return daoStateService.getGenesisTxId();
    }

    private Map<P2PDataStorage.ByteArray, Set<TxOutput>> getProofOfBurnOpReturnTxOutputByHash() {
        if (!proofOfBurnOpReturnTxOutputByHash.isEmpty()) {
            return proofOfBurnOpReturnTxOutputByHash;
        }

        proofOfBurnOpReturnTxOutputByHash.putAll(burningManService.getProofOfBurnOpReturnTxOutputByHash(currentChainHeight));
        return proofOfBurnOpReturnTxOutputByHash;
    }

    private long getAccumulatedDecayedBurnedAmount() {
        if (accumulatedDecayedBurnedAmount == null) {
            Collection<BurningManCandidate> burningManCandidates = getBurningManCandidatesByName().values();
            accumulatedDecayedBurnedAmount = burnTargetService.getAccumulatedDecayedBurnedAmount(burningManCandidates, currentChainHeight);
        }
        return accumulatedDecayedBurnedAmount;
    }
}
