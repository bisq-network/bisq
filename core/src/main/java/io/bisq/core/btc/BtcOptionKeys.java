package io.bisq.core.btc;

public class BtcOptionKeys {
    public static final String BTC_NODES = "btcNodes";
    public static final String USE_TOR_FOR_BTC = "useTorForBtc";
    public static final String SOCKS5_DISCOVER_MODE = "socks5DiscoverMode";
    public static final String BASE_CURRENCY_NETWORK = "baseCurrencyNetwork";
    public static final String WALLET_DIR = "walletDir";
    public static final String USER_AGENT = "userAgent";
    public static final String USE_ALL_PROVIDED_NODES = "useAllProvidedNodes"; // We only use onion nodes if tor is enabled. That flag overrides that default behavior.
    public static final String NUM_CONNECTIONS_FOR_BTC = "numConnectionForBtc";
    public static final String REG_TEST_HOST = "bitcoinRegtestHost";
}
