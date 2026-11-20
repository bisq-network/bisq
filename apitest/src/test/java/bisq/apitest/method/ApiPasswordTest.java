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

import bisq.cli.GrpcClient;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The daemon accepts a call only with the configured api password.
 */
@Slf4j
public class ApiPasswordTest extends DockerMethodTest {

    @Test
    public void testIncorrectPasswordIsRefused() {
        String host = System.getProperty("apiHost.alice", "localhost");
        int port = Integer.parseInt(System.getProperty("apiPort.alice", "9998"));
        String password = System.getProperty("apiPassword", "xyz");
        GrpcClient client = new GrpcClient(host, port, password + "-incorrect");

        Throwable exception = assertThrows(StatusRuntimeException.class, client::getVersion);
        assertEquals("UNAUTHENTICATED: incorrect 'password' rpc header value", exception.getMessage());
    }
}
