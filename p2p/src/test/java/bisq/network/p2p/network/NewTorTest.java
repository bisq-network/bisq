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

package bisq.network.p2p.network;

import org.berndpruenster.netlayer.tor.NativeTor;

import java.io.File;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.MockedConstruction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockConstruction;

class NewTorTest {

    @Test
    void leavesNativeTorShutdownToBisq(@TempDir File torDir) {
        var context = new AtomicReference<MockedConstruction.Context>();
        try (var construction = mockConstruction(NativeTor.class,
                (mock, constructionContext) -> context.set(constructionContext))) {
            var newTor = new NewTor(torDir, null, "", List::of);

            var tor = assertDoesNotThrow(newTor::getTor);

            assertEquals(1, construction.constructed().size());
            assertSame(construction.constructed().get(0), tor);
            assertEquals(4, context.get().arguments().size());
            assertFalse((Boolean) context.get().arguments().get(3));
        }
    }
}
