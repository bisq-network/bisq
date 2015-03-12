/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.persistence;

import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple storage solution for serialized data
 * TODO: Should be improved with a more robust solution or maybe a lightweight database.
 * TODO: Should run in a dedicated thread.
 */
public class Persistence {
    private static final Logger log = LoggerFactory.getLogger(Persistence.class);
    private static final ReentrantLock lock = Threading.lock("Storage");

    public static final String DIR_KEY = "persistence.dir";
    public static final String PREFIX_KEY = "persistence.prefix";
    private static final long MIN_INTERVAL_BETWEEN_WRITE_OPERATIONS = 1000;

    @GuardedBy("lock")
    private Map<String, Serializable> rootMap = new HashMap<>();
    private Map<String, Long> timestampMap = new HashMap<>();

    private final File dir;
    private final String prefix;
    private final File storageFile;
    private int resetCounter = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Persistence(
            @Named(DIR_KEY) File dir,
            @Named(PREFIX_KEY) String prefix) {
        this.dir = dir;
        this.prefix = prefix;
        this.storageFile = new File(dir, prefix + ".ser");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init() {
        try {
            lock.lock();
            final Map<String, Serializable> map = readRootMap();

            if (map == null)
                saveObjectToFile((Serializable) rootMap);
            else
                rootMap = map;
        } finally {
            lock.unlock();
        }
    }

    // Map
    public void write(String key, Map<String, ? extends Serializable> value) {
        write(key, (Serializable) value);
    }

    public void write(Object classInstance, String propertyKey, Map<String, ? extends Serializable> value) {
        write(classInstance.getClass().getName() + "." + propertyKey, value);
    }

    public void write(Object classInstance, Map<String, ? extends Serializable> value) {
        write(classInstance.getClass().getName(), value);
    }

    // List
    public void write(String key, List<? extends Serializable> value) {
        write(key, (Serializable) value);
    }

    public void write(Object classInstance, String propertyKey, List<? extends Serializable> value) {
        write(classInstance.getClass().getName() + "." + propertyKey, value);
    }

    public void write(Object classInstance, List<? extends Serializable> value) {
        write(classInstance.getClass().getName(), value);
    }

    // Serializable
    public void write(Object classInstance, String propertyKey, Serializable value) {
        write(classInstance.getClass().getName() + "." + propertyKey, value);
    }

    public void write(Object classInstance, Serializable value) {
        write(classInstance.getClass().getName(), value);
    }

    public void write(Serializable classInstance) {
        write(classInstance.getClass().getName(), classInstance);
    }

    public void write(String key, Serializable value) {
        //log.trace("Write object with key = " + key + " / value = " + value);
        // TODO add throttle to limit write operations

        try {
            lock.lock();
            rootMap.put(key, value);
            saveObjectToFile((Serializable) rootMap);
        } finally {
            lock.unlock();
        }
    }

    public Serializable read(Object classInstance) {
        return read(classInstance.getClass().getName());
    }

    public Serializable read(Object classInstance, String propertyKey) {
        return read(classInstance.getClass().getName() + "." + propertyKey);
    }

    // read from local rootMap, just if not found read from disc
    public Serializable read(String key) {
        try {
            lock.lock();
            if (rootMap.containsKey(key)) {
                // log.trace("Read object with key = " + key + " / value = " + rootMap.get(key));
                return rootMap.get(key);
            }
            else {
                final Map<String, Serializable> map = readRootMap();
                if (map != null) {
                    rootMap = map;
                }
                if (rootMap.containsKey(key)) {
                    // log.trace("Read object with key = " + key + " / value = " + rootMap.get(key));
                    return rootMap.get(key);
                }
                else {
                    log.info("Object with key = " + key + " not found.");
                    return null;
                }
            }
        } finally {
            lock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    private Map<String, Serializable> readRootMap() {
        try {
            final Object object = readObjectFromFile(storageFile);
            if (object == null) {
                log.error("readRootMap returned null object.");
                return null;
            }
            else {
                if (object instanceof Map) {
                    return (Map<String, Serializable>) object;
                }
                else {
                    log.error("Object is not type of Map<String, Serializable>");
                    return null;
                }
            }

        } catch (FileNotFoundException e) {

            log.trace("File not found is ok for the first execute.");
            return null;
        } catch (ClassNotFoundException | IOException e2) {
            log.warn("Could not read rootMap. " + e2);

            // If there are problems with incompatible versions, we reset the persisted data
            // TODO We need a clean solution when we use another persistence solution
            rootMap = new HashMap<>();
            saveObjectToFile((Serializable) rootMap);
            resetCounter++;

            // We only try that once, if it fails repeatedly we 
            if (resetCounter == 1) {
                log.warn("We reset the persisted data and try again to read the root map.");
                return readRootMap();
            }
            else {
                e2.printStackTrace();
                log.error("We tried already to reset the persisted data, but we get an error again, so we give up.");
                return null;
            }
        }
    }

    private void saveObjectToFile(Serializable serializable) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            tempFile = File.createTempFile("temp_" + prefix, null, dir);

            // Don't use auto closeable resources in try() as we would need too many try/catch clauses (for tempFile)
            // and we need to close it
            // manually before replacing file with temp file
            fileOutputStream = new FileOutputStream(tempFile);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(serializable);

            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            fileOutputStream.flush();
            fileOutputStream.getFD().sync();

            // Close resources before replacing file with temp file because otherwise it causes problems on windows
            // when rename temp file
            fileOutputStream.close();
            objectOutputStream.close();

            writeTempFileToFile(tempFile, storageFile);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("save object to file failed." + e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save.");
                if (!tempFile.delete()) log.error("Cannot delete temp file.");
            }

            try {
                if (objectOutputStream != null) objectOutputStream.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Cannot close resources.");
            }
        }
    }

    private Object readObjectFromFile(File file) throws IOException, ClassNotFoundException {
        lock.lock();
        try (final FileInputStream fileInputStream = new FileInputStream(file);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            return objectInputStream.readObject();
        } finally {
            lock.unlock();
        }
    }

    public void writeTempFileToFile(File tempFile, File file) throws IOException {
        if (Utils.isWindows()) {
            // Work around an issue on Windows whereby you can't rename over existing files.
            final File canonical = file.getCanonicalFile();
            if (canonical.exists() && !canonical.delete()) {
                throw new IOException("Failed to delete canonical file for replacement with save");
            }
            if (!tempFile.renameTo(canonical)) {
                throw new IOException("Failed to rename " + tempFile + " to " + canonical);
            }
        }
        else if (!tempFile.renameTo(file)) {
            throw new IOException("Failed to rename " + tempFile + " to " + file);
        }
    }
}
