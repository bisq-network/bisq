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

package bisq.grpc;

import bisq.core.CoreApi;
import bisq.core.CoreModule;
import bisq.core.app.BisqExecutable;
import bisq.core.app.BisqHeadlessAppMain;
import bisq.core.app.BisqSetup;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.setup.CommonSetup;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Main class to start gRPC server with a headless BisqGrpcApp instance.
 */
public class BisqGrpcServerMain extends BisqHeadlessAppMain implements BisqSetup.BisqSetupListener {
    private static BisqGrpcServer bisqGrpcServer;

    public static void main(String[] args) throws Exception {
        if (BisqExecutable.setupInitialOptionParser(args)) {
            // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
            // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
            Thread.currentThread().setContextClassLoader(BisqGrpcServerMain.class.getClassLoader());

            new BisqGrpcServerMain().execute(args);
        }
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
        headlessApp = new BisqGrpcApp();
        CommonSetup.setup(BisqGrpcServerMain.this.headlessApp);

        UserThread.execute(this::onApplicationLaunched);
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
        headlessApp.setGracefulShutDownHandler(this);
    }

    @Override
    public void onSetupComplete() {
        CoreApi coreApi = injector.getInstance(CoreApi.class);
        bisqGrpcServer = new BisqGrpcServer(coreApi);

        // If we start headless we need to keep the main thread busy...
        try {
            bisqGrpcServer.blockUntilShutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new CoreModule(bisqEnvironment);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        headlessApp.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        // We need to be in user thread! We mapped at launchApplication already...
        headlessApp.startApplication();

        // In headless mode we don't have an async behaviour so we trigger the setup by calling onApplicationStarted
        onApplicationStarted();
    }

    @Override
    protected void onApplicationStarted() {
        super.onApplicationStarted();

        CoreApi coreApi = injector.getInstance(CoreApi.class);
        bisqGrpcServer = new BisqGrpcServer(coreApi);
    }
}
