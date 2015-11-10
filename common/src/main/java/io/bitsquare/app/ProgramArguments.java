package io.bitsquare.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO too app specific for common...
public class ProgramArguments {
    // program arg names
    public static final String TOR_DIR = "torDir";
    public static final String USE_LOCALHOST = "useLocalhost";
    public static final String DEV_TEST = "devTest";


    public static final String NAME_KEY = "node.name";
    public static final String PORT_KEY = "node.port";

    public static final String NETWORK_ID = "network.id";

    private static final Logger log = LoggerFactory.getLogger(ProgramArguments.class);
}
