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

package bisq.apitest.dao;

import bisq.cli.GrpcClient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Smoke check: the dispute-agent host container (the 4th node) successfully registers
 * MEDIATOR and REFUND_AGENT roles using the dev privilege key. We re-run registration
 * here defensively — the container entrypoint also does it on startup. Registration
 * is idempotent at the storage layer.
 */
public class DisputeAgentBootstrapTest extends DaoTestBase {

    /** Dev privilege key used to authorize dispute agent registration in regtest. */
    private static final String DEV_KEY =
            "6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a";

    @Test
    public void mediatorAndRefundAgentRegister() {
        // arb daemon is reachable on the host's published port (9997) when running
        // gradle outside docker; falls back to docker bridge hostname inside container.
        String host = System.getProperty("apiHost.arb", "localhost");
        int port = Integer.parseInt(System.getProperty("apiPort.arb", "9997"));
        String pw = System.getProperty("apiPassword", "xyz");
        GrpcClient arb = new GrpcClient(host, port, pw);

        // Registration should succeed (or be no-op if already registered).
        assertDoesNotThrow(() -> arb.registerDisputeAgent("mediator", DEV_KEY));
        assertDoesNotThrow(() -> arb.registerDisputeAgent("refundagent", DEV_KEY));
    }
}
