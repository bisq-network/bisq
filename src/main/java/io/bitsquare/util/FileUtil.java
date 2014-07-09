package io.bitsquare.util;

import com.google.bitcoin.core.Utils;
import io.bitsquare.BitSquare;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil
{
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);


    public static File getFile(String name, String suffix)
    {
        return new File(StorageDirectory.getStorageDirectory(), name + "." + suffix);
    }

    public static File getTempFile(String prefix) throws IOException
    {
        if (Utils.isWindows())
        {
            return getFile("temp_" + prefix, ".tmp");
        }
        else
        {
            return File.createTempFile("temp_" + prefix, null, StorageDirectory.getStorageDirectory());
        }
    }

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
        return BitSquare.getAppName();
    }


    public static void writeTempFileToFile(File tempFile, File file) throws IOException
    {
        if (Utils.isWindows())
        {
            // renameTo fails on win 8
            String canonicalPath = file.getCanonicalPath();
            file.delete();
            final File canonicalFile = new File(canonicalPath);
            Files.copy(tempFile.toPath(), canonicalFile.toPath());

            if (tempFile.exists() && !tempFile.delete())
            {
                log.error("Cannot delete temp file.");
            }
        }
        else
        {
            if (!tempFile.renameTo(file))
            {
                throw new IOException("Failed to rename " + tempFile + " to " + file);
            }
        }
    }
}
