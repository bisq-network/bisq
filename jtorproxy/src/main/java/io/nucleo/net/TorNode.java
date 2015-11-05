package io.nucleo.net;

import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OnionProxyManager;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;

public abstract class TorNode<M extends OnionProxyManager, C extends OnionProxyContext> {

    private static final String PROXY_LOCALHOST = "127.0.0.1";
    private static final int RETRY_SLEEP = 500;
    private static final int TOTAL_SEC_PER_STARTUP = 4 * 60;
    private static final int TRIES_PER_STARTUP = 5;
    private static final int TRIES_PER_HS_STARTUP = 150;

    private static final Logger log = LoggerFactory.getLogger(TorNode.class);

    private final OnionProxyManager tor;
    private final Socks5Proxy proxy;

    @SuppressWarnings("unchecked")
    public TorNode(File torDirectory) throws IOException, InstantiationException {
        Class<M> mgr;
        Class<C> ctx;
        try {
            mgr = (Class<M>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            ctx = (Class<C>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        } catch (Throwable t) {
            throw new InstantiationException(
                    "Could not reify Types of OnionProxyManager and OnionProxyContext! Is this class being used with raw types?");
        }
        log.debug("Running Tornode with " + mgr.getSimpleName() + " and  " + ctx.getSimpleName());
        tor = initTor(torDirectory, mgr, ctx);
        int proxyPort = tor.getIPv4LocalHostSocksPort();
        log.info("TorSocks running on port " + proxyPort);
        this.proxy = setupSocksProxy(proxyPort);
    }

    private Socks5Proxy setupSocksProxy(int proxyPort) throws UnknownHostException {
        Socks5Proxy proxy = new Socks5Proxy(PROXY_LOCALHOST, proxyPort);
        proxy.resolveAddrLocally(false);
        return proxy;
    }

    public Socket connectToHiddenService(String onionUrl, int port) throws IOException {
        return connectToHiddenService(onionUrl, port, 5);
    }

    public Socket connectToHiddenService(String onionUrl, int port, int numTries) throws IOException {
        return connectToHiddenService(onionUrl, port, numTries, true);
    }

    private Socket connectToHiddenService(String onionUrl, int port, int numTries, boolean debug) throws IOException {
        long before = GregorianCalendar.getInstance().getTimeInMillis();
        for (int i = 0; i < numTries; ++i) {
            try {
                SocksSocket ssock = new SocksSocket(proxy, onionUrl, port);
                if (debug)
                    log.info("Took " + (GregorianCalendar.getInstance().getTimeInMillis() - before)
                            + " milliseconds to connect to " + onionUrl + ":" + port);
                ssock.setTcpNoDelay(true);
                return ssock;
            } catch (UnknownHostException exx) {
                try {
                    if (debug)
                        log.debug(
                                "Try " + (i + 1) + " connecting to " + onionUrl + ":" + port + " failed. retrying...");
                    Thread.sleep(RETRY_SLEEP);
                    continue;
                } catch (InterruptedException e) {
                }
            } catch (Exception e) {
                throw new IOException("Cannot connect to hidden service");
            }
        }
        throw new IOException("Cannot connect to hidden service");
    }

    public HiddenServiceDescriptor createHiddenService(int localPort, int servicePort) throws IOException {
        long before = GregorianCalendar.getInstance().getTimeInMillis();
        String hiddenServiceName = tor.publishHiddenService(servicePort, localPort);
        final HiddenServiceDescriptor hiddenServiceDescriptor = new HiddenServiceDescriptor(hiddenServiceName,
                localPort, servicePort);
        return tryConnectToHiddenService(servicePort, before, hiddenServiceName, hiddenServiceDescriptor);

    }

    private HiddenServiceDescriptor tryConnectToHiddenService(int servicePort, long before, String hiddenServiceName,
                                                              final HiddenServiceDescriptor hiddenServiceDescriptor) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    hiddenServiceDescriptor.getServerSocket().accept().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        for (int i = 0; i < TRIES_PER_HS_STARTUP; ++i) {
            try {
                final Socket socket = connectToHiddenService(hiddenServiceName, servicePort, 1, false);
                socket.close();
            } catch (IOException e) {
                log.info("Hidden service " + hiddenServiceName + ":" + servicePort + " is not yet reachable");
                try {
                    Thread.sleep(RETRY_SLEEP);
                } catch (InterruptedException e1) {
                }
                continue;
            }
            log.info("Took " + (GregorianCalendar.getInstance().getTimeInMillis() - before)
                    + " milliseconds to connect to publish " + hiddenServiceName + ":" + servicePort);
            return hiddenServiceDescriptor;
        }
        throw new IOException("Could not publish Hidden Service!");
    }

    public HiddenServiceDescriptor createHiddenService(int port) throws IOException {
        return createHiddenService(port, port);
    }

    public void shutdown() throws IOException {
        tor.stop();
    }

    static <M extends OnionProxyManager, C extends OnionProxyContext> OnionProxyManager initTor(File torDir,
                                                                                                Class<M> mgrType, Class<C> ctxType) throws IOException {

        log.debug("Trying to start tor in directory {}", torDir);
        C ctx;
        final M onionProxyManager;
        try {
            ctx = ctxType.getConstructor(File.class).newInstance(torDir);
            onionProxyManager = mgrType.getConstructor(OnionProxyContext.class).newInstance(ctx);
        } catch (Exception e1) {
            throw new IOException(e1);
        }

        try {
            if (!onionProxyManager.startWithRepeat(TOTAL_SEC_PER_STARTUP, TRIES_PER_STARTUP)) {
                throw new IOException("Could not Start Tor. Is another instance already running?");
            } else {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        try {
                            onionProxyManager.stop();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        return onionProxyManager;
    }
}
