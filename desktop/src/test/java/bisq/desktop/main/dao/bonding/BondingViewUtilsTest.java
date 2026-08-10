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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.dao.bonding;

import bisq.desktop.Navigation;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.bond.reputation.MyReputationListService;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.util.coin.BsqFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javafx.beans.property.SimpleIntegerProperty;

import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BondingViewUtilsTest {
    private final P2PService p2PService = mock(P2PService.class);
    private final WalletsSetup walletsSetup = mock(WalletsSetup.class);
    private final BondedRolesRepository bondedRolesRepository = mock(BondedRolesRepository.class);
    private final DaoFacade daoFacade = mock(DaoFacade.class);

    private BondingViewUtils bondingViewUtils;

    @BeforeEach
    public void setup() {
        DevEnv.setDevMode(true);
        DevEnv.setIgnorePopupsInDevMode(true);
        when(p2PService.isBootstrapped()).thenReturn(true);
        when(p2PService.getNumConnectedPeers()).thenReturn(new SimpleIntegerProperty(1));
        when(walletsSetup.hasSufficientPeersForBroadcast()).thenReturn(true);
        when(walletsSetup.isDownloadComplete()).thenReturn(true);

        bondingViewUtils = new BondingViewUtils(
                p2PService,
                mock(MyReputationListService.class),
                bondedRolesRepository,
                walletsSetup,
                daoFacade,
                mock(Navigation.class),
                mock(BsqFormatter.class));
    }

    @AfterEach
    public void tearDown() {
        DevEnv.setDevMode(false);
    }

    @Test
    public void existingConfirmedBondDoesNotBlockAnotherRoleLockup() {
        Role role = new Role(
                "alice",
                "https://bisq.network/roles/81",
                BondedRoleType.NETLAYER_MAINTAINER);
        RoleProposal proposal = new RoleProposal(role, new TreeMap<>());
        Optional<RoleProposal> optionalProposal = Optional.of(proposal);
        when(bondedRolesRepository.getAcceptedBondedRoleProposal(role)).thenReturn(optionalProposal);
        when(bondedRolesRepository.canCreateNewLockup(role)).thenReturn(true);
        when(daoFacade.getRequiredBond(optionalProposal)).thenReturn(20_000L);
        @SuppressWarnings("unchecked")
        Consumer<String> resultHandler = mock(Consumer.class);

        bondingViewUtils.lockupBondForBondedRole(role, resultHandler);

        verify(daoFacade).publishLockupTx(
                eq(Coin.valueOf(20_000L)),
                eq(proposal.getUnlockTime()),
                eq(LockupReason.BONDED_ROLE),
                eq(role.getHash()),
                any(),
                any());
    }
}
