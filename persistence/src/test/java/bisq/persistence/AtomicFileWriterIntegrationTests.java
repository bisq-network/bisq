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

import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.io.IOException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@ExtendWith(MockitoExtension.class)
public class AtomicFileWriterIntegrationTests {
    private static ExecutorService executorService;
    private Path filePath;
    private AtomicFileWriter atomicFileWriter;

    @BeforeAll
    static void beforeAll() {
        executorService = Executors.newSingleThreadExecutor();
    }

    @AfterAll
    static void afterAll() {
        executorService.shutdownNow();
    }

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        filePath = tempDir.resolve("file");
        var fileChannel = AsynchronousFileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        AsyncFileChannelWriter asyncFileChannelWriter = new AsyncFileChannelWriter(filePath, fileChannel);
        PersistenceFileWriter persistenceFileWriter = new PersistenceFileWriter(asyncFileChannelWriter, executorService);
        atomicFileWriter = new AtomicFileWriter(filePath, persistenceFileWriter);
    }

    @Test
    void singleWrite() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        byte[] expectedData = "Hello World!".getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.write(expectedData).get(30, TimeUnit.SECONDS);

        byte[] actualData = Files.readAllBytes(filePath);
        assertThat(actualData, is(expectedData));
    }

    @Test
    void twoWritesSecondSmaller() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        byte[] expectedData = "Hello World!".getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.write(expectedData).get(30, TimeUnit.SECONDS);

        expectedData = "Bye!".getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.write(expectedData).get(30, TimeUnit.SECONDS);

        byte[] actualData = Files.readAllBytes(filePath);
        assertThat(actualData, is(expectedData));
    }

    @Test
    void twoWriteSecondLarger() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        byte[] expectedData = "Hello World!".getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.write(expectedData).get(30, TimeUnit.SECONDS);

        expectedData = "Bye! Hello World!".getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.write(expectedData).get(30, TimeUnit.SECONDS);

        byte[] actualData = Files.readAllBytes(filePath);
        assertThat(actualData, is(expectedData));
    }
}
