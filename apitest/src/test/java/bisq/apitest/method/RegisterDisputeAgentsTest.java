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

import bisq.proto.grpc.RegisterDisputeAgentRequest;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.common.app.DevEnv.DEV_PRIVILEGE_PRIV_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;


@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class RegisterDisputeAgentsTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        try {
            setUpScaffold(bitcoind, seednode, arbdaemon);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(1)
    public void testRegisterArbitratorShouldThrowException() {
        var req =
                createRegisterDisputeAgentRequest(ARBITRATOR);
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                grpcStubs(arbdaemon).disputeAgentsService.registerDisputeAgent(req));
        assertEquals("INVALID_ARGUMENT: arbitrators must be registered in a Bisq UI",
                exception.getMessage());
    }

    @Test
    @Order(2)
    public void testInvalidDisputeAgentTypeArgShouldThrowException() {
        var req =
                createRegisterDisputeAgentRequest("badagent");
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                grpcStubs(arbdaemon).disputeAgentsService.registerDisputeAgent(req));
        assertEquals("INVALID_ARGUMENT: unknown dispute agent type badagent",
                exception.getMessage());
    }

    @Test
    @Order(3)
    public void testInvalidRegistrationKeyArgShouldThrowException() {
        var req = RegisterDisputeAgentRequest.newBuilder()
                .setDisputeAgentType(REFUND_AGENT)
                .setRegistrationKey("invalid" + DEV_PRIVILEGE_PRIV_KEY).build();
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                grpcStubs(arbdaemon).disputeAgentsService.registerDisputeAgent(req));
        assertEquals("INVALID_ARGUMENT: invalid registration key",
                exception.getMessage());
    }

    @Test
    @Order(4)
    public void testRegisterMediator() {
        var req =
                createRegisterDisputeAgentRequest(MEDIATOR);
        grpcStubs(arbdaemon).disputeAgentsService.registerDisputeAgent(req);
    }

    @Test
    @Order(5)
    public void testRegisterRefundAgent() {
        var req =
                createRegisterDisputeAgentRequest(REFUND_AGENT);
        grpcStubs(arbdaemon).disputeAgentsService.registerDisputeAgent(req);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
