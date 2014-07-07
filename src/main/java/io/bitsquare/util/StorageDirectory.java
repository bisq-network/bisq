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
        File propertiesFile = new File(USER_PROPERTIES_FILE_NAME);
        if (propertiesFile.exists())
        {
            return new File("");
        }
        else
        {
            // when running form a packed app the file structure is different on mac
            String operatingSystemName = System.getProperty("os.name");
            if (operatingSystemName != null && operatingSystemName.startsWith("Mac"))
            {
                if (new File("../../../../" + USER_PROPERTIES_FILE_NAME).exists())
                {
                    return new File("../../../..");
                }
                else
                {
                    return null;
                }
            }
            return null;
        }
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
                log.error("Could not create the application data directory of '" + storageDirectory + "'");
        }
    }
}

