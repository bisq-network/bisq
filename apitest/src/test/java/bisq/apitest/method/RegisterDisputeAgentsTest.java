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

package bisq.apitest.method;

import bisq.common.app.DevEnv;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.ARBITRATOR;
import static bisq.apitest.config.ApiTestConfig.MEDIATOR;
import static bisq.apitest.config.ApiTestConfig.REFUND_AGENT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs against the docker-compose stack. Mediator + refund-agent are already
 * registered by the arb container's entrypoint, so the positive cases here only
 * verify that re-registration is idempotent. The negative cases (arbitrator
 * type, unknown type, bad key) remain meaningful regardless of prior state.
 */
@Slf4j
@SuppressWarnings("ResultOfMethodCallIgnored")
public class RegisterDisputeAgentsTest extends DockerMethodTest {

    @Test
    public void testRegisterArbitratorShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                arbClient.registerDisputeAgent(ARBITRATOR, DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
        assertEquals("UNIMPLEMENTED: legacy arbitrator registration is no longer supported",
                exception.getMessage());
    }

    @Test
    public void testInvalidDisputeAgentTypeArgShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                arbClient.registerDisputeAgent("badagent", DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
        assertEquals("INVALID_ARGUMENT: unknown dispute agent type 'badagent'",
                exception.getMessage());
    }

    @Test
    public void testInvalidRegistrationKeyArgShouldThrowException() {
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                arbClient.registerDisputeAgent(REFUND_AGENT, "invalid" + DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
        assertEquals("INVALID_ARGUMENT: invalid registration key",
                exception.getMessage());
    }

    @Test
    public void testRegisterMediatorIsIdempotent() {
        assertDoesNotThrow(() ->
                arbClient.registerDisputeAgent(MEDIATOR, DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
    }

    @Test
    public void testRegisterRefundAgentIsIdempotent() {
        assertDoesNotThrow(() ->
                arbClient.registerDisputeAgent(REFUND_AGENT, DevEnv.getDEV_PRIVILEGE_PRIV_KEY()));
    }
}
