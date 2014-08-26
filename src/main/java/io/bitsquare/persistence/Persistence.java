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

import io.bitsquare.BitSquare;
import io.bitsquare.util.FileUtil;

import com.google.bitcoin.utils.Threading;

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

    private final String prefix = BitSquare.getAppName() + "_pref";
    private final File storageFile = FileUtil.getFile(prefix, "ser");

    @GuardedBy("lock")
    private Map<String, Serializable> rootMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Persistence() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init() {
        try {
            lock.lock();
            final Map<String, Serializable> map = readRootMap();

            if (map == null) {
                lock.lock();
                try {
                    saveObjectToFile((Serializable) rootMap);
                } finally {
                    lock.unlock();
                }
            }
            else {
                rootMap = map;
            }
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
        // log.trace("Write object with key = " + key + " / value = " + value);
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
            e2.printStackTrace();
            log.error("Could not read rootMap. " + e2);
            return null;
        }
    }

    private void saveObjectToFile(Serializable serializable) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            tempFile = FileUtil.getTempFile(prefix);

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

            FileUtil.writeTempFileToFile(tempFile, storageFile);
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
}
