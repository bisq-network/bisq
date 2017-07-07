package io.bisq.gui.main.overlays.windows.downloadupdate;

/**
 * A utility that downloads a file from a URL.
 *
 * @author www.codejava.net
 */


import com.google.common.collect.Lists;
import io.bisq.common.locale.Res;
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
     * Prepares a task to download a file from {@code fileDescriptors} to the sysyem's temp dir specified in java.io.tmpdir.
     *
     * @param fileURL HTTP URL of the file to be downloaded
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
        updateMessage(Res.get("displayUpdateDownloadWindow.download.starting"));
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

        // go twice over the filedescriptors: first fill in the saveFile, then download the file and fill in the status
        return fileDescriptors.stream()
                .map(fileDescriptor -> {
                    fileDescriptor.setSaveFile(new File(partialSaveFilePath + fileDescriptor.getFileName()));
                    return fileDescriptor;
                })
                .map(fileDescriptor -> {
                    updateMessage(Res.get("displayUpdateDownloadWindow.download.file", fileDescriptor.getFileName()));
                    log.info("Downloading {}", fileDescriptor.getFileName());
                    URL url = null;
                    try {
                        url = new URL(fileDescriptor.getLoadUrl());
                        download(url, fileDescriptor.getSaveFile());
                        fileDescriptor.setDownloadStatus(BisqInstaller.DownloadStatusEnum.OK);
                    } catch (Exception e) {
                        fileDescriptor.setDownloadStatus(BisqInstaller.DownloadStatusEnum.FAIL);
                        log.error("Error downloading file:" + fileDescriptor.toString(), e);
                    }
                    return fileDescriptor;
                })
                .collect(Collectors.toList());
    }

    private void download(URL url, File outputFile) throws IOException {
        URLConnection urlConnection = null;
        urlConnection = url.openConnection();
        urlConnection.connect();
        int file_size = urlConnection.getContentLength();
        copyInputStreamToFileNew(urlConnection.getInputStream(), outputFile, file_size);
    }



    public void copyInputStreamToFileNew(final InputStream source, final File destination, int fileSize) throws IOException {
        try {
            final FileOutputStream output = FileUtils.openOutputStream(destination);
            try {
                final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                long count = 0;
                int n = 0;
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

