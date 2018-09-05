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

package bisq.common.storage;

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonFileManager {
    private final ThreadPoolExecutor executor = Utilities.getThreadPoolExecutor("saveToDiscExecutor", 5, 50, 60);
    private final File dir;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public JsonFileManager(File dir) {
        this.dir = dir;

        if (!dir.exists())
            if (!dir.mkdir())
                log.warn("make dir failed");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UserThread.execute(JsonFileManager.this::shutDown);
        }, "WriteOnlyFileManager.ShutDownHook"));
    }

    public void shutDown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void writeToDisc(String json, String fileName) {
        executor.execute(() -> {
            File jsonFile = new File(Paths.get(dir.getAbsolutePath(), fileName + ".json").toString());
            File tempFile = null;
            PrintWriter printWriter = null;
            try {
                tempFile = File.createTempFile("temp", null, dir);
                if (!executor.isShutdown() && !executor.isTerminated() && !executor.isTerminating())
                    tempFile.deleteOnExit();

                printWriter = new PrintWriter(tempFile);
                printWriter.println(json);

                FileUtil.renameFile(tempFile, jsonFile);
            } catch (Throwable t) {
                log.error("storageFile " + jsonFile.toString());
                t.printStackTrace();
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    log.warn("Temp file still exists after failed save. We will delete it now. storageFile=" + fileName);
                    if (!tempFile.delete())
                        log.error("Cannot delete temp file.");
                }

                if (printWriter != null)
                    printWriter.close();
            }
        });
    }

    public Object readJsonFromDisc(String fileName) {
        final File jsonFile = new File(Paths.get(dir.getAbsolutePath(), fileName + ".json").toString());
        JSONParser parser = new JSONParser();
        try {
            return parser.parse(new FileReader(jsonFile));
        } catch (ParseException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
