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

package io.bisq.gui.main.overlays.windows.downloadupdate;

import com.google.common.collect.Lists;
import io.bisq.common.storage.FileUtil;
import io.bisq.gui.main.overlays.windows.downloadupdate.BisqInstaller.FileDescriptor;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class DownloadTask extends Task<List<FileDescriptor>> {
    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private String fileName = null;
    private final List<FileDescriptor> fileDescriptors;
    private final String saveDir;

    /**
     * Prepares a task to download a file from {@code fileDescriptors} to the system's download dir.
     */
    public DownloadTask(final FileDescriptor fileDescriptor) {
        this(Lists.newArrayList(fileDescriptor));
    }

    public DownloadTask(final FileDescriptor fileDescriptor, final String saveDir) {
        this(Lists.newArrayList(fileDescriptor), saveDir);
    }

    public DownloadTask(final List<FileDescriptor> fileDescriptors) {
        this(Lists.newArrayList(fileDescriptors), System.getProperty("java.io.tmpdir"));
    }

    /**
     * Prepares a task to download a file from {@code fileDescriptors} to {@code saveDir}.
     *
     * @param fileDescriptors HTTP URL of the file to be downloaded
     * @param saveDir         path of the directory to save the file
     */
    public DownloadTask(final List<FileDescriptor> fileDescriptors, final String saveDir) {
        super();
        this.fileDescriptors = fileDescriptors;
        this.saveDir = saveDir;
        log.info("Starting DownloadTask with file:{}, saveDir:{}, nr of files: {}", fileDescriptors, saveDir, fileDescriptors.size());
    }

    /**
     * Starts the task and therefore the actual download.
     *
     * @return A reference to the created file or {@code null} if no file could be found at the provided URL
     * @throws IOException Forwarded exceotions from HttpURLConnection and file handling methods
     */
    @Override
    protected List<FileDescriptor> call() throws IOException {
        log.debug("DownloadTask started...");

        String partialSaveFilePath = saveDir + (saveDir.endsWith(File.separator) ? "" : File.separator);

        // go twice over the fileDescriptors: first fill in the saveFile, then download the file and fill in the status
        return fileDescriptors.stream()
                .map(fileDescriptor -> {
                    fileDescriptor.setSaveFile(new File(partialSaveFilePath + fileDescriptor.getFileName()));

                    log.info("Downloading {}", fileDescriptor.getLoadUrl());
                    try {
                        updateMessage(fileDescriptor.getFileName());
                        download(new URL(fileDescriptor.getLoadUrl()), fileDescriptor.getSaveFile());
                        log.info("Download for {} done", fileDescriptor.getLoadUrl());
                        fileDescriptor.setDownloadStatus(BisqInstaller.DownloadStatusEnum.OK);
                    } catch (Exception e) {
                        fileDescriptor.setDownloadStatus(BisqInstaller.DownloadStatusEnum.FAIL);
                        log.error("Error downloading file:" + fileDescriptor.toString(), e);
                        e.printStackTrace();
                    }
                    return fileDescriptor;
                })
                .collect(Collectors.toList());
    }

    private void download(URL url, File outputFile) throws IOException {
        if (outputFile.exists()) {
            log.info("We found an existing file and rename it as *.backup.");
            FileUtil.renameFile(outputFile, new File(outputFile.getAbsolutePath() + ".backup"));
        }

        URLConnection urlConnection = url.openConnection();
        urlConnection.connect();
        int fileSize = urlConnection.getContentLength();
        copyInputStreamToFileNew(urlConnection.getInputStream(), outputFile, fileSize);
    }

    public void copyInputStreamToFileNew(final InputStream source, final File destination, int fileSize) throws IOException {
        try {
            final FileOutputStream output = FileUtils.openOutputStream(destination);
            try {
                final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                long count = 0;
                int n;
                while (EOF != (n = source.read(buffer))) {
                    output.write(buffer, 0, n);
                    count += n;
                    log.trace("Progress: {}/{}", count, fileSize);
                    updateProgress(count, fileSize);
                }
                output.close(); // don't swallow close Exception if copy completes normally
            } finally {
                IOUtils.closeQuietly(output);
            }
        } finally {
            IOUtils.closeQuietly(source);
        }
    }
}

