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

import bisq.network.tor.TorService;
import bisq.network.tor.TorTransportConfig;
import bisq.network.tor.controller.exceptions.TorBootstrapFailedException;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewTorTest {

    @Test
    void doesNotStartTorWhenBridgesAreConfigured(@TempDir File torDir) {
        try (var construction = mockConstruction(TorService.class)) {
            var newTor = new NewTor(torDir, null, "", () -> List.of("obfs4 192.0.2.1:443 cert=x iat-mode=0"));

            var exception = assertThrows(IllegalStateException.class, newTor::getTor);

            assertEquals(NewTor.BRIDGES_NOT_SUPPORTED, exception.getMessage());
            assertEquals(0, construction.constructed().size());
        }
    }

    @Test
    void startsTorWithTheTorDirAndTheTorrcOverrides(@TempDir File torDir) {
        var config = new AtomicReference<TorTransportConfig>();
        try (var construction = mockConstruction(TorService.class, (mock, context) -> {
            config.set((TorTransportConfig) context.arguments().get(0));
            when(mock.initialize()).thenReturn(CompletableFuture.completedFuture(true));
        })) {
            var newTor = new NewTor(torDir, null, "SocksPort 9150", List::of);

            var torService = newTor.getTor();

            assertSame(construction.constructed().get(0), torService);
            assertEquals(torDir.toPath(), config.get().getDataDirPath());
            assertEquals(Map.of("SocksPort", "9150"), config.get().getTorrcOverrides());
            assertFalse(config.get().isUseExternalTor());
        }
    }

    @Test
    void doesNotStartTorAgainAfterAnotherFailure(@TempDir File torDir) {
        try (var construction = mockConstruction(TorService.class,
                (mock, context) -> when(mock.initialize()).thenThrow(new IllegalStateException("bootstrap failed")))) {
            var newTor = new NewTor(torDir, null, "", List::of);

            assertThrows(IllegalStateException.class, newTor::getTor);

            assertEquals(1, construction.constructed().size());
        }
    }

    @Test
    void startsTorAgainWithoutItsFilesWhenItDoesNotBootstrap(@TempDir File torDir) throws IOException {
        Files.createDirectories(torDir.toPath().resolve("hiddenservice"));
        Files.writeString(torDir.toPath().resolve("external_tor.config"), "");
        var startAttempts = new AtomicInteger();
        try (var construction = mockConstruction(TorService.class, (mock, context) -> {
            int attempt = startAttempts.incrementAndGet();
            Files.writeString(torDir.toPath().resolve("cached-microdescs" + attempt), "");
            if (attempt < 3) {
                when(mock.initialize()).thenThrow(new CompletionException(
                        new TorBootstrapFailedException("Could not bootstrap Tor in 240 seconds")));
            } else {
                when(mock.initialize()).thenReturn(CompletableFuture.completedFuture(true));
            }
        })) {
            var newTor = new NewTor(torDir, null, "", List::of);

            var torService = newTor.getTor();

            assertEquals(3, construction.constructed().size());
            assertSame(construction.constructed().get(2), torService);
            // TorService stops the Tor of a failed start itself.
            construction.constructed().forEach(mock -> verify(mock, never()).shutdown());
            Set<String> fileNames = Arrays.stream(torDir.list()).collect(Collectors.toSet());
            assertEquals(Set.of("hiddenservice", "external_tor.config", "cached-microdescs3"), fileNames);
        }
    }

    @Test
    void givesUpAfterFiveStartsWithoutBootstrap(@TempDir File torDir) {
        try (var construction = mockConstruction(TorService.class,
                (mock, context) -> when(mock.initialize()).thenThrow(new CompletionException(
                        new TorBootstrapFailedException("Could not bootstrap Tor in 240 seconds"))))) {
            var newTor = new NewTor(torDir, null, "", List::of);

            assertThrows(CompletionException.class, newTor::getTor);

            assertEquals(5, construction.constructed().size());
        }
    }

    @Test
    void ignoresBridgeLinesOfTenCharactersOrLess() {
        String bridge = "obfs4 192.0.2.1:443 cert=x iat-mode=0";

        assertEquals(List.of(), NewTor.getBridgeLines(null));
        assertEquals(List.of(), NewTor.getBridgeLines(List.of("", "  ", "1.2.3.4:1")));
        assertEquals(List.of(bridge), NewTor.getBridgeLines(Arrays.asList("", bridge, null)));
    }
}
