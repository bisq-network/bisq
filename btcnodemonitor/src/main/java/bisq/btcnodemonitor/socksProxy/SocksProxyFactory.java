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

package bisq.btcnodemonitor.socksProxy;

import bisq.network.p2p.network.Socks5ProxyInternalFactory;
import bisq.network.tor.TorService;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class SocksProxyFactory implements Socks5ProxyInternalFactory {
    @Setter
    @Nullable
    private TorService torService;
    private Socks5Proxy socksProxy;

    @Override
    public Socks5Proxy getSocksProxy() {
        if (torService == null) {
            return null;
        } else {
            try {
                if (socksProxy == null) {
                    socksProxy = torService.getSocks5Proxy();
                }
                return socksProxy;
            } catch (Throwable t) {
                log.error("Error at getSocksProxy", t);
                return null;
            }
        }
    }
}
