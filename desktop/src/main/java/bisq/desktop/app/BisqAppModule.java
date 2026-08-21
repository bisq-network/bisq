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

package bisq.desktop.app;

import bisq.desktop.DesktopModule;

import bisq.core.app.CoreModule;

import bisq.common.app.AppModule;
import bisq.common.config.Config;

public class BisqAppModule extends AppModule {

    public BisqAppModule(Config config) {
        super(config);
    }

    @Override
    protected void configure() {
        install(new CoreModule(config));
        install(new DesktopModule(config));
    }
}
