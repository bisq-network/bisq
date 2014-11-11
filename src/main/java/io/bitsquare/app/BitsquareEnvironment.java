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
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.persistence.Persistence;

import com.google.common.base.Preconditions;

import java.io.IOException;

import java.nio.file.Paths;

import java.util.Properties;

import joptsimple.OptionSet;
import org.springframework.core.env.JOptCommandLinePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

public class BitsquareEnvironment extends StandardEnvironment {

    public static final String APP_NAME_KEY = "app.name";
    public static final String DEFAULT_APP_NAME = "Bitsquare";

    public static final String USER_DATA_DIR_KEY = "user.data.dir";
    public static final String DEFAULT_USER_DATA_DIR = defaultUserDataDir();

    public static final String APP_DATA_DIR_KEY = "app.data.dir";
    public static final String DEFAULT_APP_DATA_DIR = appDataDir(DEFAULT_USER_DATA_DIR, DEFAULT_APP_NAME);

    private static final String BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME = "bitsquareDefaultProperties";
    private static final String BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME = "bitsquareClasspathProperties";
    private static final String BITSQUARE_FILESYSTEM_PROPERTY_SOURCE_NAME = "bitsquareFilesystemProperties";
    private static final String BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME = "bitsquareCommandLineProperties";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    public BitsquareEnvironment(OptionSet options) {
        Preconditions.checkArgument(options != null, "Options must not be null");

        PropertySource commandLineProperties =
                new JOptCommandLinePropertySource(BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME, options);

        String appName = commandLineProperties.containsProperty(APP_NAME_KEY) ?
                (String) commandLineProperties.getProperty(APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        String userDataDir = commandLineProperties.containsProperty(USER_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(USER_DATA_DIR_KEY) :
                DEFAULT_USER_DATA_DIR;

        String appDataDir = commandLineProperties.containsProperty(APP_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(APP_DATA_DIR_KEY) :
                appDataDir(userDataDir, appName);

        MutablePropertySources propertySources = this.getPropertySources();
        propertySources.addBefore(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, defaultProperties(appDataDir, appName));
        propertySources.addBefore(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, classpathProperties());
        propertySources.addBefore(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, filesystemProperties(appDataDir));
        propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, commandLineProperties);
    }


    private PropertySource<?> defaultProperties(String appDataDir, String appName) {
        return new PropertiesPropertySource(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {{
            setProperty(APP_DATA_DIR_KEY, appDataDir);
            setProperty(APP_NAME_KEY, appName);

            setProperty(UserAgent.NAME_KEY, appName);
            setProperty(UserAgent.VERSION_KEY, "0.1");

            setProperty(WalletFacade.DIR_KEY, appDataDir);
            setProperty(WalletFacade.PREFIX_KEY, appName);

            setProperty(Persistence.DIR_KEY, appDataDir);
            setProperty(Persistence.PREFIX_KEY, appName + "_pref");

            setProperty(ViewCB.TITLE_KEY, appName);
        }});
    }

    private PropertySource<?> classpathProperties() {
        try {
            Resource resource = resourceLoader.getResource("classpath:bitsquare.properties");
            return new ResourcePropertySource(BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME, resource);
        } catch (IOException ex) {
            throw new BitsquareException(ex);
        }
    }

    private PropertySource<?> filesystemProperties(String appDir) {
        String location = String.format("file:%s/bitsquare.conf", appDir);
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists()) {
            return new PropertySource.StubPropertySource(BITSQUARE_FILESYSTEM_PROPERTY_SOURCE_NAME);
        }

        try {
            return new ResourcePropertySource(BITSQUARE_FILESYSTEM_PROPERTY_SOURCE_NAME, resource);
        } catch (IOException ex) {
            throw new BitsquareException(ex);
        }
    }


    private static String defaultUserDataDir() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win"))
            return System.getenv("APPDATA");

        if (os.contains("mac"))
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toString();

        // *nix
        return Paths.get(System.getProperty("user.home"), ".local", "share").toString();
    }

    private static String appDataDir(String userDataDir, String appName) {
        return Paths.get(userDataDir, appName).toString();
    }
}
