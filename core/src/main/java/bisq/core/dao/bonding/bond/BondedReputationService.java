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

package bisq.core.dao.bonding.bond;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
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
public class BondedReputationService implements PersistedDataHost, DaoStateListener {

    public interface BondedReputationListChangeListener {
        void onListChanged(List<BondedReputation> list);
    }

    private final DaoStateService daoStateService;
    private final Storage<BondedReputationList> storage;
    private final BondedReputationList bondedReputationList = new BondedReputationList();

    @Getter
    private final List<BondedReputationListChangeListener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedReputationService(Storage<BondedReputationList> storage, DaoStateService daoStateService) {
        this.storage = storage;
        this.daoStateService = daoStateService;

        daoStateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BondedReputationList persisted = storage.initAndGetPersisted(bondedReputationList, 100);
            if (persisted != null) {
                bondedReputationList.clear();
                bondedReputationList.addAll(persisted.getList());
                listeners.forEach(l -> l.onListChanged(bondedReputationList.getList()));
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        bondedReputationList.getList().forEach(bondedReputation -> {
            daoStateService.getLockupTxOutputs().forEach(lockupTxOutput -> {
                String lockupTxId = lockupTxOutput.getTxId();
                daoStateService.getTx(lockupTxId)
                        .ifPresent(lockupTx -> {
                            byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
                            byte[] hash = BondingConsensus.getHashFromOpReturnData(opReturnData);
                            Optional<BondedReputation> candidate = getBondedReputationFromHash(hash);
                            if (candidate.isPresent() && bondedReputation.equals(candidate.get())) {
                                if (bondedReputation.getLockupTxId() == null) {
                                    bondedReputation.setLockupTxId(lockupTxId);
                                    persist();
                                }

                                if (!daoStateService.isUnspent(lockupTxOutput.getKey())) {
                                    daoStateService.getSpentInfo(lockupTxOutput)
                                            .map(SpentInfo::getTxId)
                                            .map(daoStateService::getTx)
                                            .map(Optional::get)
                                            // TODO(sq): What if the tx is burnt and not unlocked, need to check on that
                                            .filter(unlockTx -> unlockTx.getTxType() == TxType.UNLOCK)
                                            .ifPresent(unlockTx -> {
                                                if (bondedReputation.getUnlockTxId() == null) {
                                                    bondedReputation.setUnlockTxId(unlockTx.getId());
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

    public void addListener(BondedReputationListChangeListener listener) {
        listeners.add(listener);
    }

//    public void addAcceptedBondedReputation(BondedReputation bondedReputation) {
//        if (bondedReputationList.getList().stream().noneMatch(role -> role.equals(bondedReputation))) {
//            bondedReputationList.add(bondedReputation);
//            persist();
//            listeners.forEach(l -> l.onListChanged(bondedReputationList.getList()));
//        }
//    }

    public List<BondedReputation> getBondedReputationList() {
        return bondedReputationList.getList();
    }

   /* public List<BondedReputation> getValidBondedReputationList() {
        //TODO validation ???
        return bondedReputationList.getList();
    }*/

    public Optional<BondedReputation> getBondedReputationFromHash(byte[] hash) {
        return bondedReputationList.getList().stream()
                .filter(bondedReputation -> {
                    byte[] candidateHash = bondedReputation.getHash();
                   /* log.error("getBondedReputationFromHash: equals?={}, hash={}, candidateHash={}\bondedReputation={}",
                            Arrays.equals(candidateHash, hash),
                            Utilities.bytesAsHexString(hash),
                            Utilities.bytesAsHexString(candidateHash),
                            bondedReputation.toString());*/
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


    /*public static Optional<BondedReputation> getBondedReputationByLockupTxId(String lockupTxId) {
        return BondedReputations.stream()
                .filter(BondedReputation -> BondedReputation.getLockupTxId().equals(lockupTxId)).
                        findAny();
    }*/

   /* public static Optional<BondedReputation> getBondedReputationByHashOfBondId(byte[] hash) {
        return Optional.empty();
      *//*  BondedReputations.stream()
                .filter(BondedReputation -> Arrays.equals(BondedReputation.getHash(), hash))
                .findAny();*//*
    }*/
}
