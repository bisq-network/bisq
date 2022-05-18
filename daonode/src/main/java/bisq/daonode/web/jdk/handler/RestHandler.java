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

package bisq.daonode.web.jdk.handler;

import bisq.core.dao.state.DaoStateService;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static bisq.daonode.web.jdk.handler.HandlerUtil.sendResponse;
import static bisq.daonode.web.jdk.handler.HandlerUtil.toJson;
import static bisq.daonode.web.jdk.handler.HandlerUtil.wrapErrorResponse;
import static bisq.daonode.web.jdk.handler.ResourcePathElement.PROOFOFBURN;
import static java.util.Objects.requireNonNull;



import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * All HTTP requests are routed to a singleton RestHandler, then passed on to
 * appropriate sub handler instances.
 */
@Slf4j
public class RestHandler implements HttpHandler {

    private final DaoStateService daoStateService;

    public RestHandler(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            requireNonNull(httpExchange, "HttpExchange cannot be null.");

            // Parse the request URI details here, and pass them to a new
            // sub handler instance.
            RequestSpec requestSpec = new RequestSpec(httpExchange);

            // Atm we only use GET, no plans yet for allow POST for DaoNode API.
            if (!requestSpec.getMethod().equals("GET")) {
                sendResponse(405,
                        httpExchange,
                        wrapErrorResponse(toJson("Forbidden HTTP method " + requestSpec.getMethod())));
            } else if (requestSpec.isRequestingResource(PROOFOFBURN)) {
                new GetProofOfBurnHandler(daoStateService, requestSpec).handle(httpExchange);
            } else {
                sendResponse(404, httpExchange, wrapErrorResponse(toJson("Not Found")));
            }

        } catch (RuntimeException ex) {
            sendResponse(500,
                    httpExchange,
                    wrapErrorResponse(toJson(ex.getMessage())));
        } finally {
            httpExchange.close();
        }
    }
}
