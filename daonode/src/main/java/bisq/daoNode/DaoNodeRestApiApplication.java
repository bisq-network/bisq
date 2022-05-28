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

package bisq.daoNode;

import java.net.URI;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;



import bisq.daoNode.endpoints.ProofOfBurnApi;
import bisq.daoNode.error.CustomExceptionMapper;
import bisq.daoNode.error.StatusException;
import bisq.daoNode.util.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Application to start and config the rest service.
 * This creates and rest service at BASE_URL for clients to connect and for users to browse the documentation.
 * <p>
 * Swagger doc are available at <a href="http://localhost:8082/doc/v1/index.html">REST API documentation</a>
 */
@Slf4j
public class DaoNodeRestApiApplication extends ResourceConfig {
    public static final String BASE_URL = "http://localhost:8082/api/v1";

    public static void main(String[] args) throws Exception {
        DaoNodeRestApiApplication daoNodeRestApiApplication = new DaoNodeRestApiApplication(args);
        daoNodeRestApiApplication
                .register(CustomExceptionMapper.class)
                .register(StatusException.StatusExceptionMapper.class)
                .register(ProofOfBurnApi.class)
                .register(SwaggerResolution.class);

        daoNodeRestApiApplication.startServer();
    }

    @Getter
    private final DaoNodeExecutable daoNodeExecutable;
    private HttpServer httpServer;

    public DaoNodeRestApiApplication(String[] args) {
        daoNodeExecutable = new DaoNodeExecutable();
        new Thread(() -> {
            daoNodeExecutable.execute(args);
        }).start();
    }

    private void startServer() throws Exception {
        httpServer = JdkHttpServerFactory.createHttpServer(URI.create(BASE_URL), this);
        httpServer.createContext("/doc", new StaticFileHandler("/doc/v1/"));

        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));

        log.info("Server started at {}.", BASE_URL);

        // block and wait shut down signal, like CTRL+C
        Thread.currentThread().join();

        stopServer();
    }

    private void stopServer() {
        daoNodeExecutable.gracefulShutDown(() -> {
            httpServer.stop(1);
        });
    }
}
