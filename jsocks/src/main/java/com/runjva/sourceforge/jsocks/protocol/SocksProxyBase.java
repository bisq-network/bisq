package com.runjva.sourceforge.jsocks.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Abstract class Proxy, base for classes Socks4Proxy and Socks5Proxy. Defines
 * methods for specifying default proxy, to be used by all classes of this
 * package.
 */

public abstract class SocksProxyBase {

    // Data members
    protected InetRange directHosts = new InetRange();

    protected InetAddress proxyIP = null;
    protected String proxyHost = null;
    protected int proxyPort;
    protected Socket proxySocket = null;

    protected InputStream in;
    protected OutputStream out;

    protected int version;

    protected SocksProxyBase chainProxy = null;

    // Protected static/class variables
    protected static SocksProxyBase defaultProxy = null;

    // Constructors
    // ====================
    SocksProxyBase(SocksProxyBase chainProxy, String proxyHost, int proxyPort)
            throws UnknownHostException {
        this.chainProxy = chainProxy;
        this.proxyHost = proxyHost;

        if (chainProxy == null) {
            this.proxyIP = InetAddress.getByName(proxyHost);
        }

        this.proxyPort = proxyPort;
    }

    SocksProxyBase(String proxyHost, int proxyPort) throws UnknownHostException {
        this(null, proxyHost, proxyPort);
    }

    SocksProxyBase(SocksProxyBase chainProxy, InetAddress proxyIP, int proxyPort) {
        this.chainProxy = chainProxy;
        this.proxyIP = proxyIP;
        this.proxyPort = proxyPort;
    }

    SocksProxyBase(InetAddress proxyIP, int proxyPort) {
        this(null, proxyIP, proxyPort);
    }

    SocksProxyBase(SocksProxyBase p) {
        this.proxyIP = p.proxyIP;
        this.proxyPort = p.proxyPort;
        this.version = p.version;
        this.directHosts = p.directHosts;
    }

    // Public instance methods
    // ========================

    /**
     * Get the port on which proxy server is running.
     *
     * @return Proxy port.
     */
    public int getPort() {
        return proxyPort;
    }

    /**
     * Get the ip address of the proxy server host.
     *
     * @return Proxy InetAddress.
     */
    public InetAddress getInetAddress() {
        return proxyIP;
    }

    /**
     * Adds given ip to the list of direct addresses. This machine will be
     * accessed without using proxy.
     */
    public void addDirect(InetAddress ip) {
        directHosts.add(ip);
    }

    /**
     * Adds host to the list of direct addresses. This machine will be accessed
     * without using proxy.
     */
    public boolean addDirect(String host) {
        return directHosts.add(host);
    }

    /**
     * Adds given range of addresses to the lsit of direct addresses, machines
     * within this range will be accessed without using proxy.
     */
    public void addDirect(InetAddress from, InetAddress to) {
        directHosts.add(from, to);
    }

    /**
     * Sets given InetRange as the list of direct address, previous list will be
     * discarded, any changes done previously with addDirect(Inetaddress) will
     * be lost. The machines in this range will be accessed without using proxy.
     *
     * @param ir InetRange which should be used to look up direct addresses.
     * @see InetRange
     */
    public void setDirect(InetRange ir) {
        directHosts = ir;
    }

    /**
     * Get the list of direct hosts.
     *
     * @return Current range of direct address as InetRange object.
     * @see InetRange
     */
    public InetRange getDirect() {
        return directHosts;
    }

    /**
     * Check wether the given host is on the list of direct address.
     *
     * @param host Host name to check.
     * @return true if the given host is specified as the direct addresses.
     */
    public boolean isDirect(String host) {
        return directHosts.contains(host);
    }

    /**
     * Check wether the given host is on the list of direct addresses.
     *
     * @param host Host address to check.
     * @return true if the given host is specified as the direct address.
     */
    public boolean isDirect(InetAddress host) {
        return directHosts.contains(host);
    }

    /**
     * Set the proxy which should be used to connect to given proxy.
     *
     * @param chainProxy Proxy to use to connect to this proxy.
     */
    public void setChainProxy(SocksProxyBase chainProxy) {
        this.chainProxy = chainProxy;
    }

    /**
     * Get proxy which is used to connect to this proxy.
     *
     * @return Proxy which is used to connect to this proxy, or null if proxy is
     * to be contacted directly.
     */
    public SocksProxyBase getChainProxy() {
        return chainProxy;
    }

    /**
     * Get string representation of this proxy.
     *
     * @returns string in the form:proxyHost:proxyPort \t Version versionNumber
     */
    public String toString() {
        return ("" + proxyIP.getHostName() + ":" + proxyPort + "\tVersion " + version);
    }

    // Public Static(Class) Methods
    // ==============================

    /**
     * Sets SOCKS4 proxy as default.
     *
     * @param hostName Host name on which SOCKS4 server is running.
     * @param port     Port on which SOCKS4 server is running.
     * @param user     Username to use for communications with proxy.
     */
    public static void setDefaultProxy(String hostName, int port, String user)
            throws UnknownHostException {
        defaultProxy = new Socks4Proxy(hostName, port, user);
    }

    /**
     * Sets SOCKS4 proxy as default.
     *
     * @param ipAddress Host address on which SOCKS4 server is running.
     * @param port      Port on which SOCKS4 server is running.
     * @param user      Username to use for communications with proxy.
     */
    public static void setDefaultProxy(InetAddress ipAddress, int port,
                                       String user) {
        defaultProxy = new Socks4Proxy(ipAddress, port, user);
    }

    /**
     * Sets SOCKS5 proxy as default. Default proxy only supports
     * no-authentication.
     *
     * @param hostName Host name on which SOCKS5 server is running.
     * @param port     Port on which SOCKS5 server is running.
     */
    public static void setDefaultProxy(String hostName, int port)
            throws UnknownHostException {
        defaultProxy = new Socks5Proxy(hostName, port);
    }

    /**
     * Sets SOCKS5 proxy as default. Default proxy only supports
     * no-authentication.
     *
     * @param ipAddress Host address on which SOCKS5 server is running.
     * @param port      Port on which SOCKS5 server is running.
     */
    public static void setDefaultProxy(InetAddress ipAddress, int port) {
        defaultProxy = new Socks5Proxy(ipAddress, port);
    }

    /**
     * Sets default proxy.
     *
     * @param p Proxy to use as default proxy.
     */
    public static void setDefaultProxy(SocksProxyBase p) {
        defaultProxy = p;
    }

    /**
     * Get current default proxy.
     *
     * @return Current default proxy, or null if none is set.
     */
    public static SocksProxyBase getDefaultProxy() {
        return defaultProxy;
    }

    /**
     * Parses strings in the form: host[:port:user:password], and creates proxy
     * from information obtained from parsing.
     * <p>
     * Defaults: port = 1080.<br>
     * If user specified but not password, creates Socks4Proxy, if user not
     * specified creates Socks5Proxy, if both user and password are speciefied
     * creates Socks5Proxy with user/password authentication.
     *
     * @param proxy_entry String in the form host[:port:user:password]
     * @return Proxy created from the string, null if entry was somehow
     * invalid(host unknown for example, or empty string)
     */
    public static SocksProxyBase parseProxy(String proxy_entry) {

        String proxy_host;
        int proxy_port = 1080;
        String proxy_user = null;
        String proxy_password = null;
        SocksProxyBase proxy;

        final java.util.StringTokenizer st = new java.util.StringTokenizer(
                proxy_entry, ":");
        if (st.countTokens() < 1) {
            return null;
        }

        proxy_host = st.nextToken();
        if (st.hasMoreTokens()) {
            try {
                proxy_port = Integer.parseInt(st.nextToken().trim());
            } catch (final NumberFormatException nfe) {
            }
        }

        if (st.hasMoreTokens()) {
            proxy_user = st.nextToken();
        }

        if (st.hasMoreTokens()) {
            proxy_password = st.nextToken();
        }

        try {
            if (proxy_user == null) {
                proxy = new Socks5Proxy(proxy_host, proxy_port);
            } else if (proxy_password == null) {
                proxy = new Socks4Proxy(proxy_host, proxy_port, proxy_user);
            } else {
                proxy = new Socks5Proxy(proxy_host, proxy_port);
                final UserPasswordAuthentication upa = new UserPasswordAuthentication(
                        proxy_user, proxy_password);

                ((Socks5Proxy) proxy).setAuthenticationMethod(
                        UserPasswordAuthentication.METHOD_ID, upa);
            }
        } catch (final UnknownHostException uhe) {
            return null;
        }

        return proxy;
    }

    // Protected Methods
    // =================

    protected void startSession() throws SocksException {
        try {
            if (chainProxy == null) {
                proxySocket = new Socket(proxyIP, proxyPort);
            } else if (proxyIP != null) {
                proxySocket = new SocksSocket(chainProxy, proxyIP, proxyPort);
            } else {
                proxySocket = new SocksSocket(chainProxy, proxyHost, proxyPort);
            }

            in = proxySocket.getInputStream();
            out = proxySocket.getOutputStream();
        } catch (final SocksException se) {
            throw se;
        } catch (final IOException io_ex) {
            throw new SocksException(SOCKS_PROXY_IO_ERROR, "" + io_ex);
        }
    }

    /**
     * Create a copy of this proxy for use by individual threads.
     *
     * @return proxy
     */
    protected abstract SocksProxyBase copy();

    protected abstract ProxyMessage formMessage(int cmd, InetAddress ip,
                                                int port);

    protected abstract ProxyMessage formMessage(int cmd, String host, int port)
            throws UnknownHostException;

    protected abstract ProxyMessage formMessage(InputStream in)
            throws SocksException, IOException;

    protected ProxyMessage connect(InetAddress ip, int port)
            throws SocksException {
        try {
            startSession();
            final ProxyMessage request = formMessage(SOCKS_CMD_CONNECT, ip,
                    port);
            return exchange(request);
        } catch (final SocksException se) {
            endSession();
            throw se;
        }
    }

    protected ProxyMessage connect(String host, int port)
            throws UnknownHostException, SocksException {
        try {
            startSession();
            final ProxyMessage request = formMessage(SOCKS_CMD_CONNECT, host,
                    port);
            return exchange(request);
        } catch (final SocksException se) {
            endSession();
            throw se;
        }
    }

    protected ProxyMessage bind(InetAddress ip, int port) throws SocksException {
        try {
            startSession();
            final ProxyMessage request = formMessage(SOCKS_CMD_BIND, ip, port);
            return exchange(request);
        } catch (final SocksException se) {
            endSession();
            throw se;
        }
    }

    protected ProxyMessage bind(String host, int port)
            throws UnknownHostException, SocksException {
        try {
            startSession();
            final ProxyMessage request = formMessage(SOCKS_CMD_BIND, host, port);
            return exchange(request);
        } catch (final SocksException se) {
            endSession();
            throw se;
        }
    }

    protected ProxyMessage accept() throws IOException, SocksException {
        ProxyMessage msg;
        try {
            msg = formMessage(in);
        } catch (final InterruptedIOException iioe) {
            throw iioe;
        } catch (final IOException io_ex) {
            endSession();
            throw new SocksException(SOCKS_PROXY_IO_ERROR,
                    "While Trying accept:" + io_ex);
        }
        return msg;
    }

    protected ProxyMessage udpAssociate(InetAddress ip, int port)
            throws SocksException {
        try {
            startSession();
            final ProxyMessage request = formMessage(SOCKS_CMD_UDP_ASSOCIATE,
                    ip, port);
            if (request != null) {
                return exchange(request);
            }
        } catch (final SocksException se) {
            endSession();
            throw se;
        }
        // Only get here if request was null
        endSession();
        throw new SocksException(SOCKS_METHOD_NOTSUPPORTED,
                "This version of proxy does not support UDP associate, use version 5");
    }

    protected ProxyMessage udpAssociate(String host, int port)
            throws UnknownHostException, SocksException {
        try {
            startSession();
            final ProxyMessage request = formMessage(SOCKS_CMD_UDP_ASSOCIATE,
                    host, port);
            if (request != null) {
                return exchange(request);
            }
        } catch (final SocksException se) {
            endSession();
            throw se;
        }
        // Only get here if request was null
        endSession();
        throw new SocksException(SOCKS_METHOD_NOTSUPPORTED,
                "This version of proxy does not support UDP associate, use version 5");
    }

    protected void endSession() {
        try {
            if (proxySocket != null) {
                proxySocket.close();
            }
            proxySocket = null;
        } catch (final IOException io_ex) {
        }
    }

    /**
     * Sends the request to SOCKS server
     */
    protected void sendMsg(ProxyMessage msg) throws SocksException, IOException {
        msg.write(out);
    }

    /**
     * Reads the reply from the SOCKS server
     */
    protected ProxyMessage readMsg() throws SocksException, IOException {
        return formMessage(in);
    }

    /**
     * Sends the request reads reply and returns it throws exception if
     * something wrong with IO or the reply code is not zero
     */
    protected ProxyMessage exchange(ProxyMessage request) throws SocksException {
        ProxyMessage reply;
        try {
            request.write(out);
            reply = formMessage(in);
        } catch (final SocksException s_ex) {
            throw s_ex;
        } catch (final IOException ioe) {
            throw (new SocksException(SOCKS_PROXY_IO_ERROR, "" + ioe));
        }
        return reply;
    }

    // Private methods
    // ===============

    // Constants

    public static final int SOCKS_SUCCESS = 0;
    public static final int SOCKS_FAILURE = 1;
    public static final int SOCKS_BADCONNECT = 2;
    public static final int SOCKS_BADNETWORK = 3;
    public static final int SOCKS_HOST_UNREACHABLE = 4;
    public static final int SOCKS_CONNECTION_REFUSED = 5;
    public static final int SOCKS_TTL_EXPIRE = 6;
    public static final int SOCKS_CMD_NOT_SUPPORTED = 7;
    public static final int SOCKS_ADDR_NOT_SUPPORTED = 8;

    public static final int SOCKS_NO_PROXY = 1 << 16;
    public static final int SOCKS_PROXY_NO_CONNECT = 2 << 16;
    public static final int SOCKS_PROXY_IO_ERROR = 3 << 16;
    public static final int SOCKS_AUTH_NOT_SUPPORTED = 4 << 16;
    public static final int SOCKS_AUTH_FAILURE = 5 << 16;
    public static final int SOCKS_JUST_ERROR = 6 << 16;

    public static final int SOCKS_DIRECT_FAILED = 7 << 16;
    public static final int SOCKS_METHOD_NOTSUPPORTED = 8 << 16;

    static final int SOCKS_CMD_CONNECT = 0x1;
    static final int SOCKS_CMD_BIND = 0x2;
    static final int SOCKS_CMD_UDP_ASSOCIATE = 0x3;

}
