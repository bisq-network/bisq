package com.runjva.sourceforge.jsocks.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Proxy which describes SOCKS4 proxy.
 */

public class Socks4Proxy extends SocksProxyBase implements Cloneable {

    // Data members
    String user;

    // Public Constructors
    // ====================

    /**
     * Creates the SOCKS4 proxy
     *
     * @param p         Proxy to use to connect to this proxy, allows proxy chaining.
     * @param proxyHost Address of the proxy server.
     * @param proxyPort Port of the proxy server
     * @param user      User name to use for identification purposes.
     * @throws UnknownHostException If proxyHost can't be resolved.
     */
    public Socks4Proxy(SocksProxyBase p, String proxyHost, int proxyPort,
                       String user) throws UnknownHostException {
        super(p, proxyHost, proxyPort);
        this.user = new String(user);
        version = 4;
    }

    /**
     * Creates the SOCKS4 proxy
     *
     * @param proxyHost Address of the proxy server.
     * @param proxyPort Port of the proxy server
     * @param user      User name to use for identification purposes.
     * @throws UnknownHostException If proxyHost can't be resolved.
     */
    public Socks4Proxy(String proxyHost, int proxyPort, String user)
            throws UnknownHostException {
        this(null, proxyHost, proxyPort, user);
    }

    /**
     * Creates the SOCKS4 proxy
     *
     * @param p         Proxy to use to connect to this proxy, allows proxy chaining.
     * @param proxyIP   Address of the proxy server.
     * @param proxyPort Port of the proxy server
     * @param user      User name to use for identification purposes.
     */
    public Socks4Proxy(SocksProxyBase p, InetAddress proxyIP, int proxyPort,
                       String user) {
        super(p, proxyIP, proxyPort);
        this.user = new String(user);
        version = 4;
    }

    /**
     * Creates the SOCKS4 proxy
     *
     * @param proxyIP   Address of the proxy server.
     * @param proxyPort Port of the proxy server
     * @param user      User name to use for identification purposes.
     */
    public Socks4Proxy(InetAddress proxyIP, int proxyPort, String user) {
        this(null, proxyIP, proxyPort, user);
    }

    // Public instance methods
    // ========================

    /**
     * Creates a clone of this proxy. Changes made to the clone should not
     * affect this object.
     */
    public Object clone() {
        final Socks4Proxy newProxy = new Socks4Proxy(proxyIP, proxyPort, user);
        newProxy.directHosts = (InetRange) directHosts.clone();
        newProxy.chainProxy = chainProxy;
        return newProxy;
    }

    // Public Static(Class) Methods
    // ==============================

    // Protected Methods
    // =================

    protected SocksProxyBase copy() {
        final Socks4Proxy copy = new Socks4Proxy(proxyIP, proxyPort, user);
        copy.directHosts = this.directHosts;
        copy.chainProxy = chainProxy;
        return copy;
    }

    protected ProxyMessage formMessage(int cmd, InetAddress ip, int port) {
        switch (cmd) {
            case SOCKS_CMD_CONNECT:
                cmd = Socks4Message.REQUEST_CONNECT;
                break;
            case SOCKS_CMD_BIND:
                cmd = Socks4Message.REQUEST_BIND;
                break;
            default:
                return null;
        }
        return new Socks4Message(cmd, ip, port, user);
    }

    protected ProxyMessage formMessage(int cmd, String host, int port)
            throws UnknownHostException {

        return formMessage(cmd, InetAddress.getByName(host), port);
    }

    protected ProxyMessage formMessage(InputStream in) throws SocksException,
            IOException {

        return new Socks4Message(in, true);
    }

}
