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

import bisq.common.CommonOptionKeys;
import bisq.common.app.DevEnv;
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
    private static final String BISQ_HOME_DIR_PROPERTY_SOURCE_NAME = "bisqHomeDirProperties";
    private static final String BISQ_CLASSPATH_PROPERTY_SOURCE_NAME = "bisqClasspathProperties";

    private static String staticAppDataDir;

    public static String getStaticAppDataDir() {
        return staticAppDataDir;
    }

    @SuppressWarnings("SameReturnValue")
    public static BaseCurrencyNetwork getDefaultBaseCurrencyNetwork() {
        return BaseCurrencyNetwork.BTC_MAINNET;
    }

    protected static BaseCurrencyNetwork baseCurrencyNetwork = getDefaultBaseCurrencyNetwork();

    public static boolean isDAOActivatedAndBaseCurrencySupportingBsq() {
        //noinspection ConstantConditions,PointlessBooleanExpression
        return DevEnv.isDaoActivated() && isBaseCurrencySupportingBsq();
    }

    public static boolean isBaseCurrencySupportingBsq() {
        return getBaseCurrencyNetwork().getCurrencyCode().equals("BTC");
    }

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
        Boolean daoActivatedFromOptions = environment.getProperty(DaoOptionKeys.DAO_ACTIVATED, Boolean.class, false);
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        boolean isRegTestOrTestNet = (baseCurrencyNetwork.isTestnet() || baseCurrencyNetwork.isRegtest());
        return daoActivatedFromOptions || isRegTestOrTestNet;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected final ResourceLoader resourceLoader = new DefaultResourceLoader();

    protected final String appName;
    protected final String userDataDir;
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
            rpcPort, rpcBlockNotificationPort, dumpBlockchainData, fullDaoNode,
            myAddress, banList, dumpStatistics, maxMemory, socks5ProxyBtcAddress,
            torRcFile, torRcOptions, externalTorControlPort, externalTorPassword, externalTorCookieFile,
            socks5ProxyHttpAddress, useAllProvidedNodes, numConnectionForBtc, genesisTxId, genesisBlockHeight, referralId, daoActivated;

    protected final boolean externalTorUseSafeCookieAuthentication, torStreamIsolation;

    @Getter
    protected final String httpApiHost;
    @Getter
    protected final Integer httpApiPort;
    @Getter
    protected boolean httpApiExperimentalFeaturesEnabled;

    public BisqEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(
                options)));
    }

    @SuppressWarnings("ConstantConditions")
    public BisqEnvironment(PropertySource commandLineProperties) {
        //CommonOptionKeys
        logLevel = commandLineProperties.containsProperty(CommonOptionKeys.LOG_LEVEL_KEY) ?
                (String) commandLineProperties.getProperty(CommonOptionKeys.LOG_LEVEL_KEY) :
                LOG_LEVEL_DEFAULT;

        //AppOptionKeys
        userDataDir = commandLineProperties.containsProperty(AppOptionKeys.USER_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.USER_DATA_DIR_KEY) :
                DEFAULT_USER_DATA_DIR;

        appName = commandLineProperties.containsProperty(AppOptionKeys.APP_NAME_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        appDataDir = commandLineProperties.containsProperty(AppOptionKeys.APP_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.APP_DATA_DIR_KEY) :
                appDataDir(userDataDir, appName);
        staticAppDataDir = appDataDir;

        desktopWithHttpApi = commandLineProperties.containsProperty(AppOptionKeys.DESKTOP_WITH_HTTP_API) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.DESKTOP_WITH_HTTP_API) :
                "false";
        desktopWithGrpcApi = commandLineProperties.containsProperty(AppOptionKeys.DESKTOP_WITH_GRPC_API) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.DESKTOP_WITH_GRPC_API) :
                "false";
        ignoreDevMsg = commandLineProperties.containsProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY) :
                "";
        useDevPrivilegeKeys = commandLineProperties.containsProperty(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) :
                "";
        referralId = commandLineProperties.containsProperty(AppOptionKeys.REFERRAL_ID) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.REFERRAL_ID) :
                "";
        httpApiExperimentalFeaturesEnabled = commandLineProperties.containsProperty(AppOptionKeys.HTTP_API_EXPERIMENTAL_FEATURES_ENABLED);
        httpApiHost = commandLineProperties.containsProperty(AppOptionKeys.HTTP_API_HOST) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.HTTP_API_HOST) :
                "127.0.0.1";
        httpApiPort = Integer.parseInt(commandLineProperties.containsProperty(AppOptionKeys.HTTP_API_PORT) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.HTTP_API_PORT) :
                "8080");
        useDevMode = commandLineProperties.containsProperty(CommonOptionKeys.USE_DEV_MODE) ?
                (String) commandLineProperties.getProperty(CommonOptionKeys.USE_DEV_MODE) :
                "";
        dumpStatistics = commandLineProperties.containsProperty(AppOptionKeys.DUMP_STATISTICS) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.DUMP_STATISTICS) :
                "";
        maxMemory = commandLineProperties.containsProperty(AppOptionKeys.MAX_MEMORY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.MAX_MEMORY) :
                "";
        providers = commandLineProperties.containsProperty(AppOptionKeys.PROVIDERS) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.PROVIDERS) :
                "";

        //NetworkOptionKeys
        seedNodes = commandLineProperties.containsProperty(NetworkOptionKeys.SEED_NODES_KEY) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SEED_NODES_KEY) :
                "";

        myAddress = commandLineProperties.containsProperty(NetworkOptionKeys.MY_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.MY_ADDRESS) :
                "";
        banList = commandLineProperties.containsProperty(NetworkOptionKeys.BAN_LIST) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.BAN_LIST) :
                "";
        socks5ProxyBtcAddress = commandLineProperties.containsProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) :
                "";
        socks5ProxyHttpAddress = commandLineProperties.containsProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) :
                "";
        torRcFile = commandLineProperties.containsProperty(NetworkOptionKeys.TORRC_FILE) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.TORRC_FILE) :
                "";
        torRcOptions = commandLineProperties.containsProperty(NetworkOptionKeys.TORRC_OPTIONS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.TORRC_OPTIONS) :
                "";
        externalTorControlPort = commandLineProperties.containsProperty(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT) :
                "";
        externalTorPassword = commandLineProperties.containsProperty(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD) :
                "";
        externalTorCookieFile = commandLineProperties.containsProperty(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE) :
                "";
        externalTorUseSafeCookieAuthentication = commandLineProperties.containsProperty(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE) ?
                true :
                false;
        torStreamIsolation = commandLineProperties.containsProperty(NetworkOptionKeys.TOR_STREAM_ISOLATION) ?
                true :
                false;

        //RpcOptionKeys
        rpcUser = commandLineProperties.containsProperty(DaoOptionKeys.RPC_USER) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_USER) :
                "";
        rpcPassword = commandLineProperties.containsProperty(DaoOptionKeys.RPC_PASSWORD) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_PASSWORD) :
                "";
        rpcPort = commandLineProperties.containsProperty(DaoOptionKeys.RPC_PORT) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_PORT) :
                "";
        rpcBlockNotificationPort = commandLineProperties.containsProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) :
                "";
        dumpBlockchainData = commandLineProperties.containsProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) :
                "";
        fullDaoNode = commandLineProperties.containsProperty(DaoOptionKeys.FULL_DAO_NODE) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.FULL_DAO_NODE) :
                "";
        genesisTxId = commandLineProperties.containsProperty(DaoOptionKeys.GENESIS_TX_ID) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.GENESIS_TX_ID) :
                "";
        genesisBlockHeight = commandLineProperties.containsProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) :
                "-1";
        daoActivated = commandLineProperties.containsProperty(DaoOptionKeys.DAO_ACTIVATED) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.DAO_ACTIVATED) :
                "";

        btcNodes = commandLineProperties.containsProperty(BtcOptionKeys.BTC_NODES) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.BTC_NODES) :
                "";

        useTorForBtc = commandLineProperties.containsProperty(BtcOptionKeys.USE_TOR_FOR_BTC) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.USE_TOR_FOR_BTC) :
                "";
        userAgent = commandLineProperties.containsProperty(BtcOptionKeys.USER_AGENT) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.USER_AGENT) :
                "Bisq";
        useAllProvidedNodes = commandLineProperties.containsProperty(BtcOptionKeys.USE_ALL_PROVIDED_NODES) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.USE_ALL_PROVIDED_NODES) :
                "false";
        numConnectionForBtc = commandLineProperties.containsProperty(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC) :
                "9";

        MutablePropertySources propertySources = this.getPropertySources();
        propertySources.addFirst(commandLineProperties);
        try {
            propertySources.addLast(getAppDirProperties());

            final String bannedPriceRelayNodesAsString = getProperty(FilterManager.BANNED_PRICE_RELAY_NODES, "");
            bannedPriceRelayNodes = !bannedPriceRelayNodesAsString.isEmpty() ? Arrays.asList(StringUtils.deleteWhitespace(bannedPriceRelayNodesAsString).split(",")) : null;

            final String bannedSeedNodesAsString = getProperty(FilterManager.BANNED_SEED_NODES, "");
            bannedSeedNodes = !bannedSeedNodesAsString.isEmpty() ? Arrays.asList(StringUtils.deleteWhitespace(bannedSeedNodesAsString).split(",")) : null;

            final String bannedBtcNodesAsString = getProperty(FilterManager.BANNED_BTC_NODES, "");
            bannedBtcNodes = !bannedBtcNodesAsString.isEmpty() ? Arrays.asList(StringUtils.deleteWhitespace(bannedBtcNodesAsString).split(",")) : null;

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

    public String getAppDataDir() {
        return appDataDir;
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

    private PropertySource<?> homeDirProperties() {
        return new PropertySource.StubPropertySource(BISQ_HOME_DIR_PROPERTY_SOURCE_NAME);
    }

    private PropertySource<?> classpathProperties() {
        return new PropertySource.StubPropertySource(BISQ_CLASSPATH_PROPERTY_SOURCE_NAME);
    }

    private PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BISQ_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            {
                setProperty(CommonOptionKeys.LOG_LEVEL_KEY, logLevel);
                setProperty(CommonOptionKeys.USE_DEV_MODE, useDevMode);

                setProperty(NetworkOptionKeys.SEED_NODES_KEY, seedNodes);
                setProperty(NetworkOptionKeys.MY_ADDRESS, myAddress);
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
                setProperty(DaoOptionKeys.RPC_PORT, rpcPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, rpcBlockNotificationPort);
                setProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, dumpBlockchainData);
                setProperty(DaoOptionKeys.FULL_DAO_NODE, fullDaoNode);
                setProperty(DaoOptionKeys.GENESIS_TX_ID, genesisTxId);
                setProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, genesisBlockHeight);
                setProperty(DaoOptionKeys.DAO_ACTIVATED, daoActivated);

                setProperty(BtcOptionKeys.BTC_NODES, btcNodes);
                setProperty(BtcOptionKeys.USE_TOR_FOR_BTC, useTorForBtc);
                setProperty(BtcOptionKeys.WALLET_DIR, btcNetworkDir);
                setProperty(BtcOptionKeys.USER_AGENT, userAgent);
                setProperty(BtcOptionKeys.USE_ALL_PROVIDED_NODES, useAllProvidedNodes);
                setProperty(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC, numConnectionForBtc);

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);

                setProperty(Storage.STORAGE_DIR, Paths.get(btcNetworkDir, "db").toString());
                setProperty(KeyStorage.KEY_STORAGE_DIR, Paths.get(btcNetworkDir, "keys").toString());
            }
        });
    }
}
