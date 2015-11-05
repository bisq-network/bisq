package com.runjva.sourceforge.jsocks.protocol;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * SOCKS5 none authentication. Dummy class does almost nothing.
 */
public class AuthenticationNone implements Authentication {

    public Object[] doSocksAuthentication(final int methodId,
                                          final java.net.Socket proxySocket) throws java.io.IOException {

        if (methodId != 0) {
            return null;
        }

        InputStream in = proxySocket.getInputStream();
        OutputStream out = proxySocket.getOutputStream();
        return new Object[]{in, out};
    }
}
