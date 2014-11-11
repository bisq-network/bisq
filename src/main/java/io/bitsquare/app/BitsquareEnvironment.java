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
import io.bitsquare.persistence.Persistence;

import com.google.common.base.Preconditions;

import java.io.IOException;

import java.util.Properties;

import joptsimple.OptionSet;
import lighthouse.files.AppDirectory;
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

    public static final String APP_NAME_KEY = "appName";
    public static final String DEFAULT_APP_NAME = "Bitsquare";

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
                DEFAULT_APP_NAME + "-" + commandLineProperties.getProperty(APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        MutablePropertySources propertySources = this.getPropertySources();
        propertySources.addBefore(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, defaultProperties(appName));
        propertySources.addBefore(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, classpathProperties());
        propertySources.addBefore(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, filesystemProperties(appName));
        propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, commandLineProperties);
    }

    private PropertySource<?> defaultProperties(String appName) {
        return new PropertiesPropertySource(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {{
            setProperty(APP_NAME_KEY, appName);

            setProperty(UserAgent.NAME_KEY, appName);
            setProperty(UserAgent.VERSION_KEY, "0.1");

            setProperty(WalletFacade.DIR_KEY, AppDirectory.dir(appName).toString());
            setProperty(WalletFacade.PREFIX_KEY, appName);

            setProperty(Persistence.DIR_KEY, AppDirectory.dir(appName).toString());
            setProperty(Persistence.PREFIX_KEY, appName + "_pref");
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

    private PropertySource<?> filesystemProperties(String appName) {
        String location = String.format("file:%s/bitsquare.conf", AppDirectory.dir(appName));
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
}
