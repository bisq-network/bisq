package io.bisq.network;

import com.google.inject.Inject;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.net.UnknownHostException;

/**
 * Provides Socks5Proxies for the bitcoin network and http requests
 * <p/>
 * By default there is only used the bisq internal Tor proxy, which is used for the P2P network, Btc network
 * (if Tor for btc is enabled) and http requests (if Tor for http requests is enabled).
 * If the user provides a socks5ProxyHttpAddress it will be used for http requests.
 * If the user provides a socks5ProxyBtcAddress, this will be used for the btc network.
 * If socks5ProxyBtcAddress is present but no socks5ProxyHttpAddress the socks5ProxyBtcAddress will be used for http
 * requests.
 * If no socks5ProxyBtcAddress and no socks5ProxyHttpAddress is defined (default) we use socks5ProxyInternal.
 */
public class Socks5ProxyProvider {
    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyProvider.class);

    @Nullable
    private Socks5Proxy socks5ProxyInternal;

    // proxy used for btc network
    @Nullable
    private final Socks5Proxy socks5ProxyBtc;

    // if defined proxy used for http requests
    @Nullable
    private final Socks5Proxy socks5ProxyHttp;

    @Inject
    public Socks5ProxyProvider(@Named(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) String socks5ProxyBtcAddress,
                               @Named(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) String socks5ProxyHttpAddress) {
        socks5ProxyBtc = getProxyFromAddress(socks5ProxyBtcAddress);
        socks5ProxyHttp = getProxyFromAddress(socks5ProxyHttpAddress);
    }

    @Nullable
    public Socks5Proxy getSocks5Proxy() {
        if (socks5ProxyBtc != null)
            return socks5ProxyBtc;
        else if (socks5ProxyInternal != null)
            return socks5ProxyInternal;
        else
            return null;
    }

    @Nullable
    public Socks5Proxy getSocks5ProxyBtc() {
        return socks5ProxyBtc;
    }

    @Nullable
    public Socks5Proxy getSocks5ProxyHttp() {
        return socks5ProxyHttp;
    }

    @Nullable
    public Socks5Proxy getSocks5ProxyInternal() {
        return socks5ProxyInternal;
    }

    public void setSocks5ProxyInternal(@Nullable Socks5Proxy bisqSocks5Proxy) {
        this.socks5ProxyInternal = bisqSocks5Proxy;
    }

    @Nullable
    private Socks5Proxy getProxyFromAddress(String socks5ProxyAddress) {
        if (!socks5ProxyAddress.isEmpty()) {
            String[] tokens = socks5ProxyAddress.split(":");
            if (tokens.length == 2) {
                try {
                    return new Socks5Proxy(tokens[0], Integer.valueOf(tokens[1]));
                } catch (UnknownHostException e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                }
            } else {
                log.error("Incorrect format for socks5ProxyAddress. Should be: host:port.\n" +
                        "socks5ProxyAddress=" + socks5ProxyAddress);
            }
        }
        return null;
    }
}
