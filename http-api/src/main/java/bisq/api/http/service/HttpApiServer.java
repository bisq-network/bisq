package bisq.api.http.service;

import bisq.core.app.BisqEnvironment;

import javax.inject.Inject;

import java.net.InetSocketAddress;

import lombok.extern.slf4j.Slf4j;



import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

@SuppressWarnings("Duplicates")
@Slf4j
public class HttpApiServer {
    private final HttpApiInterfaceV1 httpApiInterfaceV1;
    private final BisqEnvironment bisqEnvironment;

    @Inject
    public HttpApiServer(HttpApiInterfaceV1 httpApiInterfaceV1,
                         BisqEnvironment bisqEnvironment) {
        this.httpApiInterfaceV1 = httpApiInterfaceV1;
        this.bisqEnvironment = bisqEnvironment;
    }

    public void startServer() {
        try {
            ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();
            contextHandlerCollection.setHandlers(new Handler[]{buildAPIHandler(), buildSwaggerUIOverrideHandler(), buildSwaggerUIHandler()});
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
        resourceConfig.register(httpApiInterfaceV1);
        resourceConfig.packages("io.swagger.v3.jaxrs2.integration.resources");
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
        servletContextHandler.setContextPath("/");
        servletContextHandler.addServlet(new ServletHolder(new ServletContainer(resourceConfig)), "/*");
        return servletContextHandler;
    }

    private ContextHandler buildSwaggerUIOverrideHandler() throws Exception {
        ResourceHandler swaggerUIResourceHandler = new ResourceHandler();
        swaggerUIResourceHandler.setResourceBase(getClass().getClassLoader().getResource("META-INF/custom-swagger-ui").toURI().toString());
        ContextHandler swaggerUIContext = new ContextHandler();
        swaggerUIContext.setContextPath("/docs");
        swaggerUIContext.setHandler(swaggerUIResourceHandler);
        return swaggerUIContext;
    }

    private ContextHandler buildSwaggerUIHandler() throws Exception {
        ResourceHandler swaggerUIResourceHandler = new ResourceHandler();
        swaggerUIResourceHandler.setResourceBase(getClass().getClassLoader().getResource("META-INF/resources/webjars/swagger-ui/3.20.1").toURI().toString());
        ContextHandler swaggerUIContext = new ContextHandler();
        swaggerUIContext.setContextPath("/docs");
        swaggerUIContext.setHandler(swaggerUIResourceHandler);
        return swaggerUIContext;
    }
}
