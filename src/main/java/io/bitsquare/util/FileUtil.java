package io.bitsquare.util;

import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil
{
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);
    private final static File rootDirectory = new File(FileUtil.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getParentFile();

    public static File getRootDirectory()
    {
        return rootDirectory;
    }

    // not used yet
   /* public static void setRootDirectory( File rootDirectory)
    {
        FileUtil.rootDirectory = rootDirectory;
        if (!rootDirectory.exists())
        {
            if (!rootDirectory.mkdir())
                log.error("Could not create directory. " + rootDirectory.getAbsolutePath());
        }
    } */


    public static File getDirectory(String name)
    {
        final File dir = new File(rootDirectory, name);
        if (!dir.exists())
        {
            if (!dir.mkdir())
                log.error("Could not create directory. " + dir.getAbsolutePath());
        }

        return dir;
    }


    public static File getFile(String name, String suffix)
    {
        return new File(rootDirectory, name + "." + suffix);
    }


    public static File getTempFile(String prefix) throws IOException
    {
        return File.createTempFile("temp_" + prefix, null, rootDirectory);
    }

}
