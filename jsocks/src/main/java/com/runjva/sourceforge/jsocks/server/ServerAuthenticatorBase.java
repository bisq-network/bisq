package com.runjva.sourceforge.jsocks.server;

import com.runjva.sourceforge.jsocks.protocol.ProxyMessage;
import com.runjva.sourceforge.jsocks.protocol.UDPEncapsulation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;

/**
 * An implementation of ServerAuthenticator, which does <b>not</b> do any
 * authentication.
 * <p>
 * <FONT size="+3" color ="FF0000"> Warning!!</font><br>
 * Should not be used on machines which are not behind the firewall.
 * <p>
 * It is only provided to make implementing other authentication schemes easier.
 * <br>
 * For Example: <tt><pre>
 * class MyAuth extends socks.server.ServerAuthenticator{
 * ...
 * public ServerAuthenticator startSession(java.net.Socket s){
 * if(!checkHost(s.getInetAddress()) return null;
 * return super.startSession(s);
 * }
 * <p>
 * boolean checkHost(java.net.Inetaddress addr){
 * boolean allow;
 * //Do it somehow
 * return allow;
 * }
 * }
 * </pre></tt>
 */
public abstract class ServerAuthenticatorBase implements ServerAuthenticator {

    static final byte[] socks5response = {5, 0};

    InputStream in;
    OutputStream out;

    /**
     * Creates new instance of the ServerAuthenticatorNone.
     */
    public ServerAuthenticatorBase() {
        this.in = null;
        this.out = null;
    }

    /**
     * Constructs new ServerAuthenticatorNone object suitable for returning from
     * the startSession function.
     *
     * @param in  Input stream to return from getInputStream method.
     * @param out Output stream to return from getOutputStream method.
     */
    public ServerAuthenticatorBase(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * Grants access to everyone.Removes authentication related bytes from the
     * stream, when a SOCKS5 connection is being made, selects an authentication
     * NONE.
     */
    public ServerAuthenticator startSession(Socket s) throws IOException {

        final PushbackInputStream in = new PushbackInputStream(s
                .getInputStream());
        final OutputStream out = s.getOutputStream();

        final int version = in.read();
        if (version == 5) {
            if (!selectSocks5Authentication(in, out, 0)) {
                return null;
            }
        } else if (version == 4) {
            // Else it is the request message already, version 4
            in.unread(version);
        } else {
            return null;
        }

        return new ServerAuthenticatorNone(in, out);
    }

    /**
     * Get input stream.
     *
     * @return Input stream speciefied in the constructor.
     */
    public InputStream getInputStream() {
        return in;
    }

    /**
     * Get output stream.
     *
     * @return Output stream speciefied in the constructor.
     */
    public OutputStream getOutputStream() {
        return out;
    }

    /**
     * Allways returns null.
     *
     * @return null
     */
    public UDPEncapsulation getUdpEncapsulation() {
        return null;
    }

    /**
     * Allways returns true.
     */
    public boolean checkRequest(ProxyMessage msg) {
        return true;
    }

    /**
     * Allways returns true.
     */
    public boolean checkRequest(java.net.DatagramPacket dp, boolean out) {
        return true;
    }

    /**
     * Does nothing.
     */
    public void endSession() {
    }

    /**
     * Convinience routine for selecting SOCKSv5 authentication.
     * <p>
     * This method reads in authentication methods that client supports, checks
     * wether it supports given method. If it does, the notification method is
     * written back to client, that this method have been chosen for
     * authentication. If given method was not found, authentication failure
     * message is send to client ([5,FF]).
     *
     * @param in       Input stream, version byte should be removed from the stream
     *                 before calling this method.
     * @param out      Output stream.
     * @param methodId Method which should be selected.
     * @return true if methodId was found, false otherwise.
     */
    static public boolean selectSocks5Authentication(InputStream in,
                                                     OutputStream out, int methodId) throws IOException {

        final int num_methods = in.read();
        if (num_methods <= 0) {
            return false;
        }
        final byte method_ids[] = new byte[num_methods];
        final byte response[] = new byte[2];
        boolean found = false;

        response[0] = (byte) 5; // SOCKS version
        response[1] = (byte) 0xFF; // Not found, we are pessimistic

        int bread = 0; // bytes read so far
        while (bread < num_methods) {
            bread += in.read(method_ids, bread, num_methods - bread);
        }

        for (int i = 0; i < num_methods; ++i) {
            if (method_ids[i] == methodId) {
                found = true;
                response[1] = (byte) methodId;
                break;
            }
        }

        out.write(response);
        return found;
    }
}
