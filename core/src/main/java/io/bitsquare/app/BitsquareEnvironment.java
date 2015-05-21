/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import io.bitsquare.BitsquareException;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.UserAgent;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.KeyStorage;
import io.bitsquare.p2p.tomp2p.TomP2PModule;
import io.bitsquare.storage.Storage;
import io.bitsquare.util.Utilities;
import io.bitsquare.util.spring.JOptCommandLinePropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Paths;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import joptsimple.OptionSet;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

import static com.google.common.base.Preconditions.checkNotNull;

public class BitsquareEnvironment extends StandardEnvironment {
    private static final Logger log = LoggerFactory.getLogger(BitsquareEnvironment.class);

    private static final String BITCOIN_NETWORK_PROP = "bitcoinNetwork.properties";
    public static final String USER_DATA_DIR_KEY = "user.data.dir";

    public static final String DEFAULT_USER_DATA_DIR = defaultUserDataDir();

    public static final String APP_NAME_KEY = "app.name";
    public static final String DEFAULT_APP_NAME = "Bitsquare";

    public static final String APP_DATA_DIR_KEY = "app.data.dir";
    public static final String DEFAULT_APP_DATA_DIR = appDataDir(DEFAULT_USER_DATA_DIR, DEFAULT_APP_NAME);

    public static final String APP_DATA_DIR_CLEAN_KEY = "app.data.dir.clean";
    public static final String DEFAULT_APP_DATA_DIR_CLEAN = "false";

    static final String BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME = "bitsquareCommandLineProperties";
    static final String BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME = "bitsquareAppDirProperties";
    static final String BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME = "bitsquareHomeDirProperties";
    public static final String BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME = "bitsquareClasspathProperties";
    static final String BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME = "bitsquareDefaultProperties";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    protected final String appName;
    protected final String userDataDir;
    protected final String appDataDir;
    protected final String btcNetworkDir;
    protected final String bootstrapNodePort;

    public BitsquareEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
    }

    public BitcoinNetwork getBtcNetworkProperty() {
        String dirString = Paths.get(userDataDir, appName).toString();
        String fileString = Paths.get(dirString, BITCOIN_NETWORK_PROP).toString();
        File dir = new File(dirString);
        File file = new File(fileString);
        if (!dir.exists())
            dir.mkdirs();

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Throwable e) {
                log.error(e.getMessage());
            }
        }
        try (InputStream fileInputStream = new FileInputStream(file)) {
            Properties properties = new Properties();
            properties.load(fileInputStream);
            String bitcoinNetwork = properties.getProperty("bitcoinNetwork", BitcoinNetwork.DEFAULT.name());
            return BitcoinNetwork.valueOf(bitcoinNetwork);
        } catch (Throwable e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return BitcoinNetwork.DEFAULT;
        }
    }

    public void setBitcoinNetwork(BitcoinNetwork bitcoinNetwork) {
        String path = Paths.get(userDataDir, appName, BITCOIN_NETWORK_PROP).toString();
        File file = new File(path);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Properties properties = new Properties();
            properties.setProperty("bitcoinNetwork", bitcoinNetwork.name());
            properties.store(fos, null);
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    protected BitsquareEnvironment(PropertySource commandLineProperties) {
        userDataDir = commandLineProperties.containsProperty(USER_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(USER_DATA_DIR_KEY) :
                DEFAULT_USER_DATA_DIR;

        appName = commandLineProperties.containsProperty(APP_NAME_KEY) ?
                (String) commandLineProperties.getProperty(APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        appDataDir = commandLineProperties.containsProperty(APP_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(APP_DATA_DIR_KEY) :
                appDataDir(userDataDir, appName);

        btcNetworkDir = Paths.get(appDataDir, getBtcNetworkProperty().name().toLowerCase()).toString();
        File btcNetworkDirFile = new File(btcNetworkDir);
        if (!btcNetworkDirFile.exists())
            btcNetworkDirFile.mkdir();

        bootstrapNodePort = commandLineProperties.containsProperty(TomP2PModule.BOOTSTRAP_NODE_PORT_KEY) ?
                (String) commandLineProperties.getProperty(TomP2PModule.BOOTSTRAP_NODE_PORT_KEY) : "-1";

        MutablePropertySources propertySources = this.getPropertySources();
        propertySources.addFirst(commandLineProperties);
        try {
            propertySources.addLast(appDirProperties());
            propertySources.addLast(homeDirProperties());
            propertySources.addLast(classpathProperties());
            propertySources.addLast(defaultProperties());
        } catch (Exception ex) {
            throw new BitsquareException(ex);
        }
    }

    PropertySource<?> appDirProperties() throws Exception {
        String location = String.format("file:%s/bitsquare.properties", btcNetworkDir);
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists())
            return new PropertySource.StubPropertySource(BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME);

        return new ResourcePropertySource(BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME, resource);
    }

    PropertySource<?> homeDirProperties() throws Exception {
        String location = String.format("file:%s/.bitsquare/bitsquare.properties", getProperty("user.home"));
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists())
            return new PropertySource.StubPropertySource(BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME);

        return new ResourcePropertySource(BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME, resource);
    }

    PropertySource<?> classpathProperties() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:bitsquare.properties");
        return new ResourcePropertySource(BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME, resource);
    }

    protected PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            private static final long serialVersionUID = -8478089705207326165L;

            {
                setProperty(APP_DATA_DIR_KEY, appDataDir);
                setProperty(APP_DATA_DIR_CLEAN_KEY, DEFAULT_APP_DATA_DIR_CLEAN);

                setProperty(APP_NAME_KEY, appName);
                setProperty(USER_DATA_DIR_KEY, userDataDir);

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);

                setProperty(WalletService.DIR_KEY, btcNetworkDir);
                setProperty(WalletService.PREFIX_KEY, appName);

                setProperty(Storage.DIR_KEY, Paths.get(btcNetworkDir, "db").toString());
                setProperty(KeyStorage.DIR_KEY, Paths.get(btcNetworkDir, "keys").toString());

                setProperty(TomP2PModule.BOOTSTRAP_NODE_PORT_KEY, bootstrapNodePort);
            }
        });
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
        return Paths.get(userDataDir, appName).toString();
    }
}
