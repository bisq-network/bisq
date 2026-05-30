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

package bisq.core.dao.monitoring.serialization;

import bisq.core.dao.state.model.DaoState;

/**
 * Produces the byte stream fed into the DAO state hash chain. Each implementation owns
 * exactly one serialization format; the chosen format is consensus-critical and must not
 * vary by JVM, JDK version, or library version. The active implementation is selected by
 * {@link bisq.core.dao.monitoring.DaoStateMonitoringService} based on block height.
 */
public interface DaoStateHashSerializer {
    byte[] serialize(DaoState daoState);
}
