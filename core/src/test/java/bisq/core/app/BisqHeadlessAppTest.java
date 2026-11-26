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
package bisq.core.app;

import bisq.core.locale.Res;
import bisq.core.trade.TradeManager;

import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.setup.GracefulShutDownHandler;

import com.google.inject.Injector;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BisqHeadlessAppTest {
    @BeforeEach
    void setUp() {
        Res.setup();
    }

    // Without a window nobody accepts the terms on the screen, so a headless start without an acceptance shuts down
    @Test
    void shutsDownWhenTheTermsAreNotAccepted() {
        BisqSetup bisqSetup = mock(BisqSetup.class);
        GracefulShutDownHandler gracefulShutDownHandler = mock(GracefulShutDownHandler.class);
        BisqHeadlessApp app = createApp(bisqSetup, gracefulShutDownHandler);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Runnable>> tacHandler = ArgumentCaptor.forClass(Consumer.class);

        app.startApplication();

        verify(bisqSetup).setDisplayTacHandler(tacHandler.capture());
        AtomicInteger acceptedCount = new AtomicInteger();
        tacHandler.getValue().accept(acceptedCount::incrementAndGet);
        assertEquals(0, acceptedCount.get(), "the headless application does not accept the terms by itself");
        verify(gracefulShutDownHandler).gracefulShutDown(any());
    }

    private static BisqHeadlessApp createApp(BisqSetup bisqSetup, GracefulShutDownHandler gracefulShutDownHandler) {
        CorruptedStorageFileHandler corruptedStorageFileHandler = mock(CorruptedStorageFileHandler.class);
        when(corruptedStorageFileHandler.getFiles()).thenReturn(Optional.empty());
        Injector injector = mock(Injector.class);
        when(injector.getInstance(BisqSetup.class)).thenReturn(bisqSetup);
        when(injector.getInstance(CorruptedStorageFileHandler.class)).thenReturn(corruptedStorageFileHandler);
        when(injector.getInstance(TradeManager.class)).thenReturn(mock(TradeManager.class));
        BisqHeadlessApp app = new BisqHeadlessApp();
        app.setInjector(injector);
        app.setGracefulShutDownHandler(gracefulShutDownHandler);
        return app;
    }
}
