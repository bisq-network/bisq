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

package bisq.daoNode.util;

import java.net.URI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Arrays;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;



import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * JDK Server needs handler for serving files, will change in JDK 18
 * Currently this is only to serve the swagger-ui content to the client.
 * So any call to this handler must begin with api/v1. We keep v1 in case
 * we will have incompatible changes in the future.
 * This handler is limited to html,css,json and javascript files.
 */
@Slf4j
public class StaticFileHandler implements HttpHandler {
    private static final String NOT_FOUND = "404 (Not Found)\n";
    public static final String[] VALID_SUFFIX = {".html", ".json", ".css", ".js"};

    @Getter
    protected final String rootContext;

    public StaticFileHandler(String rootContext) {
        this.rootContext = rootContext;
    }

    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();

        log.debug("requesting: " + uri.getPath());
        String filename = uri.getPath();
        if (filename == null || !filename.startsWith(rootContext) ||
                Arrays.stream(VALID_SUFFIX).noneMatch(filename::endsWith)) {
            respond404(exchange);
            return;
        }
        // resource loading without leading slash
        String resourceName = filename.replace("..", "");
        if (filename.charAt(0) == '/') {
            resourceName = filename.substring(1);
        }

        // we are using getResourceAsStream to ultimately prevent load from parent directories
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (resource == null) {
                respond404(exchange);
                return;
            }
            log.debug("sending: " + resourceName);
            // Object exists and is a file: accept with response code 200.
            String mime = "text/html";
            if (resourceName.endsWith(".js")) mime = "application/javascript";
            if (resourceName.endsWith(".json")) mime = "application/json";
            if (resourceName.endsWith(".css")) mime = "text/css";
            if (resourceName.endsWith(".png")) mime = "image/png";

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", mime);
            headers.add("Cache-Control", "max-age=3600"); // cache static content on browser for 3600 seconds
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                byte[] buffer = new byte[0x10000];
                int count;
                while ((count = resource.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, count);
                }
            }
        }
    }

    private void respond404(HttpExchange exchange) throws IOException {
        // Object does not exist or is not a file: reject with 404 error.
        exchange.sendResponseHeaders(404, NOT_FOUND.length());
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(NOT_FOUND.getBytes());
        }
    }
}
