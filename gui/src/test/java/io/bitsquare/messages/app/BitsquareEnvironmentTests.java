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

package io.bitsquare.messages.app;

import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockPropertySource;

import static io.bitsquare.messages.app.BitsquareEnvironment.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.springframework.core.env.PropertySource.named;

public class BitsquareEnvironmentTests {

    @Test
    public void testPropertySourcePrecedence() {
        PropertySource commandlineProps = new MockPropertySource(BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME)
                .withProperty("key.x", "x.commandline");

        PropertySource filesystemProps = new MockPropertySource(BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME)
                .withProperty("key.x", "x.env")
                .withProperty("key.y", "y.env");

        ConfigurableEnvironment env = new BitsquareEnvironment(commandlineProps) {
            @Override
            PropertySource<?> appDirProperties() {
                return filesystemProps;
            }
        };
        MutablePropertySources propertySources = env.getPropertySources();

        assertThat(propertySources.precedenceOf(named(BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME)), equalTo(0));
        assertThat(propertySources.precedenceOf(named(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)), equalTo(1));
        assertThat(propertySources.precedenceOf(named(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)), equalTo(2));
        assertThat(propertySources.precedenceOf(named(BITSQUARE_APP_DIR_PROPERTY_SOURCE_NAME)), equalTo(3));
        assertThat(propertySources.precedenceOf(named(BITSQUARE_HOME_DIR_PROPERTY_SOURCE_NAME)), equalTo(4));
        assertThat(propertySources.precedenceOf(named(BITSQUARE_CLASSPATH_PROPERTY_SOURCE_NAME)), equalTo(5));
        assertThat(propertySources.precedenceOf(named(BITSQUARE_DEFAULT_PROPERTY_SOURCE_NAME)), equalTo(6));
        assertThat(propertySources.size(), equalTo(7));

        assertThat(env.getProperty("key.x"), equalTo("x.commandline")); // commandline value wins due to precedence
        assertThat(env.getProperty("key.y"), equalTo("y.env")); // env value wins because it's the only one available
    }
}