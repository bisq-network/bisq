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

package bisq.persistence;

import java.io.IOException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PersistenceFileWriterTests {
    private static final ExecutorService writeRequestScheduler = Executors.newSingleThreadExecutor();
    private final byte[] DATA = new byte[100];
    private AsyncFileWriter asyncWriter;
    private PersistenceFileWriter fileWriter;

    @BeforeEach
    void setup(@Mock AsyncFileWriter asyncWriter) {
        this.asyncWriter = asyncWriter;
        fileWriter = new PersistenceFileWriter(asyncWriter, writeRequestScheduler);
    }

    @AfterAll
    static void teardown() {
        writeRequestScheduler.shutdownNow();
    }

    @Test
    void failTruncation() throws ExecutionException, InterruptedException, TimeoutException {
        var ioException = new IOException("Truncation failed.");
        doReturn(failedFuture(ioException))
                .when(asyncWriter).truncate();

        CountDownLatch exceptionThrownLatch = new CountDownLatch(1);
        fileWriter.write(DATA)
                .exceptionally(throwable -> {
                    assertThat(throwable.getCause(), is(ioException));
                    exceptionThrownLatch.countDown();
                    return null;
                })
                .get(30, TimeUnit.SECONDS);

        assertThat(exceptionThrownLatch.getCount(), is(0L));
        verify(asyncWriter, times(1)).truncate();
        verify(asyncWriter, never()).write(any(), anyInt());
    }

    @Test
    void writeInOneGo() throws InterruptedException, ExecutionException, TimeoutException {
        doReturn(completedFuture(null)).when(asyncWriter).truncate();
        doReturn(completedFuture(DATA.length))
                .when(asyncWriter).write(any(), anyInt());

        fileWriter.write(DATA)
                .get(30, TimeUnit.SECONDS);

        verify(asyncWriter, times(1)).write(any(), anyInt());
    }

    @Test
    void writeInTwoPhases() throws InterruptedException, ExecutionException, TimeoutException {
        doReturn(completedFuture(null)).when(asyncWriter).truncate();
        doReturn(completedFuture(25), completedFuture(75))
                .when(asyncWriter).write(any(), anyInt());

        fileWriter.write(DATA)
                .get(30, TimeUnit.SECONDS);

        verify(asyncWriter, times(2)).write(any(), anyInt());
    }

    @Test
    void writeInFivePhases() throws InterruptedException, ExecutionException, TimeoutException {
        doReturn(completedFuture(null)).when(asyncWriter).truncate();
        doReturn(completedFuture(10), completedFuture(20),
                completedFuture(30), completedFuture(15),
                completedFuture(25))
                .when(asyncWriter).write(any(), anyInt());

        fileWriter.write(DATA)
                .get(30, TimeUnit.SECONDS);

        verify(asyncWriter, times(5)).write(any(), anyInt());
    }
}
