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
import io.bitsquare.btc.UserAgent;
import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.util.Utilities;
import io.bitsquare.util.spring.JOptCommandLinePropertySource;

import java.nio.file.Paths;

import java.util.Properties;

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

    public static final String APP_VERSION_KEY = "app.version";

    // TODO what is the difference to APP_DATA_DIR ?
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
    static final String BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME = "bitsquareClasspathProperties";
    static final String BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME = "bitsquareDefaultProperties";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private final String appName;
    private final String appDataDir;

    public BitsquareEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
    }

    BitsquareEnvironment(PropertySource commandLineProperties) {
        String userDataDir = commandLineProperties.containsProperty(USER_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(USER_DATA_DIR_KEY) :
                DEFAULT_USER_DATA_DIR;

        this.appName = commandLineProperties.containsProperty(APP_NAME_KEY) ?
                (String) commandLineProperties.getProperty(APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        this.appDataDir = commandLineProperties.containsProperty(APP_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(APP_DATA_DIR_KEY) :
                appDataDir(userDataDir, appName);

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
        String location = String.format("file:%s/bitsquare.properties", appDataDir);
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

    PropertySource<?> defaultProperties() throws Exception {
        return new PropertiesPropertySource(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {{
            setProperty(APP_DATA_DIR_KEY, appDataDir);
            setProperty(APP_DATA_DIR_CLEAN_KEY, DEFAULT_APP_DATA_DIR_CLEAN);

            setProperty(APP_NAME_KEY, appName);

            setProperty(UserAgent.NAME_KEY, appName);
            setProperty(UserAgent.VERSION_KEY, BitsquareEnvironment.this.getRequiredProperty(APP_VERSION_KEY));

            setProperty(WalletService.DIR_KEY, appDataDir);
            setProperty(WalletService.PREFIX_KEY, appName);

            setProperty(Persistence.DIR_KEY, appDataDir);
            setProperty(Persistence.PREFIX_KEY, appName + "_pref");

            setProperty(MainView.TITLE_KEY, appName);
        }});
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
