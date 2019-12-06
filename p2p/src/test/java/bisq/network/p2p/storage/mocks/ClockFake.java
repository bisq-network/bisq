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

package bisq.network.p2p.storage.mocks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Fake implementation of the Clock object that can be used in tests that need finer control over the current time.
 *
 * @see <a href="https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs">Reference</a>
 */
public class ClockFake extends Clock {
    private Instant currentInstant;

    public ClockFake() {
        this.currentInstant = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        throw new UnsupportedOperationException("ClockFake does not support getZone");
    }

    @Override
    public Clock withZone(ZoneId zoneId) {
        throw new UnsupportedOperationException("ClockFake does not support withZone");
    }

    @Override
    public Instant instant() {
        return this.currentInstant;
    }

    public void increment(long milliseconds) {
        this.currentInstant = this.currentInstant.plusMillis(milliseconds);
    }
}
