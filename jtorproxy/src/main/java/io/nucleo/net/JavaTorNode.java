package io.nucleo.net;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;

import java.io.File;
import java.io.IOException;

public class JavaTorNode extends TorNode<JavaOnionProxyManager, JavaOnionProxyContext> {

    public JavaTorNode(File torDirectory, boolean useBridges) throws IOException {
        super(new JavaOnionProxyManager(new JavaOnionProxyContext(torDirectory)), useBridges);
    }

}
