package com.runjva.sourceforge.jsocks.protocol;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Class InetRange provides the means of defining the range of inetaddresses.
 * It's used by Proxy class to store and look up addresses of machines, that
 * should be contacted directly rather then through the proxy.
 * <p>
 * InetRange provides several methods to add either standalone addresses, or
 * ranges (e.g. 100.200.300.0:100.200.300.255, which covers all addresses on on
 * someones local network). It also provides methods for checking wether given
 * address is in this range. Any number of ranges and standalone addresses can
 * be added to the range.
 */
public class InetRange implements Cloneable {

    Hashtable<String, Object[]> host_names;
    Vector<Object[]> all;
    Vector<String> end_names;

    boolean useSeparateThread = true;

    /**
     * Creates the empty range.
     */
    public InetRange() {
        all = new Vector<Object[]>();
        host_names = new Hashtable<String, Object[]>();
        end_names = new Vector<String>();
    }

    /**
     * Adds another host or range to this range. The String can be one of those:
     * <UL>
     * <li>Host name. eg.(Athena.myhost.com or 45.54.56.65)
     * <p>
     * <li>Range in the form .myhost.net.au <BR>
     * In which case anything that ends with .myhost.net.au will be considered
     * in the range.
     * <p>
     * <li>Range in the form ddd.ddd.ddd. <BR>
     * This will be treated as range ddd.ddd.ddd.0 to ddd.ddd.ddd.255. It is not
     * necessary to specify 3 first bytes you can use just one or two. For
     * example 130. will cover address between 130.0.0.0 and 13.255.255.255.
     * <p>
     * <li>Range in the form host_from[: \t\n\r\f]host_to. <br>
     * That is two hostnames or ips separated by either whitespace or colon.
     * </UL>
     */
    public synchronized boolean add(final String s0) {
        if (s0 == null) {
            return false;
        }

        String s = s0.trim();
        if (s.length() == 0) {
            return false;
        }

        Object[] entry;

        if (s.endsWith(".")) {
            // thing like: 111.222.33.
            // it is being treated as range 111.222.33.000 - 111.222.33.255

            final int[] addr = ip2intarray(s);
            long from, to;
            from = to = 0;

            if (addr == null) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (addr[i] >= 0) {
                    from += (((long) addr[i]) << 8 * (3 - i));
                } else {
                    to = from;
                    while (i < 4) {
                        to += 255l << 8 * (3 - i++);
                    }
                    break;
                }
            }
            entry = new Object[]{s, null, new Long(from), new Long(to)};
            all.addElement(entry);

        } else if (s.startsWith(".")) {
            // Thing like: .myhost.com

            end_names.addElement(s);
            all.addElement(new Object[]{s, null, null, null});
        } else {
            final StringTokenizer tokens = new StringTokenizer(s, " \t\r\n\f:");
            if (tokens.countTokens() > 1) {
                entry = new Object[]{s, null, null, null};
                resolve(entry, tokens.nextToken(), tokens.nextToken());
                all.addElement(entry);
            } else {
                entry = new Object[]{s, null, null, null};
                all.addElement(entry);
                host_names.put(s, entry);
                resolve(entry);
            }

        }

        return true;
    }

    /**
     * Adds another ip for this range.
     *
     * @param ip IP os the host which should be added to this range.
     */
    public synchronized void add(final InetAddress ip) {
        long from, to;
        from = to = ip2long(ip);
        all.addElement(new Object[]{ip.getHostName(), ip, new Long(from),
                new Long(to)});
    }

    /**
     * Adds another range of ips for this range.Any host with ip address greater
     * than or equal to the address of from and smaller than or equal to the
     * address of to will be included in the range.
     *
     * @param from IP from where range starts(including).
     * @param to   IP where range ends(including).
     */
    public synchronized void add(final InetAddress from, final InetAddress to) {
        all.addElement(new Object[]{
                from.getHostAddress() + ":" + to.getHostAddress(), null,
                new Long(ip2long(from)), new Long(ip2long(to))});
    }

    /**
     * Checks wether the givan host is in the range. Attempts to resolve host
     * name if required.
     *
     * @param host Host name to check.
     * @return true If host is in the range, false otherwise.
     * @see InetRange#contains(String, boolean)
     */
    public synchronized boolean contains(final String host) {
        return contains(host, true);
    }

    /**
     * Checks wether the given host is in the range.
     * <p>
     * Algorithm: <BR>
     * <ol>
     * <li>Look up if the hostname is in the range (in the Hashtable).
     * <li>Check if it ends with one of the speciefied endings.
     * <li>Check if it is ip(eg.130.220.35.98). If it is check if it is in the
     * range.
     * <li>If attemptResolve is true, host is name, rather than ip, and all
     * previous attempts failed, try to resolve the hostname, and check wether
     * the ip associated with the host is in the range.It also repeats all
     * previos steps with the hostname obtained from InetAddress, but the name
     * is not allways the full name,it is quite likely to be the same. Well it
     * was on my machine.
     * </ol>
     *
     * @param host           Host name to check.
     * @param attemptResolve Wether to lookup ip address which corresponds to the host,if
     *                       required.
     * @return true If host is in the range, false otherwise.
     */
    public synchronized boolean contains(final String host0,
                                         final boolean attemptResolve) {
        if (all.size() == 0) {
            return false; // Empty range
        }

        String host = host0.trim();
        if (host.length() == 0) {
            return false;
        }

        if (checkHost(host)) {
            return true;
        }
        if (checkHostEnding(host)) {
            return true;
        }

        final long l = host2long(host);
        if (l >= 0) {
            return contains(l);
        }

        if (!attemptResolve) {
            return false;
        }

        try {
            final InetAddress ip = InetAddress.getByName(host);
            return contains(ip);
        } catch (final UnknownHostException uhe) {

        }

        return false;
    }

    /**
     * Checks wether the given ip is in the range.
     *
     * @param ip Address of the host to check.
     * @return true If host is in the range, false otherwise.
     */
    public synchronized boolean contains(final InetAddress ip) {
        if (checkHostEnding(ip.getHostName())) {
            return true;
        }
        if (checkHost(ip.getHostName())) {
            return true;
        }
        return contains(ip2long(ip));
    }

    /**
     * Get all entries in the range as strings. <BR>
     * These strings can be used to delete entries from the range with remove
     * function.
     *
     * @return Array of entries as strings.
     * @see InetRange#remove(String)
     */
    public synchronized String[] getAll() {
        final int size = all.size();
        Object entry[];
        final String all_names[] = new String[size];

        for (int i = 0; i < size; ++i) {
            entry = all.elementAt(i);
            all_names[i] = (String) entry[0];
        }
        return all_names;
    }

    /**
     * Removes an entry from this range.<BR>
     *
     * @param s Entry to remove.
     * @return true if successfull.
     */
    public synchronized boolean remove(final String s) {
        final Enumeration<Object[]> enumx = all.elements();
        while (enumx.hasMoreElements()) {
            final Object[] entry = enumx.nextElement();
            if (s.equals(entry[0])) {
                all.removeElement(entry);
                end_names.removeElement(s);
                host_names.remove(s);
                return true;
            }
        }
        return false;
    }

    /**
     * Get string representaion of this Range.
     */
    public String toString() {
        final String all[] = getAll();
        if (all.length == 0) {
            return "";
        }

        String s = all[0];
        for (int i = 1; i < all.length; ++i) {
            s += "; " + all[i];
        }
        return s;
    }

    /**
     * Creates a clone of this Object
     */

    @SuppressWarnings("unchecked")
    public Object clone() {
        final InetRange new_range = new InetRange();
        new_range.all = (Vector<Object[]>) all.clone();
        new_range.end_names = (Vector<String>) end_names.clone();
        new_range.host_names = (Hashtable<String, Object[]>) host_names.clone();
        return new_range;
    }

    // Private methods
    // ///////////////

    /**
     * Same as previous but used internally, to avoid unnecessary convertion of
     * IPs, when checking subranges
     */
    private synchronized boolean contains(final long ip) {
        final Enumeration<Object[]> enumx = all.elements();
        while (enumx.hasMoreElements()) {
            final Object[] obj = enumx.nextElement();
            final Long from = obj[2] == null ? null : (Long) obj[2];
            final Long to = obj[3] == null ? null : (Long) obj[3];
            if ((from != null) && (from.longValue() <= ip)
                    && (to.longValue() >= ip)) {
                return true;
            }

        }
        return false;
    }

    private boolean checkHost(final String host) {
        return host_names.containsKey(host);
    }

    private boolean checkHostEnding(final String host) {
        final Enumeration<String> enumx = end_names.elements();
        while (enumx.hasMoreElements()) {
            if (host.endsWith(enumx.nextElement())) {
                return true;
            }
        }
        return false;
    }

    private void resolve(final Object[] entry) {
        // First check if it's in the form ddd.ddd.ddd.ddd.
        final long ip = host2long((String) entry[0]);
        if (ip >= 0) {
            entry[2] = entry[3] = new Long(ip);
        } else {
            final InetRangeResolver res = new InetRangeResolver(entry);
            res.resolve(useSeparateThread);
        }
    }

    private void resolve(final Object[] entry, final String from,
                         final String to) {
        long f, t;
        if (((f = host2long(from)) >= 0) && ((t = host2long(to)) >= 0)) {
            entry[2] = new Long(f);
            entry[3] = new Long(t);
        } else {
            final InetRangeResolver res = new InetRangeResolver(entry, from, to);
            res.resolve(useSeparateThread);
        }
    }

    // Class methods
    // /////////////

    // Converts ipv4 to long value(unsigned int)
    // /////////////////////////////////////////
    static long ip2long(final InetAddress ip) {
        long l = 0;
        final byte[] addr = ip.getAddress();

        if (addr.length == 4) { // IPV4
            for (int i = 0; i < 4; ++i) {
                l += (((long) addr[i] & 0xFF) << 8 * (3 - i));
            }
        } else { // IPV6
            return 0; // Have no idea how to deal with those
        }
        return l;
    }

    long host2long(final String host) {
        long ip = 0;

        // check if it's ddd.ddd.ddd.ddd
        if (!Character.isDigit(host.charAt(0))) {
            return -1;
        }

        final int[] addr = ip2intarray(host);
        if (addr == null) {
            return -1;
        }

        for (int i = 0; i < addr.length; ++i) {
            ip += ((long) (addr[i] >= 0 ? addr[i] : 0)) << 8 * (3 - i);
        }

        return ip;
    }

    static int[] ip2intarray(final String host) {
        final int[] address = {-1, -1, -1, -1};
        int i = 0;
        final StringTokenizer tokens = new StringTokenizer(host, ".");
        if (tokens.countTokens() > 4) {
            return null;
        }
        while (tokens.hasMoreTokens()) {
            try {
                address[i++] = Integer.parseInt(tokens.nextToken()) & 0xFF;
            } catch (final NumberFormatException nfe) {
                return null;
            }

        }
        return address;
    }

	/*
     * //* This was the test main function //**********************************
	 * 
	 * public static void main(String args[])throws UnknownHostException{ int i;
	 * 
	 * InetRange ir = new InetRange();
	 * 
	 * 
	 * for(i=0;i<args.length;++i){ System.out.println("Adding:" + args[i]);
	 * ir.add(args[i]); }
	 * 
	 * String host; java.io.DataInputStream din = new
	 * java.io.DataInputStream(System.in); try{ host = din.readLine();
	 * while(host!=null){ if(ir.contains(host)){
	 * System.out.println("Range contains ip:"+host); }else{
	 * System.out.println(host+" is not in the range"); } host = din.readLine();
	 * } }catch(java.io.IOException io_ex){ io_ex.printStackTrace(); } }
	 * ******************
	 */

}

class InetRangeResolver implements Runnable {

    Object[] entry;

    String from;
    String to;

    InetRangeResolver(final Object[] entry) {
        this.entry = entry;
        from = null;
        to = null;
    }

    InetRangeResolver(final Object[] entry, final String from, final String to) {
        this.entry = entry;
        this.from = from;
        this.to = to;
    }

    public final void resolve() {
        resolve(true);
    }

    public final void resolve(final boolean inSeparateThread) {
        if (inSeparateThread) {
            final Thread t = new Thread(this);
            t.start();
        } else {
            run();
        }

    }

    public void run() {
        try {
            if (from == null) {
                final InetAddress ip = InetAddress.getByName((String) entry[0]);
                entry[1] = ip;
                final Long l = new Long(InetRange.ip2long(ip));
                entry[2] = l;
                entry[3] = l;
            } else {
                final InetAddress f = InetAddress.getByName(from);
                final InetAddress t = InetAddress.getByName(to);
                entry[2] = new Long(InetRange.ip2long(f));
                entry[3] = new Long(InetRange.ip2long(t));

            }
        } catch (final UnknownHostException uhe) {
            // System.err.println("Resolve failed for "+from+','+to+','+entry[0]);
        }
    }

}
