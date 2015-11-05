package com.runjva.sourceforge.jsocks.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * SOCKS5 Proxy.
 */

public class Socks5Proxy extends SocksProxyBase implements Cloneable {

    // Data members
    private Hashtable<Integer, Authentication> authMethods = new Hashtable<Integer, Authentication>();
    private int selectedMethod;

    boolean resolveAddrLocally = true;
    UDPEncapsulation udp_encapsulation = null;

    // Public Constructors
    // ====================

    /**
     * Creates SOCKS5 proxy.
     *
     * @param p         Proxy to use to connect to this proxy, allows proxy chaining.
     * @param proxyHost Host on which a Proxy server runs.
     * @param proxyPort Port on which a Proxy server listens for connections.
     * @throws UnknownHostException If proxyHost can't be resolved.
     */
    public Socks5Proxy(SocksProxyBase p, String proxyHost, int proxyPort)
            throws UnknownHostException {

        super(p, proxyHost, proxyPort);
        version = 5;
        setAuthenticationMethod(0, new AuthenticationNone());
    }

    /**
     * Creates SOCKS5 proxy.
     *
     * @param proxyHost Host on which a Proxy server runs.
     * @param proxyPort Port on which a Proxy server listens for connections.
     * @throws UnknownHostException If proxyHost can't be resolved.
     */
    public Socks5Proxy(String proxyHost, int proxyPort)
            throws UnknownHostException {
        this(null, proxyHost, proxyPort);
    }

    /**
     * Creates SOCKS5 proxy.
     *
     * @param p         Proxy to use to connect to this proxy, allows proxy chaining.
     * @param proxyIP   Host on which a Proxy server runs.
     * @param proxyPort Port on which a Proxy server listens for connections.
     */
    public Socks5Proxy(SocksProxyBase p, InetAddress proxyIP, int proxyPort) {
        super(p, proxyIP, proxyPort);
        version = 5;
        setAuthenticationMethod(0, new AuthenticationNone());
    }

    /**
     * Creates SOCKS5 proxy.
     *
     * @param proxyIP   Host on which a Proxy server runs.
     * @param proxyPort Port on which a Proxy server listens for connections.
     */
    public Socks5Proxy(InetAddress proxyIP, int proxyPort) {
        this(null, proxyIP, proxyPort);
    }

    // Public instance methods
    // ========================

    /**
     * Wether to resolve address locally or to let proxy do so.
     * <p>
     * SOCKS5 protocol allows to send host names rather then IPs in the
     * requests, this option controls wether the hostnames should be send to the
     * proxy server as names, or should they be resolved locally.
     *
     * @param doResolve Wether to perform resolution locally.
     * @return Previous settings.
     */
    public boolean resolveAddrLocally(boolean doResolve) {
        final boolean old = resolveAddrLocally;
        resolveAddrLocally = doResolve;
        return old;
    }

    /**
     * Get current setting on how the addresses should be handled.
     *
     * @return Current setting for address resolution.
     * @see Socks5Proxy#resolveAddrLocally(boolean doResolve)
     */
    public boolean resolveAddrLocally() {
        return resolveAddrLocally;
    }

    /**
     * Adds another authentication method.
     *
     * @param methodId Authentication method id, see rfc1928
     * @param method   Implementation of Authentication
     * @see Authentication
     */
    public boolean setAuthenticationMethod(int methodId, Authentication method) {
        if ((methodId < 0) || (methodId > 255)) {
            return false;
        }
        if (method == null) {
            // Want to remove a particular method
            return (authMethods.remove(new Integer(methodId)) != null);
        } else {// Add the method, or rewrite old one
            authMethods.put(new Integer(methodId), method);
        }
        return true;
    }

    /**
     * Get authentication method, which corresponds to given method id
     *
     * @param methodId Authentication method id.
     * @return Implementation for given method or null, if one was not set.
     */
    public Authentication getAuthenticationMethod(int methodId) {
        final Object method = authMethods.get(new Integer(methodId));
        if (method == null) {
            return null;
        }
        return (Authentication) method;
    }

    /**
     * Creates a clone of this Proxy. clone() returns an
     */
    @SuppressWarnings("unchecked")
    public Object clone() {
        final Socks5Proxy newProxy = new Socks5Proxy(proxyIP, proxyPort);

        final Object o = this.authMethods.clone();
        newProxy.authMethods = (Hashtable<Integer, Authentication>) o;

        newProxy.directHosts = (InetRange) directHosts.clone();
        newProxy.resolveAddrLocally = resolveAddrLocally;
        newProxy.chainProxy = chainProxy;
        return newProxy;
    }

    // Public Static(Class) Methods
    // ==============================

    // Protected Methods
    // =================

    protected SocksProxyBase copy() {
        final Socks5Proxy copy = new Socks5Proxy(proxyIP, proxyPort);

        copy.authMethods = this.authMethods; // same Hash, no copy
        copy.directHosts = this.directHosts;
        copy.chainProxy = this.chainProxy;
        copy.resolveAddrLocally = this.resolveAddrLocally;
        return copy;
    }

    /**
     *
     *
     */
    protected void startSession() throws SocksException {
        super.startSession();
        Authentication auth;
        final Socket ps = proxySocket; // The name is too long

        try {

            final byte nMethods = (byte) authMethods.size(); // Number of
            // methods

            final byte[] buf = new byte[2 + nMethods]; // 2 is for VER,NMETHODS
            buf[0] = (byte) version;
            buf[1] = nMethods; // Number of methods
            int i = 2;

            final Enumeration<Integer> ids = authMethods.keys();
            while (ids.hasMoreElements()) {
                buf[i++] = (byte) ids.nextElement().intValue();
            }

            out.write(buf);
            out.flush();

            final int versionNumber = in.read();
            selectedMethod = in.read();

            if ((versionNumber < 0) || (selectedMethod < 0)) {
                // EOF condition was reached
                endSession();
                final String s = "Connection to proxy lost.";
                throw new SocksException(SOCKS_PROXY_IO_ERROR, s);
            }

            if (versionNumber < version) {
                // What should we do??
            }

            if (selectedMethod == 0xFF) { // No method selected
                ps.close();
                throw (new SocksException(SOCKS_AUTH_NOT_SUPPORTED));
            }

            auth = getAuthenticationMethod(selectedMethod);
            if (auth == null) {
                // This shouldn't happen, unless method was removed by other
                // thread, or the server stuffed up
                final String s = "Specified Authentication not found!";
                throw new SocksException(SOCKS_JUST_ERROR, s);
            }

            final Object[] in_out;
            in_out = auth.doSocksAuthentication(selectedMethod, ps);

            if (in_out == null) {
                // Authentication failed by some reason
                throw (new SocksException(SOCKS_AUTH_FAILURE));
            }

			/*
             * Most authentication methods are expected to return simply the
			 * input/output streams associated with the socket. However if the
			 * auth. method requires some kind of encryption/decryption being
			 * done on the connection it should provide classes to handle I/O.
			 */

            in = (InputStream) in_out[0];
            out = (OutputStream) in_out[1];
            if (in_out.length > 2) {
                udp_encapsulation = (UDPEncapsulation) in_out[2];
            }

        } catch (final SocksException s_ex) {
            throw s_ex;
        } catch (final UnknownHostException uh_ex) {
            throw new SocksException(SOCKS_PROXY_NO_CONNECT, uh_ex);
        } catch (final SocketException so_ex) {
            throw new SocksException(SOCKS_PROXY_NO_CONNECT, so_ex);
        } catch (final IOException io_ex) {
            throw new SocksException(SOCKS_PROXY_IO_ERROR, io_ex);
        }
    }

    protected ProxyMessage formMessage(int cmd, InetAddress ip, int port) {
        return new Socks5Message(cmd, ip, port);
    }

    protected ProxyMessage formMessage(int cmd, String host, int port)
            throws UnknownHostException {
        if (resolveAddrLocally) {
            return formMessage(cmd, InetAddress.getByName(host), port);
        } else {
            return new Socks5Message(cmd, host, port);
        }
    }

    protected ProxyMessage formMessage(InputStream in) throws SocksException,
            IOException {
        return new Socks5Message(in);
    }

}
