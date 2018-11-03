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

package bisq.core.dao.governance.bond.reputation;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BondedReputationService implements PersistedDataHost, DaoStateListener {

    public interface ReputationListChangeListener {
        void onListChanged(List<Reputation> list);
    }

    private final DaoStateService daoStateService;
    private final Storage<ReputationList> storage;
    private final ReputationList reputationList = new ReputationList();

    @Getter
    private final List<ReputationListChangeListener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedReputationService(Storage<ReputationList> storage, DaoStateService daoStateService) {
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
            ReputationList persisted = storage.initAndGetPersisted(reputationList, 100);
            if (persisted != null) {
                reputationList.clear();
                reputationList.addAll(persisted.getList());
                listeners.forEach(l -> l.onListChanged(reputationList.getList()));
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
       /* bondedReputationList.getList().forEach(bondedReputation -> {
            daoStateService.getLockupTxOutputs().forEach(lockupTxOutput -> {
                String lockupTxId = lockupTxOutput.getTxId();
                daoStateService.getTx(lockupTxId)
                        .ifPresent(lockupTx -> {
                            byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
                            byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnData);
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
        });*/
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }

    public void addListener(ReputationListChangeListener listener) {
        listeners.add(listener);
    }

    public void addReputation(Reputation reputation) {
        if (!reputationList.contains(reputation)) {
            reputationList.add(reputation);
            persist();
        }
    }

    public List<BondedReputation> getReputationList() {
        return new ArrayList<>(); //bondedReputationList.getList();
    }

    public Optional<BondedReputation> getBondedReputationFromHash(byte[] hash) {
        return Optional.empty();
        /*
        return bondedReputationList.getList().stream()
                .filter(bondedReputation -> {
                    byte[] candidateHash = bondedReputation.getHash();
                   *//* log.error("getBondedReputationFromHash: equals?={}, hash={}, candidateHash={}\bondedReputation={}",
                            Arrays.equals(candidateHash, hash),
                            Utilities.bytesAsHexString(hash),
                            Utilities.bytesAsHexString(candidateHash),
                            bondedReputation.toString());*//*
                    return Arrays.equals(candidateHash, hash);
                })
                .findAny();*/
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persist() {
        storage.queueUpForSave(20);
    }
}
