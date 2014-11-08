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

package io.bitsquare.app.gui;

import io.bitsquare.BitsquareModule;
import io.bitsquare.app.AppModule;
import io.bitsquare.gui.GuiModule;
import io.bitsquare.util.ConfigLoader;

import javafx.stage.Stage;

import net.sourceforge.argparse4j.inf.Namespace;

class MainModule extends BitsquareModule {

    private final String appName;
    private final Stage primaryStage;
    private final Namespace argumentsNamespace;

    public MainModule(String appName, Namespace argumentsNamespace, Stage primaryStage) {
        super(ConfigLoader.loadConfig(appName));
        this.appName = appName;
        this.argumentsNamespace = argumentsNamespace;
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        install(new AppModule(properties, argumentsNamespace, appName));
        install(new GuiModule(properties, primaryStage));
    }
}
