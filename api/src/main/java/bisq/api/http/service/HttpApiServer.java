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

package bisq.api.http.service;

import bisq.api.http.exceptions.ExceptionMappers;
import bisq.api.http.service.auth.ApiPasswordManager;
import bisq.api.http.service.auth.AuthFilter;

import bisq.core.app.BisqEnvironment;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.inject.Inject;

import java.net.InetSocketAddress;
import java.net.URL;

import java.io.BufferedInputStream;
import java.io.IOException;

import java.util.EnumSet;

import lombok.extern.slf4j.Slf4j;



import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

@Slf4j
public class HttpApiServer {
    private final HttpApiInterfaceV1 httpApiInterfaceV1;
    private final BisqEnvironment bisqEnvironment;
    private final ApiPasswordManager apiPasswordManager;

    @Inject
    public HttpApiServer(ApiPasswordManager apiPasswordManager, BisqEnvironment bisqEnvironment, HttpApiInterfaceV1 httpApiInterfaceV1) {
        this.apiPasswordManager = apiPasswordManager;
        this.bisqEnvironment = bisqEnvironment;
        this.httpApiInterfaceV1 = httpApiInterfaceV1;
    }

    public void startServer() {
        try {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            contextHandlerCollection.setHandlers(new Handler[]{buildSwaggerUIHandler(), buildOpenAPIJsonHandler(), buildAPIHandler()});
            // Start server
            InetSocketAddress socketAddress = new InetSocketAddress(bisqEnvironment.getHttpApiHost(), bisqEnvironment.getHttpApiPort());
            Server server = new Server(socketAddress);
            server.setHandler(contextHandlerCollection);
            server.setRequestLog(new Slf4jRequestLog());
            server.start();
            log.info("HTTP API started on {}", socketAddress);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ContextHandler buildAPIHandler() {
        ResourceConfig resourceConfig = new ResourceConfig();
        ExceptionMappers.register(resourceConfig);
        resourceConfig.register(httpApiInterfaceV1);
        resourceConfig.packages("io.swagger.v3.jaxrs2.integration.resources");
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
        servletContextHandler.setContextPath("/");
        servletContextHandler.addServlet(new ServletHolder(new ServletContainer(resourceConfig)), "/*");
        setupAuth(servletContextHandler);
        return servletContextHandler;
    }

    private ContextHandler buildSwaggerUIHandler() throws Exception {
        ResourceHandler swaggerUIResourceHandler = new ResourceHandler();
        swaggerUIResourceHandler.setResourceBase(getClass().getClassLoader().getResource("META-INF/swagger/ui").toURI().toString());
        ContextHandler swaggerUIContext = new ContextHandler();
        swaggerUIContext.setContextPath("/docs");
        swaggerUIContext.setHandler(swaggerUIResourceHandler);
        return swaggerUIContext;
    }

    private ContextHandler buildOpenAPIJsonHandler() throws Exception {
        URL openAPIJSONResource = getClass().getClassLoader().getResource("META-INF/swagger/openapi.json");
        assert openAPIJSONResource != null;
        String openAPIJSONContent = new String(((BufferedInputStream) openAPIJSONResource.getContent()).readAllBytes());
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setContextPath("/");
        contextHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (!"/openapi.json".equals(target)) {
                    return;
                }
                response.setContentType(MimeTypes.Type.APPLICATION_JSON.asString());
                response.getWriter().write(openAPIJSONContent);
                baseRequest.setHandled(true);
            }
        });
        return contextHandler;
    }


    private void setupAuth(ServletContextHandler appContextHandler) {
        AuthFilter authFilter = new AuthFilter(apiPasswordManager);
        appContextHandler.addFilter(new FilterHolder(authFilter), "/*", EnumSet.allOf(DispatcherType.class));
    }
}
