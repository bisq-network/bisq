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

import io.bitsquare.btc.UserAgent;
import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.p2p.tomp2p.TomP2PModule;
import io.bitsquare.storage.Storage;

import java.nio.file.Paths;

import java.util.Properties;

import joptsimple.OptionSet;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

public class BitsquareAppEnvironment extends BitsquareEnvironment {

    public BitsquareAppEnvironment(OptionSet options) {
        super(options);
    }

    BitsquareAppEnvironment(PropertySource commandLineProperties) {
        super(commandLineProperties);
    }

    @Override
    protected PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            private static final long serialVersionUID = -8478089705207326165L;

            {
                setProperty(APP_DATA_DIR_KEY, appDataDir);
                setProperty(APP_DATA_DIR_CLEAN_KEY, DEFAULT_APP_DATA_DIR_CLEAN);

                setProperty(APP_NAME_KEY, appName);

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);

                setProperty(WalletService.DIR_KEY, appDataDir);
                setProperty(WalletService.PREFIX_KEY, appName);

                setProperty(Storage.DIR_KEY, Paths.get(appDataDir, "db").toString());

                setProperty(MainView.TITLE_KEY, appName);

                setProperty(TomP2PModule.BOOTSTRAP_NODE_PORT_KEY, bootstrapNodePort);
            }
        });
    }
}
