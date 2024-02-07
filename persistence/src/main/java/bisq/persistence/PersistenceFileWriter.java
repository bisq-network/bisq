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

import java.nio.file.Path;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class PersistenceFileWriter {
    private final AsyncFileWriter asyncWriter;
    private final ExecutorService writeRequestScheduler;

    public PersistenceFileWriter(AsyncFileWriter asyncWriter, ExecutorService writeRequestScheduler) {
        this.asyncWriter = asyncWriter;
        this.writeRequestScheduler = writeRequestScheduler;
    }

    public CountDownLatch write(byte[] data) {
        CountDownLatch writeFinished = new CountDownLatch(1);
        scheduleAsyncWrite(data, 0, data.length, writeFinished);
        return writeFinished;
    }

    public Path getFilePath() {
        return asyncWriter.getFilePath();
    }

    private void scheduleAsyncWrite(byte[] data, int offset, int size, CountDownLatch writeFinished) {
        asyncWriter.write(data, offset)
                .thenAcceptAsync(writeUntilEndAsync(data, offset, size, writeFinished), writeRequestScheduler);
    }

    private Consumer<Integer> writeUntilEndAsync(byte[] data,
                                                 int offset,
                                                 int totalBytes,
                                                 CountDownLatch writeFinished) {
        return writtenBytes -> {
            if (writtenBytes == totalBytes) {
                writeFinished.countDown();
                return;
            }

            int remainingBytes = totalBytes - writtenBytes;
            if (remainingBytes > 0) {
                int newOffset = offset + writtenBytes;
                scheduleAsyncWrite(data, newOffset, remainingBytes, writeFinished);
            }
        };
    }
}
