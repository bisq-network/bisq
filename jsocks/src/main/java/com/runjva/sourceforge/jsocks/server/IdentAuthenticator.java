package com.runjva.sourceforge.jsocks.server;

import com.runjva.sourceforge.jsocks.protocol.InetRange;
import com.runjva.sourceforge.jsocks.protocol.ProxyMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * An implementation of socks.ServerAuthentication which provides simple
 * authentication based on the host from which the connection is made and the
 * name of the user on the remote machine, as reported by identd daemon on the
 * remote machine.
 * <p>
 * It can also be used to provide authentication based only on the contacting
 * host address.
 */

public class IdentAuthenticator extends ServerAuthenticatorBase {
    /**
     * Vector of InetRanges
     */
    Vector<InetRange> hosts;

    /**
     * Vector of user hashes
     */
    Vector<Hashtable<?, ?>> users;

    String user;

    /**
     * Constructs empty IdentAuthenticator.
     */
    public IdentAuthenticator() {
        hosts = new Vector<InetRange>();
        users = new Vector<Hashtable<?, ?>>();
    }

    /**
     * Used to create instances returned from startSession.
     *
     * @param in   Input stream.
     * @param out  OutputStream.
     * @param user Username associated with this connection,could be null if name
     *             was not required.
     */
    IdentAuthenticator(InputStream in, OutputStream out, String user) {
        super(in, out);
        this.user = user;
    }

    /**
     * Adds range of addresses from which connection is allowed. Hashtable users
     * should contain user names as keys and anything as values (value is not
     * used and will be ignored).
     *
     * @param hostRange Range of ip addresses from which connection is allowed.
     * @param users     Hashtable of users for whom connection is allowed, or null to
     *                  indicate that anybody is allowed to connect from the hosts
     *                  within given range.
     */
    public synchronized void add(InetRange hostRange, Hashtable<?, ?> users) {
        this.hosts.addElement(hostRange);
        this.users.addElement(users);
    }

    /**
     * Grants permission only to those users, who connect from one of the hosts
     * registered with add(InetRange,Hashtable) and whose names, as reported by
     * identd daemon, are listed for the host the connection came from.
     */
    public ServerAuthenticator startSession(Socket s) throws IOException {

        final int ind = getRangeIndex(s.getInetAddress());
        String user = null;

        // System.out.println("getRangeReturned:"+ind);

        if (ind < 0) {
            return null; // Host is not on the list.
        }

        final ServerAuthenticator serverAuthenticator = super.startSession(s);
        final ServerAuthenticatorBase auth = (ServerAuthenticatorBase) serverAuthenticator;

        // System.out.println("super.startSession() returned:"+auth);
        if (auth == null) {
            return null;
        }

        // do the authentication

        final Hashtable<?, ?> user_names = users.elementAt(ind);

        if (user_names != null) { // If need to do authentication
            Ident ident;
            ident = new Ident(s);
            // If can't obtain user name, fail
            if (!ident.successful) {
                return null;
            }
            // If user name is not listed for this address, fail
            if (!user_names.containsKey(ident.userName)) {
                return null;
            }
            user = ident.userName;
        }
        return new IdentAuthenticator(auth.in, auth.out, user);

    }

    /**
     * For SOCKS5 requests allways returns true. For SOCKS4 requests checks
     * wether the user name supplied in the request corresponds to the name
     * obtained from the ident daemon.
     */
    public boolean checkRequest(ProxyMessage msg, java.net.Socket s) {
        // If it's version 5 request, or if anybody is permitted, return true;
        if ((msg.version == 5) || (user == null)) {
            return true;
        }

        if (msg.version != 4) {
            return false; // Who knows?
        }

        return user.equals(msg.user);
    }

    /**
     * Get String representaion of the IdentAuthenticator.
     */
    public String toString() {
        String s = "";

        for (int i = 0; i < hosts.size(); ++i) {
            s += "(Range:" + hosts.elementAt(i) + "," + //
                    " Users:" + userNames(i) + ") ";
        }
        return s;
    }

    // Private Methods
    // ////////////////
    private int getRangeIndex(InetAddress ip) {
        int index = 0;
        final Enumeration<InetRange> enumx = hosts.elements();
        while (enumx.hasMoreElements()) {
            final InetRange ir = enumx.nextElement();
            if (ir.contains(ip)) {
                return index;
            }
            index++;
        }
        return -1; // Not found
    }

    private String userNames(int i) {
        if (users.elementAt(i) == null) {
            return "Everybody is permitted.";
        }

        final Enumeration<?> enumx = ((Hashtable<?, ?>) users.elementAt(i))
                .keys();
        if (!enumx.hasMoreElements()) {
            return "";
        }
        String s = enumx.nextElement().toString();
        while (enumx.hasMoreElements()) {
            s += "; " + enumx.nextElement();
        }

        return s;
    }

}
