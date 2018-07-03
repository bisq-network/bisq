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
package network.bisq.api.app;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.setup.CommonSetup;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.core.app.BisqExecutable;
import joptsimple.OptionSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiMain extends BisqExecutable implements GracefulShutDownHandler {

    private Api application;

    public static void main(String[] args) throws Exception {
        if (BisqExecutable.setupInitialOptionParser(args)) {
            // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
            // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
            Thread.currentThread().setContextClassLoader(ApiMain.class.getClassLoader());

            new ApiMain().execute(args);
        }
    }

    @Override
    protected void setupEnvironment(OptionSet options) {
        bisqEnvironment = new ApiEnvironment(options);
    }

    @Override
    protected void doExecute(OptionSet options) {
        super.doExecute(options);

        keepRunning();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void configUserThread() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
    }

    @Override
    protected void launchApplication() {
        application = new Api();
        CommonSetup.setup(ApiMain.this.application);

        UserThread.execute(this::onApplicationLaunched);
    }

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
        return new StandaloneApiModule(bisqEnvironment);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        application.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        // We need to be in user thread! We mapped at launchApplication already...
        application.startApplication();
    }

    protected void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
