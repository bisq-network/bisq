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

import java.io.File;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtomicFileWriter {
    private final PersistenceFileWriter rollingFileWriter;
    private File activeFile;
    private File rollingFile;


    public AtomicFileWriter(Path destinationPath,
                            PersistenceFileWriter rollingFileWriter) {
        this.rollingFileWriter = rollingFileWriter;
        activeFile = destinationPath.toFile();
        rollingFile = rollingFileWriter.getFilePath().toFile();
    }

    public synchronized void write(byte[] data) {
        try {
            CountDownLatch countDownLatch = rollingFileWriter.write(data);
            boolean isSuccess = countDownLatch.await(45, TimeUnit.SECONDS);
            if (!isSuccess) {
                throw new AtomicFileWriteFailedException("Async atomic file write timeout triggered after 45 seconds.");
            }

            isSuccess = rollingFile.renameTo(activeFile);
            if (!isSuccess) {
                throw new AtomicFileWriteFailedException("Couldn't rename rolling file to active file.");
            }

            File tmpFile = activeFile;
            activeFile = rollingFile;
            rollingFile = tmpFile;

        } catch (InterruptedException e) {
            log.error("AtomicFileWriter got interrupted during write.", e);
        }
    }
}
