package com.runjva.sourceforge.jsocks.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SOCKS5 request/response message.
 */

class Socks5Message extends ProxyMessage {
    /**
     * Address type of given message
     */
    public int addrType;

    byte[] data;

    private Logger log = LoggerFactory.getLogger(Socks5Message.class);

    /**
     * Server error response.
     *
     * @param cmd Error code.
     */
    public Socks5Message(int cmd) {
        super(cmd, null, 0);
        data = new byte[3];
        data[0] = SOCKS_VERSION; // Version.
        data[1] = (byte) cmd; // Reply code for some kind of failure.
        data[2] = 0; // Reserved byte.
    }

    /**
     * Construct client request or server response.
     *
     * @param cmd - Request/Response code.
     * @param ip  - IP field.
     * @paarm port - port field.
     */
    public Socks5Message(int cmd, InetAddress ip, int port) {
        super(cmd, ip, port);

        if (ip == null) {
            this.host = "0.0.0.0";
        } else {
            this.host = ip.getHostName();
        }

        this.version = SOCKS_VERSION;

        byte[] addr;

        if (ip == null) {
            addr = new byte[4];
            addr[0] = addr[1] = addr[2] = addr[3] = 0;
        } else {
            addr = ip.getAddress();
        }

        if (addr.length == 4) {
            addrType = SOCKS_ATYP_IPV4;
        } else {
            addrType = SOCKS_ATYP_IPV6;
        }

        data = new byte[6 + addr.length];
        data[0] = (byte) SOCKS_VERSION; // Version
        data[1] = (byte) command; // Command
        data[2] = (byte) 0; // Reserved byte
        data[3] = (byte) addrType; // Address type

        // Put Address
        System.arraycopy(addr, 0, data, 4, addr.length);
        // Put port
        data[data.length - 2] = (byte) (port >> 8);
        data[data.length - 1] = (byte) (port);
    }

    /**
     * Construct client request or server response.
     *
     * @param cmd      - Request/Response code.
     * @param hostName - IP field as hostName, uses ADDR_TYPE of HOSTNAME.
     * @paarm port - port field.
     */
    public Socks5Message(int cmd, String hostName, int port) {
        super(cmd, null, port);
        this.host = hostName;
        this.version = SOCKS_VERSION;

        //log.debug("Doing ATYP_DOMAINNAME");

        addrType = SOCKS_ATYP_DOMAINNAME;
        final byte addr[] = hostName.getBytes();

        data = new byte[7 + addr.length];
        data[0] = (byte) SOCKS_VERSION; // Version
        data[1] = (byte) command; // Command
        data[2] = (byte) 0; // Reserved byte
        data[3] = (byte) SOCKS_ATYP_DOMAINNAME; // Address type
        data[4] = (byte) addr.length; // Length of the address

        // Put Address
        System.arraycopy(addr, 0, data, 5, addr.length);
        // Put port
        data[data.length - 2] = (byte) (port >> 8);
        data[data.length - 1] = (byte) (port);
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
    public Socks5Message(InputStream in) throws SocksException, IOException {
        this(in, true);
    }

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
    public Socks5Message(InputStream in, boolean clientMode)
            throws SocksException, IOException {

        read(in, clientMode);
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
    public void read(InputStream in) throws SocksException, IOException {
        read(in, true);
    }

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
    public void read(InputStream in, boolean clientMode) throws SocksException,
            IOException {

        data = null;
        ip = null;

        final DataInputStream di = new DataInputStream(in);

        version = di.readUnsignedByte();
        command = di.readUnsignedByte();

        if (clientMode && (command != 0)) {
            throw new SocksException(command);
        }

        di.readUnsignedByte();
        addrType = di.readUnsignedByte();

        byte addr[];

        switch (addrType) {
            case SOCKS_ATYP_IPV4:
                addr = new byte[4];
                di.readFully(addr);
                host = bytes2IPV4(addr, 0);
                break;
            case SOCKS_ATYP_IPV6:
                addr = new byte[SOCKS_IPV6_LENGTH];// I believe it is 16 bytes,huge!
                di.readFully(addr);
                host = bytes2IPV6(addr, 0);
                break;
            case SOCKS_ATYP_DOMAINNAME:
                log.debug("Reading ATYP_DOMAINNAME");
                addr = new byte[di.readUnsignedByte()];// Next byte shows the length
                di.readFully(addr);
                host = new String(addr);
                break;
            default:
                throw (new SocksException(SocksProxyBase.SOCKS_JUST_ERROR));
        }

        port = di.readUnsignedShort();

        if ((addrType != SOCKS_ATYP_DOMAINNAME) && doResolveIP) {
            try {
                ip = InetAddress.getByName(host);
            } catch (final UnknownHostException uh_ex) {
            }
        }
    }

    /**
     * Writes the message to the stream.
     *
     * @param out Output stream to which message should be written.
     */
    public void write(OutputStream out) throws SocksException, IOException {
        if (data == null) {
            Socks5Message msg;

            if (addrType == SOCKS_ATYP_DOMAINNAME) {
                msg = new Socks5Message(command, host, port);
            } else {
                if (ip == null) {
                    try {
                        ip = InetAddress.getByName(host);
                    } catch (final UnknownHostException uh_ex) {
                        throw new SocksException(
                                SocksProxyBase.SOCKS_JUST_ERROR);
                    }
                }
                msg = new Socks5Message(command, ip, port);
            }
            data = msg.data;
        }
        out.write(data);
    }

    /**
     * Returns IP field of the message as IP, if the message was created with
     * ATYP of HOSTNAME, it will attempt to resolve the hostname, which might
     * fail.
     *
     * @throws UnknownHostException if host can't be resolved.
     */
    public InetAddress getInetAddress() throws UnknownHostException {
        if (ip != null) {
            return ip;
        }

        return (ip = InetAddress.getByName(host));
    }

    /**
     * Returns string representation of the message.
     */
    public String toString() {
        // FIXME: Single line version, please.
        final String s = "Socks5Message:" + "\n" + "VN   " + version + "\n"
                + "CMD  " + command + "\n" + "ATYP " + addrType + "\n"
                + "ADDR " + host + "\n" + "PORT " + port + "\n";
        return s;
    }

    /**
     * Wether to resolve hostIP returned from SOCKS server that is wether to
     * create InetAddress object from the hostName string
     */
    static public boolean resolveIP() {
        return doResolveIP;
    }

    /**
     * Wether to resolve hostIP returned from SOCKS server that is wether to
     * create InetAddress object from the hostName string
     *
     * @param doResolve Wether to resolve hostIP from SOCKS server.
     * @return Previous value.
     */
    static public boolean resolveIP(boolean doResolve) {
        final boolean old = doResolveIP;
        doResolveIP = doResolve;
        return old;
    }

	/*
     * private static final void debug(String s){ if(DEBUG) System.out.print(s);
	 * } private static final boolean DEBUG = false;
	 */

    // SOCKS5 constants
    public static final int SOCKS_VERSION = 5;

    public static final int SOCKS_ATYP_IPV4 = 0x1; // Where is 2??
    public static final int SOCKS_ATYP_DOMAINNAME = 0x3; // !!!!rfc1928
    public static final int SOCKS_ATYP_IPV6 = 0x4;

    public static final int SOCKS_IPV6_LENGTH = 16;

    static boolean doResolveIP = true;

}
