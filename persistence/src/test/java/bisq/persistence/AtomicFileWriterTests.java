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

import java.util.concurrent.CountDownLatch;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class AtomicFileWriterTests {
    private static final byte[] DATA = "Hello World!".getBytes(StandardCharsets.UTF_8);
    @Mock
    private PersistenceFileWriter persistenceFileWriter;
    @Mock
    private File rollingFile = mock(File.class);
    @Mock
    private CountDownLatch countDownLatch;
    private AtomicFileWriter atomicFileWriter;

    @BeforeEach
    void setup(@TempDir Path tempDir, @Mock Path rollingFilePath) {
        doReturn(countDownLatch).when(persistenceFileWriter).write(any());
        doReturn(rollingFile).when(rollingFilePath).toFile();
        doReturn(rollingFilePath).when(persistenceFileWriter).getFilePath();

        var file = tempDir.resolve("my_file");
        atomicFileWriter = new AtomicFileWriter(file, persistenceFileWriter);
    }

    @Test
    void triggerFileWriteTimeout() throws InterruptedException {
        doReturn(false).when(countDownLatch).await(anyLong(), any());
        assertThrows(AtomicFileWriteFailedException.class,
                () -> atomicFileWriter.write(DATA));
    }

    @Test
    void renameFailure() throws InterruptedException {
        doReturn(true).when(countDownLatch).await(anyLong(), any());
        doReturn(false).when(rollingFile).renameTo(any());

        assertThrows(AtomicFileWriteFailedException.class,
                () -> atomicFileWriter.write(DATA));
    }

    @Test
    void write() throws InterruptedException {
        doReturn(true).when(countDownLatch).await(anyLong(), any());
        doReturn(true).when(rollingFile).renameTo(any());
        atomicFileWriter.write(DATA);
    }
}
