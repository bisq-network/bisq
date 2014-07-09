package io.bitsquare.util;

import com.google.bitcoin.core.Utils;
import io.bitsquare.BitSquare;
import java.io.*;
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
        return File.createTempFile("temp_" + prefix, null, StorageDirectory.getStorageDirectory());
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

    public static void saveFile(String fileName, File sourceFile, Serializable serializable)
    {
        try
        {
            File tempFile;
            if (Utils.isWindows())
                tempFile = sourceFile;
            else
                tempFile = FileUtil.getTempFile("temp_" + fileName);

            try (final FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                 final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream))
            {
                objectOutputStream.writeObject(serializable);

                // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
                // to not write through to physical media for at least a few seconds, but this is the best we can do.
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();

            } catch (IOException e)
            {
                e.printStackTrace();
                log.error("save serializable object to file failed." + e);

                if (tempFile.exists())
                {
                    log.warn("Temp file still exists after failed save.");
                    if (!tempFile.delete())
                    {
                        log.warn("Cannot delete temp file.");
                    }
                }
            }

            if (!Utils.isWindows())
            {
                if (!tempFile.renameTo(sourceFile))
                {
                    log.error("Failed to rename " + tempFile.toString() + " to " + sourceFile.toString());
                }

            }
        } catch (IOException e)
        {
            e.printStackTrace();
            log.error("Exception at saveFile " + e);
        }
    }

    public static void saveTempFileToFile(File tempFile, File file) throws IOException
    {
        if (Utils.isWindows())
        {
            // Work around an issue on Windows whereby you can't rename over existing files.
            final File canonicalFile = file.getCanonicalFile();
            if (canonicalFile.exists() && !canonicalFile.delete())
            {
                throw new IOException("Failed to delete pubKeyCanonicalFile for replacement with save");
            }
            if (!tempFile.renameTo(canonicalFile))
            {
                throw new IOException("Failed to rename " + tempFile + " to " + canonicalFile);
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
