package io.bitsquare.util;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageDirectory
{
    private static final Logger log = LoggerFactory.getLogger(StorageDirectory.class);
    private static final String USER_PROPERTIES_FILE_NAME = "bitsquare.properties";
    private static File storageDirectory;

    static
    {
        useApplicationDirectory();
        log.info("Default application data directory = " + storageDirectory);
    }

    public static File getStorageDirectory()
    {
        return storageDirectory;
    }

    public static void setStorageDirectory(File directory)
    {
        storageDirectory = directory;
        log.info("User defined application data directory = " + directory);

        createDirIfNotExists();
    }

    public static void useApplicationDirectory()
    {
        setStorageDirectory(getApplicationDirectory());
    }

    public static void useSystemApplicationDataDirectory()
    {
        setStorageDirectory(getSystemApplicationDataDirectory());
    }

    public static File getApplicationDirectory()
    {
        File executionRoot = new File(StorageDirectory.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        log.trace("executionRoot " + executionRoot.getAbsolutePath());
        // check if it is packed into a mac app  (e.g.: "/Users/mk/Desktop/bitsquare.app/Contents/Java/bitsquare.jar")
        if (executionRoot.getAbsolutePath().endsWith("/bitsquare.app/Contents/Java/bitsquare.jar") && System.getProperty("os.name").startsWith("Mac"))
            return executionRoot.getParentFile().getParentFile().getParentFile().getParentFile();
        else if (executionRoot.getAbsolutePath().endsWith("/target/classes"))
            return executionRoot.getParentFile();   // dev e.g.: /Users/mk/Documents/_intellij/bitsquare/target/classes -> use target as root
        else if (executionRoot.getAbsolutePath().endsWith("/bitsquare.jar"))
            return executionRoot.getParentFile();    // dev with jar e.g.: Users/mk/Documents/_intellij/bitsquare/out/artifacts/bitsquare2/bitsquare.jar  -> use target as root
        else
            return executionRoot;
    }

    public static File getSystemApplicationDataDirectory()
    {
        String osName = System.getProperty("os.name");
        if (osName != null && osName.startsWith("Windows"))
            return new File(System.getenv("APPDATA") + File.separator + "BitSquare");
        else if (osName != null && osName.startsWith("Mac"))
            return new File(System.getProperty("user.home") + "/Library/Application Support/BitSquare");
        else
            return new File(System.getProperty("user.home") + "/BitSquare");
    }

    private static void createDirIfNotExists()
    {
        if (!storageDirectory.exists())
        {
            boolean created = storageDirectory.mkdir();
            if (!created)
                throw new RuntimeException("Could not create the application data directory of '" + storageDirectory + "'");
        }
    }
}

