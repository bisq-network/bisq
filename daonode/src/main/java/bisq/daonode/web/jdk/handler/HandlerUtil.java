package bisq.daonode.web.jdk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;

import lombok.SneakyThrows;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;



import com.sun.net.httpserver.HttpExchange;

class HandlerUtil {

    static void setDefaultResponseHeaders(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    }

    static void sendResponse(HttpExchange httpExchange, String response) throws IOException {
        sendResponse(200, httpExchange, response);
    }

    static void sendResponse(int status, HttpExchange httpExchange, String response) throws IOException {
        setDefaultResponseHeaders(httpExchange);

        byte[] responseBytes = response.getBytes(UTF_8);
        httpExchange.sendResponseHeaders(status, responseBytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    // TODO make as function toWhat?
    static String wrapResponse(String jsonData) {
        return format("{\"data\":%s}", jsonData);
    }

    // TODO make as function toErrorWhat?
    static String wrapErrorResponse(String jsonError) {
        return format("{\"error\":%s}", jsonError);
    }

    @SneakyThrows
    static String toJson(Object object) {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }
}
