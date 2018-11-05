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
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.BondService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages bonded roles if they got accepted by voting.
 */
@Slf4j
public class BondedRolesService extends BondService<BondedRole, Role> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRolesService(DaoStateService daoStateService, BsqWalletService bsqWalletService) {
        super(daoStateService, bsqWalletService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMyRole(Role role) {
        Set<String> myWalletTransactionIds = bsqWalletService.getWalletTransactions().stream()
                .map(Transaction::getHashAsString)
                .collect(Collectors.toSet());
        return getBondedRoleProposalStream()
                .filter(roleProposal -> roleProposal.getRole().equals(role))
                .map(Proposal::getTxId)
                .anyMatch(myWalletTransactionIds::contains);
    }

    public Optional<BondedRoleType> getBondedRoleType(String lockUpTxId) {
        return findBondByLockupTxId(lockUpTxId)
                .map(Bond::getBondedAsset)
                .map(Role::getBondedRoleType);
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

    private Stream<RoleProposal> getBondedRoleProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RoleProposal)
                .map(e -> ((RoleProposal) e.getProposal()));
    }
}
