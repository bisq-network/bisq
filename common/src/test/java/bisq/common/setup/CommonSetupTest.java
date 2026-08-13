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

package bisq.common.setup;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonSetupTest {

    @Test
    void shutdownHookCanBeRemovedBeforeAControlledExit() {
        AtomicInteger shutdownCalls = new AtomicInteger();
        CommonSetup.setupShutdownHandler(resultHandler -> {
            shutdownCalls.incrementAndGet();
            resultHandler.handleResult();
        });

        assertTrue(CommonSetup.removeShutdownHook());
        assertFalse(CommonSetup.removeShutdownHook());
        assertEquals(0, shutdownCalls.get());
    }
}
