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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondRepository;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Collect MyBondedReputations from the myReputationListService and provides access to the collection.
 * Gets updated after a new block is parsed or at bsqWallet transaction change to detect also state changes by
 * unconfirmed txs.
 */
@Slf4j
//TODO maybe extend BondRepository as well?
public class MyBondedReputationRepository implements DaoSetupService {
    private final DaoStateService daoStateService;
    private final BsqWalletService bsqWalletService;
    private final MyReputationListService myReputationListService;
    @Getter
    private final ObservableList<MyBondedReputation> myBondedReputations = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBondedReputationRepository(DaoStateService daoStateService,
                                        BsqWalletService bsqWalletService,
                                        MyReputationListService myReputationListService) {
        this.daoStateService = daoStateService;
        this.bsqWalletService = bsqWalletService;
        this.myReputationListService = myReputationListService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addBsqStateListener(new DaoStateListener() {
            @Override
            public void onParseTxsCompleteAfterBatchProcessing(Block block) {
                update();
            }
        });
        bsqWalletService.getWalletTransactions().addListener((ListChangeListener<Transaction>) c -> update());
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void update() {
        // It can be that the same salt/hash is in several lockupTxs, so we use the bondByLockupTxIdMap to eliminate
        // duplicates by the collection algorithm.
        Map<String, MyBondedReputation> bondByLockupTxIdMap = new HashMap<>();
        myReputationListService.getMyReputationList().stream()
                .flatMap(this::getMyBondedReputation)
                .forEach(e -> bondByLockupTxIdMap.putIfAbsent(e.getLockupTxId(), e));

        myBondedReputations.setAll(bondByLockupTxIdMap.values().stream()
                .peek(myBondedReputation -> {
                    if (BondRepository.isConfiscated(myBondedReputation, daoStateService)) {
                        myBondedReputation.setBondState(BondState.CONFISCATED);
                    } else {
                        // We don't have a UI use case for showing LOCKUP_TX_PENDING yet, but lets keep the code so if needed
                        // its there.
                        if (BondRepository.isLockupTxUnconfirmed(bsqWalletService, myBondedReputation.getBondedAsset()) &&
                                myBondedReputation.getBondState() == BondState.READY_FOR_LOCKUP) {
                            myBondedReputation.setBondState(BondState.LOCKUP_TX_PENDING);
                        } else if (BondRepository.isUnlockTxUnconfirmed(bsqWalletService, daoStateService, myBondedReputation.getBondedAsset()) &&
                                myBondedReputation.getBondState() == BondState.LOCKUP_TX_CONFIRMED) {
                            myBondedReputation.setBondState(BondState.UNLOCK_TX_PENDING);
                        }
                    }
                })
                .collect(Collectors.toList()));
    }

    private Stream<MyBondedReputation> getMyBondedReputation(MyReputation myReputation) {
        return daoStateService.getLockupTxOutputs().stream()
                .flatMap(lockupTxOutput -> {
                    String lockupTxId = lockupTxOutput.getTxId();
                    return daoStateService.getTx(lockupTxId)
                            .map(lockupTx -> {
                                byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
                                byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnData);
                                // There could be multiple txs with the same hash, so we collect a stream and not use an optional.
                                if (Arrays.equals(hash, myReputation.getHash())) {
                                    MyBondedReputation myBondedReputation = new MyBondedReputation(myReputation);
                                    BondRepository.applyBondState(daoStateService, myBondedReputation, lockupTx, lockupTxOutput);
                                    return myBondedReputation;
                                } else {
                                    return null;
                                }
                            })
                            .stream();
                })
                .filter(Objects::nonNull);
    }
}
