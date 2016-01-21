package com.runjva.sourceforge.jsocks.protocol;

import java.io.IOException;
import java.net.*;

/**
 * SocksServerSocket allows to accept connections from one particular host
 * through the SOCKS4 or SOCKS5 proxy.
 */
public class SocksServerSocket extends ServerSocket {
    // Data members
    protected SocksProxyBase proxy;
    protected String localHost;
    protected InetAddress localIP;
    protected int localPort;

    boolean doing_direct = false;
    InetAddress remoteAddr;

    /**
     * Creates ServerSocket capable of accepting one connection through the
     * firewall, uses default Proxy.
     *
     * @param host Host from which the connection should be recieved.
     * @param port Port number of the primary connection.
     */
    public SocksServerSocket(String host, int port) throws SocksException,
            UnknownHostException, IOException {
        this(SocksProxyBase.defaultProxy, host, port);
    }

    /**
     * Creates ServerSocket capable of accepting one connection through the
     * firewall, uses given proxy.
     *
     * @param p    Proxy object to use.
     * @param host Host from which the connection should be recieved.
     * @param port Port number of the primary connection.
     */
    public SocksServerSocket(SocksProxyBase p, String host, int port)
            throws SocksException, UnknownHostException, IOException {

        super(0);
        if (p == null) {
            throw new SocksException(SocksProxyBase.SOCKS_NO_PROXY);
        }
        // proxy=p;
        proxy = p.copy();
        if (proxy.isDirect(host)) {
            remoteAddr = InetAddress.getByName(host);
            proxy = null;
            doDirect();
        } else {
            processReply(proxy.bind(host, port));
        }
    }

    /**
     * Creates ServerSocket capable of accepting one connection through the
     * firewall, uses default Proxy.
     *
     * @param ip   Host from which the connection should be recieved.
     * @param port Port number of the primary connection.
     */
    public SocksServerSocket(InetAddress ip, int port) throws SocksException,
            IOException {
        this(SocksProxyBase.defaultProxy, ip, port);
    }

    /**
     * Creates ServerSocket capable of accepting one connection through the
     * firewall, uses given proxy.
     *
     * @param p    Proxy object to use.
     * @param ip   Host from which the connection should be recieved.
     * @param port Port number of the primary connection.
     */
    public SocksServerSocket(SocksProxyBase p, InetAddress ip, int port)
            throws SocksException, IOException {
        super(0);

        if (p == null) {
            throw new SocksException(SocksProxyBase.SOCKS_NO_PROXY);
        }
        this.proxy = p.copy();

        if (proxy.isDirect(ip)) {
            remoteAddr = ip;
            doDirect();
        } else {
            processReply(proxy.bind(ip, port));
        }
    }

    /**
     * Accepts the incoming connection.
     */
    public Socket accept() throws IOException {
        Socket s;

        if (!doing_direct) {
            if (proxy == null) {
                return null;
            }

            final ProxyMessage msg = proxy.accept();
            s = msg.ip == null ? new SocksSocket(msg.host, msg.port, proxy)
                    : new SocksSocket(msg.ip, msg.port, proxy);
            // Set timeout back to 0
            proxy.proxySocket.setSoTimeout(0);
        } else { // Direct Connection

            // Mimic the proxy behaviour,
            // only accept connections from the speciefed host.
            while (true) {
                s = super.accept();
                if (s.getInetAddress().equals(remoteAddr)) {
                    // got the connection from the right host
                    // Close listenning socket.
                    break;
                } else {
                    s.close(); // Drop all connections from other hosts
                }
            }

        }
        proxy = null;
        // Return accepted socket
        return s;
    }

    /**
     * Closes the connection to proxy if socket have not been accepted, if the
     * direct connection is used, closes direct ServerSocket. If the client
     * socket have been allready accepted, does nothing.
     */
    public void close() throws IOException {
        super.close();
        if (proxy != null) {
            proxy.endSession();
        }
        proxy = null;
    }

    /**
     * Get the name of the host proxy is using to listen for incoming
     * connection.
     * <p>
     * Usefull when address is returned by proxy as the hostname.
     *
     * @return the hostname of the address proxy is using to listen for incoming
     * connection.
     */
    public String getHost() {
        return localHost;
    }

    /**
     * Get address assigned by proxy to listen for incomming connections, or the
     * local machine address if doing direct connection.
     */
    public InetAddress getInetAddress() {
        if (localIP == null) {
            try {
                localIP = InetAddress.getByName(localHost);
            } catch (final UnknownHostException e) {
                return null;
            }
        }
        return localIP;
    }

    /**
     * Get port assigned by proxy to listen for incoming connections, or the
     * port chosen by local system, if accepting directly.
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * Set Timeout.
     *
     * @param timeout Amount of time in milliseconds, accept should wait for
     *                incoming connection before failing with exception. Zero
     *                timeout implies infinity.
     */
    public void setSoTimeout(int timeout) throws SocketException {
        super.setSoTimeout(timeout);
        if (!doing_direct) {
            proxy.proxySocket.setSoTimeout(timeout);
        }
    }

    // Private Methods
    // ////////////////

    private void processReply(ProxyMessage reply) throws SocksException {
        localPort = reply.port;
        /*
         * If the server have assigned same host as it was contacted on it might
		 * return an address of all zeros
		 */
        if (reply.host.equals("0.0.0.0")) {
            localIP = proxy.proxyIP;
            localHost = localIP.getHostName();
        } else {
            localHost = reply.host;
            localIP = reply.ip;
        }
    }

    private void doDirect() {
        doing_direct = true;
        localPort = super.getLocalPort();
        localIP = super.getInetAddress();
        localHost = localIP.getHostName();
    }

}
