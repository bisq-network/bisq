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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.dao.burningman.BurningManAddressList;
import bisq.core.dao.burningman.BurningManAddressListService;
import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.governance.proposal.generic.GenericProposalValidator;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.trade.statistics.TradeStatistics3;

import bisq.common.app.Version;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;

import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.params.MainNetParams;

import java.nio.charset.StandardCharsets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Release audit of the bundled mainnet DAO resources. This checks the block history which is stored separately from
 * {@code DaoStateStore_BTC_MAINNET}; auditing only the state maps cannot identify which transaction spent a historical
 * lockup output. It is intentionally opt-in because it scans the complete bundled history.
 */
@EnabledIfSystemProperty(named = "bisq.runResourceAudits", matches = "true")
class BundledDaoStateAuditTest {
    private static final String DAO_STATE_RESOURCE = "/DaoStateStore_BTC_MAINNET";
    private static final String BLOCK_RESOURCE_DIR = "/BsqBlocks_BTC_MAINNET/";
    private static final int MAINNET_GENESIS_HEIGHT = 571_747;
    private static final int PREVIOUS_BOND_AUDIT_HEIGHT = 962_500;

    @Test
    void bundledBlockHistoryIsCompleteAndContainsOnlyCanonicalLockupSpends() throws IOException {
        protobuf.PersistableEnvelope daoStateEnvelope = readDelimitedEnvelope(DAO_STATE_RESOURCE);
        assertTrue(daoStateEnvelope.hasDaoStateStore(), "resource is not a DAO state store");
        List<protobuf.DaoStateHash> daoStateHashes = daoStateEnvelope.getDaoStateStore().getDaoStateHashList();
        protobuf.DaoState daoState = daoStateEnvelope.getDaoStateStore().getDaoState();
        assertTrue(!daoStateHashes.isEmpty(), "DAO state hash chain is empty");
        assertEquals(daoState.getChainHeight(), daoStateHashes.get(daoStateHashes.size() - 1).getHeight(),
                "DAO state and its hash chain end at different heights");
        Map<Integer, String> daoStateHashByHeight = new HashMap<>();
        daoStateHashes.forEach(hash -> daoStateHashByHeight.put(hash.getHeight(),
                HexFormat.of().formatHex(hash.getHash().toByteArray())));
        int checkpointCount = 0;
        int latestCheckpointHeight = 0;
        try (InputStream inputStream = BundledDaoStateAuditTest.class
                .getResourceAsStream("/dao/daoStateHash.checkpoints")) {
            assertNotNull(inputStream, "DAO state checkpoint resource not found");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] fields = trimmed.split(",", -1);
                    assertEquals(2, fields.length, "invalid DAO checkpoint line: " + line);
                    int height = Integer.parseInt(fields[0]);
                    assertEquals(daoStateHashByHeight.get(height), fields[1],
                            "DAO checkpoint does not match bundled state hash at " + height);
                    assertTrue(height > latestCheckpointHeight, "DAO checkpoints are not strictly ascending");
                    latestCheckpointHeight = height;
                    checkpointCount++;
                }
            }
        }
        assertTrue(checkpointCount > 0, "DAO checkpoint resource is empty");

        Set<String> roleProposalTxIds = new HashSet<>();
        int evaluatedRoleProposals = 0;
        int acceptedRoleProposals = 0;
        int roleProposalsWithMismatchedTerms = 0;
        for (protobuf.EvaluatedProposal evaluatedProposal : daoState.getEvaluatedProposalListList()) {
            protobuf.Proposal proposal = evaluatedProposal.getProposalVoteResult().getProposal();
            if (!proposal.hasRoleProposal()) {
                continue;
            }

            evaluatedRoleProposals++;
            if (evaluatedProposal.getIsAccepted()) {
                acceptedRoleProposals++;
            }
            roleProposalTxIds.add(proposal.getTxId());
            protobuf.RoleProposal roleProposal = proposal.getRoleProposal();
            BondedRoleType roleType = BondedRoleType.valueOf(roleProposal.getRole().getBondedRoleType());
            if (roleProposal.getRequiredBondUnit() != roleType.getRequiredBondUnit() ||
                    roleProposal.getUnlockTime() != roleType.getUnlockTimeInBlocks()) {
                roleProposalsWithMismatchedTerms++;
            }
        }

        Map<String, LockupRecord> lockupsByOutputKey = new HashMap<>();
        Map<String, String> spenderTxIdByLockupOutputKey = new HashMap<>();
        // Retaining every historical BTC output exhausts the test heap; unlock reconstruction needs only DAO spends.
        Map<String, Long> spentOutputValueByOutputKey = new HashMap<>();
        Map<String, Integer> roleProposalHeightByTxId = new HashMap<>();
        EnumMap<LockupReason, Integer> lockupsByReason = new EnumMap<>(LockupReason.class);
        int blockCount = 0;
        int txCount = 0;
        int unlockSpendCount = 0;
        int unlockSpendsAfterPreviousAudit = 0;
        int lockupsAfterPreviousAudit = 0;
        int roleLockupsAtLeastFiftyThousandBlocks = 0;
        int previousHeight = MAINNET_GENESIS_HEIGHT - 1;
        String previousHash = null;

        int firstBucket = MAINNET_GENESIS_HEIGHT / 1_000 + 1;
        int lastBucket = (daoState.getChainHeight() + 999) / 1_000;
        for (int bucket = firstBucket; bucket <= lastBucket; bucket++) {
            int first = bucket * 1_000 - 999;
            int last = bucket * 1_000;
            String resource = BLOCK_RESOURCE_DIR + "BsqBlocks_" + first + "-" + last;
            protobuf.PersistableEnvelope envelope = readDelimitedEnvelope(resource);
            assertTrue(envelope.hasBsqBlockStore(), resource + " is not a BSQ block store");

            for (protobuf.BaseBlock block : envelope.getBsqBlockStore().getBlocksList()) {
                assertEquals(previousHeight + 1, block.getHeight(), "non-contiguous bundled block history");
                if (previousHash != null) {
                    assertEquals(previousHash, block.getPreviousBlockHash(),
                            "previous-block hash mismatch at " + block.getHeight());
                }
                assertTrue(block.hasBlock(), "bundled block is not parsed at " + block.getHeight());

                blockCount++;
                previousHeight = block.getHeight();
                previousHash = block.getHash();
                for (protobuf.BaseTx tx : block.getBlock().getTxsList()) {
                    txCount++;
                    assertEquals(block.getHeight(), tx.getBlockHeight(), "transaction height mismatch: " + tx.getId());
                    assertEquals(block.getHash(), tx.getBlockHash(), "transaction block hash mismatch: " + tx.getId());
                    assertTrue(tx.hasTx(), "bundled DAO transaction is not parsed: " + tx.getId());

                    if (roleProposalTxIds.contains(tx.getId())) {
                        roleProposalHeightByTxId.put(tx.getId(), tx.getBlockHeight());
                    }

                    List<SpentLockup> spentLockups = tx.getTxInputsList().stream()
                            .map(input -> outputKey(input.getConnectedTxOutputTxId(),
                                    input.getConnectedTxOutputIndex()))
                            .filter(lockupsByOutputKey::containsKey)
                            .map(outputKey -> new SpentLockup(outputKey, lockupsByOutputKey.get(outputKey)))
                            .toList();
                    if (!spentLockups.isEmpty()) {
                        assertEquals(1, spentLockups.size(),
                                "transaction spends more than one lockup output: " + tx.getId());
                        SpentLockup spentLockup = spentLockups.get(0);
                        assertCanonicalUnlock(tx, spentLockup, spentOutputValueByOutputKey,
                                daoState.getSpentInfoMapMap());
                        assertFalse(spenderTxIdByLockupOutputKey.containsKey(spentLockup.outputKey()),
                                "lockup output was spent more than once: " + spentLockup.outputKey());
                        spenderTxIdByLockupOutputKey.put(spentLockup.outputKey(), tx.getId());
                        unlockSpendCount++;
                        if (tx.getBlockHeight() > PREVIOUS_BOND_AUDIT_HEIGHT) {
                            unlockSpendsAfterPreviousAudit++;
                        }
                    }

                    List<protobuf.BaseTxOutput> outputs = tx.getTx().getTxOutputsList();
                    for (protobuf.BaseTxOutput output : outputs) {
                        assertEquals(tx.getId(), output.getTxId(), "output transaction id mismatch");
                        assertEquals(block.getHeight(), output.getBlockHeight(), "output block height mismatch");
                        String outputKey = outputKey(output.getTxId(), output.getIndex());
                        if (daoState.getSpentInfoMapMap().containsKey(outputKey)) {
                            assertFalse(spentOutputValueByOutputKey.containsKey(outputKey),
                                    "duplicate spent transaction output key: " + outputKey);
                            spentOutputValueByOutputKey.put(outputKey, output.getValue());
                        }
                    }

                    for (protobuf.BaseTxOutput output : outputs) {
                        if (!output.hasTxOutput() ||
                                output.getTxOutput().getTxOutputType() != protobuf.TxOutputType.LOCKUP_OUTPUT) {
                            continue;
                        }

                        protobuf.BaseTxOutput opReturnOutput = outputs.stream()
                                .filter(candidate -> candidate.hasTxOutput() && candidate.getTxOutput()
                                        .getTxOutputType() == protobuf.TxOutputType.LOCKUP_OP_RETURN_OUTPUT)
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("lockup has no parsed OP_RETURN: " + tx.getId()));
                        byte[] opReturnData = opReturnOutput.getOpReturnData().toByteArray();
                        assertTrue(BondConsensus.hasOpReturnDataValidLength(opReturnData),
                                "invalid lockup OP_RETURN length: " + tx.getId());
                        LockupReason reason = BondConsensus.getLockupReason(opReturnData)
                                .orElseThrow(() -> new AssertionError("unknown lockup reason: " + tx.getId()));
                        int lockTime = BondConsensus.getLockTime(opReturnData);
                        assertEquals(lockTime, output.getTxOutput().getLockTime(),
                                "lock time differs between lockup outputs: " + tx.getId());

                        String outputKey = outputKey(tx.getId(), output.getIndex());
                        assertFalse(lockupsByOutputKey.containsKey(outputKey), "duplicate lockup output key: " + outputKey);
                        lockupsByOutputKey.put(outputKey,
                                new LockupRecord(tx.getBlockHeight(), output.getValue(), reason, lockTime));
                        lockupsByReason.merge(reason, 1, Integer::sum);
                        if (tx.getBlockHeight() > PREVIOUS_BOND_AUDIT_HEIGHT) {
                            lockupsAfterPreviousAudit++;
                        }
                        if (reason == LockupReason.BONDED_ROLE && lockTime >= 50_000) {
                            roleLockupsAtLeastFiftyThousandBlocks++;
                        }
                    }
                }
            }
        }

        assertEquals(daoState.getChainHeight(), previousHeight,
                "bundled block history and DAO state end at different heights");
        assertEquals(roleProposalTxIds, roleProposalHeightByTxId.keySet(),
                "not every evaluated role proposal transaction exists in bundled blocks");
        assertEquals(0, roleProposalsWithMismatchedTerms,
                "historical role proposal terms differ from the current role type constants");

        int unspentLockups = 0;
        int spentLockups = 0;
        for (Map.Entry<String, LockupRecord> entry : lockupsByOutputKey.entrySet()) {
            String outputKey = entry.getKey();
            if (daoState.getUnspentTxOutputMapMap().containsKey(outputKey)) {
                unspentLockups++;
                assertFalse(spenderTxIdByLockupOutputKey.containsKey(outputKey),
                        "block history and DAO UTXO map disagree for " + outputKey);
            } else {
                protobuf.SpentInfo spentInfo = daoState.getSpentInfoMapMap().get(outputKey);
                assertNotNull(spentInfo, "lockup is absent from both state maps: " + outputKey);
                assertEquals(spenderTxIdByLockupOutputKey.get(outputKey), spentInfo.getTxId(),
                        "block history and DAO spent-info map disagree for " + outputKey);
                spentLockups++;
            }
        }
        assertEquals(spentLockups, unlockSpendCount);

        long roleProposalsAfterPreviousAudit = roleProposalHeightByTxId.values().stream()
                .filter(height -> height > PREVIOUS_BOND_AUDIT_HEIGHT)
                .count();
        long zeroValueLockups = lockupsByOutputKey.values().stream().filter(lockup -> lockup.value == 0).count();
        System.out.println("Bundled mainnet DAO/block resource audit"
                + "\n  chainHeight=" + daoState.getChainHeight()
                + "\n  DAO state hashes=" + daoStateHashes.size()
                + "\n  matching DAO checkpoints=" + checkpointCount
                + " (latest height=" + latestCheckpointHeight + ")"
                + "\n  blocks=" + blockCount
                + "\n  DAO transactions=" + txCount
                + "\n  lockups=" + lockupsByOutputKey.size() + " " + lockupsByReason
                + "\n  unspent lockups=" + unspentLockups
                + "\n  lockups spent by canonical UNLOCK=" + spentLockups
                + "\n  non-canonical lockup spends=" + 0
                + "\n  zero-value lockups=" + zeroValueLockups
                + "\n  BONDED_ROLE lockups with lock time >= 50000=" + roleLockupsAtLeastFiftyThousandBlocks
                + "\n  evaluated role proposals=" + evaluatedRoleProposals
                + " (accepted=" + acceptedRoleProposals + ")"
                + "\n  role proposals with mismatched current terms=" + roleProposalsWithMismatchedTerms
                + "\n  since height " + PREVIOUS_BOND_AUDIT_HEIGHT + ": lockups=" + lockupsAfterPreviousAudit
                + ", unlock spends=" + unlockSpendsAfterPreviousAudit
                + ", evaluated role proposals=" + roleProposalsAfterPreviousAudit);
    }

    @Test
    void refreshedBundledStoresAreReadableAndInternallyNonEmpty() throws IOException {
        protobuf.PersistableEnvelope accountAge =
                readDelimitedEnvelope("/AccountAgeWitnessStore_1.10.5_BTC_MAINNET");
        protobuf.PersistableEnvelope signedWitness = readDelimitedEnvelope("/SignedWitnessStore_BTC_MAINNET");
        protobuf.PersistableEnvelope tradeStatistics =
                readDelimitedEnvelope("/TradeStatistics3Store_1.10.5_BTC_MAINNET");
        protobuf.PersistableEnvelope blindVotes = readDelimitedEnvelope("/BlindVoteStore_BTC_MAINNET");
        protobuf.PersistableEnvelope proposals = readDelimitedEnvelope("/ProposalStore_BTC_MAINNET");
        protobuf.PersistableEnvelope tempProposals = readDelimitedEnvelope("/TempProposalStore_BTC_MAINNET");
        protobuf.PersistableEnvelope accounting =
                readDelimitedEnvelope("/BurningManAccountingStore_v3_BTC_MAINNET");

        assertTrue(accountAge.hasAccountAgeWitnessStore());
        assertTrue(signedWitness.hasSignedWitnessStore());
        assertTrue(tradeStatistics.hasTradeStatistics3Store());
        assertTrue(blindVotes.hasBlindVoteStore());
        assertTrue(proposals.hasProposalStore());
        assertTrue(tempProposals.hasTempProposalStore());
        assertTrue(accounting.hasBurningManAccountingStore());

        int accountAgeCount = accountAge.getAccountAgeWitnessStore().getItemsCount();
        int signedWitnessCount = signedWitness.getSignedWitnessStore().getItemsCount();
        int tradeStatisticsCount = tradeStatistics.getTradeStatistics3Store().getItemsCount();
        int blindVoteCount = blindVotes.getBlindVoteStore().getItemsCount();
        int proposalCount = proposals.getProposalStore().getItemsCount();
        int tempProposalCount = tempProposals.getTempProposalStore().getItemsCount();
        List<protobuf.AccountingBlock> accountingBlocks = accounting.getBurningManAccountingStore().getBlocksList();

        assertTrue(accountAgeCount > 0);
        assertTrue(signedWitnessCount > 0);
        assertTrue(tradeStatisticsCount > 0);
        assertTrue(blindVoteCount > 0);
        assertTrue(proposalCount > 0);
        assertTrue(tempProposalCount > 0);
        assertTrue(!accountingBlocks.isEmpty());

        Map<ByteString, Long> accountAgeDateByHash = new HashMap<>();
        int accountAgeStoreCount = 0;
        int accountAgeRecordsAcrossStores = 0;
        for (String version : Version.HISTORICAL_RESOURCE_FILE_VERSION_TAGS) {
            String resource = "/AccountAgeWitnessStore_" + version + "_BTC_MAINNET";
            if (BundledDaoStateAuditTest.class.getResource(resource) == null) {
                continue;
            }
            protobuf.PersistableEnvelope historicalAccountAge = readDelimitedEnvelope(resource);
            assertTrue(historicalAccountAge.hasAccountAgeWitnessStore(),
                    resource + " is not an account-age witness store");
            accountAgeStoreCount++;
            accountAgeRecordsAcrossStores += historicalAccountAge.getAccountAgeWitnessStore().getItemsCount();
            historicalAccountAge.getAccountAgeWitnessStore().getItemsList().forEach(witness -> {
                assertEquals(20, witness.getHash().size(), "invalid account-age witness hash length");
                assertTrue(witness.getDate() > 0, "invalid account-age witness date");
                AccountAgeWitness.fromProto(witness);
                Long previousDate = accountAgeDateByHash.putIfAbsent(witness.getHash(), witness.getDate());
                assertTrue(previousDate == null || previousDate == witness.getDate(),
                        "account-age witness date differs across historical stores");
            });
        }
        assertTrue(accountAgeStoreCount > 0);
        assertEquals(accountAgeCount,
                accountAge.getAccountAgeWitnessStore().getItemsList().stream()
                        .map(protobuf.AccountAgeWitness::getHash)
                        .distinct()
                        .count(),
                "duplicate account-age witness hashes in the 1.10.5 store");
        Set<protobuf.SignedWitness> uniqueSignedWitnesses = new HashSet<>();
        Set<ByteString> signedAccountAgeHashes = new HashSet<>();
        signedWitness.getSignedWitnessStore().getItemsList().forEach(witness -> {
            assertEquals(20, witness.getAccountAgeWitnessHash().size(),
                    "invalid signed-witness account hash length");
            assertTrue(!witness.getWitnessOwnerPubKey().isEmpty(), "signed witness has no owner public key");
            assertTrue(witness.getDate() > 0, "invalid signed-witness date");
            SignedWitness.fromProto(witness);
            uniqueSignedWitnesses.add(witness);
            signedAccountAgeHashes.add(witness.getAccountAgeWitnessHash());
        });
        assertEquals(signedWitnessCount, uniqueSignedWitnesses.size(), "duplicate signed-witness records");
        Set<ByteString> signedAccountAgeHashesMissingFromAccountStores = new HashSet<>(signedAccountAgeHashes);
        signedAccountAgeHashesMissingFromAccountStores.removeAll(accountAgeDateByHash.keySet());

        Set<ByteString> blindVoteHashes = new HashSet<>();
        Set<String> blindVoteTxIds = new HashSet<>();
        blindVotes.getBlindVoteStore().getItemsList().forEach(payload -> {
            BlindVotePayload.fromProto(payload);
            assertTrue(blindVoteHashes.add(payload.getHash()), "duplicate blind-vote payload hash");
            assertTrue(blindVoteTxIds.add(payload.getBlindVote().getTxId()), "duplicate blind-vote transaction id");
        });
        tradeStatistics.getTradeStatistics3Store().getItemsList().forEach(TradeStatistics3::fromProto);

        int proposalRoleCount = 0;
        int proposalRoleTermMismatches = 0;
        int proposalsWithInvalidCommonDataFields = 0;
        Set<String> proposalTxIds = new HashSet<>();
        ProposalValidator commonProposalValidator = new GenericProposalValidator(null, null);
        for (protobuf.ProposalPayload payload : proposals.getProposalStore().getItemsList()) {
            ProposalPayload proposalPayload = ProposalPayload.fromProto(payload);
            if (!commonProposalValidator.areCommonDataFieldsValid(proposalPayload.getProposal())) {
                proposalsWithInvalidCommonDataFields++;
            }
            assertTrue(proposalTxIds.add(proposalPayload.getProposal().getTxId()),
                    "duplicate append-only proposal transaction id");
            protobuf.Proposal proposal = payload.getProposal();
            if (proposal.hasRoleProposal()) {
                proposalRoleCount++;
                if (!hasCurrentRoleTerms(proposal.getRoleProposal())) {
                    proposalRoleTermMismatches++;
                }
            }
        }
        assertEquals(0, proposalsWithInvalidCommonDataFields,
                "append-only proposal fails common data-field validation");
        int tempProposalRoleCount = 0;
        int tempProposalRoleTermMismatches = 0;
        for (protobuf.ProtectedStorageEntry entry : tempProposals.getTempProposalStore().getItemsList()) {
            assertTrue(entry.getStoragePayload().hasTempProposalPayload(),
                    "TempProposalStore contains a different protected payload type");
            protobuf.Proposal proposal = entry.getStoragePayload().getTempProposalPayload().getProposal();
            if (proposal.hasRoleProposal()) {
                tempProposalRoleCount++;
                if (!hasCurrentRoleTerms(proposal.getRoleProposal())) {
                    tempProposalRoleTermMismatches++;
                }
            }
        }
        assertEquals(0, proposalRoleTermMismatches, "append-only role proposal terms differ from current constants");
        assertEquals(0, tempProposalRoleTermMismatches, "temporary role proposal terms differ from current constants");

        int firstAccountingHeight = accountingBlocks.get(0).getHeight();
        int lastAccountingHeight = accountingBlocks.get(accountingBlocks.size() - 1).getHeight();
        for (int index = 0; index < accountingBlocks.size(); index++) {
            assertEquals(firstAccountingHeight + index, accountingBlocks.get(index).getHeight(),
                    "non-contiguous Burning Man accounting store");
        }

        StoredBlock latestWalletCheckpoint;
        int walletCheckpointCount;
        try (InputStream inputStream = BundledDaoStateAuditTest.class
                .getResourceAsStream("/wallet/checkpoints.txt")) {
            assertNotNull(inputStream, "wallet checkpoint resource not found");
            CheckpointManager checkpointManager = new CheckpointManager(MainNetParams.get(), inputStream);
            walletCheckpointCount = checkpointManager.numCheckpoints();
            latestWalletCheckpoint = checkpointManager.getCheckpointBefore(Long.MAX_VALUE);
        }
        assertTrue(walletCheckpointCount > 0);
        assertNotNull(latestWalletCheckpoint);

        BurningManAddressListService addressListService = new BurningManAddressListService();
        BurningManAddressList latestAddressList =
                addressListService.getAddressList(addressListService.getLatestVersion());

        System.out.println("Refreshed bundled P2P store audit"
                + "\n  account-age witnesses in 1.10.5 store=" + accountAgeCount
                + "\n  account-age historical stores=" + accountAgeStoreCount
                + " (records=" + accountAgeRecordsAcrossStores + ", unique hashes=" + accountAgeDateByHash.size() + ")"
                + "\n  signed witnesses=" + signedWitnessCount
                + " (account hashes=" + signedAccountAgeHashes.size() + ")"
                + "\n  signed-witness account hashes absent from all bundled account-age stores="
                + signedAccountAgeHashesMissingFromAccountStores.size()
                + "\n  trade statistics=" + tradeStatisticsCount
                + "\n  blind votes=" + blindVoteCount
                + "\n  append-only proposals=" + proposalCount
                + " (role proposals=" + proposalRoleCount + ", role-term mismatches="
                + proposalRoleTermMismatches + ", common-field failures="
                + proposalsWithInvalidCommonDataFields + ", duplicate transaction ids="
                + (proposalCount - proposalTxIds.size()) + ")"
                + "\n  temporary proposals=" + tempProposalCount
                + " (role proposals=" + tempProposalRoleCount + ", role-term mismatches="
                + tempProposalRoleTermMismatches + ")"
                + "\n  Burning Man accounting blocks=" + accountingBlocks.size()
                + " (" + firstAccountingHeight + ".." + lastAccountingHeight + ")"
                + "\n  bitcoinj wallet checkpoints=" + walletCheckpointCount
                + " (latest height=" + latestWalletCheckpoint.getHeight() + ")"
                + "\n  Burning Man address lists=" + addressListService.getSupportedVersions()
                + " (latest chain height=" + latestAddressList.getChainHeight()
                + ", selection height=" + latestAddressList.getBurningManSelectionHeight() + ")");
    }

    private static String outputKey(String txId, int index) {
        return txId + ':' + index;
    }

    private static void assertCanonicalUnlock(protobuf.BaseTx tx,
                                              SpentLockup spentLockup,
                                              Map<String, Long> spentOutputValueByOutputKey,
                                              Map<String, protobuf.SpentInfo> spentInfoByOutputKey) {
        String context = "lockup=" + spentLockup.outputKey() + ", spender=" + tx.getId();
        List<ConnectedBsqInput> connectedBsqInputs = new ArrayList<>();
        List<protobuf.TxInput> inputs = tx.getTxInputsList();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            protobuf.TxInput input = inputs.get(inputIndex);
            String connectedOutputKey = outputKey(input.getConnectedTxOutputTxId(),
                    input.getConnectedTxOutputIndex());
            protobuf.SpentInfo spentInfo = spentInfoByOutputKey.get(connectedOutputKey);
            if (spentInfo == null) {
                continue;
            }

            assertEquals(tx.getId(), spentInfo.getTxId(),
                    "connected output has a different DAO spender: " + context);
            assertEquals(inputIndex, spentInfo.getInputIndex(),
                    "DAO spent-info input index mismatch: " + context);
            Long connectedOutputValue = spentOutputValueByOutputKey.get(connectedOutputKey);
            assertNotNull(connectedOutputValue,
                    "BSQ input does not resolve to an earlier bundled output: " + context);
            connectedBsqInputs.add(new ConnectedBsqInput(connectedOutputKey, connectedOutputValue));
        }

        assertEquals(1, connectedBsqInputs.size(),
                "canonical unlock must have exactly one BSQ input: " + context);
        ConnectedBsqInput connectedBsqInput = connectedBsqInputs.get(0);
        assertEquals(spentLockup.outputKey(), connectedBsqInput.outputKey(),
                "canonical unlock's only BSQ input is not the lockup output: " + context);
        long availableBsqInputValue = connectedBsqInputs.stream()
                .mapToLong(ConnectedBsqInput::value)
                .reduce(0L, Math::addExact);
        assertEquals(spentLockup.lockup().value(), availableBsqInputValue,
                "canonical unlock input value differs from the lockup value: " + context);

        List<protobuf.BaseTxOutput> outputs = tx.getTx().getTxOutputsList();
        assertFalse(outputs.isEmpty(), "canonical unlock has no outputs: " + context);
        for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
            protobuf.BaseTxOutput output = outputs.get(outputIndex);
            assertEquals(outputIndex, output.getIndex(), "unlock output index/order mismatch: " + context);
            assertTrue(output.hasTxOutput(), "unlock contains an unparsed output: " + context);
            assertTrue(output.getOpReturnData().isEmpty(), "canonical unlock contains OP_RETURN: " + context);
            protobuf.TxOutputType expectedType = outputIndex == 0
                    ? protobuf.TxOutputType.UNLOCK_OUTPUT
                    : protobuf.TxOutputType.BTC_OUTPUT;
            assertEquals(expectedType, output.getTxOutput().getTxOutputType(),
                    "canonical unlock contains an unexpected output type: " + context);
        }

        protobuf.BaseTxOutput unlockOutput = outputs.get(0);
        assertEquals(spentLockup.lockup().value(), unlockOutput.getValue(),
                "unlock output value differs from the lockup value: " + context);
        assertEquals(availableBsqInputValue, unlockOutput.getValue(),
                "unlock output does not carry the whole available BSQ input value: " + context);
        assertEquals(0L, tx.getTx().getBurntBsq(), "canonical unlock burns BSQ: " + context);
        assertEquals(protobuf.TxType.UNLOCK, tx.getTx().getTxType(),
                "canonical unlock shape has a different parsed transaction type: " + context);
    }

    private static boolean hasCurrentRoleTerms(protobuf.RoleProposal roleProposal) {
        BondedRoleType roleType = BondedRoleType.valueOf(roleProposal.getRole().getBondedRoleType());
        return roleProposal.getRequiredBondUnit() == roleType.getRequiredBondUnit() &&
                roleProposal.getUnlockTime() == roleType.getUnlockTimeInBlocks();
    }

    private static protobuf.PersistableEnvelope readDelimitedEnvelope(String resource) throws IOException {
        try (InputStream inputStream = BundledDaoStateAuditTest.class.getResourceAsStream(resource)) {
            assertNotNull(inputStream, "resource not found: " + resource);
            CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
            codedInputStream.setSizeLimit(Integer.MAX_VALUE);
            int size = codedInputStream.readRawVarint32();
            int oldLimit = codedInputStream.pushLimit(size);
            protobuf.PersistableEnvelope envelope = protobuf.PersistableEnvelope.parser().parseFrom(codedInputStream);
            codedInputStream.popLimit(oldLimit);
            return envelope;
        }
    }

    private record LockupRecord(int blockHeight, long value, LockupReason reason, int lockTime) {
    }

    private record SpentLockup(String outputKey, LockupRecord lockup) {
    }

    private record ConnectedBsqInput(String outputKey, long value) {
    }
}
