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

package bisq.seednode;

import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.user.CookieKey;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeedNodeMain extends ExecutableForAppWithP2p {
    public static void main(String[] args) {
        new SeedNodeMain().execute(args);
    }

    private SeedNode seedNode;
    private DaoStateSnapshotService daoStateSnapshotService;

    public SeedNodeMain() {
        super("Bisq Seednode", "bisq-seednode", "bisq_seednode", Version.VERSION);
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        checkMemory(config, this);
        keepRunning();
    }

    @Override
    protected void addCapabilities() {
        Capabilities.app.addAll(Capability.SEED_NODE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void applyInjector() {
        super.applyInjector();

        daoStateSnapshotService = injector.getInstance(DaoStateSnapshotService.class);
        seedNode = new SeedNode(injector);
    }

    @Override
    protected void startApplication() {
        if (cookie.getAsOptionalBoolean(CookieKey.DELAY_STARTUP).orElse(false)) {
            cookie.remove(CookieKey.DELAY_STARTUP);
            try {
                // We create a deterministic delay per seed to avoid that all seeds start up at the
                // same time in case of a reorg.
                long delay = getMyIndex() * TimeUnit.SECONDS.toMillis(30);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        super.startApplication();
        seedNode.startApplication();

        daoStateSnapshotService.setResyncDaoStateFromResourcesHandler(
                // We set DELAY_STARTUP and shut down. At start up we delay with a deterministic delay to avoid
                // that all seeds get restarted at the same time.
                () -> {
                    injector.getInstance(User.class).getCookie().putAsBoolean(CookieKey.DELAY_STARTUP, true);
                    shutDown(this);
                }
        );
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        seedNode.shutDown();

        super.gracefulShutDown(resultHandler);
    }

    @Override
    public void startShutDownInterval() {
        if (DevEnv.isDevMode() || injector.getInstance(Config.class).useLocalhostForP2P) {
            return;
        }

        int myIndex = getMyIndex();
        if (myIndex == -1) {
            super.startShutDownInterval();
            return;
        }

        // We interpret the value of myIndex as hour of day (0-23). That way we avoid the risk of a restart of
        // multiple nodes around the same time in case it would be not deterministic.

        // We wrap our periodic check in a delay of 2 hours to avoid that we get
        // triggered multiple times after a restart while being in the same hour. It can be that we miss our target
        // hour during that delay but that is not considered problematic, the seed would just restart a bit longer than
        // 24 hours.
        UserThread.runAfter(() -> {
            // We check every hour if we are in the target hour.
            UserThread.runPeriodically(() -> {
                int currentHour = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).getHour();

                // distribute evenly between 0-23
                int size = injector.getInstance(SeedNodeRepository.class).getSeedNodeAddresses().size();
                long target = Math.round(24d / size * myIndex) % 24;
                if (currentHour == target) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "Shut down node at hour {} (UTC time is {})" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            target,
                            ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).toString());
                    shutDown(this);
                }
            }, TimeUnit.MINUTES.toSeconds(10));
        }, TimeUnit.HOURS.toSeconds(2));
    }

    private int getMyIndex() {
        SeedNodeRepository seedNodeRepository = injector.getInstance(SeedNodeRepository.class);
        List<NodeAddress> seedNodeAddresses = new ArrayList<>(seedNodeRepository.getSeedNodeAddresses());
        seedNodeAddresses.sort(Comparator.comparing(NodeAddress::getFullAddress));

        NodeAddress myAddress = injector.getInstance(P2PService.class).getAddress();
        return seedNodeAddresses.indexOf(myAddress);
    }
}
