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

package bisq.monitor.metric;

import bisq.monitor.Metric;
import bisq.monitor.Reporter;

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * A dummy metric for development purposes.
 *
 * @author Florian Reimair
 */
@Slf4j
public class Dummy extends Metric {

    public Dummy(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        log.info(this.configuration.toString());
        // TODO check if we need to restart this Metric
    }

    @Override
    protected void execute() {
        log.info(this.getName() + " running");
    }

}
