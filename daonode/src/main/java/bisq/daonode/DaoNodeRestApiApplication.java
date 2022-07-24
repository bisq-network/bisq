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

import bisq.common.config.Config;

import java.net.URI;

import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;



import bisq.daonode.endpoints.BondedReputationApi;
import bisq.daonode.endpoints.ProofOfBurnApi;
import bisq.daonode.error.CustomExceptionMapper;
import bisq.daonode.error.StatusException;
import bisq.daonode.util.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Application to start and config the rest service.
 * This creates a rest service for clients to connect and for users to browse the documentation.
 * <p>
 * Swagger doc are available at <a href="http://localhost:8082/doc/v1/index.html">REST API documentation</a>
 */
@Slf4j
public class DaoNodeRestApiApplication extends ResourceConfig {
    @Getter
    private static String baseUrl;

    public static void main(String[] args) throws Exception {
        DaoNodeRestApiApplication daoNodeRestApiApplication = new DaoNodeRestApiApplication();
        daoNodeRestApiApplication.startDaoNode(args, config -> {
            daoNodeRestApiApplication
                    .register(CustomExceptionMapper.class)
                    .register(StatusException.StatusExceptionMapper.class)
                    .register(ProofOfBurnApi.class)
                    .register(BondedReputationApi.class)
                    .register(SwaggerResolution.class);
            daoNodeRestApiApplication.startServer(config.daoNodeApiUrl, config.daoNodeApiPort);
        });
    }


    @Getter
    private final DaoNode daoNode;

    private HttpServer httpServer;

    public DaoNodeRestApiApplication() {
        daoNode = new DaoNode();
    }

    private void startDaoNode(String[] args, Consumer<Config> configConsumer) {
        new Thread(() -> {
            daoNode.execute(args);
            configConsumer.accept(daoNode.getConfig());
            try {
                // Keep running
                Thread.currentThread().setName("daoNodeThread");
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                log.error("daoNodeThread interrupted", e);
                e.printStackTrace();
                shutDown();
            }
        }).start();
    }

    private void startServer(String url, int port) {
        baseUrl = url + ":" + port + "/api/v1";
        httpServer = JdkHttpServerFactory.createHttpServer(URI.create(baseUrl), this);
        httpServer.createContext("/doc", new StaticFileHandler("/doc/v1/"));

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown));

        log.info("Server started at {}.", baseUrl);

        // block and wait shut down signal, like CTRL+C
        try {
            Thread.currentThread().setName("serverThread");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("serverThread interrupted", e);
            System.exit(1);
        }

        shutDown();
    }

    private void shutDown() {
        if (daoNode != null) {
            daoNode.gracefulShutDown(this::stopServer);
        } else {
            stopServer();
        }
    }

    private void stopServer() {
        if (httpServer != null) {
            httpServer.stop(1);
        }
    }
}
