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

package bisq.apitest.botsupport.util;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtil {

    public static void deleteFileIfExists(File file) throws IOException {
        deleteFileIfExists(file, true);
    }

    public static void deleteFileIfExists(File file, boolean ignoreLockedFiles) throws IOException {
        try {
            if (file.exists() && !file.delete()) {
                if (ignoreLockedFiles) {
                    // We check if file is locked. On Windows all open files are locked by the OS, so we
                    if (isFileLocked(file))
                        log.info("Failed to delete locked file: " + file.getAbsolutePath());
                } else {
                    final String message = "Failed to delete file: " + file.getAbsolutePath();
                    log.error(message);
                    throw new IOException(message);
                }
            }
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            throw new IOException(t);
        }
    }

    private static boolean isFileLocked(File file) {
        return !file.canWrite();
    }
}
