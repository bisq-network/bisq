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

import bisq.desktop.common.UITimer;
import bisq.desktop.common.view.guice.InjectorViewFactory;
import bisq.desktop.setup.DesktopPersistedDataHost;

import bisq.core.app.BisqExecutable;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.CommonSetup;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.inject.Injector;

import javafx.application.Application;
import javafx.application.Platform;

import lombok.extern.slf4j.Slf4j;



import io.bisq.api.app.ApiEnvironment;
import io.bisq.api.app.ApiOptionCustomizer;
import io.bisq.api.service.BisqApiApplication;

@Slf4j
public class BisqAppMain extends BisqExecutable {
    private BisqApp application;

    public static void main(String[] args) throws Exception {
        if (BisqExecutable.setupInitialOptionParser(args)) {
            // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
            // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
            Thread.currentThread().setContextClassLoader(BisqAppMain.class.getClassLoader());

            new BisqAppMain().execute(args);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void customizeOptionParsing(OptionParser parser) {
        super.customizeOptionParsing(parser);
        ApiOptionCustomizer.customizeOptionParsing(parser);
    }

    @Override
    protected void setupEnvironment(OptionSet options) {
        bisqEnvironment = new ApiEnvironment(options);
    }

    @Override
    protected void configUserThread() {
        UserThread.setExecutor(Platform::runLater);
        UserThread.setTimerClass(UITimer.class);
    }

    @Override
    protected void launchApplication() {
        BisqApp.setAppLaunchedHandler(application -> {
            BisqAppMain.this.application = (BisqApp) application;
            // Necessary to do the setup at this point to prevent Bouncy Castle errors
            CommonSetup.setup(BisqAppMain.this.application);
            // Map to user thread!
            UserThread.execute(this::onApplicationLaunched);
        });

        Application.launch(BisqApp.class);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // As application is a JavaFX application we need to wait for onApplicationLaunched
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
        application.setGracefulShutDownHandler(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new BisqAppModule(bisqEnvironment);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        application.setInjector(injector);
        injector.getInstance(InjectorViewFactory.class).setInjector(injector);
    }

    @Override
    protected void setupPersistedDataHosts(Injector injector) {
        super.setupPersistedDataHosts(injector);
        PersistedDataHost.apply(DesktopPersistedDataHost.getPersistedDataHosts(injector));
    }

    @Override
    protected void startApplication() {
        // We need to be in user thread! We mapped at launchApplication already...
        application.startApplication();
        if (((ApiEnvironment) bisqEnvironment).isApiEnabled())
            injector.getInstance(BisqApiApplication.class).run();
    }
}
