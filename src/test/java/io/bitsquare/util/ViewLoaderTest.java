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

package io.bitsquare.util;

import io.bitsquare.FatalException;
import io.bitsquare.di.BitsquareModule;
import io.bitsquare.gui.Navigation;

import com.google.inject.Guice;
import com.google.inject.Injector;

import javafx.application.Application;
import javafx.stage.Stage;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ViewLoaderTest {

    public static class TestApp extends Application {
        @Override
        public void start(Stage primaryStage) throws Exception {
        }
    }

    @BeforeClass
    public static void initJavaFX() {
        Thread t = new Thread("JavaFX Init Thread") {
            public void run() {
                Application.launch(TestApp.class);
            }
        };
        t.setDaemon(true);
        t.start();
    }


    @Before
    public void setUp() {
        Injector injector = Guice.createInjector(new BitsquareModule());
        ViewLoader.setInjector(injector);
    }

    @After
    public void tearDown() {
        ViewLoader.setInjector(null);
    }

    @Test(expected = FatalException.class)
    public void loadingBogusFxmlResourceShouldThrow() {
        new ViewLoader(() -> "a bogus fxml resource", false).load();
    }

    @Test
    public void loadingValidFxmlResourceShouldNotThrow() {
        new ViewLoader(Navigation.Item.ACCOUNT, false).load();
    }
}