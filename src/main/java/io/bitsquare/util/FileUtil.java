package io.bitsquare.util;

import io.bitsquare.BitSquare;
import java.io.File;
import java.io.IOException;
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
        log.trace("getApplicationFileName " + executionRoot.getAbsolutePath());
        // check if it is packed into a mac app  (e.g.: "/Users/mk/Desktop/bitsquare.app/Contents/Java/bitsquare.jar")
        if (executionRoot.getAbsolutePath().endsWith(".app/Contents/Java/bitsquare.jar") && System.getProperty("os.name").startsWith("Mac"))
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
        return BitSquare.getAppName();
    }

}
