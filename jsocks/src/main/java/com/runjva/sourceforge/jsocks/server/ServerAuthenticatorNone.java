package com.runjva.sourceforge.jsocks.server;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Simplest possible ServerAuthenticator implementation. Extends common base.
 */
public class ServerAuthenticatorNone extends ServerAuthenticatorBase {

    public ServerAuthenticatorNone(InputStream in, OutputStream out) {
        super(in, out);
    }

}
