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
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.ScriptPattern;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Collect bonds and bond asset data from other sources and provides access to the collection.
 * Gets updated after a new block is parsed or at bsqWallet transaction change to detect also state changes by
 * unconfirmed txs.
 */
@Slf4j
public abstract class BondRepository<T extends Bond, R extends BondedAsset> implements DaoSetupService,
        BsqWalletService.WalletTransactionsChangeListener {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void applyBondState(DaoStateService daoStateService, Bond bond, Tx lockupTx, TxOutput lockupTxOutput) {
        if (bond.getBondState() != BondState.LOCKUP_TX_PENDING || bond.getBondState() != BondState.UNLOCK_TX_PENDING)
            bond.setBondState(BondState.LOCKUP_TX_CONFIRMED);

        bond.setLockupTxId(lockupTx.getId());
        // We use the tx time as we want to have a unique time for all users
        bond.setLockupDate(lockupTx.getTime());
        bond.setAmount(lockupTx.getLockedAmount());
        bond.setLockTime(lockupTx.getLockTime());

        if (!daoStateService.isUnspent(lockupTxOutput.getKey())) {
            // Lockup is already spent (in unlock tx)
            daoStateService.getSpentInfo(lockupTxOutput)
                    .map(SpentInfo::getTxId)
                    .flatMap(daoStateService::getTx)
                    .filter(unlockTx -> unlockTx.getTxType() == TxType.UNLOCK)
                    .ifPresent(unlockTx -> {
                        // cross check if it is in daoStateService.getUnlockTxOutputs() ?
                        String unlockTxId = unlockTx.getId();
                        bond.setUnlockTxId(unlockTxId);
                        bond.setBondState(BondState.UNLOCK_TX_CONFIRMED);
                        bond.setUnlockDate(unlockTx.getTime());
                        boolean unlocking = daoStateService.isUnlockingAndUnspent(unlockTxId);
                        if (unlocking) {
                            bond.setBondState(BondState.UNLOCKING);
                        } else {
                            bond.setBondState(BondState.UNLOCKED);
                        }
                    });
        }

        if ((bond.getLockupTxId() != null && daoStateService.isConfiscatedLockupTxOutput(bond.getLockupTxId())) ||
                (bond.getUnlockTxId() != null && daoStateService.isConfiscatedUnlockTxOutput(bond.getUnlockTxId()))) {
            bond.setBondState(BondState.CONFISCATED);
        }
    }

    public static boolean isLockupTxUnconfirmed(BsqWalletService bsqWalletService, BondedAsset bondedAsset) {
        return bsqWalletService.getPendingWalletTransactionsStream()
                .map(transaction -> transaction.getOutputs().get(transaction.getOutputs().size() - 1))
                .filter(lastOutput -> ScriptPattern.isOpReturn(lastOutput.getScriptPubKey()))
                .map(lastOutput -> lastOutput.getScriptPubKey().getChunks())
                .filter(chunks -> chunks.size() > 1)
                .map(chunks -> chunks.get(1).data)
                .anyMatch(data -> Arrays.equals(BondConsensus.getHashFromOpReturnData(data), bondedAsset.getHash()));
    }

    public static boolean isUnlockTxUnconfirmed(BsqWalletService bsqWalletService, DaoStateService daoStateService, BondedAsset bondedAsset) {
        return bsqWalletService.getPendingWalletTransactionsStream()
                .filter(transaction -> transaction.getInputs().size() > 1)
                .flatMap(transaction -> transaction.getInputs().stream()) // We need to iterate all inputs
                .map(TransactionInput::getConnectedOutput)
                .filter(Objects::nonNull)
                .filter(transactionOutput -> transactionOutput.getIndex() == 0) // The output at the lockupTx must be index 0
                .map(TransactionOutput::getParentTransaction)
                .filter(Objects::nonNull)
                .map(Transaction::getTxId)
                .map(Sha256Hash::toString)
                .map(lockupTxId -> daoStateService.getLockupOpReturnTxOutput(lockupTxId).orElse(null))
                .filter(Objects::nonNull)
                .map(BaseTxOutput::getOpReturnData)
                .anyMatch(data -> Arrays.equals(BondConsensus.getHashFromOpReturnData(data), bondedAsset.getHash()));
    }

    public static boolean isConfiscated(Bond bond, DaoStateService daoStateService) {
        return (bond.getLockupTxId() != null && daoStateService.isConfiscatedLockupTxOutput(bond.getLockupTxId())) ||
                (bond.getUnlockTxId() != null && daoStateService.isConfiscatedUnlockTxOutput(bond.getUnlockTxId()));
    }


    protected final DaoStateService daoStateService;
    protected final BsqWalletService bsqWalletService;

    // This map is just for convenience. The data which are used to fill the map are stored in the DaoState (role, txs).
    protected final Map<String, T> bondByUidMap = new HashMap<>();
    @Getter
    protected final ObservableList<T> bonds = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondRepository(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        this.daoStateService = daoStateService;
        this.bsqWalletService = bsqWalletService;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(new DaoStateListener() {
            @Override
            public void onParseBlockCompleteAfterBatchProcessing(Block block) {
                update();
            }
        });
        bsqWalletService.addWalletTransactionsChangeListener(this);
    }

    @Override
    public void start() {
        update();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqWalletService.WalletTransactionsChangeListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onWalletTransactionsChange() {
        update();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isBondedAssetAlreadyInBond(R bondedAsset) {
        boolean contains = bondByUidMap.containsKey(bondedAsset.getUid());
        return contains && bondByUidMap.get(bondedAsset.getUid()).getLockupTxId() != null;
    }

    public List<Bond> getActiveBonds() {
        return bonds.stream().filter(Bond::isActive).collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract T createBond(R bondedAsset);

    protected abstract void updateBond(T bond, R bondedAsset, TxOutput lockupTxOutput);

    protected abstract Stream<R> getBondedAssetStream();

    protected void update() {
        log.debug("update");
        getBondedAssetStream().forEach(bondedAsset -> {
            String uid = bondedAsset.getUid();
            bondByUidMap.putIfAbsent(uid, createBond(bondedAsset));
            T bond = bondByUidMap.get(uid);

            daoStateService.getLockupTxOutputs().forEach(lockupTxOutput -> {
                updateBond(bond, bondedAsset, lockupTxOutput);
            });
        });

        updateBondStateFromUnconfirmedLockupTxs();
        updateBondStateFromUnconfirmedUnlockTxs();

        bonds.setAll(bondByUidMap.values());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBondStateFromUnconfirmedLockupTxs() {
        getBondedAssetStream().filter(bondedAsset -> isLockupTxUnconfirmed(bsqWalletService, bondedAsset))
                .map(bondedAsset -> bondByUidMap.get(bondedAsset.getUid()))
                .filter(bond -> bond.getBondState() == BondState.READY_FOR_LOCKUP)
                .forEach(bond -> bond.setBondState(isConfiscated(bond, daoStateService) ? BondState.CONFISCATED : BondState.LOCKUP_TX_PENDING));
    }

    private void updateBondStateFromUnconfirmedUnlockTxs() {
        getBondedAssetStream().filter(bondedAsset -> isUnlockTxUnconfirmed(bsqWalletService, daoStateService, bondedAsset))
                .map(bondedAsset -> bondByUidMap.get(bondedAsset.getUid()))
                .filter(bond -> bond.getBondState() == BondState.LOCKUP_TX_CONFIRMED)
                .forEach(bond -> bond.setBondState(isConfiscated(bond, daoStateService) ? BondState.CONFISCATED : BondState.UNLOCK_TX_PENDING));
    }
}
