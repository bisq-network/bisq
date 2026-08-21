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

/**
 * Copyright (C) 2010-2014 Leon Blakey <lord.quackstar at gmail.com>
 * <p/>
 * This file is part of PircBotX.
 * <p/>
 * PircBotX is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p/>
 * PircBotX is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with
 * PircBotX. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.nodes;

import javax.net.SocketFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import java.io.IOException;

/**
 * A basic SocketFactory for creating sockets that connect through the specified
 * proxy.
 *
 * @author Leon Blakey
 */
public class ProxySocketFactory extends SocketFactory {
    protected final Proxy proxy;

    /**
     * Create all sockets with the specified proxy.
     *
     * @param proxy An existing proxy
     */
    public ProxySocketFactory(Proxy proxy) {
        this.proxy = proxy;
    }

    /**
     * A convenience constructor for creating a proxy with the specified host
     * and port.
     *
     * @param proxyType The type of proxy were connecting to
     * @param hostname  The hostname of the proxy server
     * @param port      The port of the proxy server
     */
    public ProxySocketFactory(Proxy.Type proxyType, String hostname, int port) {
        this.proxy = new Proxy(proxyType, new InetSocketAddress(hostname, port));
    }

    @Override
    public Socket createSocket() throws IOException {
        Socket socket = new Socket(proxy);
        return socket;
    }

    @Override
    public Socket createSocket(String string, int i) throws IOException {
        Socket socket = new Socket(proxy);
        socket.connect(new InetSocketAddress(string, i));
        return socket;
    }

    @Override
    public Socket createSocket(String string, int i, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = new Socket(proxy);
        socket.bind(new InetSocketAddress(localAddress, localPort));
        socket.connect(new InetSocketAddress(string, i));
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress ia, int i) throws IOException {
        Socket socket = new Socket(proxy);
        socket.connect(new InetSocketAddress(ia, i));
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress ia, int i, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = new Socket(proxy);
        socket.bind(new InetSocketAddress(localAddress, localPort));
        socket.connect(new InetSocketAddress(ia, i));
        return socket;
    }
}
