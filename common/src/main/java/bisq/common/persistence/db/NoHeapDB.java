package bisq.common.persistence.db;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.util.Utilities;

import java.nio.charset.StandardCharsets;

import java.io.File;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author ebruno
 */
@Slf4j
public class NoHeapDB {
    protected final static int MEGABYTE = 1024 * 1024;
    protected final static int DEFAULT_STORE_SIZE = MEGABYTE * 100;
    private static Thread thread;
    static long c;
    static long ts;
    static String key;

    public static void main(String[] args) throws Exception {
        NoHeapDB db = new NoHeapDB();
       /* db.createStore(
                "MyTestDataStore",
                DataStore.Storage.IN_MEMORY, //or DataStore.Storage.PERSISTED
                256); // in MB*/

        db.createStore(
                "MyTestDataStore",
                DataStore.Storage.PERSISTED, //or DataStore.Storage.PERSISTED
                50, 5); // in MB

        DataStore store = db.getStore("MyTestDataStore");
        String sep = "_";
        String result = store.getString(2 + sep + 100);
        log.error("result {}", result);
        String name = "MyTestDataStore";

        byte[] array = new byte[10000000];
        new Random().nextBytes(array);
        String random = new String(array, StandardCharsets.UTF_8);
        log.error(Utilities.readableFileSize(array.length));
        log.error(Utilities.readableFileSize(random.getBytes(StandardCharsets.UTF_8).length));

        c = store.getRecordCount();
        key = c + sep;
        ts = System.currentTimeMillis();
        String res1 = store.getString(key);
        // log.error("read took {} ms.  {}", System.currentTimeMillis() - ts, res1);
        Timer timer = UserThread.runPeriodically(() -> {

            ts = System.currentTimeMillis();
            key = c + sep;
            String val1 = random + c;
            store.putString(key, val1);
            //log.error("write took {} ms", System.currentTimeMillis() - ts);

            ts = System.currentTimeMillis();
            String res = store.getString(key);
            // log.error("read took {} ms. res={}, val1={}, match {}", System.currentTimeMillis() - ts, res, val1, res.equals(val1));
            //  log.error("read took {} ms. match {}", System.currentTimeMillis() - ts, res.equals(val1));
            c++;
            log.error("getFilesize {} getRecordCount {}", Utilities.readableFileSize(store.getFilesize()), store.getRecordCount());
            System.gc();
            if (store.getFilesize() > 1800000000) {
                log.error("too large");
                System.exit(0);
            }
            // 400 000
          /*  long ts = System.currentTimeMillis();
            int size = 10000000;
            for (int i = 0; i < size; i++) {
                String val = String.valueOf(c * i);
                String key = c + sep + i;
                store.putString(key, val); //400 000
               // log.error("write key/val {}/{}", key, val);
            }

            log.error("write took {} ms", System.currentTimeMillis() - ts);
            ts = System.currentTimeMillis();
            for (int i = 0; i < size; i++) {
                String key = c + sep + i;
                String val = store.getString(key);
                //log.error("read key/val {}/{}", key, val);
            }
            log.error("read took {} ms", System.currentTimeMillis() - ts);
            c++;*/
        }, 100, TimeUnit.MILLISECONDS);


        thread = new Thread(() -> {
            while (true) {
            }
        });
        thread.start();
        UserThread.runAfter(() -> {
            timer.stop();
            thread.interrupt();
        }, 500);
    }

    HashMap<String, DataStore> stores = new HashMap<>();

    String homeDirectory =
            System.getProperty("user.home") +
                    File.separator + "JavaOffHeap";

    public NoHeapDB() {
    }

    public NoHeapDB(String homeDirectory) {
        this.homeDirectory = homeDirectory;
    }

    public boolean createStore(String name) throws Exception {
        return createStore(name,
                DataStore.Storage.IN_MEMORY,
                100, 10);
    }

    public boolean createStore(String name,
                               DataStore.Storage storageType)
            throws Exception {
        return createStore(name,
                storageType,
                100, 10);
    }

    public boolean createStore(String name,
                               DataStore.Storage storageType,
                               int size,
                               int indexFileSize) throws Exception {
        if (size > Integer.MAX_VALUE) {
            throw new Exception("Database size exceeds " + Integer.MAX_VALUE);
        }

        NoHeapDBStore nohdb = new NoHeapDBStore(homeDirectory,
                name,
                storageType,
                size * MEGABYTE,
                indexFileSize * MEGABYTE,
                true);

        stores.put(name, nohdb);

        return true;
    }

    public DataStore getStore(String storeName) {
        return this.stores.get(storeName);
    }

    public boolean deleteStore(String storeName) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            // Delete the store here
            store.delete();
            if (stores.remove(storeName) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean putString(String storeName, String key, String value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putString(key, value);
        }

        return false;
    }

    public boolean putInteger(String storeName, String key, Integer value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putInteger(key, value);
        }

        return false;
    }

    public boolean putShort(String storeName, String key, Short value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putShort(key, value);
        }

        return false;
    }

    public boolean putLong(String storeName, String key, Long value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putLong(key, value);
        }

        return false;
    }

    public boolean putFloat(String storeName, String key, Float value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putFloat(key, value);
        }

        return false;
    }

    public boolean putDouble(String storeName, String key, Double value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putDouble(key, value);
        }

        return false;
    }

    public boolean putChar(String storeName, String key, char value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putChar(key, value);
        }

        return false;
    }

    public boolean putObject(String storeName, String key, Object value) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.putObject(key, value);
        }

        return false;
    }

    public String getString(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getString(key);
        }

        return null;
    }

    public Integer getInteger(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getInteger(key);
        }

        return null;
    }

    public Short getShort(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getShort(key);
        }

        return null;
    }

    public Long getLong(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getLong(key);
        }

        return null;
    }

    public Float getFloat(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getFloat(key);
        }

        return null;
    }

    public Double getDouble(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getDouble(key);
        }

        return null;
    }

    public char getChar(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getChar(key);
        }

        return (char) 0;
    }

    public Object getObject(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.getObject(key);
        }

        return null;
    }

    public boolean remove(String storeName, String key) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.remove(key);
        }

        return false;
    }

    public Object iterateStart(String storeName) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.iterateStart();
        }

        return null;
    }

    public Object iterateNext(String storeName) {
        DataStore store = this.stores.get(storeName);
        if (store != null) {
            return store.iterateNext();
        }

        return null;
    }


    public int getCollisions(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        return niop.getCollisions();
    }

    public int getIndexLoad(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        return niop.getIndexLoad();
    }

    public long getObjectRetrievalTime(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        return niop.getObjectRetrievalTime();
    }

    public long getObjectStorageTime(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        return niop.getObjectStorageTime();
    }

    public void outputStats(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        niop.outputStats();
    }

    public long getRecordCount(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        return niop.getRecordCount();
    }

    public long getEmptyCount(String storeName) {
        NoHeapDBStore niop = (NoHeapDBStore) stores.get(storeName);
        return niop.getEmptyCount();
    }
}
