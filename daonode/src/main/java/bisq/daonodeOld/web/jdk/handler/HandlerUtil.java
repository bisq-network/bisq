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

package bisq.daonodeOld.web.jdk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;

import lombok.SneakyThrows;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;



import com.sun.net.httpserver.HttpExchange;

public class HandlerUtil {

    public static void setDefaultResponseHeaders(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    }

    public static void sendResponse(HttpExchange httpExchange, String response) throws IOException {
        sendResponse(200, httpExchange, response);
    }

    public static void sendResponse(int status, HttpExchange httpExchange, String response) throws IOException {
        setDefaultResponseHeaders(httpExchange);

        byte[] responseBytes = response.getBytes(UTF_8);
        httpExchange.sendResponseHeaders(status, responseBytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    // TODO make as function toWhat?
    public static String wrapResponse(String jsonData) {
        return format("{\"data\":%s}", jsonData);
    }

    // TODO make as function toErrorWhat?
    public static String wrapErrorResponse(String jsonError) {
        return format("{\"error\":%s}", jsonError);
    }

    @SneakyThrows
    public static String toJson(Object object) {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }
}
