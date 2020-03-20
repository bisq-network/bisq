/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import com.google.common.base.Charsets;

import java.net.InetAddress;
import java.net.Socket;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs DNS lookup over Socks5 proxy that implements the RESOLVE extension.
 * At this time, Tor is only known Socks5 proxy that supports it.
 * <p/>
 * Adapted from https://github.com/btcsuite/btcd/blob/master/connmgr/tor.go
 */
public class DnsLookupTor {
    private static final Logger log = LoggerFactory.getLogger(DnsLookupTor.class);
    private static final Map<Byte, String> torStatusErrors = DnsLookupTor.createMap();

    private static Map<Byte, String> createMap() {
        HashMap<Byte, String> map = new HashMap<>();
        map.put(b('\u0000'), "tor succeeded");
        map.put(b('\u0001'), "tor general error");
        map.put(b('\u0002'), "tor not allowed");
        map.put(b('\u0003'), "tor network is unreachable");
        map.put(b('\u0004'), "tor host is unreachable");
        map.put(b('\u0005'), "tor connection refused");
        map.put(b('\u0006'), "tor TTL expired");
        map.put(b('\u0007'), "tor command not supported");
        map.put(b('\u0008'), "tor address type not supported");
        return map;
    }

    /**
     * Performs DNS lookup and returns a single InetAddress
     */
    public static InetAddress lookup(Socks5Proxy proxy, String host) throws DnsLookupException {
        try {
            // note:  This is creating a new connection to our proxy, without any authentication.
            //        This works fine when connecting to bisq's internal Tor proxy, but
            //        would fail if user has configured an external proxy that requires auth.
            //        It would be much better to use the already connected proxy socket, but when I
            //        tried that I get weird errors and the lookup fails.
            //
            //        So this is an area for future improvement.
            Socket proxySocket = new Socket(proxy.getInetAddress(), proxy.getPort());

            proxySocket.getOutputStream().write(new byte[]{b('\u0005'), b('\u0001'), b('\u0000')});
            byte[] buf = new byte[2];
            //noinspection ResultOfMethodCallIgnored
            proxySocket.getInputStream().read(buf);

            if (buf[0] != b('\u0005')) {
                throw new DnsLookupException("Invalid Proxy Response");
            }
            if (buf[1] != b('\u0000')) {
                throw new DnsLookupException("Unrecognized Tor Auth Method");
            }

            byte[] hostBytes = host.getBytes(Charsets.UTF_8);
            buf = new byte[7 + hostBytes.length];
            buf[0] = b('\u0005');               // version SOCKS5
            buf[1] = b('\u00f0');               // CMD_RESOLVE
            buf[2] = b('\u0000');               // (reserved)
            buf[3] = b('\u0003');               // SOCKS5_ATYPE_HOSTNAME
            buf[4] = (byte) hostBytes.length;
            System.arraycopy(hostBytes, 0, buf, 5, hostBytes.length);
            buf[5 + hostBytes.length] = 0;

            proxySocket.getOutputStream().write(buf);

            buf = new byte[4];
            //noinspection UnusedAssignment
            int bytesRead = proxySocket.getInputStream().read(buf);

            // TODO: Should not be a length check here as well?
           /* if (bytesRead != 4)
                throw new DnsLookupException("Invalid Tor Address Response");*/


            if (buf[0] != b('\u0005'))
                throw new DnsLookupException("Invalid Tor Proxy Response");

            if (buf[1] != b('\u0000')) {
                if (!torStatusErrors.containsKey(buf[1])) {
                    throw new DnsLookupException("Invalid Tor Proxy Response");
                }
                throw new DnsLookupException(torStatusErrors.get(buf[1]) + "(host=" + host + ")");
            }

            final char SOCKS5_ATYPE_IPV4 = '\u0001';
            final char SOCKS5_ATYPE_IPV6 = '\u0004';
            final byte atype = buf[3];
            if (atype != b(SOCKS5_ATYPE_IPV4) && atype != b(SOCKS5_ATYPE_IPV6))
                throw new DnsLookupException(torStatusErrors.get(b('\u0001')) + "(host=" + host + ")");

            final int octets = (atype == SOCKS5_ATYPE_IPV4 ? 4 : 16);
            buf = new byte[octets];
            bytesRead = proxySocket.getInputStream().read(buf);
            if (bytesRead != octets)
                throw new DnsLookupException("Invalid Tor Address Response");

            return InetAddress.getByAddress(buf);
        } catch (IOException | DnsLookupException e) {
            log.warn("Error resolving " + host + ". Exception:\n" + e.toString());
            throw new DnsLookupException(e);
        }
    }

    /**
     * so we can have prettier code without a bunch of casts.
     */
    private static byte b(char c) {
        return (byte) c;
    }
}
