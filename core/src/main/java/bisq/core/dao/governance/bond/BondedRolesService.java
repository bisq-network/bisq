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

package bisq.core.dao.governance.bond;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.governance.bonding.BondingConsensus;
import bisq.core.dao.governance.bonding.bond.BondWithHash;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Manages bonded roles if they got accepted by voting.
 */
@Slf4j
public class BondedRolesService implements DaoStateListener {
    private final DaoStateService daoStateService;
    private final BsqWalletService bsqWalletService;

    // This map is just for convenience. The data which are used to fill the map are store din the DaoState (role, txs).
    private final Map<String, BondedRole> bondedRoleByRoleUidMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRolesService(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        this.daoStateService = daoStateService;
        this.bsqWalletService = bsqWalletService;

        daoStateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        // TODO optimize to not re-write the whole map at each block
        getBondedRoleStream().forEach(bondedRole -> {
            bondedRoleByRoleUidMap.putIfAbsent(bondedRole.getUid(), new BondedRole(bondedRole));
            BondedRole bondedRoleState = bondedRoleByRoleUidMap.get(bondedRole.getUid());

            // Lets see if we have a lock up tx.
            daoStateService.getLockupTxOutputs().forEach(lockupTxOutput -> {
                String lockupTxId = lockupTxOutput.getTxId();
                // log.error("lockupTxId " + lockupTxId);

                daoStateService.getTx(lockupTxId).ifPresent(lockupTx -> {
                    byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();

                    // We used the hash of th bonded role object as our hash in OpReturn of the lock up tx to have a
                    // unique binding of the tx to the data object.
                    byte[] hash = BondingConsensus.getHashFromOpReturnData(opReturnData);
                    Optional<Role> candidate = getBondedRoleFromHash(hash);
                    if (candidate.isPresent() && bondedRole.equals(candidate.get())) {
                        bondedRoleState.setBondedRoleState(BondedRoleState.LOCKUP_TX_CONFIRMED);
                        bondedRoleState.setLockupTxId(lockupTxId);
                        // We use the tx time as we want to have a unique time for all users
                        bondedRoleState.setStartDate(lockupTx.getTime());

                        if (!daoStateService.isUnspent(lockupTxOutput.getKey())) {
                            // Lockup is already spent (in unlock tx)
                            daoStateService.getSpentInfo(lockupTxOutput)
                                    .map(SpentInfo::getTxId)
                                    .flatMap(daoStateService::getTx)
                                    .filter(unlockTx -> unlockTx.getTxType() == TxType.UNLOCK)
                                    .ifPresent(unlockTx -> {
                                        // cross check if it is in daoStateService.getUnlockTxOutputs() ?
                                        String unlockTxId = unlockTx.getId();
                                        bondedRoleState.setUnlockTxId(unlockTxId);
                                        bondedRoleState.setBondedRoleState(BondedRoleState.UNLOCK_TX_CONFIRMED);
                                        bondedRoleState.setRevokeDate(unlockTx.getTime());
                                        boolean unlocking = daoStateService.isUnlocking(unlockTxId);
                                        if (unlocking) {
                                            bondedRoleState.setBondedRoleState(BondedRoleState.UNLOCKING);
                                        } else {
                                            bondedRoleState.setBondedRoleState(BondedRoleState.UNLOCKED);
                                        }
                                    });
                        }
                    }
                });
            });
        });

        updateBondedRoleStateFromUnconfirmedLockupTxs();
        updateBondedRoleStateFromUnconfirmedUnlockTxs();
    }


    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }


    public List<Role> getBondedRoleList() {
        return getBondedRoleStream().collect(Collectors.toList());
    }

    public Collection<BondedRole> getBondedRoles() {
        return bondedRoleByRoleUidMap.values();
    }

    // bonded roles which are active and can be confiscated
    public List<Role> getActiveBondedRoles() {
        //TODO
        return getBondedRoleList();
    }

    private void updateBondedRoleStateFromUnconfirmedLockupTxs() {
        getBondedRoleStream().filter(this::isLockupTxUnconfirmed)
                .map(role -> bondedRoleByRoleUidMap.get(role.getUid()))
                .filter(bondedRole -> bondedRole.getBondedRoleState() == BondedRoleState.READY_FOR_LOCKUP)
                .forEach(bondedRole -> bondedRole.setBondedRoleState(BondedRoleState.LOCKUP_TX_PENDING));
    }

    private void updateBondedRoleStateFromUnconfirmedUnlockTxs() {
        getBondedRoleStream().filter(this::isUnlockTxUnconfirmed)
                .map(role -> bondedRoleByRoleUidMap.get(role.getUid()))
                .filter(bondedRole -> bondedRole.getBondedRoleState() == BondedRoleState.LOCKUP_TX_CONFIRMED)
                .forEach(bondedRole -> bondedRole.setBondedRoleState(BondedRoleState.UNLOCK_TX_PENDING));
    }

    private boolean isLockupTxUnconfirmed(BondWithHash bondWithHash) {
        return bsqWalletService.getWalletTransactions().stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING)
                .map(transaction -> transaction.getOutputs().get(transaction.getOutputs().size() - 1))
                .filter(lastOutput -> lastOutput.getScriptPubKey().isOpReturn())
                .map(lastOutput -> lastOutput.getScriptPubKey().getChunks())
                .filter(chunks -> chunks.size() > 1)
                .map(chunks -> chunks.get(1).data)
                .anyMatch(data -> Arrays.equals(BondingConsensus.getHashFromOpReturnData(data), bondWithHash.getHash()));
    }

    private boolean isUnlockTxUnconfirmed(BondWithHash bondWithHash) {
        return bsqWalletService.getWalletTransactions().stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING)
                .filter(transaction -> transaction.getInputs().size() > 1)
                .map(transaction -> transaction.getInputs().get(0))
                .map(TransactionInput::getConnectedOutput)
                .filter(Objects::nonNull)
                .map(TransactionOutput::getParentTransaction)
                .filter(Objects::nonNull)
                .map(Transaction::getHashAsString)
                .flatMap(lockupTxId -> daoStateService.getLockupOpReturnTxOutput(lockupTxId).stream())
                .map(BaseTxOutput::getOpReturnData)
                .anyMatch(data -> Arrays.equals(BondingConsensus.getHashFromOpReturnData(data), bondWithHash.getHash()));
    }

    public Optional<Role> getBondedRoleFromHash(byte[] hash) {
        return getBondedRoleStream()
                .filter(bondedRole -> {
                    byte[] candidateHash = bondedRole.getHash();
                   /* log.error("getBondedRoleFromHash: equals?={}, hash={}, candidateHash={}\nbondedRole={}",
                            Arrays.equals(candidateHash, hash),
                            Utilities.bytesAsHexString(hash),
                            Utilities.bytesAsHexString(candidateHash),
                            bondedRole.toString());*/
                    return Arrays.equals(candidateHash, hash);
                })
                .findAny();
    }

    public Optional<BondedRole> getBondedRoleStateFromLockupTxId(String lockupTxId) {
        return bondedRoleByRoleUidMap.values().stream()
                .filter(bondedRoleState -> lockupTxId.equals(bondedRoleState.getLockupTxId()))
                .findAny();
    }


    public Optional<BondedRoleType> getBondedRoleType(String lockUpTxId) {
        return getBondedRoleStateFromLockupTxId(lockUpTxId)
                .map(BondedRole::getRole)
                .map(Role::getBondedRoleType);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private Stream<Role> getBondedRoleStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .map(e -> ((RoleProposal) e.getProposal()).getRole());
    }

    private Stream<RoleProposal> getBondedRoleProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .map(e -> ((RoleProposal) e.getProposal()));
    }

    public boolean wasRoleAlreadyBonded(Role role) {
        BondedRole bondedRole = bondedRoleByRoleUidMap.get(role.getUid());
        checkArgument(bondedRole != null, "bondedRole must not be null");
        return bondedRole.getLockupTxId() != null;
    }

    public boolean isMyRole(Role role) {
        Set<String> myWalletTransactionIds = bsqWalletService.getWalletTransactions().stream()
                .map(Transaction::getHashAsString)
                .collect(Collectors.toSet());
        return getBondedRoleProposalStream()
                .filter(roleProposal -> roleProposal.getRole().equals(role))
                .map(Proposal::getTxId)
                .anyMatch(myWalletTransactionIds::contains);
    }
}
