package io.bitsquare.util;

import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
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
   /* public static void setRootDirectory(@NotNull File rootDirectory)
    {
        FileUtil.rootDirectory = rootDirectory;
        if (!rootDirectory.exists())
        {
            if (!rootDirectory.mkdir())
                log.error("Could not create directory. " + rootDirectory.getAbsolutePath());
        }
    } */

    @NotNull
    public static File getDirectory(@NotNull String name)
    {
        final File dir = new File(rootDirectory, name);
        if (!dir.exists())
        {
            if (!dir.mkdir())
                log.error("Could not create directory. " + dir.getAbsolutePath());
        }

        return dir;
    }

    @NotNull
    public static File getFile(@NotNull String name, @NotNull String suffix)
    {
        return new File(rootDirectory, name + "." + suffix);
    }

    @NotNull
    public static File getTempFile(@NotNull String prefix) throws IOException
    {
        return File.createTempFile("temp_" + prefix, null, rootDirectory);
    }

}
