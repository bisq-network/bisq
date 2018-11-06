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
import bisq.core.dao.governance.bond.BondService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BondedReputationService extends BondService<BondedReputation, Reputation> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedReputationService(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        super(daoStateService, bsqWalletService);

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<BondedReputation> getActiveBondedReputations() {
        return bondByUidMap.values().stream()
                .filter(e -> e.isActive())
                .collect(Collectors.toList());
    }

    public List<BondedReputation> getAllBondedReputations() {
        return new ArrayList<>(bondByUidMap.values());
    }

    public List<BondedReputation> getUnconfirmedBondedReputations() {
        //TODO
       /* Set<String> myWalletTransactionIds = bsqWalletService.getWalletTransactions().stream()
                .map(Transaction::getHashAsString)
                .collect(Collectors.toSet());
*/
        return bondByUidMap.values().stream()
                .filter(e -> e.isActive())
                .collect(Collectors.toList());
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
    protected void updateMap() {
        //TODO
     /*   bondByUidMap.clear();
        getBondedReputationStream().forEach(bondedReputation -> {
            bondByUidMap.put(bondedReputation.getBondedAsset().getUid(), bondedReputation);
        });*/
    }


    private Stream<BondedReputation> getBondedReputationStream() {
        return daoStateService.getLockupTxOutputs().stream()
                .map(lockupTxOutput -> {
                    String lockupTxId = lockupTxOutput.getTxId();
                    // long time = daoStateService.getTx(lockupTxId).map(BaseTx::getTime).orElse(0L);
                    // lockupTxOutput is first output, but we need the data from the opReturn
                    Optional<TxOutput> optionalOpReturnTxOutput = daoStateService.getLockupOpReturnTxOutput(lockupTxId);
                    if (optionalOpReturnTxOutput.isPresent()) {
                        TxOutput opReturnTxOutput = optionalOpReturnTxOutput.get();
                        byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnTxOutput.getOpReturnData());
                        Reputation reputation = new Reputation(hash);
                        BondedReputation bondedReputation = new BondedReputation(reputation);
                        //TODO
                        //updateBond(bondedReputation, reputation, lockupTxOutput);
                        return bondedReputation;
                    } else {
                        return null;
                    }

                })
                .filter(Objects::nonNull);
    }
}
