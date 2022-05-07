package bisq.daonode.web.jdk;

import bisq.core.dao.state.DaoStateService;

import bisq.common.util.Utilities;

import java.net.InetSocketAddress;

import java.io.IOException;

import java.util.concurrent.Executor;

import lombok.extern.slf4j.Slf4j;

import static bisq.daonode.web.jdk.handler.ResourcePathElement.DAONODE;



import bisq.daonode.web.WebServer;
import bisq.daonode.web.jdk.handler.RestHandler;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// https://dev.to/piczmar_0/framework-less-rest-api-in-java-1jbl

// https://doc.networknt.com/getting-started/light-rest-4j
// https://github.com/piczmar/pure-java-rest-api
// https://stackoverflow.com/questions/3732109/simple-http-server-in-java-using-only-java-se-api
// https://www.programcreek.com/java-api-examples/index.php?api=com.sun.net.httpserver.HttpServer

/**
 * From https://stackoverflow.com/questions/3732109/simple-http-server-in-java-using-only-java-se-api
 *
 * Note that this is, in contrary to what some developers think, absolutely not forbidden
 * by the well known FAQ Why Developers Should Not Write Programs That Call 'sun' Packages.
 * That FAQ concerns the sun.* package (such as sun.misc.BASE64Encoder) for internal usage
 * by the Oracle JRE (which would thus kill your application when you run it on a different
 * JRE), not the com.sun.* package. Sun/Oracle also just develop software on top of the
 * Java SE API themselves like as every other company such as Apache and so on. Moreover,
 * this specific HttpServer must be present in every JDK so there is absolutely no means
 * of "portability" issue like as would happen with sun.* package. Using com.sun.* classes
 * is only discouraged (but not forbidden) when it concerns an implementation of a certain
 * Java API, such as GlassFish (Java EE impl), Mojarra (JSF impl), Jersey (JAX-RS impl), etc.
 */
@Slf4j
public class JdkServer extends HttpServer implements WebServer {

    public static void main(String[] args) throws InterruptedException {
        WebServer webServer = new JdkServer(8080, null);
        webServer.start();
        Thread.sleep(40000);
        webServer.stop(0);
    }

    private final int port;
    private final DaoStateService daoStateService;

    private HttpServer server;

    public JdkServer(int port, DaoStateService daoStateService) {
        this.port = port;
        this.daoStateService = daoStateService;
        configure();
    }

    private void configure() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
            // As use case is intended for a 1 client environment we can stick with a single thread.
            setExecutor(Utilities.getSingleThreadExecutor("DaoNode-API"));
            // Map all request URLs starting with "/daonode" to a single RestHandler.
            // The RestHandler will pass valid requests on to an appropriate handler.
            createContext("/" + DAONODE, new RestHandler(daoStateService));
        } catch (IOException ex) {
            log.error(ex.toString());
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void bind(InetSocketAddress addr, int backlog) throws IOException {
        server.bind(addr, backlog);
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void setExecutor(Executor executor) {
        server.setExecutor(executor);
    }

    @Override
    public Executor getExecutor() {
        return server.getExecutor();
    }

    @Override
    public HttpContext createContext(String path, HttpHandler handler) {
        return server.createContext(path, handler);
    }

    @Override
    public HttpContext createContext(String path) {
        return server.createContext(path);
    }

    @Override
    public void removeContext(String path) throws IllegalArgumentException {
        server.removeContext(path);
    }

    @Override
    public void removeContext(HttpContext context) {
        server.removeContext(context);
    }

    @Override
    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    @Override
    public void stop(int delay) {
        if (server != null) {
            server.stop(0);
        }
    }
}
