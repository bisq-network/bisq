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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class AtomicFileWriterTests {
    private static final byte[] DATA = "Hello World!".getBytes(StandardCharsets.UTF_8);
    @Mock
    private PersistenceFileWriter persistenceFileWriter;
    @Mock
    private File rollingFile = mock(File.class);
    private AtomicFileWriter atomicFileWriter;

    @BeforeEach
    void setup(@TempDir Path tempDir, @Mock Path rollingFilePath) {
        doReturn(rollingFile).when(rollingFilePath).toFile();
        doReturn(rollingFilePath).when(persistenceFileWriter).getFilePath();

        var file = tempDir.resolve("my_file");
        atomicFileWriter = new AtomicFileWriter(file, persistenceFileWriter);
    }

    @Test
    void writeFails() throws ExecutionException, InterruptedException, TimeoutException {
        var ioException = new IOException();
        doReturn(CompletableFuture.failedFuture(ioException))
                .when(persistenceFileWriter).write(any());

        CountDownLatch exceptionTriggeredLatch = new CountDownLatch(1);
        atomicFileWriter.write(DATA)
                .exceptionally(throwable -> {
                    assertThat(throwable.getCause(), is(ioException));
                    exceptionTriggeredLatch.countDown();
                    return null;
                })
                .get(30, TimeUnit.SECONDS);

        assertThat(exceptionTriggeredLatch.getCount(), is(0L));
    }

    @Test
    void renameFailure() throws InterruptedException, ExecutionException, TimeoutException {
        doReturn(CompletableFuture.<Void>completedFuture(null))
                .when(persistenceFileWriter).write(any());

        doReturn(false).when(rollingFile).renameTo(any());

        CountDownLatch exceptionTriggeredLatch = new CountDownLatch(1);
        atomicFileWriter.write(DATA)
                .exceptionally(throwable -> {
                    assertThat(throwable.getCause(), instanceOf(AtomicFileWriteFailedException.class));
                    exceptionTriggeredLatch.countDown();
                    return null;
                })
                .get(30, TimeUnit.SECONDS);

        assertThat(exceptionTriggeredLatch.getCount(), is(0L));
    }

    @Test
    void write() throws InterruptedException, ExecutionException, TimeoutException {
        doReturn(CompletableFuture.<Void>completedFuture(null))
                .when(persistenceFileWriter).write(any());

        doReturn(true).when(rollingFile).renameTo(any());

        atomicFileWriter.write(DATA).get(30, TimeUnit.SECONDS);
    }
}
