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
import bisq.core.dao.SignVerifyService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondRepository;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Collects the independent lockup lifecycles associated with bonded-role proposals.
 */
@Slf4j
public class BondedRolesRepository extends BondRepository<BondedRole, Role> {
    private record RoleLockup(ByteString roleHash, Tx tx, TxOutput txOutput) {
    }

    private record RoleProposalAtHeight(RoleProposal roleProposal, int blockHeight) {
    }

    private final SignVerifyService signVerifyService;
    // Includes every known BONDED_ROLE lockup whose hash matches an evaluated role proposal. Invalid candidates and
    // rejected proposals remain here for bond management and confiscation, but never grant Bisq 2 authority.
    @Getter
    private final ObservableList<BondedRole> allRoleBonds = FXCollections.observableArrayList();

    @Inject
    public BondedRolesRepository(DaoStateService daoStateService,
                                 BsqWalletService bsqWalletService,
                                 SignVerifyService signVerifyService) {
        super(daoStateService, bsqWalletService);
        this.signVerifyService = signVerifyService;
    }

    public boolean isMyRole(Role role) {
        Set<String> myWalletTransactionIds = getMyWalletTransactionIds();
        return getAcceptedBondedRoleProposal(role).stream()
                .map(Proposal::getTxId)
                .anyMatch(myWalletTransactionIds::contains);
    }

    public boolean isMyLockupTx(String lockupTxId) {
        return getMyWalletTransactionIds().contains(lockupTxId);
    }

    private Set<String> getMyWalletTransactionIds() {
        return bsqWalletService.getClonedWalletTransactions().stream()
                .map(Transaction::getTxId)
                .map(Sha256Hash::toString)
                .collect(Collectors.toSet());
    }

    // A Role uid is client generated and is also the historical key used by BondRepository. Keep the uniquely oldest
    // accepted proposal authoritative so a later accepted copy cannot replace the proposal-key identity.
    public Optional<RoleProposal> getAcceptedBondedRoleProposal(Role role) {
        List<RoleProposal> candidates = getAcceptedBondedRoleProposalStream()
                .filter(roleProposal -> roleProposal.getRole().getUid().equals(role.getUid()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<RoleProposalAtHeight> candidatesAtHeight = candidates.stream()
                .map(this::findRoleProposalAtHeight)
                .flatMap(Optional::stream)
                .toList();
        if (candidatesAtHeight.size() != candidates.size()) {
            log.error("Could not resolve the block height of every accepted role proposal for role uid {}.",
                    role.getUid());
            return Optional.empty();
        }

        int oldestBlockHeight = candidatesAtHeight.stream()
                .mapToInt(RoleProposalAtHeight::blockHeight)
                .min()
                .orElseThrow();
        List<RoleProposalAtHeight> oldestCandidates = candidatesAtHeight.stream()
                .filter(candidate -> candidate.blockHeight() == oldestBlockHeight)
                .toList();
        if (oldestCandidates.size() != 1) {
            log.error("Found {} accepted role proposals for role uid {} at oldest block height {}. " +
                            "The role identity is ambiguous.",
                    oldestCandidates.size(), role.getUid(), oldestBlockHeight);
            return Optional.empty();
        }

        RoleProposal candidate = oldestCandidates.getFirst().roleProposal();
        return candidate.getRole().equals(role) ? Optional.of(candidate) : Optional.empty();
    }

    private Optional<RoleProposal> getAcceptedBondedRoleProposal(String proposalTxId) {
        return getAcceptedBondedRoleProposalStream()
                .filter(proposal -> proposalTxId.equals(proposal.getTxId()))
                .filter(proposal -> getAcceptedBondedRoleProposal(proposal.getRole())
                        .filter(canonical -> proposalTxId.equals(canonical.getTxId()))
                        .isPresent())
                .findFirst();
    }

    private Optional<RoleProposalAtHeight> findRoleProposalAtHeight(RoleProposal roleProposal) {
        return Optional.ofNullable(roleProposal.getTxId())
                .flatMap(daoStateService::getTx)
                .map(tx -> new RoleProposalAtHeight(roleProposal, tx.getBlockHeight()));
    }

    public synchronized List<BondedRole> getAcceptedBonds() {
        return bonds.stream()
                .filter(bondedRole -> getAcceptedBondedRoleProposal(bondedRole.getBondedAsset()).isPresent())
                .toList();
    }

    public boolean canCreateNewLockup(Role role) {
        return getAcceptedBondedRoleProposal(role).isPresent() &&
                !isLockupTxUnconfirmed(bsqWalletService, role);
    }

    public synchronized void verifyBondedRole(BondedRoleRegistration registration) {
        checkArgument(registration.protocolVersion() == BondedRoleRegistration.CURRENT_PROTOCOL_VERSION,
                "Unsupported bonded-role registration protocol version: %s", registration.protocolVersion());

        RoleProposal proposal = getAcceptedBondedRoleProposal(registration.proposalTxId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No canonical accepted role proposal found for proposalTxId=" + registration.proposalTxId()));
        Role role = proposal.getRole();
        checkArgument(role.getName().equals(registration.bondUserName()) &&
                        role.getBondedRoleType().name().equals(registration.roleType()),
                "Role proposal does not match bondUserName=%s and roleType=%s",
                registration.bondUserName(), registration.roleType());

        BondedRole bond = getAcceptedBonds().stream()
                .filter(candidate -> candidate.getBondedAsset().equals(role))
                .filter(candidate -> registration.lockupTxId().equals(candidate.getLockupTxId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No valid role lockup found for lockupTxId=" + registration.lockupTxId()));
        checkArgument(bond.getBondState() == BondState.LOCKUP_TX_CONFIRMED,
                "Role lockup is not confirmed and unspent. lockupTxId=%s", registration.lockupTxId());

        String pubKey = daoStateService.getTx(registration.proposalTxId())
                .flatMap(BondedRolesRepository::findPubKeyOfFirstInput)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No proposal verification pub key found for proposalTxId=" + registration.proposalTxId()));
        String message = getRegistrationSignatureMessage(
                registration.proposalTxId(), registration.lockupTxId(), registration.profileId());
        checkArgument(signVerifyService.isValidSignature(message, pubKey, registration.signatureBase64()),
                "Invalid signature for proposalTxId=%s and lockupTxId=%s",
                registration.proposalTxId(), registration.lockupTxId());
    }

    public synchronized Optional<String> findVerificationTxId(Role role, String lockupTxId) {
        boolean hasConfirmedLockup = bonds.stream()
                .filter(bond -> bond.getBondedAsset().equals(role))
                .filter(bond -> lockupTxId.equals(bond.getLockupTxId()))
                .anyMatch(bond -> bond.getBondState() == BondState.LOCKUP_TX_CONFIRMED);
        if (!hasConfirmedLockup) {
            return Optional.empty();
        }
        return getAcceptedBondedRoleProposal(role)
                .map(Proposal::getTxId)
                .filter(txId -> daoStateService.getTx(txId)
                        .flatMap(BondedRolesRepository::findPubKeyOfFirstInput)
                        .isPresent());
    }

    public String getRegistrationSignatureMessage(String proposalTxId, String lockupTxId, String profileId) {
        return BondedRoleRegistrationSignature.getMessage(proposalTxId, lockupTxId, profileId);
    }

    private Optional<RoleLockup> findValidRoleLockup(TxOutput lockupTxOutput,
                                                     Map<ByteString, Role> acceptedRoleByHash) {
        return daoStateService.getTx(lockupTxOutput.getTxId())
                .flatMap(lockupTx -> findRoleHash(lockupTx)
                        .map(roleHash -> new RoleLockup(roleHash, lockupTx, lockupTxOutput)))
                .filter(roleLockup -> Optional.ofNullable(acceptedRoleByHash.get(roleLockup.roleHash()))
                        .flatMap(this::getAcceptedBondedRoleProposal)
                        .filter(roleProposal -> isValidLockupForRole(roleLockup.tx(), roleProposal))
                        .isPresent());
    }

    private boolean isValidLockupForRole(Tx lockupTx, RoleProposal roleProposal) {
        Optional<RoleProposalAtHeight> roleProposalAtHeight = findRoleProposalAtHeight(roleProposal);
        if (roleProposalAtHeight.isEmpty() ||
                lockupTx.getBlockHeight() <= roleProposalAtHeight.get().blockHeight() ||
                lockupTx.getLockTime() < roleProposal.getUnlockTime()) {
            return false;
        }
        return getRequiredBond(roleProposal, roleProposalAtHeight.get().blockHeight()).stream()
                .anyMatch(requiredBond -> lockupTx.getLockedAmount() >= requiredBond);
    }

    private OptionalLong getRequiredBond(RoleProposal roleProposal, int proposalBlockHeight) {
        if (roleProposal.getRequiredBondUnit() <= 0 || roleProposal.getUnlockTime() <= 0) {
            return OptionalLong.empty();
        }
        long bondedRoleFactor = daoStateService.getParamValueAsCoin(
                Param.BONDED_ROLE_FACTOR, proposalBlockHeight).value;
        if (bondedRoleFactor <= 0) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Math.multiplyExact(roleProposal.getRequiredBondUnit(), bondedRoleFactor));
        } catch (ArithmeticException e) {
            log.error("Required bond overflows for role proposal {}.", roleProposal.getTxId());
            return OptionalLong.empty();
        }
    }

    private static Optional<ByteString> findRoleHash(Tx lockupTx) {
        // A LOCKUP_OUTPUT is indexed only after the parser has validated the parent's 25-byte lockup OP_RETURN.
        return Optional.ofNullable(lockupTx.getLastTxOutput().getOpReturnData())
                .filter(opReturnData -> BondConsensus.getLockupReason(opReturnData)
                        .filter(LockupReason.BONDED_ROLE::equals)
                        .isPresent())
                .map(opReturnData -> ByteString.copyFrom(BondConsensus.getHashFromOpReturnData(opReturnData)));
    }

    private static Optional<String> findPubKeyOfFirstInput(Tx tx) {
        return tx.getTxInputs().stream()
                .findFirst()
                .map(TxInput::getPubKey)
                .filter(pubKey -> pubKey != null && !pubKey.isEmpty());
    }

    @Override
    protected BondedRole createBond(Role role) {
        return new BondedRole(role);
    }

    @Override
    protected Stream<Role> getBondedAssetStream() {
        return getAcceptedBondedRoleProposalStream()
                .map(RoleProposal::getRole)
                .map(this::getAcceptedBondedRoleProposal)
                .flatMap(Optional::stream)
                .map(RoleProposal::getRole)
                .distinct();
    }

    @Override
    protected synchronized void update() {
        try {
            bondByUidMap.clear();
            List<Role> acceptedRoles = getBondedAssetStream().toList();
            Map<ByteString, Role> acceptedRoleByHash = acceptedRoles.stream()
                    .collect(Collectors.toMap(role -> ByteString.copyFrom(role.getHash()), role -> role));

            List<RoleLockup> validRoleLockups = daoStateService.getLockupTxOutputs().stream()
                    .map(lockupTxOutput -> findValidRoleLockup(lockupTxOutput, acceptedRoleByHash))
                    .flatMap(Optional::stream)
                    .toList();
            List<BondedRole> acceptedBonds = validRoleLockups.stream()
                    .map(roleLockup -> createRoleBond(
                            acceptedRoleByHash.get(roleLockup.roleHash()), roleLockup.tx(), roleLockup.txOutput()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Set<ByteString> rolesWithLockups = validRoleLockups.stream()
                    .map(RoleLockup::roleHash)
                    .collect(Collectors.toSet());
            acceptedRoles.stream()
                    .filter(role -> !rolesWithLockups.contains(ByteString.copyFrom(role.getHash())))
                    .map(this::createBond)
                    .forEach(acceptedBonds::add);

            applyPendingWalletState(acceptedBonds);
            acceptedBonds.sort(getBondComparator());
            bonds.setAll(acceptedBonds);
            allRoleBonds.setAll(findAllRoleBonds());
        } catch (RuntimeException e) {
            bondByUidMap.clear();
            bonds.clear();
            allRoleBonds.clear();
            throw e;
        }
    }

    private void applyPendingWalletState(List<BondedRole> acceptedBonds) {
        acceptedBonds.stream()
                .filter(bond -> bond.getBondState() == BondState.READY_FOR_LOCKUP)
                .filter(bond -> isLockupTxUnconfirmed(bsqWalletService, bond.getBondedAsset()))
                .forEach(bond -> bond.setBondState(BondState.LOCKUP_TX_PENDING));
        acceptedBonds.stream()
                .filter(bond -> bond.getBondState() == BondState.LOCKUP_TX_CONFIRMED)
                .filter(bond -> isUnlockTxUnconfirmed(bsqWalletService, bond.getLockupTxId()))
                .forEach(bond -> bond.setBondState(BondState.UNLOCK_TX_PENDING));
    }

    private List<BondedRole> findAllRoleBonds() {
        Map<String, BondedRole> acceptedBondByLockupTxId = bonds.stream()
                .filter(bond -> bond.getLockupTxId() != null)
                .collect(Collectors.toMap(BondedRole::getLockupTxId, bond -> bond));
        Map<ByteString, Role> roleByHash = getBondedRoleProposalStream()
                .map(RoleProposal::getRole)
                .distinct()
                .collect(Collectors.toMap(role -> ByteString.copyFrom(role.getHash()), role -> role));

        List<BondedRole> result = daoStateService.getLockupTxOutputs().stream()
                .map(lockupTxOutput -> findRoleBond(lockupTxOutput, roleByHash, acceptedBondByLockupTxId))
                .flatMap(Optional::stream)
                .sorted(getBondComparator())
                .collect(Collectors.toCollection(ArrayList::new));
        bonds.stream()
                .filter(bond -> bond.getLockupTxId() == null)
                .forEach(result::add);
        return List.copyOf(result);
    }

    private Optional<BondedRole> findRoleBond(TxOutput lockupTxOutput,
                                              Map<ByteString, Role> roleByHash,
                                              Map<String, BondedRole> acceptedBondByLockupTxId) {
        return daoStateService.getTx(lockupTxOutput.getTxId())
                .flatMap(lockupTx -> findRoleHash(lockupTx)
                        .map(roleByHash::get)
                        .map(role -> Optional.ofNullable(acceptedBondByLockupTxId.get(lockupTx.getId()))
                                .orElseGet(() -> createRoleBond(role, lockupTx, lockupTxOutput))));
    }

    private BondedRole createRoleBond(Role role, Tx lockupTx, TxOutput lockupTxOutput) {
        BondedRole bond = createBond(role);
        applyBondState(daoStateService, bond, lockupTx, lockupTxOutput);
        return bond;
    }

    private static Comparator<BondedRole> getBondComparator() {
        return Comparator.comparing(BondedRole::getLockupDate)
                .thenComparing(BondedRole::getLockupTxId, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    @Override
    protected void updateBond(BondedRole bond, Role bondedAsset, TxOutput lockupTxOutput) {
        // update() builds one BondedRole per lockup directly; the base-class single-bond hook is intentionally unused.
    }

    private Stream<RoleProposal> getAcceptedBondedRoleProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .filter(EvaluatedProposal::isAccepted)
                .map(e -> (RoleProposal) e.getProposal());
    }

    private Stream<RoleProposal> getBondedRoleProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .map(e -> (RoleProposal) e.getProposal());
    }
}
