package io.bitsquare.app;

public class DevFlags {
    // Was used for P2P network stress test to adjust several setting for the tests (e.g. use lower btc fees for offers,..)
    public static final boolean STRESS_TEST_MODE = false;

    // If that is true all the privileged features which requires a private key to enable it are overridden by a dev ey pair.
    // The UI got set the private dev key so the developer does not need to do anything and can test those features.
    // Features: Arbitration registration (alt+R at account), Alert/Update (alt+m), private message to a 
    // peer (click user icon and alt+r), filter/block offers by various data like offer ID (cmd + f).
    // The user can set a program argument to ignore all of those privileged messages. They are intended for 
    // emergency cases only (beside update message and arbitrator registration).
    public static final boolean USE_DEV_PRIVILEGE_KEYS = true;

    // If set to true we ignore several UI behavior like confirmation popups as well dummy accounts are created and 
    // offers are filled with default values. Intended to make dev testing faster.
    public static final boolean DEV_MODE = STRESS_TEST_MODE || true;
}
