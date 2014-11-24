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

import io.bitsquare.BitsquareException;
import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.gui.Navigation;
import io.bitsquare.locale.BSResources;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.net.MalformedURLException;

import java.util.ResourceBundle;

import viewfx.view.support.ViewLoader;
import viewfx.view.support.guice.GuiceViewFactory;

import javafx.application.Application;
import javafx.stage.Stage;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import joptsimple.OptionParser;

public class ViewLoaderTests {

    public static class TestApp extends Application {
        static Stage primaryStage;

        @Override
        public void start(Stage primaryStage) throws Exception {
            TestApp.primaryStage = primaryStage;
        }
    }

    @BeforeClass
    public static void initJavaFX() throws InterruptedException {
        Thread t = new Thread("JavaFX Init Thread") {
            public void run() {
                Application.launch(TestApp.class);
            }
        };
        t.setDaemon(true);
        t.start();
        while (TestApp.primaryStage == null)
            Thread.sleep(10);
    }

    private GuiceViewFactory viewFactory;
    private ResourceBundle resourceBundle;

    @Before
    public void setUp() {
        OptionParser parser = new OptionParser();
        BitsquareEnvironment env = new BitsquareEnvironment(parser.parse(new String[]{}));
        Injector injector = Guice.createInjector(new BitsquareAppModule(env, TestApp.primaryStage));
        viewFactory = injector.getInstance(GuiceViewFactory.class);
        viewFactory.setInjector(injector);

        resourceBundle = BSResources.getResourceBundle();
    }

    @Test(expected = BitsquareException.class)
    public void loadingBogusFxmlResourceShouldThrow() throws MalformedURLException {
        new ViewLoader(viewFactory, resourceBundle).load(Navigation.Item.BOGUS.getFxmlUrl(), false);
    }

    @Test
    public void loadingValidFxmlResourceShouldNotThrow() {
        new ViewLoader(viewFactory, resourceBundle).load(Navigation.Item.ACCOUNT.getFxmlUrl(), false);
    }
}