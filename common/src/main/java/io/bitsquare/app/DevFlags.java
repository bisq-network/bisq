package io.bitsquare.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DevFlags {
    private static final Logger log = LoggerFactory.getLogger(DevFlags.class);

    public static final boolean STRESS_TEST_MODE = false;
    public static final boolean DEV_MODE = STRESS_TEST_MODE || false;
}
