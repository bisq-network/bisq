package io.nucleo.net;

import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OnionProxyManager;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;

public abstract class TorNode<M extends OnionProxyManager, C extends OnionProxyContext> {

    static final String PROXY_LOCALHOST = "127.0.0.1";

    private static final int RETRY_SLEEP = 500;
    private static final int TOTAL_SEC_PER_STARTUP = 4 * 60;
    private static final int TRIES_PER_STARTUP = 5;

    private static final Logger log = LoggerFactory.getLogger(TorNode.class);

    private final OnionProxyManager tor;
    private final Socks5Proxy proxy;

    public TorNode(M mgr) throws IOException {
        OnionProxyContext ctx = mgr.getOnionProxyContext();
        log.debug("Running Tornode with " + mgr.getClass().getSimpleName() + " and  " + ctx.getClass().getSimpleName());
        tor = initTor(mgr, ctx);
        int proxyPort = tor.getIPv4LocalHostSocksPort();
        log.debug("TorSocks running on port " + proxyPort);
        this.proxy = setupSocksProxy(proxyPort);
    }

    public Socks5Proxy getSocksProxy() {
        return proxy;
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
                    log.debug("Took " + (GregorianCalendar.getInstance().getTimeInMillis() - before)
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

    public void addHiddenServiceReadyListener(HiddenServiceDescriptor hiddenServiceDescriptor,
                                              HiddenServiceReadyListener listener) throws IOException {
        tor.attachHiddenServiceReadyListener(hiddenServiceDescriptor, listener);
    }

    public HiddenServiceDescriptor createHiddenService(final int localPort, final int servicePort) throws IOException {
        return createHiddenService(localPort, servicePort, null);
    }

    public HiddenServiceDescriptor createHiddenService(final int localPort, final int servicePort,
                                                       final HiddenServiceReadyListener listener) throws IOException {
        log.debug("Publishing Hidden Service. This will at least take half a minute...");
        final String hiddenServiceName = tor.publishHiddenService(servicePort, localPort);
        final HiddenServiceDescriptor hiddenServiceDescriptor = new HiddenServiceDescriptor(hiddenServiceName,
                localPort, servicePort);
        if (listener != null)
            tor.attachHiddenServiceReadyListener(hiddenServiceDescriptor, listener);
        return hiddenServiceDescriptor;
    }

    public HiddenServiceDescriptor createHiddenService(int port, HiddenServiceReadyListener listener)
            throws IOException {
        return createHiddenService(port, port, listener);
    }

    public void shutdown() throws IOException {
        tor.stop();
    }

    static <M extends OnionProxyManager, C extends OnionProxyContext> OnionProxyManager initTor(final M mgr, C ctx)
            throws IOException {

        log.debug("Trying to start tor in directory {}", mgr.getWorkingDirectory());

        try {
            if (!mgr.startWithRepeat(TOTAL_SEC_PER_STARTUP, TRIES_PER_STARTUP)) {
                throw new IOException("Could not Start Tor.");
            } else {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        try {
                            mgr.stop();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        return mgr;
    }
}
