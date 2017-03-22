package io.bitsquare.common.util;

/**
 * A utility that downloads a file from a URL.
 * @author www.codejava.net
 *
 */


import javafx.concurrent.Task;
import javafx.scene.control.ProgressIndicator;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;


public class DownloadUtil extends Task<File> {

/*    public static void main(String [] args) {
        try {
            downloadFile("https://github.com/bitsquare/bitsquare/releases/download/v0.4.9.9/Bitsquare-64bit-0.4.9.9.exe", "/home/bob/test");
        } catch (IOException e) {e.printStackTrace();}
    }
*/
    private static final int BUFFER_SIZE = 4096;
    private final String fileURL;
    private final String saveDir;

    /**
     * Downloads a file from a URL
     * @param fileURL HTTP URL of the file to be downloaded
     * @param saveDir path of the directory to save the file
     */
    public DownloadUtil (final String fileURL, final String saveDir) {
        this.fileURL = fileURL;
        this.saveDir = saveDir;
    }

    /**
     * Downloads a file from a URL
     * @param fileURL HTTP URL of the file to be downloaded
     */
    public DownloadUtil (final String fileURL) {
        this.fileURL = fileURL;
        this.saveDir = System.getProperty("java.io.tmpdir");
        System.out.println("Auto-selected temp dir " + this.saveDir);
    }

    @Override protected File call() throws Exception{
        System.out.println("Task started....");
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();
            if (!(contentLength > 0))
                contentLength = -1;

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    fileName = disposition.substring(index + 9, disposition.length());
                }
            } else {
                // extracts file name from URL
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
            }

/*            System.out.println("Content-Type = " + contentType);
            System.out.println("Content-Disposition = " + disposition);
            System.out.println("Content-Length = " + contentLength);
            System.out.println("fileName = " + fileName);
*/
            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();
            String saveFilePath = saveDir + File.separator + fileName;

            // opens an output stream to save into file
            File outputFile = new File(saveFilePath);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            int bytesRead;
            int totalRead = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (this.isCancelled())
                    break;
                outputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                updateProgress(totalRead, contentLength);
            }

            try {
                outputStream.close();
            } catch (IOException e) {
            } finally {
                inputStream.close();
            }

            System.out.println("File downloaded");
            return outputFile.getParentFile();
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
        return null;
    }
}