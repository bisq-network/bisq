package io.bitsquare.util;

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

}
