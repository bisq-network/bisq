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

import bisq.monitor.metrics.MetricsModel;

import com.google.inject.Injector;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Monitor {
    @Setter
    private Injector injector;
    @Getter
    private MetricsModel metricsModel;

    public Monitor() {
    }

    public void startApplication() {
        metricsModel = injector.getInstance(MetricsModel.class);

        MonitorAppSetup appSetup = injector.getInstance(MonitorAppSetup.class);
        appSetup.start();
    }
}
