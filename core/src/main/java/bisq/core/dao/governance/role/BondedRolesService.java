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

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxType;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BondedRolesService implements PersistedDataHost, BsqStateListener {

    public interface BondedRoleListChangeListener {
        void onListChanged(List<BondedRole> list);
    }

    private final BsqStateService bsqStateService;
    private final Storage<BondedRoleList> storage;
    private final BondedRoleList bondedRoleList = new BondedRoleList();

    @Getter
    private final List<BondedRoleListChangeListener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRolesService(Storage<BondedRoleList> storage, BsqStateService bsqStateService) {
        this.storage = storage;
        this.bsqStateService = bsqStateService;

        bsqStateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BondedRoleList persisted = storage.initAndGetPersisted(bondedRoleList, 100);
            if (persisted != null) {
                bondedRoleList.clear();
                bondedRoleList.addAll(persisted.getList());
                listeners.forEach(l -> l.onListChanged(bondedRoleList.getList()));
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        bondedRoleList.getList().forEach(bondedRole -> {

            bsqStateService.getLockupTxOutputs().forEach(lockupTxOutput -> {
                String lockupTxId = lockupTxOutput.getTxId();
                // log.error("lockupTxId " + lockupTxId);

                bsqStateService.getTx(lockupTxId)
                        .ifPresent(lockupTx -> {
                            byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
                            byte[] hash = BondingConsensus.getHashFromOpReturnData(opReturnData);
                            Optional<BondedRole> candidate = getBondedRoleFromHash(hash);
                            if (candidate.isPresent() && bondedRole.equals(candidate.get())) {
                                if (bondedRole.getLockupTxId() == null) {
                                    bondedRole.setLockupTxId(lockupTxId);
                                    // We use the tx time as we want to have a unique time for all users
                                    bondedRole.setStartDate(lockupTx.getTime());
                                    persist();
                                }

                                if (!bsqStateService.isUnspent(lockupTxOutput.getKey())) {
                                    bsqStateService.getSpentInfo(lockupTxOutput)
                                            .map(SpentInfo::getTxId)
                                            .map(bsqStateService::getTx)
                                            .map(Optional::get)
                                            .filter(unlockTx -> unlockTx.getTxType() == TxType.UNLOCK)
                                            .ifPresent(unlockTx -> {
                                                if (bondedRole.getUnlockTxId() == null) {
                                                    bondedRole.setUnlockTxId(unlockTx.getId());
                                                    bondedRole.setRevokeDate(unlockTx.getTime());
                                                    persist();
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

    public void addListener(BondedRoleListChangeListener listener) {
        listeners.add(listener);
    }

    public void addAcceptedBondedRole(BondedRole bondedRole) {
        if (bondedRoleList.getList().stream().noneMatch(role -> role.equals(bondedRole))) {
            bondedRoleList.add(bondedRole);
            persist();
            listeners.forEach(l -> l.onListChanged(bondedRoleList.getList()));
        }
    }

    public List<BondedRole> getBondedRoleList() {
        return bondedRoleList.getList();
    }

    public List<BondedRole> getValidBondedRoleList() {
        return bondedRoleList.getList();
    }

    public Optional<BondedRole> getBondedRoleFromHash(byte[] hash) {
        return bondedRoleList.getList().stream()
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persist() {
        storage.queueUpForSave(20);
    }


    /*public static Optional<BondedRole> getBondedRoleByLockupTxId(String lockupTxId) {
        return bondedRoles.stream()
                .filter(bondedRole -> bondedRole.getLockupTxId().equals(lockupTxId)).
                        findAny();
    }*/

    public static Optional<BondedRole> getBondedRoleByHashOfBondId(byte[] hash) {
        return Optional.empty();
      /*  bondedRoles.stream()
                .filter(bondedRole -> Arrays.equals(bondedRole.getHash(), hash))
                .findAny();*/
    }
}
