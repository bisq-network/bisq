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
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Manages bonds.
 */
@Slf4j
public abstract class BondService<T extends Bond, R extends BondedAsset> implements DaoStateListener, DaoSetupService {
    protected final DaoStateService daoStateService;
    protected final BsqWalletService bsqWalletService;

    // This map is just for convenience. The data which are used to fill the map are stored in the DaoState (role, txs).
    protected final Map<String, T> bondByUidMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondService(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        this.daoStateService = daoStateService;
        this.bsqWalletService = bsqWalletService;

        daoStateService.addBsqStateListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
        updateMap();
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
        updateMap();
    }

    protected void updateMap() {
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
    }

    public void updateBond(T bond, R bondedAsset, TxOutput lockupTxOutput) {
        // Lets see if we have a lock up tx.
        String lockupTxId = lockupTxOutput.getTxId();
        daoStateService.getTx(lockupTxId).ifPresent(lockupTx -> {
            byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
            // We used the hash of th bonded bondedAsset object as our hash in OpReturn of the lock up tx to have a
            // unique binding of the tx to the data object.
            byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnData);
            Optional<R> candidate = findBondedAssetByHash(hash);
            if (candidate.isPresent() && bondedAsset.equals(candidate.get())) {
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
                                boolean unlocking = daoStateService.isUnlocking(unlockTxId);
                                if (unlocking) {
                                    bond.setBondState(BondState.UNLOCKING);
                                } else {
                                    bond.setBondState(BondState.UNLOCKED);
                                }
                            });
                }
            }
        });
    }

    protected abstract T createBond(R bondedAsset);

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<T> getBonds() {
        return new ArrayList<>(bondByUidMap.values());
    }

    public List<T> getActiveBonds() {
        return bondByUidMap.values().stream()
                .filter(T::isActive)
                .collect(Collectors.toList());
    }

    public Optional<T> findBondByLockupTxId(String lockupTxId) {
        return bondByUidMap.values().stream()
                .filter(bond -> lockupTxId.equals(bond.getLockupTxId()))
                .findAny();
    }

    public boolean wasBondedAssetAlreadyBonded(R bondedAsset) {
        T bond = bondByUidMap.get(bondedAsset.getUid());
        checkArgument(bond != null, "bond must not be null");
        return bond.getLockupTxId() != null;
    }

    public Optional<R> findBondedAssetByHash(byte[] hash) {
        return getBondedAssetStream()
                .filter(bondedAsset -> Arrays.equals(bondedAsset.getHash(), hash))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBondStateFromUnconfirmedLockupTxs() {
        getBondedAssetStream().filter(this::isLockupTxUnconfirmed)
                .map(bondedAsset -> bondByUidMap.get(bondedAsset.getUid()))
                .filter(bond -> bond.getBondState() == BondState.READY_FOR_LOCKUP)
                .forEach(bond -> bond.setBondState(BondState.LOCKUP_TX_PENDING));
    }

    private void updateBondStateFromUnconfirmedUnlockTxs() {
        getBondedAssetStream().filter(this::isUnlockTxUnconfirmed)
                .map(bondedAsset -> bondByUidMap.get(bondedAsset.getUid()))
                .filter(bond -> bond.getBondState() == BondState.LOCKUP_TX_CONFIRMED)
                .forEach(bond -> bond.setBondState(BondState.UNLOCK_TX_PENDING));
    }

    abstract protected Stream<R> getBondedAssetStream();


    public boolean isLockupTxUnconfirmed(R bondedAsset) {
        return getPendingWalletTransactionsStream()
                .map(transaction -> transaction.getOutputs().get(transaction.getOutputs().size() - 1))
                .filter(lastOutput -> lastOutput.getScriptPubKey().isOpReturn())
                .map(lastOutput -> lastOutput.getScriptPubKey().getChunks())
                .filter(chunks -> chunks.size() > 1)
                .map(chunks -> chunks.get(1).data)
                .anyMatch(data -> Arrays.equals(BondConsensus.getHashFromOpReturnData(data), bondedAsset.getHash()));
    }

    private boolean isUnlockTxUnconfirmed(R bondedAsset) {
        return getPendingWalletTransactionsStream()
                .filter(transaction -> transaction.getInputs().size() > 1)
                .map(transaction -> transaction.getInputs().get(0))
                .map(TransactionInput::getConnectedOutput)
                .filter(Objects::nonNull)
                .map(TransactionOutput::getParentTransaction)
                .filter(Objects::nonNull)
                .map(Transaction::getHashAsString)
                .flatMap(lockupTxId -> daoStateService.getLockupOpReturnTxOutput(lockupTxId).stream())
                .map(BaseTxOutput::getOpReturnData)
                .anyMatch(data -> Arrays.equals(BondConsensus.getHashFromOpReturnData(data), bondedAsset.getHash()));
    }

    private Stream<Transaction> getPendingWalletTransactionsStream() {
        return bsqWalletService.getWalletTransactions().stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING);
    }
}
