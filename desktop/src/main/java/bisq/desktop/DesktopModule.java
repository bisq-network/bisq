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

package bisq.desktop;

import bisq.desktop.common.fxml.FxmlViewLoader;
import bisq.desktop.common.view.ViewFactory;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.guice.InjectorViewFactory;

import bisq.core.locale.Res;

import bisq.common.app.AppModule;
import bisq.common.config.Config;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import java.util.ResourceBundle;

import static bisq.common.config.Config.APP_NAME;

public class DesktopModule extends AppModule {

    public DesktopModule(Config config) {
        super(config);
    }

    @Override
    protected void configure() {
        bind(ViewFactory.class).to(InjectorViewFactory.class);

        bind(ResourceBundle.class).toInstance(Res.getResourceBundle());
        bind(ViewLoader.class).to(FxmlViewLoader.class).in(Singleton.class);

        bindConstant().annotatedWith(Names.named(APP_NAME)).to(config.appName);
    }
}
