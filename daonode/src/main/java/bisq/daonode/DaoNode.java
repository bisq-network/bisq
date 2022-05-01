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

package bisq.daonode;

import bisq.core.app.misc.AppSetup;
import bisq.core.app.misc.AppSetupWithP2PAndDAO;
import bisq.core.dao.state.DaoStateService;
import bisq.core.network.p2p.inventory.GetInventoryRequestHandler;
import bisq.core.user.Preferences;

import protobuf.BaseTx;

import com.google.protobuf.util.JsonFormat;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.inject.Injector;

import java.net.InetSocketAddress;

import java.io.IOException;
import java.io.OutputStream;

import java.util.function.Supplier;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;



import com.sun.net.httpserver.HttpServer;

@Slf4j
public class DaoNode {
    @Setter
    private Injector injector;
    private AppSetup appSetup;
    private GetInventoryRequestHandler getInventoryRequestHandler;
    private HttpServer server;
    private DaoStateService daoStateService;

    public DaoNode() {
    }

    public void startApplication() {
        appSetup = injector.getInstance(AppSetupWithP2PAndDAO.class);
        injector.getInstance(Preferences.class).setUseFullModeDaoMonitor(false);
        appSetup.start();

        getInventoryRequestHandler = injector.getInstance(GetInventoryRequestHandler.class);
        daoStateService = injector.getInstance(DaoStateService.class);

        startServer();
        addGetHandler("getProofOfBurnTxs", this::getProofOfBurnTxs);
        addGetHandler("getLastBlock", this::getLastBlock);
    }

    private void addGetHandler(String path, Supplier<String> response) {
        server.createContext("/api/" + path, exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String responseText = response.get();
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

    private String getProofOfBurnTxs() {
        return toJson(daoStateService.getProofOfBurnTxs());
    }

    private String getLastBlock() {
        return toJson(daoStateService.getLastBlock().orElse(null));
    }

    @SneakyThrows
    private String toJson(Object object) {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    @SneakyThrows
    private String protoToJson(BaseTx proto) {
        return JsonFormat.printer().print(proto);
    }

    public void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(8082), 0);
            // server.setExecutor(Utilities.getSingleThreadExecutor("REST-API-Server"));
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutDown() {
        getInventoryRequestHandler.shutDown();
        if (server != null) {
            server.stop(0);
        }
    }
}
