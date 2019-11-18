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

package bisq.core.app;

import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.btc.BtcOptionKeys;
import bisq.core.btc.UserAgent;
import bisq.core.dao.DaoOptionKeys;
import bisq.core.exceptions.BisqException;
import bisq.core.filter.FilterManager;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.network.ConnectionConfig;

import bisq.common.CommonOptionKeys;
import bisq.common.app.Version;
import bisq.common.crypto.KeyStorage;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.NetworkParameters;

import org.springframework.core.env.Environment;
import org.springframework.core.env.JOptCommandLinePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

import joptsimple.OptionSet;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import ch.qos.logback.classic.Level;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BisqEnvironment extends StandardEnvironment {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void setDefaultAppName(String defaultAppName) {
        DEFAULT_APP_NAME = defaultAppName;
    }

    public static String DEFAULT_APP_NAME = "Bisq";

    public static final String DEFAULT_USER_DATA_DIR = defaultUserDataDir();
    public static final String DEFAULT_APP_DATA_DIR = appDataDir(DEFAULT_USER_DATA_DIR, DEFAULT_APP_NAME);

    public static final String LOG_LEVEL_DEFAULT = Level.INFO.levelStr;

    public static final String BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME = "bisqCommandLineProperties";
    public static final String BISQ_APP_DIR_PROPERTY_SOURCE_NAME = "bisqAppDirProperties";
    public static final String BISQ_DEFAULT_PROPERTY_SOURCE_NAME = "bisqDefaultProperties";

    private static String staticAppDataDir;

    public static String getStaticAppDataDir() {
        return staticAppDataDir;
    }

    @SuppressWarnings("SameReturnValue")
    public static BaseCurrencyNetwork getDefaultBaseCurrencyNetwork() {
        return BaseCurrencyNetwork.BTC_MAINNET;
    }

    protected static BaseCurrencyNetwork baseCurrencyNetwork = getDefaultBaseCurrencyNetwork();

    public static NetworkParameters getParameters() {
        return getBaseCurrencyNetwork().getParameters();
    }

    public static BaseCurrencyNetwork getBaseCurrencyNetwork() {
        return baseCurrencyNetwork;
    }

    private static String defaultUserDataDir() {
        if (Utilities.isWindows())
            return System.getenv("APPDATA");
        else if (Utilities.isOSX())
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toString();
        else // *nix
            return Paths.get(System.getProperty("user.home"), ".local", "share").toString();
    }

    private static String appDataDir(String userDataDir, String appName) {
        //TODO fix for changing app name form bisq to Bisq (add dir renamed as well)
        final String newAppName = "Bisq";
        if (appName.equals(newAppName)) {
            final String oldAppName = "bisq";
            Path oldPath = Paths.get(Paths.get(userDataDir, oldAppName).toString());// bisq
            Path newPath = Paths.get(Paths.get(userDataDir, appName).toString());//Bisq
            File oldDir = new File(oldPath.toString()); // bisq
            File newDir = new File(newPath.toString()); //Bisq
            try {
                if (Files.exists(oldPath) && oldDir.getCanonicalPath().endsWith(oldAppName)) {
                    if (Files.exists(newPath) && newDir.getCanonicalPath().endsWith(newAppName)) {
                        // we have both bisq and Bisq and rename Bisq to Bisq_backup
                        File newDirBackup = new File(newDir.toString() + "_backup"); // Bisq
                        log.info("Rename Bisq data dir {} to {}", newPath.toString(), newDirBackup.toString());
                        if (!newDir.renameTo(newDirBackup))
                            throw new RuntimeException("Cannot rename dir");

                        log.info("Rename old data dir {} to {}", oldDir.toString(), newPath.toString());
                        if (!oldDir.renameTo(newDir))
                            throw new RuntimeException("Cannot rename dir");
                    } else {
                        log.info("Rename old data dir {} to {}", oldDir.toString(), newPath.toString());
                        if (!oldDir.renameTo(newDir))
                            throw new RuntimeException("Cannot rename dir");

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return Paths.get(userDataDir, appName).toString();
    }

    // Util to set isDaoActivated to true if either set as program argument or we run testnet or regtest.
    // Can be removed once DAO is live.
    public static boolean isDaoActivated(Environment environment) {
        Boolean daoActivatedFromOptions = environment.getProperty(DaoOptionKeys.DAO_ACTIVATED, Boolean.class, true);
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        return daoActivatedFromOptions || !baseCurrencyNetwork.isMainnet();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected final ResourceLoader resourceLoader = new DefaultResourceLoader();

    protected final String appName;
    protected final String userDataDir;
    @Getter
    protected final String appDataDir;
    protected final String btcNetworkDir, userAgent;
    protected final String logLevel, providers;
    @Getter
    @Setter
    protected boolean isBitcoinLocalhostNodeRunning;
    @Getter
    protected String desktopWithHttpApi, desktopWithGrpcApi;
    @Getter
    protected List<String> bannedSeedNodes, bannedBtcNodes, bannedPriceRelayNodes;

    protected final String btcNodes, seedNodes, ignoreDevMsg, useDevPrivilegeKeys, useDevMode, useTorForBtc, rpcUser, rpcPassword,
            rpcHost, rpcPort, rpcBlockNotificationPort, rpcBlockNotificationHost, dumpBlockchainData, fullDaoNode,
            banList, dumpStatistics, maxMemory, socks5ProxyBtcAddress,
            torRcFile, torRcOptions, externalTorControlPort, externalTorPassword, externalTorCookieFile,
            socks5ProxyHttpAddress, useAllProvidedNodes, numConnectionForBtc, genesisTxId, genesisBlockHeight, genesisTotalSupply,
            referralId, daoActivated, msgThrottlePerSec, msgThrottlePer10Sec, sendMsgThrottleTrigger, sendMsgThrottleSleep;

    @Getter
    protected boolean ignoreLocalBtcNode;

    protected final boolean externalTorUseSafeCookieAuthentication, torStreamIsolation;

    public BisqEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(
                options)));
    }

    @SuppressWarnings("ConstantConditions")
    public BisqEnvironment(PropertySource commandLineProperties) {
        //CommonOptionKeys
        logLevel = getProperty(commandLineProperties, CommonOptionKeys.LOG_LEVEL_KEY, LOG_LEVEL_DEFAULT);
        useDevMode = getProperty(commandLineProperties, CommonOptionKeys.USE_DEV_MODE, "");

        //AppOptionKeys
        userDataDir = getProperty(commandLineProperties, AppOptionKeys.USER_DATA_DIR_KEY, DEFAULT_USER_DATA_DIR);
        appName = getProperty(commandLineProperties, AppOptionKeys.APP_NAME_KEY, DEFAULT_APP_NAME);
        appDataDir = getProperty(commandLineProperties, AppOptionKeys.APP_DATA_DIR_KEY, appDataDir(userDataDir, appName));
        staticAppDataDir = appDataDir;

        desktopWithHttpApi = getProperty(commandLineProperties, AppOptionKeys.DESKTOP_WITH_HTTP_API, "false");
        desktopWithGrpcApi = getProperty(commandLineProperties, AppOptionKeys.DESKTOP_WITH_GRPC_API, "false");
        ignoreDevMsg = getProperty(commandLineProperties, AppOptionKeys.IGNORE_DEV_MSG_KEY, "");
        useDevPrivilegeKeys = getProperty(commandLineProperties, AppOptionKeys.USE_DEV_PRIVILEGE_KEYS, "");
        referralId = getProperty(commandLineProperties, AppOptionKeys.REFERRAL_ID, "");
        dumpStatistics = getProperty(commandLineProperties, AppOptionKeys.DUMP_STATISTICS, "");
        maxMemory = getProperty(commandLineProperties, AppOptionKeys.MAX_MEMORY, "");
        providers = getProperty(commandLineProperties, AppOptionKeys.PROVIDERS, "");

        //NetworkOptionKeys
        seedNodes = getProperty(commandLineProperties, NetworkOptionKeys.SEED_NODES_KEY, "");
        banList = getProperty(commandLineProperties, NetworkOptionKeys.BAN_LIST, "");
        socks5ProxyBtcAddress = getProperty(commandLineProperties, NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS, "");
        socks5ProxyHttpAddress = getProperty(commandLineProperties, NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS, "");
        torRcFile = getProperty(commandLineProperties, NetworkOptionKeys.TORRC_FILE, "");
        torRcOptions = getProperty(commandLineProperties, NetworkOptionKeys.TORRC_OPTIONS, "");
        externalTorControlPort = getProperty(commandLineProperties, NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT, "");
        externalTorPassword = getProperty(commandLineProperties, NetworkOptionKeys.EXTERNAL_TOR_PASSWORD, "");
        externalTorCookieFile = getProperty(commandLineProperties, NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE, "");
        externalTorUseSafeCookieAuthentication = commandLineProperties.containsProperty(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE);
        torStreamIsolation = commandLineProperties.containsProperty(NetworkOptionKeys.TOR_STREAM_ISOLATION);

        msgThrottlePerSec = getProperty(commandLineProperties, NetworkOptionKeys.MSG_THROTTLE_PER_SEC, String.valueOf(ConnectionConfig.MSG_THROTTLE_PER_SEC));
        msgThrottlePer10Sec = getProperty(commandLineProperties, NetworkOptionKeys.MSG_THROTTLE_PER_10_SEC, String.valueOf(ConnectionConfig.MSG_THROTTLE_PER_10_SEC));
        sendMsgThrottleTrigger = getProperty(commandLineProperties, NetworkOptionKeys.SEND_MSG_THROTTLE_TRIGGER, String.valueOf(ConnectionConfig.SEND_MSG_THROTTLE_TRIGGER));
        sendMsgThrottleSleep = getProperty(commandLineProperties, NetworkOptionKeys.SEND_MSG_THROTTLE_SLEEP, String.valueOf(ConnectionConfig.SEND_MSG_THROTTLE_SLEEP));


        //DaoOptionKeys
        rpcUser = getProperty(commandLineProperties, DaoOptionKeys.RPC_USER, "");
        rpcPassword = getProperty(commandLineProperties, DaoOptionKeys.RPC_PASSWORD, "");
        rpcHost = getProperty(commandLineProperties, DaoOptionKeys.RPC_HOST, "");
        rpcPort = getProperty(commandLineProperties, DaoOptionKeys.RPC_PORT, "");
        rpcBlockNotificationPort = getProperty(commandLineProperties, DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, "");
        rpcBlockNotificationHost = getProperty(commandLineProperties, DaoOptionKeys.RPC_BLOCK_NOTIFICATION_HOST, "");
        dumpBlockchainData = getProperty(commandLineProperties, DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, "");
        fullDaoNode = getProperty(commandLineProperties, DaoOptionKeys.FULL_DAO_NODE, "");
        genesisTxId = getProperty(commandLineProperties, DaoOptionKeys.GENESIS_TX_ID, "");
        genesisBlockHeight = getProperty(commandLineProperties, DaoOptionKeys.GENESIS_BLOCK_HEIGHT, "-1");
        genesisTotalSupply = getProperty(commandLineProperties, DaoOptionKeys.GENESIS_TOTAL_SUPPLY, "-1");
        daoActivated = getProperty(commandLineProperties, DaoOptionKeys.DAO_ACTIVATED, "true");

        //BtcOptionKeys
        btcNodes = getProperty(commandLineProperties, BtcOptionKeys.BTC_NODES, "");
        useTorForBtc = getProperty(commandLineProperties, BtcOptionKeys.USE_TOR_FOR_BTC, "");
        userAgent = getProperty(commandLineProperties, BtcOptionKeys.USER_AGENT, "Bisq");
        useAllProvidedNodes = getProperty(commandLineProperties, BtcOptionKeys.USE_ALL_PROVIDED_NODES, "false");
        numConnectionForBtc = getProperty(commandLineProperties, BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC, "9");
        ignoreLocalBtcNode = getProperty(commandLineProperties, BtcOptionKeys.IGNORE_LOCAL_BTC_NODE, "false").equalsIgnoreCase("true");


        MutablePropertySources propertySources = getPropertySources();
        propertySources.addFirst(commandLineProperties);
        try {
            propertySources.addLast(getAppDirProperties());

            bannedPriceRelayNodes = getListProperty(FilterManager.BANNED_PRICE_RELAY_NODES, null);
            bannedSeedNodes = getListProperty(FilterManager.BANNED_SEED_NODES, new ArrayList<>());
            bannedBtcNodes = getListProperty(FilterManager.BANNED_BTC_NODES, null);

            baseCurrencyNetwork = BaseCurrencyNetwork.valueOf(getProperty(BtcOptionKeys.BASE_CURRENCY_NETWORK,
                    getDefaultBaseCurrencyNetwork().name()).toUpperCase());

            btcNetworkDir = Paths.get(appDataDir, baseCurrencyNetwork.name().toLowerCase()).toString();
            File btcNetworkDirFile = new File(btcNetworkDir);
            if (!btcNetworkDirFile.exists())
                //noinspection ResultOfMethodCallIgnored
                btcNetworkDirFile.mkdir();

            // btcNetworkDir used in defaultProperties
            propertySources.addLast(defaultProperties());
        } catch (Exception ex) {
            throw new BisqException(ex);
        }
    }

    public void saveBaseCryptoNetwork(BaseCurrencyNetwork baseCurrencyNetwork) {
        BisqEnvironment.baseCurrencyNetwork = baseCurrencyNetwork;
        setProperty(BtcOptionKeys.BASE_CURRENCY_NETWORK, baseCurrencyNetwork.name());
    }

    public void saveBannedSeedNodes(@Nullable List<String> bannedNodes) {
        setProperty(FilterManager.BANNED_SEED_NODES, bannedNodes == null ? "" : String.join(",", bannedNodes));
    }

    public void saveBannedBtcNodes(@Nullable List<String> bannedNodes) {
        setProperty(FilterManager.BANNED_BTC_NODES, bannedNodes == null ? "" : String.join(",", bannedNodes));
    }

    public void saveBannedPriceRelayNodes(@Nullable List<String> bannedNodes) {
        setProperty(FilterManager.BANNED_PRICE_RELAY_NODES, bannedNodes == null ? "" : String.join(",", bannedNodes));
    }

    protected void setProperty(String key, String value) {
        try {
            Resource resource = getAppDirPropertiesResource();
            File file = resource.getFile();
            Properties properties = new Properties();
            if (file.exists()) {
                Object propertiesObject = getAppDirProperties().getSource();
                if (propertiesObject instanceof Properties) {
                    properties = (Properties) propertiesObject;
                } else {
                    log.warn("propertiesObject not instance of Properties");
                }
            }

            if (!value.isEmpty())
                properties.setProperty(key, value);
            else
                properties.remove(key);

            log.debug("properties=" + properties);

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                properties.store(fileOutputStream, null);
            } catch (IOException e1) {
                log.error(e1.toString());
                e1.printStackTrace();
                throw new RuntimeException(e1);
            }
        } catch (Exception e2) {
            log.error(e2.toString());
            e2.printStackTrace();
            throw new RuntimeException(e2);
        }
    }

    private Resource getAppDirPropertiesResource() {
        String location = String.format("file:%s/bisq.properties", appDataDir);
        return resourceLoader.getResource(location);
    }

    PropertySource<?> getAppDirProperties() throws Exception {
        Resource resource = getAppDirPropertiesResource();

        if (!resource.exists())
            return new PropertySource.StubPropertySource(BISQ_APP_DIR_PROPERTY_SOURCE_NAME);

        return new ResourcePropertySource(BISQ_APP_DIR_PROPERTY_SOURCE_NAME, resource);
    }

    private String getProperty(PropertySource properties, String propertyKey, String defaultValue) {
        return properties.containsProperty(propertyKey) ? (String) properties.getProperty(propertyKey) : defaultValue;
    }

    private List<String> getListProperty(String key, List<String> defaultValue) {
        final String value = getProperty(key, "");
        return value.isEmpty() ? defaultValue : Arrays.asList(StringUtils.deleteWhitespace(value).split(","));
    }

    private PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BISQ_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            {
                setProperty(CommonOptionKeys.LOG_LEVEL_KEY, logLevel);
                setProperty(CommonOptionKeys.USE_DEV_MODE, useDevMode);

                setProperty(NetworkOptionKeys.SEED_NODES_KEY, seedNodes);
                setProperty(NetworkOptionKeys.BAN_LIST, banList);
                setProperty(NetworkOptionKeys.TOR_DIR, Paths.get(btcNetworkDir, "tor").toString());
                setProperty(NetworkOptionKeys.NETWORK_ID, String.valueOf(baseCurrencyNetwork.ordinal()));
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS, socks5ProxyBtcAddress);
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS, socks5ProxyHttpAddress);
                setProperty(NetworkOptionKeys.TORRC_FILE, torRcFile);
                setProperty(NetworkOptionKeys.TORRC_OPTIONS, torRcOptions);
                setProperty(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT, externalTorControlPort);
                setProperty(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD, externalTorPassword);
                setProperty(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE, externalTorCookieFile);
                if (externalTorUseSafeCookieAuthentication)
                    setProperty(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE, "true");
                if (torStreamIsolation)
                    setProperty(NetworkOptionKeys.TOR_STREAM_ISOLATION, "true");

                setProperty(NetworkOptionKeys.MSG_THROTTLE_PER_SEC, msgThrottlePerSec);
                setProperty(NetworkOptionKeys.MSG_THROTTLE_PER_10_SEC, msgThrottlePer10Sec);
                setProperty(NetworkOptionKeys.SEND_MSG_THROTTLE_TRIGGER, sendMsgThrottleTrigger);
                setProperty(NetworkOptionKeys.SEND_MSG_THROTTLE_SLEEP, sendMsgThrottleSleep);

                setProperty(AppOptionKeys.APP_DATA_DIR_KEY, appDataDir);
                setProperty(AppOptionKeys.DESKTOP_WITH_HTTP_API, desktopWithHttpApi);
                setProperty(AppOptionKeys.DESKTOP_WITH_GRPC_API, desktopWithGrpcApi);
                setProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY, ignoreDevMsg);
                setProperty(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS, useDevPrivilegeKeys);
                setProperty(AppOptionKeys.REFERRAL_ID, referralId);
                setProperty(AppOptionKeys.DUMP_STATISTICS, dumpStatistics);
                setProperty(AppOptionKeys.APP_NAME_KEY, appName);
                setProperty(AppOptionKeys.MAX_MEMORY, maxMemory);
                setProperty(AppOptionKeys.USER_DATA_DIR_KEY, userDataDir);
                setProperty(AppOptionKeys.PROVIDERS, providers);

                setProperty(DaoOptionKeys.RPC_USER, rpcUser);
                setProperty(DaoOptionKeys.RPC_PASSWORD, rpcPassword);
                setProperty(DaoOptionKeys.RPC_HOST, rpcHost);
                setProperty(DaoOptionKeys.RPC_PORT, rpcPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, rpcBlockNotificationPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_HOST, rpcBlockNotificationHost);
                setProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, dumpBlockchainData);
                setProperty(DaoOptionKeys.FULL_DAO_NODE, fullDaoNode);
                setProperty(DaoOptionKeys.GENESIS_TX_ID, genesisTxId);
                setProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, genesisBlockHeight);
                setProperty(DaoOptionKeys.GENESIS_TOTAL_SUPPLY, genesisTotalSupply);
                setProperty(DaoOptionKeys.DAO_ACTIVATED, daoActivated);

                setProperty(BtcOptionKeys.BTC_NODES, btcNodes);
                setProperty(BtcOptionKeys.USE_TOR_FOR_BTC, useTorForBtc);
                setProperty(BtcOptionKeys.WALLET_DIR, btcNetworkDir);
                setProperty(BtcOptionKeys.USER_AGENT, userAgent);
                setProperty(BtcOptionKeys.USE_ALL_PROVIDED_NODES, useAllProvidedNodes);
                setProperty(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC, numConnectionForBtc);
                setProperty(BtcOptionKeys.IGNORE_LOCAL_BTC_NODE, String.valueOf(ignoreLocalBtcNode));

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);

                setProperty(Storage.STORAGE_DIR, Paths.get(btcNetworkDir, "db").toString());
                setProperty(KeyStorage.KEY_STORAGE_DIR, Paths.get(btcNetworkDir, "keys").toString());
            }
        });
    }
}
