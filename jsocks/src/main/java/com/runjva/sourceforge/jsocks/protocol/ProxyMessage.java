package com.runjva.sourceforge.jsocks.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Abstract class which describes SOCKS4/5 response/request.
 */
public abstract class ProxyMessage {

    /**
     * Host as an IP address
     */
    public InetAddress ip = null;

    /**
     * SOCKS version, or version of the response for SOCKS4
     */
    public int version;

    /**
     * Port field of the request/response
     */
    public int port;

    /**
     * Request/response code as an int
     */
    public int command;

    /**
     * Host as string.
     */
    public String host = null;

    /**
     * User field for SOCKS4 request messages
     */
    public String user = null;

    ProxyMessage(int command, InetAddress ip, int port) {
        this.command = command;
        this.ip = ip;
        this.port = port;
    }

    ProxyMessage() {
    }

    /**
     * Initialises Message from the stream. Reads server response from given
     * stream.
     *
     * @param in Input stream to read response from.
     * @throws SocksException If server response code is not SOCKS_SUCCESS(0), or if any
     *                        error with protocol occurs.
     * @throws IOException    If any error happens with I/O.
     */
    public abstract void read(InputStream in) throws SocksException,
            IOException;

    /**
     * Initialises Message from the stream. Reads server response or client
     * request from given stream.
     *
     * @param in         Input stream to read response from.
     * @param clinetMode If true read server response, else read client request.
     * @throws SocksException If server response code is not SOCKS_SUCCESS(0) and reading
     *                        in client mode, or if any error with protocol occurs.
     * @throws IOException    If any error happens with I/O.
     */
    public abstract void read(InputStream in, boolean client_mode)
            throws SocksException, IOException;

    /**
     * Writes the message to the stream.
     *
     * @param out Output stream to which message should be written.
     */
    public abstract void write(OutputStream out) throws SocksException,
            IOException;

    /**
     * Get the Address field of this message as InetAddress object.
     *
     * @return Host address or null, if one can't be determined.
     */
    public InetAddress getInetAddress() throws UnknownHostException {
        return ip;
    }

    /**
     * Get string representaion of this message.
     *
     * @return string representation of this message.
     */
    public String toString() {
        return "Proxy Message:\n" + "Version:" + version + "\n" + "Command:"
                + command + "\n" + "IP:     " + ip + "\n" + "Port:   " + port
                + "\n" + "User:   " + user + "\n";
    }

    // Package methods
    // ////////////////

    static final String bytes2IPV4(byte[] addr, int offset) {
        String hostName = "" + (addr[offset] & 0xFF);
        for (int i = offset + 1; i < offset + 4; i++) {
            hostName += "." + (addr[i] & 0xFF);
        }
        return hostName;
    }

    static final String bytes2IPV6(byte[] addr, int offset) {
        // Have no idea how they look like!
        return null;
    }

}
