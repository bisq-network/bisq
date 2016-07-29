package com.runjva.sourceforge.jsocks.protocol;

import com.runjva.sourceforge.jsocks.server.ServerAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;

/**
 * SOCKS4 and SOCKS5 proxy, handles both protocols simultaniously. Implements
 * all SOCKS commands, including UDP relaying.
 * <p>
 * In order to use it you will need to implement ServerAuthenticator interface.
 * There is an implementation of this interface which does no authentication
 * ServerAuthenticatorNone, but it is very dangerous to use, as it will give
 * access to your local network to anybody in the world. One should never use
 * this authentication scheme unless one have pretty good reason to do so. There
 * is a couple of other authentication schemes in socks.server package.
 *
 * @see socks.server.ServerAuthenticator
 */
public class ProxyServer implements Runnable {

    ServerAuthenticator auth;
    ProxyMessage msg = null;

    Socket sock = null, remote_sock = null;
    ServerSocket ss = null;
    UDPRelayServer relayServer = null;
    InputStream in, remote_in;
    OutputStream out, remote_out;

    int mode;
    static final int START_MODE = 0;
    static final int ACCEPT_MODE = 1;
    static final int PIPE_MODE = 2;
    static final int ABORT_MODE = 3;

    static final int BUF_SIZE = 8192;

    Thread pipe_thread1, pipe_thread2;
    long lastReadTime;

    static int iddleTimeout = 180000; // 3 minutes
    static int acceptTimeout = 180000; // 3 minutes

    static Logger log = LoggerFactory.getLogger(ProxyServer.class);
    static SocksProxyBase proxy;

    // Public Constructors
    // ///////////////////

    /**
     * Creates a proxy server with given Authentication scheme.
     *
     * @param auth Authentication scheme to be used.
     */
    public ProxyServer(final ServerAuthenticator auth) {
        this.auth = auth;
    }

    // Other constructors
    // //////////////////

    ProxyServer(final ServerAuthenticator auth, final Socket s) {
        this.auth = auth;
        this.sock = s;
        this.mode = START_MODE;
    }

    // Public methods
    // ///////////////

    /**
     * Set proxy.
     * <p>
     * Allows Proxy chaining so that one Proxy server is connected to another
     * and so on. If proxy supports SOCKSv4, then only some SOCKSv5 requests can
     * be handled, UDP would not work, however CONNECT and BIND will be
     * translated.
     *
     * @param p Proxy which should be used to handle user requests.
     */
    public static void setProxy(final SocksProxyBase p) {
        proxy = p;
        // FIXME: Side effect.
        UDPRelayServer.proxy = proxy;
    }

    /**
     * Get proxy.
     *
     * @return Proxy wich is used to handle user requests.
     */
    public static SocksProxyBase getProxy() {
        return proxy;
    }

    /**
     * Sets the timeout for connections, how long shoud server wait for data to
     * arrive before dropping the connection.<br>
     * Zero timeout implies infinity.<br>
     * Default timeout is 3 minutes.
     */
    public static void setIddleTimeout(final int timeout) {
        iddleTimeout = timeout;
    }

    /**
     * Sets the timeout for BIND command, how long the server should wait for
     * the incoming connection.<br>
     * Zero timeout implies infinity.<br>
     * Default timeout is 3 minutes.
     */
    public static void setAcceptTimeout(final int timeout) {
        acceptTimeout = timeout;
    }

    /**
     * Sets the timeout for UDPRelay server.<br>
     * Zero timeout implies infinity.<br>
     * Default timeout is 3 minutes.
     */
    public static void setUDPTimeout(final int timeout) {
        UDPRelayServer.setTimeout(timeout);
    }

    /**
     * Sets the size of the datagrams used in the UDPRelayServer.<br>
     * Default size is 64K, a bit more than maximum possible size of the
     * datagram.
     */
    public static void setDatagramSize(final int size) {
        UDPRelayServer.setDatagramSize(size);
    }

    /**
     * Start the Proxy server at given port.<br>
     * This methods blocks.
     */
    public void start(final int port) {
        start(port, 5, null);
    }

    /**
     * Create a server with the specified port, listen backlog, and local IP
     * address to bind to. The localIP argument can be used on a multi-homed
     * host for a ServerSocket that will only accept connect requests to one of
     * its addresses. If localIP is null, it will default accepting connections
     * on any/all local addresses. The port must be between 0 and 65535,
     * inclusive. <br>
     * This methods blocks.
     */
    public void start(final int port, final int backlog,
                      final InetAddress localIP) {
        try {
            ss = new ServerSocket(port, backlog, localIP);
            final String address = ss.getInetAddress().getHostAddress();
            final int localPort = ss.getLocalPort();
            log.debug("Starting SOCKS Proxy on: {}:{}", address, localPort);

            while (true) {
                final Socket s = ss.accept();
                final String hostName = s.getInetAddress().getHostName();
                final int port2 = s.getPort();
                log.debug("Accepted from:{}:{}", hostName, port2);

                final ProxyServer ps = new ProxyServer(auth, s);
                (new Thread(ps)).start();
            }
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        } finally {
        }
    }

    /**
     * Stop server operation.It would be wise to interrupt thread running the
     * server afterwards.
     */
    public void stop() {
        try {
            if (ss != null) {
                ss.close();
            }
        } catch (final IOException ioe) {
        }
    }

    // Runnable interface
    // //////////////////
    public void run() {
        switch (mode) {
            case START_MODE:
                try {
                    startSession();
                } catch (final IOException ioe) {
                    handleException(ioe);
                    // ioe.printStackTrace();
                } finally {
                    abort();
                    if (auth != null) {
                        auth.endSession();
                    }
                    log.debug("Main thread(client->remote)stopped.");
                }
                break;
            case ACCEPT_MODE:
                try {
                    doAccept();
                    mode = PIPE_MODE;
                    pipe_thread1.interrupt(); // Tell other thread that connection
                    // have
                    // been accepted.
                    pipe(remote_in, out);
                } catch (final IOException ioe) {
                    // log("Accept exception:"+ioe);
                    handleException(ioe);
                } finally {
                    abort();
                    log.debug("Accept thread(remote->client) stopped");
                }
                break;
            case PIPE_MODE:
                try {
                    pipe(remote_in, out);
                } catch (final IOException ioe) {
                } finally {
                    abort();
                    log.debug("Support thread(remote->client) stopped");
                }
                break;
            case ABORT_MODE:
                break;
            default:
                log.warn("Unexpected MODE " + mode);
        }
    }

    // Private methods
    // ///////////////
    private void startSession() throws IOException {
        sock.setSoTimeout(iddleTimeout);

        try {
            auth = auth.startSession(sock);
        } catch (final IOException ioe) {
            log.warn("Auth throwed exception:", ioe);
            auth = null;
            return;
        }

        if (auth == null) { // Authentication failed
            log.debug("Authentication failed");
            return;
        }

        in = auth.getInputStream();
        out = auth.getOutputStream();

        msg = readMsg(in);
        handleRequest(msg);
    }

    private void handleRequest(final ProxyMessage msg) throws IOException {
        if (!auth.checkRequest(msg)) {
            throw new SocksException(SocksProxyBase.SOCKS_FAILURE);
        }

        if (msg.ip == null) {
            if (msg instanceof Socks5Message) {
                msg.ip = InetAddress.getByName(msg.host);
            } else {
                throw new SocksException(SocksProxyBase.SOCKS_FAILURE);
            }
        }
        log(msg);

        switch (msg.command) {
            case SocksProxyBase.SOCKS_CMD_CONNECT:
                onConnect(msg);
                break;
            case SocksProxyBase.SOCKS_CMD_BIND:
                onBind(msg);
                break;
            case SocksProxyBase.SOCKS_CMD_UDP_ASSOCIATE:
                onUDP(msg);
                break;
            default:
                throw new SocksException(SocksProxyBase.SOCKS_CMD_NOT_SUPPORTED);
        }
    }

    private void handleException(final IOException ioe) {
        // If we couldn't read the request, return;
        if (msg == null) {
            return;
        }
        // If have been aborted by other thread
        if (mode == ABORT_MODE) {
            return;
        }
        // If the request was successfully completed, but exception happened
        // later
        if (mode == PIPE_MODE) {
            return;
        }

        int error_code = SocksProxyBase.SOCKS_FAILURE;

        if (ioe instanceof SocksException) {
            error_code = ((SocksException) ioe).errCode;
        } else if (ioe instanceof NoRouteToHostException) {
            error_code = SocksProxyBase.SOCKS_HOST_UNREACHABLE;
        } else if (ioe instanceof ConnectException) {
            error_code = SocksProxyBase.SOCKS_CONNECTION_REFUSED;
        } else if (ioe instanceof InterruptedIOException) {
            error_code = SocksProxyBase.SOCKS_TTL_EXPIRE;
        }

        if ((error_code > SocksProxyBase.SOCKS_ADDR_NOT_SUPPORTED)
                || (error_code < 0)) {
            error_code = SocksProxyBase.SOCKS_FAILURE;
        }

        sendErrorMessage(error_code);
    }

    private void onConnect(final ProxyMessage msg) throws IOException {
        Socket s;

        if (proxy == null) {
            s = new Socket(msg.ip, msg.port);
        } else {
            s = new SocksSocket(proxy, msg.ip, msg.port);
        }

        log.debug("Connected to " + s.getInetAddress() + ":" + s.getPort());

        ProxyMessage response = null;
        final InetAddress localAddress = s.getLocalAddress();
        final int localPort = s.getLocalPort();

        if (msg instanceof Socks5Message) {
            final int cmd = SocksProxyBase.SOCKS_SUCCESS;
            response = new Socks5Message(cmd, localAddress, localPort);
        } else {
            final int cmd = Socks4Message.REPLY_OK;
            response = new Socks4Message(cmd, localAddress, localPort);

        }
        response.write(out);
        startPipe(s);
    }

    private void onBind(final ProxyMessage msg) throws IOException {
        ProxyMessage response = null;

        if (proxy == null) {
            ss = new ServerSocket(0);
        } else {
            ss = new SocksServerSocket(proxy, msg.ip, msg.port);
        }

        ss.setSoTimeout(acceptTimeout);

        final InetAddress inetAddress = ss.getInetAddress();
        final int localPort = ss.getLocalPort();
        log.debug("Trying accept on {}:{}", inetAddress, localPort);

        if (msg.version == 5) {
            final int cmd = SocksProxyBase.SOCKS_SUCCESS;
            response = new Socks5Message(cmd, inetAddress, localPort);
        } else {
            final int cmd = Socks4Message.REPLY_OK;
            response = new Socks4Message(cmd, inetAddress, localPort);
        }
        response.write(out);

        mode = ACCEPT_MODE;

        pipe_thread1 = Thread.currentThread();
        pipe_thread2 = new Thread(this);
        pipe_thread2.start();

        // Make timeout infinit.
        sock.setSoTimeout(0);
        int eof = 0;

        try {
            while ((eof = in.read()) >= 0) {
                if (mode != ACCEPT_MODE) {
                    if (mode != PIPE_MODE) {
                        return;// Accept failed
                    }

                    remote_out.write(eof);
                    break;
                }
            }
        } catch (final EOFException e) {
            log.debug("Connection closed while we were trying to accept", e);
            return;
        } catch (final InterruptedIOException e) {
            log.debug("Interrupted by unsucessful accept thread", e);
            if (mode != PIPE_MODE) {
                return;
            }
        } finally {
            // System.out.println("Finnaly!");
        }

        if (eof < 0) {
            return;
        }

        // Do not restore timeout, instead timeout is set on the
        // remote socket. It does not make any difference.

        pipe(in, remote_out);
    }

    private void onUDP(final ProxyMessage msg) throws IOException {
        if (msg.ip.getHostAddress().equals("0.0.0.0")) {
            msg.ip = sock.getInetAddress();
        }
        log.debug("Creating UDP relay server for {}:{}", msg.ip, msg.port);

        relayServer = new UDPRelayServer(msg.ip, msg.port,
                Thread.currentThread(), sock, auth);

        ProxyMessage response;

        response = new Socks5Message(SocksProxyBase.SOCKS_SUCCESS,
                relayServer.relayIP, relayServer.relayPort);

        response.write(out);

        relayServer.start();

        // Make timeout infinit.
        sock.setSoTimeout(0);
        try {
            while (in.read() >= 0) {
                /* do nothing */
                ;
                // FIXME: Consider a slight delay here?
            }
        } catch (final EOFException eofe) {
        }
    }

    // Private methods
    // ////////////////

    private void doAccept() throws IOException {
        Socket s = null;
        final long startTime = System.currentTimeMillis();

        while (true) {
            s = ss.accept();
            if (s.getInetAddress().equals(msg.ip)) {
                // got the connection from the right host
                // Close listenning socket.
                ss.close();
                break;
            } else if (ss instanceof SocksServerSocket) {
                // We can't accept more then one connection
                s.close();
                ss.close();
                throw new SocksException(SocksProxyBase.SOCKS_FAILURE);
            } else {
                if (acceptTimeout != 0) { // If timeout is not infinit
                    final long passed = System.currentTimeMillis() - startTime;
                    final int newTimeout = acceptTimeout - (int) passed;

                    if (newTimeout <= 0) {
                        throw new InterruptedIOException("newTimeout <= 0");
                    }
                    ss.setSoTimeout(newTimeout);
                }
                s.close(); // Drop all connections from other hosts
            }
        }

        // Accepted connection
        remote_sock = s;
        remote_in = s.getInputStream();
        remote_out = s.getOutputStream();

        // Set timeout
        remote_sock.setSoTimeout(iddleTimeout);

        final InetAddress inetAddress = s.getInetAddress();
        final int port = s.getPort();
        log.debug("Accepted from {}:{}", s.getInetAddress(), port);

        ProxyMessage response;

        if (msg.version == 5) {
            final int cmd = SocksProxyBase.SOCKS_SUCCESS;
            response = new Socks5Message(cmd, inetAddress, port);
        } else {
            final int cmd = Socks4Message.REPLY_OK;
            response = new Socks4Message(cmd, inetAddress, port);
        }
        response.write(out);
    }

    private ProxyMessage readMsg(final InputStream in) throws IOException {
        PushbackInputStream push_in;
        if (in instanceof PushbackInputStream) {
            push_in = (PushbackInputStream) in;
        } else {
            push_in = new PushbackInputStream(in);
        }

        final int version = push_in.read();
        push_in.unread(version);

        ProxyMessage msg;

        if (version == 5) {
            msg = new Socks5Message(push_in, false);
        } else if (version == 4) {
            msg = new Socks4Message(push_in, false);
        } else {
            throw new SocksException(SocksProxyBase.SOCKS_FAILURE);
        }
        return msg;
    }

    private void startPipe(final Socket s) {
        mode = PIPE_MODE;
        remote_sock = s;
        try {
            remote_in = s.getInputStream();
            remote_out = s.getOutputStream();
            pipe_thread1 = Thread.currentThread();
            pipe_thread2 = new Thread(this);
            pipe_thread2.start();
            pipe(in, remote_out);
        } catch (final IOException ioe) {
        }
    }

    private void sendErrorMessage(final int error_code) {
        ProxyMessage err_msg;
        if (msg instanceof Socks4Message) {
            err_msg = new Socks4Message(Socks4Message.REPLY_REJECTED);
        } else {
            err_msg = new Socks5Message(error_code);
        }
        try {
            err_msg.write(out);
        } catch (final IOException ioe) {
        }
    }

    private synchronized void abort() {
        if (mode == ABORT_MODE) {
            return;
        }
        mode = ABORT_MODE;
        try {
            log.debug("Aborting operation");
            if (remote_sock != null) {
                remote_sock.close();
            }
            if (sock != null) {
                sock.close();
            }
            if (relayServer != null) {
                relayServer.stop();
            }
            if (ss != null) {
                ss.close();
            }
            if (pipe_thread1 != null) {
                pipe_thread1.interrupt();
            }
            if (pipe_thread2 != null) {
                pipe_thread2.interrupt();
            }
        } catch (final IOException ioe) {
        }
    }

    static final void log(final ProxyMessage msg) {
        log.debug("Request version: {}, Command: ", msg.version,
                command2String(msg.command));

        final String user = msg.version == 4 ? ", User:" + msg.user : "";
        log.debug("IP:" + msg.ip + ", Port:" + msg.port + user);
    }

    private void pipe(final InputStream in, final OutputStream out)
            throws IOException {
        lastReadTime = System.currentTimeMillis();
        final byte[] buf = new byte[BUF_SIZE];
        int len = 0;
        while (len >= 0) {
            try {
                if (len != 0) {
                    out.write(buf, 0, len);
                    out.flush();
                }
                len = in.read(buf);
                lastReadTime = System.currentTimeMillis();
            } catch (final InterruptedIOException iioe) {
                if (iddleTimeout == 0) {
                    return;// Other thread interrupted us.
                }
                final long timeSinceRead = System.currentTimeMillis()
                        - lastReadTime;

                if (timeSinceRead >= iddleTimeout - 1000) {
                    return;
                }
                len = 0;

            }
        }
    }

    static final String command_names[] = {"CONNECT", "BIND", "UDP_ASSOCIATE"};

    static final String command2String(int cmd) {
        if ((cmd > 0) && (cmd < 4)) {
            return command_names[cmd - 1];
        } else {
            return "Unknown Command " + cmd;
        }
    }
}
