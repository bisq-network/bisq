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

package bisq.core.dao.governance.role;

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.governance.proposal.role.RoleProposal;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.blockchain.BaseTxOutput;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxType;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // This map is just for convenience. The data which are used to fill the map are store din the DaoState (role, txs).
    private final Map<String, BondedRole> bondedRoleStateMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRolesService(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;

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
            bondedRoleStateMap.putIfAbsent(bondedRole.getUid(), new BondedRole(bondedRole));
            BondedRole bondedRoleState = bondedRoleStateMap.get(bondedRole.getUid());

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
                        if (bondedRoleState.getLockupTxId() == null) {
                            bondedRoleState.setLockupTxId(lockupTxId);
                            // We use the tx time as we want to have a unique time for all users
                            bondedRoleState.setStartDate(lockupTx.getTime());
                        } else {
                            checkArgument(bondedRoleState.getLockupTxId().equals(lockupTxId),
                                    "We have already the lockup tx set in bondedRoleState " +
                                            "but it is different to the one we found in the daoState transactions. " +
                                            "That should not happen. bondedRole={}, bondedRoleState={}",
                                    bondedRole, bondedRoleState);
                        }

                        if (!daoStateService.isUnspent(lockupTxOutput.getKey())) {
                            // Lockup is already spent (in unlock tx)
                            daoStateService.getSpentInfo(lockupTxOutput)
                                    .map(SpentInfo::getTxId)
                                    .flatMap(daoStateService::getTx)
                                    .filter(unlockTx -> unlockTx.getTxType() == TxType.UNLOCK)
                                    .ifPresent(unlockTx -> {
                                        // cross check if it is in daoStateService.getUnlockTxOutputs() ?
                                        String unlockTxId = unlockTx.getId();
                                        if (bondedRoleState.getUnlockTxId() == null) {
                                            bondedRoleState.setUnlockTxId(unlockTxId);
                                            bondedRoleState.setRevokeDate(unlockTx.getTime());
                                            bondedRoleState.setUnlocking(daoStateService.isUnlocking(unlockTxId));
                                            //TODO after locktime set to false or maybe better use states for lock state
                                        } else {
                                            checkArgument(bondedRoleState.getUnlockTxId().equals(unlockTxId),
                                                    "We have already the unlock tx set in bondedRoleState " +
                                                            "but it is different to the one we found in the daoState transactions. " +
                                                            "That should not happen. bondedRole={}, bondedRoleState={}",
                                                    bondedRole, bondedRoleState);
                                        }

                                        // TODO check lock time
                                    });
                        }
                    }
                });
            });
        });
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

    public Collection<BondedRole> getBondedRoleStates() {
        return bondedRoleStateMap.values();
    }

    // bonded roles which are active and can be confiscated
    public List<Role> getActiveBondedRoles() {
        //TODO
        return getBondedRoleList();
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
        return bondedRoleStateMap.values().stream()
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


    private Optional<byte[]> getOpReturnData(String lockUpTxId) {
        return daoStateService.getLockupOpReturnTxOutput(lockUpTxId).map(BaseTxOutput::getOpReturnData);
    }

    public boolean wasRoleAlreadyBonded(Role role) {
        BondedRole bondedRole = bondedRoleStateMap.get(role.getUid());
        return bondedRole != null && bondedRole.getLockupTxId() != null;
    }


   /* private Optional<LockupType> getOptionalLockupType(String lockUpTxId) {
        return getOpReturnData(lockUpTxId)
                .flatMap(BondingConsensus::getLockupType);
    }*/

    /*public static Optional<Role> getBondedRoleByLockupTxId(String lockupTxId) {
        return bondedRoles.stream()
                .filter(bondedRole -> bondedRole.getLockupTxId().equals(lockupTxId)).
                        findAny();
    }*/
/*
    public static Optional<Role> getBondedRoleByHashOfBondId(byte[] hash) {
        return Optional.empty();
      *//*  bondedRoles.stream()
                .filter(bondedRole -> Arrays.equals(bondedRole.getHash(), hash))
                .findAny();*//*
    }*/
}
