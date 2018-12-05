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
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Collect bonded reputations from the daoState blockchain data excluding bonded roles
 * and provides access to the collection.
 * Gets updated after a new block is parsed or at bsqWallet transaction change to detect also state changes by
 * unconfirmed txs.
 */
@Slf4j
public class BondedReputationRepository extends BondRepository<BondedReputation, Reputation> {
    private final BondedRolesRepository bondedRolesRepository;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedReputationRepository(DaoStateService daoStateService, BsqWalletService bsqWalletService,
                                      BondedRolesRepository bondedRolesRepository) {
        super(daoStateService, bsqWalletService);

        this.bondedRolesRepository = bondedRolesRepository;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        super.addListeners();

        // As event listeners do not have a deterministic ordering of callback we need to ensure
        // that we get updated our data after the bondedRolesRepository was updated.
        // The update gets triggered by daoState or wallet changes. It could be that we get triggered first the
        // listeners and update our data with stale data from bondedRolesRepository. After that the bondedRolesRepository
        // gets triggered the listeners and we would miss the current state if we would not listen here as well on the
        // bond list.
        bondedRolesRepository.getBonds().addListener((ListChangeListener<BondedRole>) c -> update());
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

    private Stream<BondedReputation> getBondedReputationStream() {
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
        // We exclude bonded roles, so we store those in a lookup set.
        Set<String> bondedRolesLockupTxIdSet = bondedRolesRepository.getBonds().stream().map(Bond::getLockupTxId).collect(Collectors.toSet());
        return daoStateService.getLockupTxOutputs().stream()
                .filter(e -> !bondedRolesLockupTxIdSet.contains(e.getTxId()));
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
