package com.runjva.sourceforge.jsocks.main;

import com.runjva.sourceforge.jsocks.protocol.InetRange;
import com.runjva.sourceforge.jsocks.protocol.ProxyServer;
import com.runjva.sourceforge.jsocks.protocol.SocksProxyBase;
import com.runjva.sourceforge.jsocks.server.IdentAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

public class SOCKS {

    private static final int DEFAULT_LISTENING_PORT = 1080;
    final private static Logger log = LoggerFactory.getLogger(SOCKS.class);

    static public void usage() {
        System.out.println("Usage: java SOCKS [inifile1 inifile2 ...]\n"
                + "If none inifile is given, uses socks.properties.\n");
    }

    static public void main(String[] args) {

        String[] file_names;
        int port = DEFAULT_LISTENING_PORT;
        String logFile = null;
        String host = null;

        final IdentAuthenticator auth = new IdentAuthenticator();

        InetAddress localIP = null;

        if (args.length == 0) {
            file_names = new String[]{"socks.properties"};
        } else {
            file_names = args;
        }

        inform("Loading properties");
        for (int i = 0; i < file_names.length; ++i) {

            inform("Reading file " + file_names[i]);

            final Properties pr = loadProperties(file_names[i]);
            if (pr == null) {
                System.err.println("Loading of properties from "
                        + file_names[i] + "failed.");
                usage();
                return;
            }
            if (!addAuth(auth, pr)) {
                System.err.println("Error in file " + file_names[i] + ".");
                usage();
                return;
            }
            // First file should contain all global settings,
            // like port and host and log.
            if (i == 0) {
                final String port_s = (String) pr.get("port");
                if (port_s != null) {
                    try {
                        port = Integer.parseInt(port_s);
                    } catch (final NumberFormatException nfe) {
                        System.err.println("Can't parse port: " + port_s);
                        return;
                    }
                }

                serverInit(pr);
                logFile = (String) pr.get("log");
                host = (String) pr.get("host");
            }

            // inform("Props:"+pr);
        }

        if (logFile != null) {
            System.err.println("log property not supported anymore.");
        }
        if (host != null) {
            try {
                localIP = InetAddress.getByName(host);
            } catch (final UnknownHostException uhe) {
                System.err.println("Can't resolve local ip: " + host);
                return;
            }
        }

        inform("Using Ident Authentication scheme: " + auth);
        final ProxyServer server = new ProxyServer(auth);
        server.start(port, 5, localIP);
    }

    static Properties loadProperties(String file_name) {

        final Properties pr = new Properties();

        try {
            final InputStream fin = new FileInputStream(file_name);
            pr.load(fin);
            fin.close();
        } catch (final IOException ioe) {
            return null;
        }
        return pr;
    }

    static boolean addAuth(IdentAuthenticator ident, Properties pr) {

        InetRange irange;

        final String range = (String) pr.get("range");
        if (range == null) {
            return false;
        }
        irange = parseInetRange(range);

        final String users = (String) pr.get("users");

        if (users == null) {
            ident.add(irange, null);
            return true;
        }

        final Hashtable<String, String> uhash = new Hashtable<String, String>();

        final StringTokenizer st = new StringTokenizer(users, ";");
        while (st.hasMoreTokens()) {
            uhash.put(st.nextToken(), "");
        }

        ident.add(irange, uhash);
        return true;
    }

    /**
     * Does server initialisation.
     */
    static void serverInit(Properties props) {
        int val;
        val = readInt(props, "iddleTimeout");
        if (val >= 0) {
            ProxyServer.setIddleTimeout(val);
            inform("Setting iddle timeout to " + val + " ms.");
        }
        val = readInt(props, "acceptTimeout");
        if (val >= 0) {
            ProxyServer.setAcceptTimeout(val);
            inform("Setting accept timeout to " + val + " ms.");
        }
        val = readInt(props, "udpTimeout");
        if (val >= 0) {
            ProxyServer.setUDPTimeout(val);
            inform("Setting udp timeout to " + val + " ms.");
        }

        val = readInt(props, "datagramSize");
        if (val >= 0) {
            ProxyServer.setDatagramSize(val);
            inform("Setting datagram size to " + val + " bytes.");
        }

        proxyInit(props);

    }

    /**
     * Initialises proxy, if any specified.
     */
    static void proxyInit(Properties props) {
        String proxy_list;
        SocksProxyBase proxy = null;
        StringTokenizer st;

        proxy_list = (String) props.get("proxy");
        if (proxy_list == null) {
            return;
        }

        st = new StringTokenizer(proxy_list, ";");
        while (st.hasMoreTokens()) {
            final String proxy_entry = st.nextToken();

            final SocksProxyBase p = SocksProxyBase.parseProxy(proxy_entry);

            if (p == null) {
                exit("Can't parse proxy entry:" + proxy_entry);
            }

            inform("Adding Proxy:" + p);

            if (proxy != null) {
                p.setChainProxy(proxy);
            }

            proxy = p;

        }
        if (proxy == null) {
            return; // Empty list
        }

        final String direct_hosts = (String) props.get("directHosts");
        if (direct_hosts != null) {
            final InetRange ir = parseInetRange(direct_hosts);
            inform("Setting direct hosts:" + ir);
            proxy.setDirect(ir);
        }

        ProxyServer.setProxy(proxy);
    }

    /**
     * Inits range from the string of semicolon separated ranges.
     */
    static InetRange parseInetRange(String source) {
        final InetRange irange = new InetRange();

        final StringTokenizer st = new StringTokenizer(source, ";");
        while (st.hasMoreTokens()) {
            irange.add(st.nextToken());
        }

        return irange;
    }

    /**
     * Integer representaion of the property named name, or -1 if one is not
     * found.
     */
    static int readInt(Properties props, String name) {
        int result = -1;
        final String val = (String) props.get(name);
        if (val == null) {
            return -1;
        }
        final StringTokenizer st = new StringTokenizer(val);
        if (!st.hasMoreElements()) {
            return -1;
        }
        try {
            result = Integer.parseInt(st.nextToken());
        } catch (final NumberFormatException nfe) {
            inform("Bad value for " + name + ":" + val);
        }
        return result;
    }

    // Display functions
    // /////////////////

    static void inform(String s) {
        log.debug(s);
    }

    static void exit(String msg) {
        System.err.println("Error:" + msg);
        System.err.println("Aborting operation");
        System.exit(0);
    }
}
