package io.bitsquare.storage;

import com.google.bitcoin.core.Utils;
import com.google.bitcoin.utils.Threading;
import com.google.inject.Inject;
import io.bitsquare.BitSquare;
import io.bitsquare.util.FileUtil;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple storage solution for serialized data
 */
public class Storage
{
    private static final Logger log = LoggerFactory.getLogger(Storage.class);
    private static final ReentrantLock lock = Threading.lock("Storage");

    private final String prefix = BitSquare.ID + "_pref";
    private final File storageFile = FileUtil.getFile(prefix, "ser");
    @NotNull
    @GuardedBy("lock")
    private Map<String, Serializable> rootMap = new HashMap<>();
    private boolean dirty;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Storage()
    {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init()
    {
        try
        {
            lock.lock();
            final Map<String, Serializable> map = readRootMap();

            if (map == null)
            {
                lock.lock();
                try
                {
                    saveObjectToFile((Serializable) rootMap);
                } finally
                {
                    lock.unlock();
                }
            }
            else
            {
                rootMap = map;
            }
        } finally
        {
            lock.unlock();
        }
    }

    // Map
    public void write(@NotNull String key, @NotNull Map<String, ? extends Serializable> value)
    {
        write(key, (Serializable) value);
    }

    public void write(@NotNull Object classInstance, @NotNull String propertyKey, @NotNull Map<String, ? extends Serializable> value)
    {
        write(classInstance.getClass().getName() + "." + propertyKey, value);
    }

    public void write(@NotNull Object classInstance, @NotNull Map<String, ? extends Serializable> value)
    {
        write(classInstance.getClass().getName(), value);
    }

    // List
    public void write(@NotNull String key, @NotNull List<? extends Serializable> value)
    {
        write(key, (Serializable) value);
    }

    public void write(@NotNull Object classInstance, @NotNull String propertyKey, @NotNull List<? extends Serializable> value)
    {
        write(classInstance.getClass().getName() + "." + propertyKey, value);
    }

    public void write(@NotNull Object classInstance, @NotNull List<? extends Serializable> value)
    {
        write(classInstance.getClass().getName(), value);
    }

    // Serializable
    public void write(@NotNull Object classInstance, @NotNull String propertyKey, @NotNull Serializable value)
    {
        write(classInstance.getClass().getName() + "." + propertyKey, value);
    }

    public void write(@NotNull Object classInstance, @NotNull Serializable value)
    {
        write(classInstance.getClass().getName(), value);
    }

    public void write(@NotNull String key, @NotNull Serializable value)
    {
        // log.trace("Write object with key = " + key + " / value = " + value);
        try
        {
            lock.lock();
            dirty = true;
            rootMap.put(key, value);
            saveObjectToFile((Serializable) rootMap);
        } finally
        {
            lock.unlock();
        }
    }


    @Nullable
    public Serializable read(@NotNull Object classInstance)
    {
        return read(classInstance.getClass().getName());
    }

    @Nullable
    public Serializable read(@NotNull Object classInstance, @NotNull String propertyKey)
    {
        return read(classInstance.getClass().getName() + "." + propertyKey);
    }

    @Nullable
    public Serializable read(@NotNull String key)
    {
        try
        {
            lock.lock();
            if (dirty)
            {
                final Map<String, Serializable> map = readRootMap();
                if (map != null)
                {
                    rootMap = map;
                    dirty = false;
                }
            }
            if (rootMap.containsKey(key))
            {
                // log.trace("Read object with key = " + key + " / value = " + rootMap.get(key));
                return rootMap.get(key);
            }
            else
            {
                log.warn("Object with key = " + key + " not found.");
                return null;
            }
        } finally
        {
            lock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    private Map<String, Serializable> readRootMap()
    {
        try
        {
            final Object object = readObjectFromFile(storageFile);
            if (object == null)
            {
                log.error("readRootMap returned null object.");
                return null;
            }
            else
            {
                if (object instanceof Map)
                {
                    //noinspection unchecked
                    return (Map<String, Serializable>) object;
                }
                else
                {
                    log.error("Object is not type of Map<String, Serializable>");
                    return null;
                }
            }

        } catch (@NotNull FileNotFoundException e)
        {

            log.trace("File not found is ok for the first run.");
            return null;
        } catch (@NotNull ClassNotFoundException | IOException e2)
        {
            e2.printStackTrace();
            log.error("Could not read rootMap. " + e2);
            return null;
        }
    }

    private void saveObjectToFile(@NotNull Serializable serializable)
    {
        try
        {
            final File tempFile = FileUtil.getTempFile("temp_" + prefix);
            try (final FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                 final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream))
            {
                objectOutputStream.writeObject(serializable);

                // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
                // to not write through to physical media for at least a few seconds, but this is the best we can do.
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();

                if (Utils.isWindows())
                {
                    // Work around an issue on Windows whereby you can't rename over existing files.
                    final File canonical = storageFile.getCanonicalFile();
                    if (canonical.exists() && !canonical.delete())
                        throw new IOException("Failed to delete canonical file for replacement with save");
                    if (!tempFile.renameTo(canonical))
                        throw new IOException("Failed to rename " + tempFile + " to " + canonical);
                }
                else if (!tempFile.renameTo(storageFile))
                {
                    throw new IOException("Failed to rename " + tempFile + " to " + storageFile);
                }

            } catch (IOException e)
            {
                e.printStackTrace();
                log.error("saveObjectToFile failed." + e);

                if (tempFile.exists())
                {
                    log.warn("Temp file still exists after failed save.");
                    if (!tempFile.delete())
                        log.warn("Cannot delete temp file.");
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
            log.error("getTempFile failed." + e);
        }
    }

    @Nullable
    private Object readObjectFromFile(@NotNull File file) throws IOException, ClassNotFoundException
    {
        lock.lock();
        try (final FileInputStream fileInputStream = new FileInputStream(file);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream))
        {
            return objectInputStream.readObject();
        } finally
        {
            lock.unlock();
        }
    }
}
