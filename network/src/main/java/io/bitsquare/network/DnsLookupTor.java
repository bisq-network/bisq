/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.network;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs DNS lookup over Socks5 proxy that implements the RESOLVE extension.
 * At this time, Tor is only known Socks5 proxy that supports it.
 *
 * Adapted from https://github.com/btcsuite/btcd/blob/master/connmgr/tor.go
 */
public class DnsLookupTor {
    private static final Logger log = LoggerFactory.getLogger(DnsLookupTor.class);
    private static final Map<Byte, String> torStatusErrors = DnsLookupTor.createMap();

    private static Map<Byte, String> createMap() {
        HashMap<Byte, String> map = new HashMap<Byte, String>();
        map.put(Byte.valueOf(b('\u0000')), "tor succeeded");
        map.put(Byte.valueOf(b('\u0001')), "tor general error");
        map.put(Byte.valueOf(b('\u0002')), "tor not allowed");
        map.put(Byte.valueOf(b('\u0003')), "tor network is unreachable");
        map.put(Byte.valueOf(b('\u0004')), "tor host is unreachable");
        map.put(Byte.valueOf(b('\u0005')), "tor connection refused");
        map.put(Byte.valueOf(b('\u0006')), "tor TTL expired");
        map.put(Byte.valueOf(b('\u0007')), "tor command not supported");
        map.put(Byte.valueOf(b('\u0008')), "tor address type not supported");
        return map;
    }

    /**
     * Performs DNS lookup and returns a single InetAddress
     */
    public static InetAddress lookup(Socks5Proxy proxy, String host) throws Exception {
        Logger log = LoggerFactory.getLogger(DnsLookupTor.class);
        try {
            log.debug("Resolving {} over tor.", (Object)host);
            return DnsLookupTor.doLookup(proxy, host);
        }
        catch (Exception e) {
            log.warn("Error resolving " + host + ". Exception:\n" + e.toString());
            throw e;
        }
    }

    /**
     * The actual lookup is performed here.
     */
    private static InetAddress doLookup(Socks5Proxy proxy, String host) throws Exception {
        
        // note:  This is creating a new connection to our proxy, without any authentication.
        //        This works fine when connecting to bitsquare's internal Tor proxy, but
        //        would fail if user has configured an external proxy that requires auth.
        //        It would be much better to use the already connected proxy socket, but when I
        //        tried that I get weird errors and the lookup fails.
        //
        //        So this is an area for future improvement.
        Socket proxySocket = new Socket(proxy.getInetAddress(), proxy.getPort());
        
        proxySocket.getOutputStream().write(new byte[]{b('\u0005'), b('\u0001'), b('\u0000')});
        byte[] buf = new byte[2];
        proxySocket.getInputStream().read(buf);
        
        if (buf[0] != b('\u0005')) {
            throw new Exception("Invalid Proxy Response");
        }
        if (buf[1] != b('\u0000')) {
            throw new Exception("Unrecognized Tor Auth Method");
        }
        
        byte[] hostBytes = host.getBytes();
        buf = new byte[7 + hostBytes.length];
        buf[0] = b('\u0005');
        buf[1] = b('\u00f0');
        buf[2] = b('\u0000');
        buf[3] = b('\u0003');
        buf[4] = (byte)hostBytes.length;
        System.arraycopy(hostBytes, 0, buf, 5, hostBytes.length);
        buf[5 + hostBytes.length] = 0;
        
        proxySocket.getOutputStream().write(buf);
        
        buf = new byte[4];
        proxySocket.getInputStream().read(buf);
        
        if (buf[0] != b('\u0005')) {
            throw new Exception("Invalid Tor Proxy Response");
        }
        
        if (buf[1] != b('\u0000')) {
            if (!torStatusErrors.containsKey(Byte.valueOf(buf[1]))) {
                throw new Exception("Invalid Tor Proxy Response");
            }
            throw new Exception(torStatusErrors.get(Byte.valueOf(buf[1])));
        }
        
        if (buf[3] != b('\u0001')) {
            throw new Exception(torStatusErrors.get(Byte.valueOf(b('\u0001'))));
        }
        
        buf = new byte[4];
        int bytesRead = proxySocket.getInputStream().read(buf);
        
        if (bytesRead != 4) {
            throw new Exception("Invalid Tor Address Response");
        }
        
        InetAddress addr = InetAddress.getByAddress(buf);
        return addr;
    }

    /**
     * so we can have prettier code without a bunch of casts.
     */
    private static byte b(char c) {
        return (byte)c;
    }
}