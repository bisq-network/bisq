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

package bisq.core.api;

import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import java.nio.file.Path;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreDisputeAgentsServiceTest {
    @Test
    void aDisputeAgentTypeInUpperCaseIsKnownAlsoInATurkishDefaultLocale(@TempDir Path userDataDir) {
        Locale defaultLocale = Locale.getDefault();
        try {
            // In Turkish, the I of ARBITRATOR becomes a dotless i when it is lower-cased
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Config config = new Config("--" + Config.USER_DATA_DIR + "=" + userDataDir,
                    "--" + Config.BASE_CURRENCY_NETWORK + "=BTC_REGTEST",
                    "--" + Config.USE_LOCALHOST_FOR_P2P + "=true");
            P2PService p2PService = mock(P2PService.class);
            when(p2PService.isBootstrapped()).thenReturn(true);
            CoreDisputeAgentsService coreDisputeAgentsService = new CoreDisputeAgentsService(config,
                    mock(KeyRing.class),
                    mock(MediatorManager.class),
                    mock(RefundAgentManager.class),
                    p2PService);

            // The type is known, so the registration stops at the arbitrator, which the API no longer registers
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                    () -> coreDisputeAgentsService.registerDisputeAgent("ARBITRATOR",
                            DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
            assertEquals("legacy arbitrator registration is no longer supported", exception.getMessage());
        } finally {
            Locale.setDefault(defaultLocale);
            // The config sets the network of the JVM; the other tests expect the default, mainnet
            new Config("--" + Config.USER_DATA_DIR + "=" + userDataDir);
        }
    }
}
