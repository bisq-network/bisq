/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.util;

import com.google.bitcoin.core.Utils;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lighthouse.files.AppDirectory;

public class FileUtil {
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    public static File getFile(String name, String suffix) {
        return new File(AppDirectory.dir().toFile(), name + "." + suffix);
    }

    public static File getTempFile(String prefix) throws IOException {
        return File.createTempFile("temp_" + prefix, null, AppDirectory.dir().toFile());
    }

    public static void writeTempFileToFile(File tempFile, File file) throws IOException {
        if (Utils.isWindows()) {
            // Work around an issue on Windows whereby you can't rename over existing files.
            final File canonical = file.getCanonicalFile();
            if (canonical.exists() && !canonical.delete()) {
                throw new IOException("Failed to delete canonical file for replacement with save");
            }
            if (!tempFile.renameTo(canonical)) {
                throw new IOException("Failed to rename " + tempFile + " to " + canonical);
            }
        }
        else if (!tempFile.renameTo(file)) {
            throw new IOException("Failed to rename " + tempFile + " to " + file);
        }

    }
}
