package io.bitsquare.network;

import com.google.inject.Inject;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.net.UnknownHostException;

public class Socks5ProxyProvider {
    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyProvider.class);

    @Nullable
    private Socks5Proxy internalSocks5Proxy;
    @Nullable
    private Socks5Proxy externalSocks5Proxy;

    @Inject
    public Socks5ProxyProvider(@Named(NetworkOptionKeys.SOCKS_5_PROXY_ADDRESS) String socks5ProxyAddress) {
        if (!socks5ProxyAddress.isEmpty()) {
            String[] tokens = socks5ProxyAddress.split(":");
            if (tokens.length == 2) {
                try {
                    externalSocks5Proxy = new Socks5Proxy(tokens[0], Integer.valueOf(tokens[1]));
                } catch (UnknownHostException e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                }
            } else {
                log.error("Incorrect format for socks5ProxyAddress. Should be: host:port.\n" +
                        "socks5ProxyAddress=" + socks5ProxyAddress);
            }
        }
    }

    @Nullable
    public Socks5Proxy getSocks5Proxy() {
        if (externalSocks5Proxy != null)
            return externalSocks5Proxy;
        else if (internalSocks5Proxy != null)
            return internalSocks5Proxy;
        else
            return null;
    }

    public void setInternalSocks5Proxy(@Nullable Socks5Proxy bitsquareSocks5Proxy) {
        this.internalSocks5Proxy = bitsquareSocks5Proxy;
    }
}
