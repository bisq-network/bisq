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

package bisq.core.dao.governance.bond.role;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoHardFork;
import bisq.core.dao.SignVerifyService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BondedRolesRepositoryTest {
    private static final long BONDED_ROLE_FACTOR = 1_000;
    private static final int PROPOSAL_HEIGHT = 800_000;
    private static final int LOCKUP_HEIGHT = 900_000;
    private static final String PROPOSAL_TX_ID = "proposalTx";
    private static final String LOCKUP_TX_ID =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String NAME = "alice";
    private static final String LINK = "https://bisq.network/roles/81";
    private static final BondedRoleType ROLE_TYPE = BondedRoleType.NETLAYER_MAINTAINER;
    private static final String PROFILE_ID = "profileId";

    private final ECKey proposalKey = new ECKey();
    private final ECKey lockupKey = new ECKey();
    private final ECKey attackerKey = new ECKey();
    private final Map<String, Tx> txById = new HashMap<>();
    private final Set<TxOutput> lockupTxOutputs = new HashSet<>();
    private final Set<TxOutputKey> unspentKeys = new HashSet<>();
    private final List<EvaluatedProposal> evaluatedProposals = new ArrayList<>();
    private final List<Transaction> myWalletTransactions = new ArrayList<>();

    private DaoStateService daoStateService;
    private BsqWalletService bsqWalletService;
    private TestableBondedRolesRepository repository;
    private Role role;

    private static class TestableBondedRolesRepository extends BondedRolesRepository {
        TestableBondedRolesRepository(DaoStateService daoStateService,
                                      BsqWalletService bsqWalletService,
                                      SignVerifyService signVerifyService) {
            super(daoStateService, bsqWalletService, signVerifyService);
        }

        void doUpdate() {
            update();
        }
    }

    @BeforeEach
    public void setup() {
        daoStateService = mock(DaoStateService.class);
        bsqWalletService = mock(BsqWalletService.class);
        when(bsqWalletService.getClonedWalletTransactions()).thenReturn(myWalletTransactions);
        when(bsqWalletService.getPendingWalletTransactionsStream())
                .thenAnswer(invocation -> java.util.stream.Stream.empty());
        repository = new TestableBondedRolesRepository(daoStateService, bsqWalletService,
                new SignVerifyService(mock(BsqWalletService.class), daoStateService));

        when(daoStateService.getEvaluatedProposalList()).thenReturn(evaluatedProposals);
        when(daoStateService.getLockupTxOutputs()).thenReturn(lockupTxOutputs);
        when(daoStateService.getTx(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(txById.get(invocation.<String>getArgument(0))));
        when(daoStateService.isUnspent(any())).thenAnswer(invocation -> unspentKeys.contains(invocation.getArgument(0)));
        when(daoStateService.getParamValueAsCoin(eq(Param.BONDED_ROLE_FACTOR), anyInt()))
                .thenReturn(Coin.valueOf(BONDED_ROLE_FACTOR));

        role = new Role(NAME, LINK, ROLE_TYPE);
        addAcceptedProposal(role, PROPOSAL_TX_ID, PROPOSAL_HEIGHT, proposalKey);
    }

    @Test
    public void proposalKeyAuthorizesTheExactConfirmedLockup() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);

        verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID));
    }

    @Test
    public void unsupportedRegistrationProtocolVersionIsRejected() {
        assertRejectedWith("Unsupported bonded-role registration protocol version: 2",
                () -> repository.verifyBondedRole(new BondedRoleRegistration(
                        2, NAME, ROLE_TYPE.name(), PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID, "signature")));
    }

    @Test
    public void legacyRegistrationUsesThePreCutoffLockupKey() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);

        repository.verifyBondedRole(BondedRoleRegistration.legacy(
                NAME, ROLE_TYPE.name(), PROFILE_ID, lockupKey.signMessage(PROFILE_ID)));
        assertRejectedWith("No confirmed pre-cutoff role lockup with a valid legacy signature",
                () -> repository.verifyBondedRole(BondedRoleRegistration.legacy(
                        NAME, ROLE_TYPE.name(), PROFILE_ID, proposalKey.signMessage(PROFILE_ID))));
    }

    @Test
    public void legacyRegistrationIsRejectedFromTheCutoffHeight() throws IOException {
        int cutoffHeight = DaoHardFork.getHardFork3ActivationHeight();
        addConfirmedBond(role, LOCKUP_TX_ID, cutoffHeight, lockupKey);

        assertRejectedWith("No confirmed pre-cutoff role lockup with a valid legacy signature",
                () -> repository.verifyBondedRole(BondedRoleRegistration.legacy(
                        NAME, ROLE_TYPE.name(), PROFILE_ID, lockupKey.signMessage(PROFILE_ID))));
        verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID));
    }

    @Test
    public void legacyRegistrationRejectsTransactionBindings() {
        assertRejectedWith("must not contain transaction bindings",
                () -> repository.verifyBondedRole(new BondedRoleRegistration(
                        BondedRoleRegistration.LEGACY_PROTOCOL_VERSION,
                        NAME,
                        ROLE_TYPE.name(),
                        PROPOSAL_TX_ID,
                        LOCKUP_TX_ID,
                        PROFILE_ID,
                        "signature")));
    }

    @Test
    public void confiscatedPreCutoffLockupCannotAuthorizeALegacyRegistration() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);
        BondedRoleRegistration registration = BondedRoleRegistration.legacy(
                NAME, ROLE_TYPE.name(), PROFILE_ID, lockupKey.signMessage(PROFILE_ID));
        repository.verifyBondedRole(registration);

        when(daoStateService.isConfiscatedLockupTxOutput(LOCKUP_TX_ID)).thenReturn(true);
        repository.doUpdate();

        assertRejectedWith("No confirmed pre-cutoff role lockup with a valid legacy signature",
                () -> repository.verifyBondedRole(registration));
    }

    @Test
    public void lockupPublisherCannotAuthorizeTheRole() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);

        assertRejectedWith("Invalid signature", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(lockupKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void registrationSignatureCannotBeMovedToAnotherLockup() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        addLockup("otherLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        repository.doUpdate();

        String signature = signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID);
        assertRejectedWith("Invalid signature", () -> verify(PROPOSAL_TX_ID, "otherLockupTx", signature));
        verify(PROPOSAL_TX_ID, "otherLockupTx",
                signatureFrom(proposalKey, PROPOSAL_TX_ID, "otherLockupTx", PROFILE_ID));
    }

    @Test
    public void registrationSignatureCannotBeMovedToAnotherProfile() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);

        String signature = signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID);
        assertRejectedWith("Invalid signature", () -> repository.verifyBondedRole(
                BondedRoleRegistration.current(
                        NAME, ROLE_TYPE.name(), PROPOSAL_TX_ID, LOCKUP_TX_ID, "otherProfile", signature)));
    }

    @Test
    public void exactProposalIdDisambiguatesRolesWithTheSameNameAndType() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        Role secondRole = new Role(NAME, "https://bisq.network/roles/82", ROLE_TYPE);
        ECKey secondProposalKey = new ECKey();
        addAcceptedProposal(secondRole, "secondProposalTx", PROPOSAL_HEIGHT + 1, secondProposalKey);
        addLockup("secondLockupTx", secondRole, LOCKUP_HEIGHT + 1, new ECKey());
        repository.doUpdate();

        verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID));
        verify("secondProposalTx", "secondLockupTx",
                signatureFrom(secondProposalKey, "secondProposalTx", "secondLockupTx", PROFILE_ID));
        assertRejectedWith("Invalid signature", () -> verify("secondProposalTx", "secondLockupTx",
                signatureFrom(proposalKey, "secondProposalTx", "secondLockupTx", PROFILE_ID)));
    }

    @Test
    public void duplicateLockupsRemainIndependentAndDoNotDisableTheRole() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        addLockup("attackerLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        repository.doUpdate();

        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond(LOCKUP_TX_ID).getBondState());
        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond("attackerLockupTx").getBondState());
        verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID));
        assertRejectedWith("Invalid signature", () -> verify(PROPOSAL_TX_ID, "attackerLockupTx",
                signatureFrom(attackerKey, PROPOSAL_TX_ID, "attackerLockupTx", PROFILE_ID)));
    }

    @Test
    public void confirmedLockupDoesNotPreventCreatingAnotherLockup() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);

        assertTrue(repository.canCreateNewLockup(role));
    }

    @Test
    public void pendingLocalLockupTemporarilyPreventsCreatingAnotherLockup() throws IOException {
        Transaction pendingLockup = new Transaction(MainNetParams.get());
        pendingLockup.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(
                BondConsensus.getLockupOpReturnData(
                        role.getBondedRoleType().getUnlockTimeInBlocks(),
                        LockupReason.BONDED_ROLE,
                        role.getHash())));
        when(bsqWalletService.getPendingWalletTransactionsStream())
                .thenAnswer(invocation -> java.util.stream.Stream.of(pendingLockup));

        repository.doUpdate();

        assertEquals(BondState.LOCKUP_TX_PENDING, findReadyBond(role).getBondState());
        assertFalse(repository.canCreateNewLockup(role));
        assertRejectedWith("No valid role lockup", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID, "signature"));
    }

    @Test
    public void pendingUnlockChangesOnlyItsExactLockup() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        addLockup("otherLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);

        Transaction lockupTransaction = mock(Transaction.class);
        when(lockupTransaction.getTxId()).thenReturn(Sha256Hash.wrap(LOCKUP_TX_ID));
        TransactionOutput connectedOutput = mock(TransactionOutput.class);
        when(connectedOutput.getIndex()).thenReturn(0);
        when(connectedOutput.getParentTransaction()).thenReturn(lockupTransaction);
        TransactionInput input = mock(TransactionInput.class);
        when(input.getConnectedOutput()).thenReturn(connectedOutput);
        Transaction pendingUnlock = mock(Transaction.class);
        when(pendingUnlock.getInputs()).thenReturn(List.of(input));
        when(bsqWalletService.getPendingWalletTransactionsStream())
                .thenAnswer(invocation -> java.util.stream.Stream.of(pendingUnlock));

        repository.doUpdate();

        assertEquals(BondState.UNLOCK_TX_PENDING, findBond(LOCKUP_TX_ID).getBondState());
        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond("otherLockupTx").getBondState());
        assertRejectedWith("not confirmed and unspent", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void unlockingDuplicateDoesNotChangeTheOriginalLockup() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        TxOutput attackerOutput = addLockup("attackerLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        unspentKeys.remove(attackerOutput.getKey());
        Tx unlockTx = mockTx("attackerUnlockTx", attackerKey, LOCKUP_HEIGHT + 2);
        when(unlockTx.getTxType()).thenReturn(TxType.UNLOCK);
        when(daoStateService.getSpentInfo(attackerOutput))
                .thenReturn(Optional.of(new SpentInfo(LOCKUP_HEIGHT + 2, "attackerUnlockTx", 0)));
        when(daoStateService.isUnlockingAndUnspent("attackerUnlockTx")).thenReturn(true);
        repository.doUpdate();

        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond(LOCKUP_TX_ID).getBondState());
        assertEquals(BondState.UNLOCKING, findBond("attackerLockupTx").getBondState());
        verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID));
        assertRejectedWith("not confirmed and unspent", () -> verify(PROPOSAL_TX_ID, "attackerLockupTx",
                signatureFrom(proposalKey, PROPOSAL_TX_ID, "attackerLockupTx", PROFILE_ID)));
    }

    @Test
    public void proposalOwnerCanRegisterAnotherLockupAfterTheFirstStartsUnlocking() throws IOException {
        TxOutput firstOutput = addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        addLockup("replacementLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        unspentKeys.remove(firstOutput.getKey());
        Tx unlockTx = mockTx("unlockTx", lockupKey, LOCKUP_HEIGHT + 2);
        when(unlockTx.getTxType()).thenReturn(TxType.UNLOCK);
        when(daoStateService.getSpentInfo(firstOutput))
                .thenReturn(Optional.of(new SpentInfo(LOCKUP_HEIGHT + 2, "unlockTx", 0)));
        when(daoStateService.isUnlockingAndUnspent("unlockTx")).thenReturn(true);
        repository.doUpdate();

        assertEquals(BondState.UNLOCKING, findBond(LOCKUP_TX_ID).getBondState());
        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond("replacementLockupTx").getBondState());
        verify(PROPOSAL_TX_ID, "replacementLockupTx",
                signatureFrom(proposalKey, PROPOSAL_TX_ID, "replacementLockupTx", PROFILE_ID));
    }

    @Test
    public void confiscatedDuplicateDoesNotChangeTheOriginalLockup() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        addLockup("attackerLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        when(daoStateService.isConfiscatedLockupTxOutput("attackerLockupTx")).thenReturn(true);
        repository.doUpdate();

        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond(LOCKUP_TX_ID).getBondState());
        assertEquals(BondState.CONFISCATED, findBond("attackerLockupTx").getBondState());
        verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID));
    }

    @Test
    public void confiscatingTheRegisteredLockupInvalidatesOnlyThatRegistration() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);
        when(daoStateService.isConfiscatedLockupTxOutput(LOCKUP_TX_ID)).thenReturn(true);
        repository.doUpdate();

        assertEquals(BondState.CONFISCATED, findBond(LOCKUP_TX_ID).getBondState());
        assertRejectedWith("not confirmed and unspent", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void illegalSpendInvalidatesOnlyTheSpentLockup() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        TxOutput attackerOutput = addLockup("attackerLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        unspentKeys.remove(attackerOutput.getKey());
        Tx spendingTx = mockTx("spendingTx", attackerKey, LOCKUP_HEIGHT + 2);
        when(spendingTx.getTxType()).thenReturn(TxType.INVALID);
        when(daoStateService.getSpentInfo(attackerOutput))
                .thenReturn(Optional.of(new SpentInfo(LOCKUP_HEIGHT + 2, "spendingTx", 0)));
        repository.doUpdate();

        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findBond(LOCKUP_TX_ID).getBondState());
        assertEquals(BondState.ILLEGALLY_SPENT, findBond("attackerLockupTx").getBondState());
    }

    @Test
    public void unavailableSpenderFailsClosedForOnlyThatLockup() throws IOException {
        TxOutput lockupOutput = addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        unspentKeys.remove(lockupOutput.getKey());
        when(daoStateService.getSpentInfo(lockupOutput))
                .thenReturn(Optional.of(new SpentInfo(LOCKUP_HEIGHT + 1, "missingTx", 0)));
        repository.doUpdate();

        assertEquals(BondState.ILLEGALLY_SPENT, findBond(LOCKUP_TX_ID).getBondState());
    }

    @Test
    public void invalidCandidateRemainsVisibleButCannotAuthorize() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        when(txById.get(LOCKUP_TX_ID).getLockedAmount())
                .thenReturn(role.getBondedRoleType().getRequiredBondUnit() * BONDED_ROLE_FACTOR - 1);
        repository.doUpdate();

        assertEquals(BondState.READY_FOR_LOCKUP, findReadyBond(role).getBondState());
        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findAllRoleBond(LOCKUP_TX_ID).getBondState());
        assertRejectedWith("No valid role lockup", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void lockupBeforeOrInProposalBlockCannotAuthorize() throws IOException {
        addLockup(LOCKUP_TX_ID, role, PROPOSAL_HEIGHT, lockupKey);
        repository.doUpdate();

        assertEquals(BondState.READY_FOR_LOCKUP, findReadyBond(role).getBondState());
        assertRejectedWith("No valid role lockup", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void wrongLockupReasonIsIgnored() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey, LockupReason.REPUTATION);
        repository.doUpdate();

        assertEquals(BondState.READY_FOR_LOCKUP, findReadyBond(role).getBondState());
        assertTrue(repository.getAllRoleBonds().stream()
                .noneMatch(bond -> LOCKUP_TX_ID.equals(bond.getLockupTxId())));
    }

    @Test
    public void rejectedRoleLockupIsVisibleButCannotAuthorize() throws IOException {
        evaluatedProposals.clear();
        addRejectedProposal(role, PROPOSAL_TX_ID, PROPOSAL_HEIGHT, proposalKey);
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        repository.doUpdate();

        assertTrue(repository.getAcceptedBonds().isEmpty());
        assertEquals(BondState.LOCKUP_TX_CONFIRMED, findAllRoleBond(LOCKUP_TX_ID).getBondState());
        assertRejectedWith("No canonical accepted role proposal", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void laterAcceptedProposalWithCopiedUidCannotReplaceIdentity() throws IOException {
        Role forgedRole = Role.fromProto(role.toProtoMessage().toBuilder()
                .setLink("https://attacker.example")
                .build());
        addAcceptedProposal(forgedRole, "forgedProposalTx", PROPOSAL_HEIGHT + 1, attackerKey);
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        repository.doUpdate();

        assertEquals(Optional.of(PROPOSAL_TX_ID), repository.findVerificationTxId(role, LOCKUP_TX_ID));
        assertTrue(repository.getAcceptedBondedRoleProposal(forgedRole).isEmpty());
    }

    @Test
    public void sameBlockOldestProposalCandidatesMakeTheRoleUnusable() throws IOException {
        addAcceptedProposal(role, "otherProposalTx", PROPOSAL_HEIGHT, attackerKey);
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        repository.doUpdate();

        assertTrue(repository.getAcceptedBonds().isEmpty());
        assertRejectedWith("No canonical accepted role proposal", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    @Test
    public void onlyTheLockupWalletOwnerCanUnlockARow() throws IOException {
        addLockup(LOCKUP_TX_ID, role, LOCKUP_HEIGHT, lockupKey);
        addLockup("otherLockupTx", role, LOCKUP_HEIGHT + 1, attackerKey);
        addMyWalletTransaction(LOCKUP_TX_ID);
        repository.doUpdate();

        assertTrue(repository.isMyLockupTx(LOCKUP_TX_ID));
        assertFalse(repository.isMyLockupTx("otherLockupTx"));
    }

    @Test
    public void removedAcceptedRoleDoesNotRemainCached() throws IOException {
        addConfirmedBond(role, LOCKUP_TX_ID, LOCKUP_HEIGHT, lockupKey);
        evaluatedProposals.clear();
        repository.doUpdate();

        assertTrue(repository.getAcceptedBonds().isEmpty());
        assertRejectedWith("No canonical accepted role proposal", () -> verify(PROPOSAL_TX_ID, LOCKUP_TX_ID,
                signatureFrom(proposalKey, PROPOSAL_TX_ID, LOCKUP_TX_ID, PROFILE_ID)));
    }

    private BondedRole findBond(String lockupTxId) {
        return repository.getBonds().stream()
                .filter(bond -> lockupTxId.equals(bond.getLockupTxId()))
                .findFirst()
                .orElseThrow();
    }

    private BondedRole findReadyBond(Role role) {
        return repository.getBonds().stream()
                .filter(bond -> bond.getBondedAsset().equals(role))
                .filter(bond -> bond.getLockupTxId() == null)
                .findFirst()
                .orElseThrow();
    }

    private BondedRole findAllRoleBond(String lockupTxId) {
        return repository.getAllRoleBonds().stream()
                .filter(bond -> lockupTxId.equals(bond.getLockupTxId()))
                .findFirst()
                .orElseThrow();
    }

    private void verify(String proposalTxId, String lockupTxId, String signatureBase64) {
        repository.verifyBondedRole(BondedRoleRegistration.current(
                NAME, ROLE_TYPE.name(), proposalTxId, lockupTxId, PROFILE_ID, signatureBase64));
    }

    private String signatureFrom(ECKey key, String proposalTxId, String lockupTxId, String profileId) {
        return key.signMessage(repository.getRegistrationSignatureMessage(proposalTxId, lockupTxId, profileId));
    }

    private void assertRejectedWith(String expectedMessagePart, Executable executable) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(exception.getMessage().contains(expectedMessagePart),
                "Expected the failure reason to contain '" + expectedMessagePart + "' but got: " + exception.getMessage());
    }

    private void addConfirmedBond(Role role, String lockupTxId, int blockHeight, ECKey key) throws IOException {
        addLockup(lockupTxId, role, blockHeight, key);
        repository.doUpdate();
    }

    private void addAcceptedProposal(Role role, String txId, int blockHeight, ECKey key) {
        addProposal(role, txId, blockHeight, key, true);
    }

    private void addRejectedProposal(Role role, String txId, int blockHeight, ECKey key) {
        addProposal(role, txId, blockHeight, key, false);
    }

    private void addProposal(Role role, String txId, int blockHeight, ECKey key, boolean accepted) {
        RoleProposal roleProposal = (RoleProposal) new RoleProposal(role, new TreeMap<>()).cloneProposal(txId);
        evaluatedProposals.add(new EvaluatedProposal(accepted,
                new ProposalVoteResult(roleProposal, 0, 0, 0, 0, 0)));
        mockTx(txId, key, blockHeight);
    }

    private TxOutput addLockup(String txId, Role role, int blockHeight, ECKey key) throws IOException {
        return addLockup(txId, role, blockHeight, key, LockupReason.BONDED_ROLE);
    }

    private TxOutput addLockup(String txId,
                               Role role,
                               int blockHeight,
                               ECKey key,
                               LockupReason lockupReason) throws IOException {
        Tx lockupTx = mockTx(txId, key, blockHeight);
        when(lockupTx.getTxType()).thenReturn(TxType.LOCKUP);
        when(lockupTx.getLockedAmount())
                .thenReturn(role.getBondedRoleType().getRequiredBondUnit() * BONDED_ROLE_FACTOR);
        when(lockupTx.getLockTime()).thenReturn(role.getBondedRoleType().getUnlockTimeInBlocks());
        TxOutput opReturnOutput = mock(TxOutput.class);
        when(opReturnOutput.getOpReturnData())
                .thenReturn(BondConsensus.getLockupOpReturnData(1, lockupReason, role.getHash()));
        when(lockupTx.getLastTxOutput()).thenReturn(opReturnOutput);

        TxOutput lockupTxOutput = mock(TxOutput.class);
        TxOutputKey outputKey = new TxOutputKey(txId, 0);
        when(lockupTxOutput.getTxId()).thenReturn(txId);
        when(lockupTxOutput.getKey()).thenReturn(outputKey);
        lockupTxOutputs.add(lockupTxOutput);
        unspentKeys.add(outputKey);
        return lockupTxOutput;
    }

    private void addMyWalletTransaction(String txId) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getTxId()).thenReturn(Sha256Hash.wrap(txId));
        myWalletTransactions.add(transaction);
    }

    private Tx mockTx(String txId, ECKey key, int blockHeight) {
        Tx tx = mock(Tx.class);
        when(tx.getId()).thenReturn(txId);
        when(tx.getTxInputs()).thenReturn(ImmutableList.of(
                new TxInput("connectedTx", 0, Utilities.bytesAsHexString(key.getPubKey()))));
        when(tx.getBlockHeight()).thenReturn(blockHeight);
        txById.put(txId, tx);
        return tx;
    }
}
