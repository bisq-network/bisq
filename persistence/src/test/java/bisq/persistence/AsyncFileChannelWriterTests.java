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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.io.IOException;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AsyncFileChannelWriterTests {
    private Path filePath;
    private AsynchronousFileChannel fileChannel;
    private AsyncFileChannelWriter asyncFileChannelWriter;

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        filePath = tempDir.resolve("file");
        fileChannel = AsynchronousFileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        asyncFileChannelWriter = new AsyncFileChannelWriter(filePath, fileChannel);
    }

    @Test
    void writeData() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        byte[] expected = new byte[1050];
        new Random().nextBytes(expected);

        CompletableFuture<Integer> completableFuture = asyncFileChannelWriter.write(expected, 0);

        int writtenBytes = completableFuture.get(30, TimeUnit.SECONDS);
        while (writtenBytes < expected.length) {
            completableFuture = asyncFileChannelWriter.write(expected, writtenBytes);
            writtenBytes += completableFuture.get(30, TimeUnit.SECONDS);
        }

        assertThat(writtenBytes, is(expected.length));

        fileChannel.close();
        byte[] actual = Files.readAllBytes(filePath);

        assertThat(expected, is(actual));
    }

    @Test
    void writeDataAtOffset() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final int startOffset = 100;
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        CompletableFuture<Integer> completableFuture = asyncFileChannelWriter.write(data, startOffset);

        int writtenBytes = startOffset + completableFuture.get(30, TimeUnit.SECONDS);
        while (writtenBytes < data.length) {
            completableFuture = asyncFileChannelWriter.write(data, writtenBytes);
            writtenBytes += completableFuture.get(30, TimeUnit.SECONDS);
        }

        assertThat(writtenBytes - startOffset, is(data.length));

        fileChannel.close();
        byte[] readFromFile = Files.readAllBytes(filePath);
        assertThat(readFromFile.length, is(startOffset + data.length));

        byte[] readFromFileWithoutOffset = Arrays.copyOfRange(readFromFile, 100, readFromFile.length);
        assertThat(readFromFileWithoutOffset, is(data));
    }
}
