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

package bisq.daonode.service;

import bisq.core.dao.state.DaoStateService;

import bisq.common.util.Utilities;

import java.net.InetSocketAddress;

import java.io.IOException;
import java.io.OutputStream;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;



import bisq.daonode.controller.ProofOfBurnController;
import bisq.daonode.controller.RestController;
import com.sun.net.httpserver.HttpServer;

// Todo We should limit usage to localhost as its not intended at that stage to be used
// as a public API, but rather be used by Bisq 2 bridge clients or BSQ explorer nodes,
// both running in a localhost environment. As long that holds, we do not require a high
// level of protection against malicious usage.

// TODO This http server is a super simple implementation. We might use some other
// lightweight REST server framework.
@Slf4j
public class DaoNodeService {
    private HttpServer server;
    private DaoStateService daoStateService;

    public DaoNodeService(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            // As use case is intended for a 1 client environment we can stick with a
            // single thread.
            server.setExecutor(Utilities.getSingleThreadExecutor("REST-API-Server"));
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        addController("get-proof-of-burn-data", new ProofOfBurnController(daoStateService));
    }

    private void addController(String path, RestController controller) {
        server.createContext("/api/" + path, exchange -> {
            // Atm we only use GET, no plans yet for allow POST for that API
            if ("GET".equals(exchange.getRequestMethod())) {
                // todo: Atm we use/support only standard queries (?1234), not REST-like
                // path segments as query (e.g. getProofOfBurnDtoList/from/1234
                Optional<String> query = Optional.ofNullable(exchange.getRequestURI().getQuery());
                String responseText = controller.getResponse(query);
                exchange.sendResponseHeaders(200, responseText.getBytes().length);
                OutputStream output = exchange.getResponseBody();
                output.write(responseText.getBytes());
                output.flush();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        });
    }

    public void shutDown() {
        if (server != null) {
            server.stop(0);
        }
    }
}
