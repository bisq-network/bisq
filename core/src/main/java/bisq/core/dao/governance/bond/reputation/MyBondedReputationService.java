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

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxType;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyBondedReputationService implements DaoSetupService {
    private final DaoStateService daoStateService;
    private final MyReputationListService myReputationListService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBondedReputationService(DaoStateService daoStateService,
                                     MyReputationListService myReputationListService) {
        this.daoStateService = daoStateService;
        this.myReputationListService = myReputationListService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<MyBondedReputation> getMyBondedReputations() {
        return myReputationListService.getMyReputationList().stream()
                .map(myReputation -> getMyBondedReputation(myReputation).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Optional<MyBondedReputation> getMyBondedReputation(MyReputation myReputation) {
        return daoStateService.getLockupTxOutputs().stream()
                .map(lockupTxOutput -> {
                    String lockupTxId = lockupTxOutput.getTxId();
                    return daoStateService.getTx(lockupTxId)
                            .map(lockupTx -> {
                                byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
                                byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnData);
                                if (Arrays.equals(hash, myReputation.getHash())) {
                                    MyBondedReputation myBondedReputation = new MyBondedReputation(myReputation);
                                    myBondedReputation.setLockTime(lockupTx.getLockTime());
                                    myBondedReputation.setBondState(BondState.LOCKUP_TX_CONFIRMED);
                                    myBondedReputation.setLockupTxId(lockupTx.getId());
                                    // We use the tx time as we want to have a unique time for all users
                                    myBondedReputation.setLockupDate(lockupTx.getTime());
                                    myBondedReputation.setAmount(lockupTx.getLockedAmount());
                                    if (!daoStateService.isUnspent(lockupTxOutput.getKey())) {
                                        // Lockup is already spent (in unlock tx)
                                        daoStateService.getSpentInfo(lockupTxOutput)
                                                .map(SpentInfo::getTxId)
                                                .flatMap(daoStateService::getTx)
                                                .filter(unlockTx -> unlockTx.getTxType() == TxType.UNLOCK)
                                                .ifPresent(unlockTx -> {
                                                    // cross check if it is in daoStateService.getUnlockTxOutputs() ?
                                                    String unlockTxId = unlockTx.getId();
                                                    myBondedReputation.setUnlockTxId(unlockTxId);
                                                    myBondedReputation.setBondState(BondState.UNLOCK_TX_CONFIRMED);
                                                    myBondedReputation.setUnlockDate(unlockTx.getTime());
                                                    boolean unlocking = daoStateService.isUnlocking(unlockTxId);
                                                    if (unlocking) {
                                                        myBondedReputation.setBondState(BondState.UNLOCKING);
                                                    } else {
                                                        myBondedReputation.setBondState(BondState.UNLOCKED);
                                                    }
                                                });
                                    }
                                    return myBondedReputation;
                                } else {
                                    return null;
                                }
                            })
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .findAny();
    }
}
