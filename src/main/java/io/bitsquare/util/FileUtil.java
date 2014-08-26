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

public class FileUtil {
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    public static File getFile(String name, String suffix) {
        return new File(StorageDirectory.getStorageDirectory(), name + "." + suffix);
    }

    public static File getTempFile(String prefix) throws IOException {
        return File.createTempFile("temp_" + prefix, null, StorageDirectory.getStorageDirectory());
    }

    /*
    public static String getApplicationFileName()
    {
        File executionRoot = new File(StorageDirectory.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        try
        {
            log.trace("getApplicationFileName " + executionRoot.getCanonicalPath());
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        // check if it is packed into a mac app  (e.g.: "/Users/mk/Desktop/bitsquare.app/Contents/Java/bitsquare.jar")
        try
        {
            if (executionRoot.getCanonicalPath().endsWith(".app/Contents/Java/bitsquare.jar") && System.getProperty("os.name").startsWith("Mac"))
            {
                File appFile = executionRoot.getParentFile().getParentFile().getParentFile();
                try
                {
                    int lastSlash = appFile.getCanonicalPath().lastIndexOf("/") + 1;
                    return appFile.getCanonicalPath().substring(lastSlash).replace(".app", "");
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
        // fallback use AppName
        return BitSquare.getAppName();
    }
*/

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
        } else if (!tempFile.renameTo(file)) {
            throw new IOException("Failed to rename " + tempFile + " to " + file);
        }

    }
}
