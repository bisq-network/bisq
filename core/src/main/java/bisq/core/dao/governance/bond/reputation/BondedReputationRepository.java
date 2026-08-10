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
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondRepository;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;

import javax.inject.Inject;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Collect bonded reputations from the daoState blockchain data and provides access to the collection.
 * Only lockups carrying the {@link LockupReason#REPUTATION} reason are bonded reputations, so the collection is
 * independent of the bonded roles.
 * Gets updated after a new block is parsed or at bsqWallet transaction change to detect also state changes by
 * unconfirmed txs.
 */
@Slf4j
public class BondedReputationRepository extends BondRepository<BondedReputation, Reputation> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedReputationRepository(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        super(daoStateService, bsqWalletService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected BondedReputation createBond(Reputation reputation) {
        return new BondedReputation(reputation);
    }

    @Override
    protected Stream<Reputation> getBondedAssetStream() {
        return getBondedReputationStream().map(Bond::getBondedAsset);
    }

    @Override
    protected void update() {
        bondByUidMap.clear();
        getBondedReputationStream().forEach(bondedReputation -> bondByUidMap.put(bondedReputation.getBondedAsset().getUid(), bondedReputation));
        bonds.setAll(bondByUidMap.values());
    }

    public Stream<BondedReputation> getBondedReputationStream() {
        return getLockupTxOutputsForBondedReputation()
                .map(lockupTxOutput -> {
                    String lockupTxId = lockupTxOutput.getTxId();
                    Optional<TxOutput> optionalOpReturnTxOutput = daoStateService.getLockupOpReturnTxOutput(lockupTxId);
                    if (optionalOpReturnTxOutput.isPresent()) {
                        TxOutput opReturnTxOutput = optionalOpReturnTxOutput.get();
                        byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnTxOutput.getOpReturnData());
                        Reputation reputation = new Reputation(hash);
                        BondedReputation bondedReputation = new BondedReputation(reputation);
                        updateBond(bondedReputation, reputation, lockupTxOutput);
                        return bondedReputation;
                    } else {
                        return null;
                    }

                })
                .filter(Objects::nonNull);
    }

    private Stream<TxOutput> getLockupTxOutputsForBondedReputation() {
        return daoStateService.getLockupTxOutputs().stream()
                .filter(lockupTxOutput -> daoStateService.getLockupOpReturnTxOutput(lockupTxOutput.getTxId())
                        .map(TxOutput::getOpReturnData)
                        .flatMap(BondConsensus::getLockupReason)
                        .filter(LockupReason.REPUTATION::equals)
                        .isPresent());
    }

    @Override
    protected void updateBond(BondedReputation bond, Reputation bondedAsset, TxOutput lockupTxOutput) {
        // Lets see if we have a lock up tx.
        String lockupTxId = lockupTxOutput.getTxId();
        daoStateService.getTx(lockupTxId).ifPresent(lockupTx -> {
            BondRepository.applyBondState(daoStateService, bond, lockupTx, lockupTxOutput);
        });
    }
}
