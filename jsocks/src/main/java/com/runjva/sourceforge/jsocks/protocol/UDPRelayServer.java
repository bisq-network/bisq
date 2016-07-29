package com.runjva.sourceforge.jsocks.protocol;

import com.runjva.sourceforge.jsocks.server.ServerAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;

/**
 * UDP Relay server, used by ProxyServer to perform udp forwarding.
 */
class UDPRelayServer implements Runnable {

    DatagramSocket client_sock;
    DatagramSocket remote_sock;

    Socket controlConnection;

    int relayPort;
    InetAddress relayIP;

    Thread pipe_thread1, pipe_thread2;
    Thread master_thread;

    ServerAuthenticator auth;

    long lastReadTime;

    static Logger log = LoggerFactory.getLogger(UDPRelayServer.class);
    static SocksProxyBase proxy = null;
    static int datagramSize = 0xFFFF;// 64K, a bit more than max udp size
    static int iddleTimeout = 180000;// 3 minutes

    /**
     * Constructs UDP relay server to communicate with client on given ip and
     * port.
     *
     * @param clientIP          Address of the client from whom datagrams will be recieved and
     *                          to whom they will be forwarded.
     * @param clientPort        Clients port.
     * @param master_thread     Thread which will be interrupted, when UDP relay server
     *                          stoppes for some reason.
     * @param controlConnection Socket which will be closed, before interrupting the master
     *                          thread, it is introduced due to a bug in windows JVM which
     *                          does not throw InterruptedIOException in threads which block
     *                          in I/O operation.
     */
    public UDPRelayServer(InetAddress clientIP, int clientPort,
                          Thread master_thread, Socket controlConnection,
                          ServerAuthenticator auth) throws IOException {

        this.master_thread = master_thread;
        this.controlConnection = controlConnection;
        this.auth = auth;

        client_sock = new Socks5DatagramSocket(true,
                auth.getUdpEncapsulation(), clientIP, clientPort);

        relayPort = client_sock.getLocalPort();
        relayIP = client_sock.getLocalAddress();

        if (relayIP.getHostAddress().equals("0.0.0.0")) {
            relayIP = InetAddress.getLocalHost();
        }

        if (proxy == null) {
            remote_sock = new DatagramSocket();
        } else {
            remote_sock = new Socks5DatagramSocket(proxy, 0, null);
        }
    }

    // Public methods
    // ///////////////

    /**
     * Sets the timeout for UDPRelay server.<br>
     * Zero timeout implies infinity.<br>
     * Default timeout is 3 minutes.
     */

    static public void setTimeout(int timeout) {
        iddleTimeout = timeout;
    }

    /**
     * Sets the size of the datagrams used in the UDPRelayServer.<br>
     * Default size is 64K, a bit more than maximum possible size of the
     * datagram.
     */
    static public void setDatagramSize(int size) {
        datagramSize = size;
    }

    /**
     * Port to which client should send datagram for association.
     */
    public int getRelayPort() {
        return relayPort;
    }

    /**
     * IP address to which client should send datagrams for association.
     */
    public InetAddress getRelayIP() {
        return relayIP;
    }

    /**
     * Starts udp relay server. Spawns two threads of execution and returns.
     */
    public void start() throws IOException {
        remote_sock.setSoTimeout(iddleTimeout);
        client_sock.setSoTimeout(iddleTimeout);

        log.debug("Starting UDP relay server on {}:{}", relayIP, relayPort);
        log.debug("Remote socket {}:{}", remote_sock.getLocalAddress(),
                remote_sock.getLocalPort());

        pipe_thread1 = new Thread(this, "pipe1");
        pipe_thread2 = new Thread(this, "pipe2");

        lastReadTime = System.currentTimeMillis();

        pipe_thread1.start();
        pipe_thread2.start();
    }

    /**
     * Stops Relay server.
     * <p>
     * Does not close control connection, does not interrupt master_thread.
     */
    public synchronized void stop() {
        master_thread = null;
        controlConnection = null;
        abort();
    }

    // Runnable interface
    // //////////////////
    public void run() {
        try {
            if (Thread.currentThread().getName().equals("pipe1")) {
                pipe(remote_sock, client_sock, false);
            } else {
                pipe(client_sock, remote_sock, true);
            }
        } catch (final IOException ioe) {
        } finally {
            abort();
            log.debug("UDP Pipe thread " + Thread.currentThread().getName()
                    + " stopped.");
        }

    }

    // Private methods
    // ///////////////
    private synchronized void abort() {
        if (pipe_thread1 == null) {
            return;
        }

        log.debug("Aborting UDP Relay Server");

        remote_sock.close();
        client_sock.close();

        if (controlConnection != null) {
            try {
                controlConnection.close();
            } catch (final IOException ioe) {
            }
        }

        if (master_thread != null) {
            master_thread.interrupt();
        }

        pipe_thread1.interrupt();
        pipe_thread2.interrupt();

        pipe_thread1 = null;
    }

    private void pipe(DatagramSocket from, DatagramSocket to, boolean out)
            throws IOException {
        final byte[] data = new byte[datagramSize];
        final DatagramPacket dp = new DatagramPacket(data, data.length);

        while (true) {
            try {
                from.receive(dp);
                lastReadTime = System.currentTimeMillis();

                if (auth.checkRequest(dp, out)) {
                    to.send(dp);
                }

            } catch (final UnknownHostException uhe) {
                log.debug("Dropping datagram for unknown host");
            } catch (final InterruptedIOException iioe) {
                // log("Interrupted: "+iioe);
                // If we were interrupted by other thread.
                if (iddleTimeout == 0) {
                    return;
                }

                // If last datagram was received, long time ago, return.
                final long timeSinceRead = System.currentTimeMillis()
                        - lastReadTime;
                if (timeSinceRead >= iddleTimeout - 100) {
                    return;
                }
            }
            dp.setLength(data.length);
        }
    }
}
