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

package bisq.core.dao.governance.bond.role;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.BondRepository;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Collect bonded roles from the evaluatedProposals from the daoState and provides access to the collection.
 */
@Slf4j
public class BondedRolesRepository extends BondRepository<BondedRole, Role> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRolesRepository(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        super(daoStateService, bsqWalletService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMyRole(Role role) {
        Set<String> myWalletTransactionIds = bsqWalletService.getClonedWalletTransactions().stream()
                .map(Transaction::getTxId)
                .map(Sha256Hash::toString)
                .collect(Collectors.toSet());
        return getAcceptedBondedRoleProposalStream()
                .filter(roleProposal -> roleProposal.getRole().equals(role))
                .map(Proposal::getTxId)
                .anyMatch(myWalletTransactionIds::contains);
    }

    public Optional<RoleProposal> getAcceptedBondedRoleProposal(Role role) {
        return getAcceptedBondedRoleProposalStream().filter(e -> e.getRole().getUid().equals(role.getUid())).findAny();
    }


    public List<BondedRole> getAcceptedBonds() {
        return bonds.stream()
                .filter(bondedRole -> getAcceptedBondedRoleProposal(bondedRole.getBondedAsset()).isPresent())
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected BondedRole createBond(Role role) {
        return new BondedRole(role);
    }

    @Override
    protected Stream<Role> getBondedAssetStream() {
        return getBondedRoleProposalStream().map(RoleProposal::getRole);
    }

    @Override
    protected void updateBond(BondedRole bond, Role bondedAsset, TxOutput lockupTxOutput) {
        // Lets see if we have a lock up tx.
        String lockupTxId = lockupTxOutput.getTxId();
        daoStateService.getTx(lockupTxId).ifPresent(lockupTx -> {
            byte[] opReturnData = lockupTx.getLastTxOutput().getOpReturnData();
            // We used the hash of the bonded bondedAsset object as our hash in OpReturn of the lock up tx to have a
            // unique binding of the tx to the data object.
            byte[] hash = BondConsensus.getHashFromOpReturnData(opReturnData);
            Optional<Role> candidate = findBondedAssetByHash(hash);
            if (candidate.isPresent() && bondedAsset.equals(candidate.get()))
                applyBondState(daoStateService, bond, lockupTx, lockupTxOutput);
        });
    }

    private Optional<Role> findBondedAssetByHash(byte[] hash) {
        return getBondedAssetStream()
                .filter(bondedAsset -> Arrays.equals(bondedAsset.getHash(), hash))
                .findAny();
    }

    private Stream<RoleProposal> getBondedRoleProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .map(e -> ((RoleProposal) e.getProposal()));
    }

    private Stream<RoleProposal> getAcceptedBondedRoleProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .filter(EvaluatedProposal::isAccepted)
                .map(e -> ((RoleProposal) e.getProposal()));
    }
}
