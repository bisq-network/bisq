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

import bisq.seednode.reporting.SeedNodeReportingService;

import bisq.core.network.p2p.inventory.GetInventoryRequestHandler;

import bisq.common.config.Config;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeedNode {
    private final GetInventoryRequestHandler getInventoryRequestHandler;
    private final SeedNodeReportingService seedNodeReportingService;
    private final boolean useSeedNodeReportingService;

    public SeedNode(Injector injector) {
        getInventoryRequestHandler = injector.getInstance(GetInventoryRequestHandler.class);
        seedNodeReportingService = injector.getInstance(SeedNodeReportingService.class);

        String seedNodeReportingServerUrl = injector.getInstance(Key.get(String.class, Names.named(Config.SEED_NODE_REPORTING_SERVER_URL)));
        useSeedNodeReportingService = seedNodeReportingServerUrl != null && !seedNodeReportingServerUrl.trim().isEmpty();
    }

    public void startApplication() {
        if (useSeedNodeReportingService) {
            seedNodeReportingService.initialize();
        }
    }

    public void shutDown() {
        if (getInventoryRequestHandler != null) {
            getInventoryRequestHandler.shutDown();
        }
        if (seedNodeReportingService != null) {
            seedNodeReportingService.shutDown();
        }
    }
}
