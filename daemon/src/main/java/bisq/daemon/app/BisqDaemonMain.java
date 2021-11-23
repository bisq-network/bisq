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

package bisq.daemon.app;

import bisq.core.app.BisqHeadlessAppMain;
import bisq.core.app.BisqSetup;
import bisq.core.app.CoreModule;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.handlers.ResultHandler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import lombok.extern.slf4j.Slf4j;



import bisq.daemon.grpc.GrpcServer;

@Slf4j
public class BisqDaemonMain extends BisqHeadlessAppMain implements BisqSetup.BisqSetupListener {

    private GrpcServer grpcServer;

    public static void main(String[] args) {
        new BisqDaemonMain().execute(args);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    /////////////////////////////////////////////////////////////////////////////////////

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
        headlessApp = new BisqDaemon();

        UserThread.execute(this::onApplicationLaunched);
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
        headlessApp.setGracefulShutDownHandler(this);
    }


    /////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    /////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new CoreModule(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        headlessApp.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        super.startApplication();
    }

    @Override
    protected void onApplicationStarted() {
        super.onApplicationStarted();

        grpcServer = injector.getInstance(GrpcServer.class);
        grpcServer.start();
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        super.gracefulShutDown(resultHandler);

        grpcServer.shutdown();
    }
}
