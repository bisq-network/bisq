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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class PersistenceFileWriter {
    private final AsyncFileWriter asyncWriter;
    private final ExecutorService writeRequestScheduler;

    public PersistenceFileWriter(AsyncFileWriter asyncWriter, ExecutorService writeRequestScheduler) {
        this.asyncWriter = asyncWriter;
        this.writeRequestScheduler = writeRequestScheduler;
    }

    public CompletableFuture<Void> write(byte[] data) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        scheduleAsyncWrite(data, 0, data.length, completableFuture);
        return completableFuture;
    }

    public Path getFilePath() {
        return asyncWriter.getFilePath();
    }

    private void scheduleAsyncWrite(byte[] data, int offset, int size, CompletableFuture<Void> completableFuture) {
        asyncWriter.write(data, offset)
                .thenAcceptAsync(writeUntilEndAsync(data, offset, size, completableFuture), writeRequestScheduler)
                .exceptionally(throwable -> {
                    completableFuture.completeExceptionally(throwable);
                    return null;
                });
    }

    private Consumer<Integer> writeUntilEndAsync(byte[] data,
                                                 int offset,
                                                 int totalBytes,
                                                 CompletableFuture<Void> completableFuture) {
        return writtenBytes -> {
            if (writtenBytes == totalBytes) {
                completableFuture.complete(null);
                return;
            }

            int remainingBytes = totalBytes - writtenBytes;
            if (remainingBytes > 0) {
                int newOffset = offset + writtenBytes;
                scheduleAsyncWrite(data, newOffset, remainingBytes, completableFuture);
            }
        };
    }
}
