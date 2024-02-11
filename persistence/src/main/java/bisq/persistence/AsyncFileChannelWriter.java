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

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;

import java.util.concurrent.CompletableFuture;

import lombok.Getter;

public class AsyncFileChannelWriter implements AsyncFileWriter {
    @Getter
    private final Path filePath;
    private final AsynchronousFileChannel fileChannel;

    public AsyncFileChannelWriter(Path filePath, AsynchronousFileChannel fileChannel) {
        this.filePath = filePath;
        this.fileChannel = fileChannel;
    }

    @Override
    public CompletableFuture<Integer> write(byte[] data, int offset) {
        var byteBuffer = ByteBuffer.wrap(data);
        var completableFuture = new CompletableFuture<Integer>();
        var completionHandler = createCompletionHandler(completableFuture);
        fileChannel.write(byteBuffer, offset, null, completionHandler);
        return completableFuture;
    }

    private CompletionHandler<Integer, Object> createCompletionHandler(CompletableFuture<Integer> completableFuture) {
        return new CompletionHandler<>() {
            @Override
            public void completed(Integer writtenData, Object o) {
                completableFuture.complete(writtenData);
            }

            @Override
            public void failed(Throwable throwable, Object o) {
                completableFuture.completeExceptionally(throwable);
            }
        };
    }
}
