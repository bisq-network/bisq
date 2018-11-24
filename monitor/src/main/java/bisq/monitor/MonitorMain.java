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

package bisq.monitor;

import bisq.core.app.BisqEnvironment;
import bisq.core.app.BisqExecutable;
import bisq.core.app.misc.ExecutableForAppWithP2p;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.setup.CommonSetup;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;
import static spark.Spark.port;



import spark.Spark;

@Slf4j
public class MonitorMain extends ExecutableForAppWithP2p {
    private static final String VERSION = "1.0.1";
    private Monitor monitor;

    public static void main(String[] args) throws Exception {
        log.info("Monitor.VERSION: " + VERSION);
        BisqEnvironment.setDefaultAppName("bisq_monitor");
        if (BisqExecutable.setupInitialOptionParser(args))
            new MonitorMain().execute(args);
    }

    @Override
    protected void doExecute(OptionSet options) {
        super.doExecute(options);

        CommonSetup.setup(this);
        checkMemory(bisqEnvironment, this);

        startHttpServer(bisqEnvironment.getProperty(MonitorOptionKeys.PORT));

        keepRunning();
    }

    @Override
    protected void setupEnvironment(OptionSet options) {
        bisqEnvironment = new MonitorEnvironment(checkNotNull(options));
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                monitor = new Monitor();
                UserThread.execute(this::onApplicationLaunched);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new MonitorModule(bisqEnvironment);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        monitor.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        monitor.startApplication();
    }

    private void startHttpServer(String port) {
        port(Integer.parseInt(port));
        Spark.get("/", (req, res) -> {
            log.info("Incoming request from: " + req.userAgent());
            final String resultAsHtml = monitor.getMetricsModel().getResultAsHtml();
            return resultAsHtml == null ? "Still starting up..." : resultAsHtml;
        });
    }

    @Override
    protected void customizeOptionParsing(OptionParser parser) {
        super.customizeOptionParsing(parser);

        parser.accepts(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL,
                "Set slack secret for seed node monitor")
                .withRequiredArg();

        parser.accepts(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL,
                "Set slack secret for Btc node monitor")
                .withRequiredArg();

        parser.accepts(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL,
                "Set slack secret for provider node monitor")
                .withRequiredArg();

        parser.accepts(MonitorOptionKeys.PORT,
                "Set port to listen on")
                .withRequiredArg()
                .defaultsTo("80");
    }
}
