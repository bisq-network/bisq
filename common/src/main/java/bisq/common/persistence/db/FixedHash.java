package bisq.common.persistence.db;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.io.File;
import java.io.RandomAccessFile;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author ebruno
 */
public class FixedHash implements HashBase {

    public enum Storage {
        IN_MEMORY,
        PERSISTED
    }

    protected static final Logger logger = Logger.getLogger("FixedHash");
    protected boolean debugLogging = false;

    protected static int LOAD_THRESHOLD = 70;
    protected static final int PAGE_SIZE = 1024 * 1024;
    protected static final int SIZE_FACTOR = 2;
    protected static final int DEFAULT_INDEX_JOURNAL_SIZE = SIZE_FACTOR * PAGE_SIZE;
    protected static final int KEY_SIZE = 0;

    //
    // 1 (byte)  key length
    // 4 (int)   key hashcode
    // 0 (bytes) key size
    // 8 (long)  record location
    //
    protected static final int INDEX_ENTRY_SIZE_BYTES =
            1 + Integer.BYTES + KEY_SIZE + Long.BYTES;

    protected long sizeInBytes = DEFAULT_INDEX_JOURNAL_SIZE;
    protected int previousOffset = 0; // the last record inserted into the index
    protected int bucketsFree = 0;
    protected int bucketsUsed = 0;
    protected int totalBuckets = 0;
    protected int collisions = 0;

    protected String journalPath = "";
    protected boolean inMemory = true;

    protected RandomAccessFile indexFile = null;
    protected FileChannel indexChannel = null;
    protected ByteBuffer indexBuffer = null;
    protected byte keyLength = 16;
    protected long indexCurrentEnd = 0;

    protected int indexRecordReadCount = 1;

    // Used when iterating through the index
    protected int iterateNext = 0;

    ///////////////////////////////////////////////////////////////////////////

    public FixedHash(String journalPath, boolean inMemory, boolean reuseExisting) {
        this(DEFAULT_INDEX_JOURNAL_SIZE, journalPath, inMemory, reuseExisting);
    }

    public FixedHash(int size, String journalPath, boolean inMemory, boolean reuseExisting) {
        boolean success = false;
        sizeInBytes = size;
        this.inMemory = inMemory;
        this.journalPath = journalPath;

        if (inMemory) {
            success = createIndexJournalBB();
        } else {
            success = createIndexJournalMBB(reuseExisting);
        }

        if (success) {
            totalBuckets = (int) (sizeInBytes / INDEX_ENTRY_SIZE_BYTES);
            bucketsFree = (int) (sizeInBytes / INDEX_ENTRY_SIZE_BYTES);
            bucketsUsed = 0;
        }
    }

    protected boolean createIndexJournalBB() {
        try {
            indexBuffer = ByteBuffer.allocateDirect((int) sizeInBytes);
            indexCurrentEnd = indexBuffer.position();
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception", e);
        }

        return false;
    }

    protected boolean createIndexJournalMBB(boolean reuseExisting) {
        try {
            journalPath += "Index";

            // If the journal file already exists, rename it unless we're
            // supposed to reuse the existing file and its contents
            boolean fileExists = false;
            try {
                File file = new File(journalPath);
                fileExists = file.exists();
                if (fileExists && !reuseExisting) {
                    File newFile = new File(journalPath + "_prev");
                    logger.info("Moving journal " + journalPath + " to " + newFile.getName());
                    file.renameTo(newFile);
                }
            } catch (Exception e) {
            }

            indexFile = new RandomAccessFile(journalPath, "rw");
            if (fileExists && reuseExisting) {
                // Existing file, so use its existing length
                sizeInBytes = indexFile.length();
            } else {
                // New file, set its length
                indexFile.setLength(sizeInBytes);
            }

            indexChannel = indexFile.getChannel();
            indexBuffer = indexChannel.map(FileChannel.MapMode.READ_WRITE, 0, sizeInBytes);
            indexCurrentEnd = indexBuffer.position();

            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception", e);
        }

        return false;
    }

    @Override
    public void reset() {
        try {
            indexBuffer.clear();
            indexBuffer.limit(0);

            if (inMemory) {
                indexBuffer = ByteBuffer.allocateDirect(0);
            } else {
                indexChannel.truncate(0);
                indexChannel.close();
                indexFile.close();
                File f = new File(journalPath);
                f.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected int getHashBucket(int hash) {
        return _getHashBucket(hash);
    }

    protected int getHashBucket(String key) {
        int hash = key.hashCode();
        return _getHashBucket(hash);
    }

    protected int _getHashBucket(int hash) {
        hash = hash ^ (hash >>> 16);
        int buckets = (int) (sizeInBytes / INDEX_ENTRY_SIZE_BYTES);
        int bucket = Math.max(1, Math.abs(hash) % (buckets - 1));
        return bucket * INDEX_ENTRY_SIZE_BYTES;
    }

    protected boolean enlargeIndex() {
        try {
            // Hold a reference to the original buffer to copy its contents
            ByteBuffer oldBuffer = indexBuffer;

            if (inMemory) {
                logger.log(Level.INFO, "Expanding in-memory index...");
                sizeInBytes += (PAGE_SIZE * SIZE_FACTOR);
                createIndexJournalBB();
            } else {
                logger.log(Level.INFO, "Expanding persisted index...");
                ((MappedByteBuffer) indexBuffer).force();
                indexFile.setLength(sizeInBytes + (PAGE_SIZE * SIZE_FACTOR));
                indexChannel = indexFile.getChannel();
                sizeInBytes = indexChannel.size();
                indexBuffer = indexChannel.map(
                        FileChannel.MapMode.READ_WRITE, 0, sizeInBytes);
            }

            // Re-hash the index
            //
            collisions = 0;
            bucketsUsed = 0;
            oldBuffer.position(INDEX_ENTRY_SIZE_BYTES);
            int buckets = (oldBuffer.capacity() / INDEX_ENTRY_SIZE_BYTES);
            for (int i = 1; i <= buckets; i++) {
                byte occupied = oldBuffer.get();
                if (occupied > 0) {
                    int keyHash = oldBuffer.getInt();
                    byte[] fixedKeyBytes = null;
                    if (KEY_SIZE > 0) {
                        fixedKeyBytes = new byte[KEY_SIZE];
                        oldBuffer.get(fixedKeyBytes);
                    }
                    Long location = oldBuffer.getLong();
                    putInternal(fixedKeyBytes, keyHash, occupied, location);
                } else {
                    // Bucket unocuppied, move to the next one
                    oldBuffer.position(i * INDEX_ENTRY_SIZE_BYTES);
                }
            }

            totalBuckets = (int) (sizeInBytes / INDEX_ENTRY_SIZE_BYTES);
            bucketsFree = totalBuckets - bucketsUsed;
            logger.log(Level.INFO, "Done!");

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    protected int findBucket(Integer hashcode, int offset, boolean mustFind) {
        boolean found = false;
        byte occupied = 1;
        while (occupied > 0 && !found) {
            int keyHash = indexBuffer.getInt();
            if (keyHash == hashcode) {
                if (KEY_SIZE > 0) {
                    indexBuffer.position(
                            offset + 1 + Integer.BYTES + KEY_SIZE);
                }
                found = true;
                break;
            } else {
                // Check for rollover past the end of the table
                offset += INDEX_ENTRY_SIZE_BYTES;
                if (offset >= (sizeInBytes - INDEX_ENTRY_SIZE_BYTES)) {
                    // Wrap to the beginning, skipping the first slot
                    // since it's reserved for the first record pointer
                    offset = INDEX_ENTRY_SIZE_BYTES;
                }

                // Skip to the next bucket
                indexBuffer.position(offset);
                occupied = indexBuffer.get();
            }
        }

        // return if the key was found in the index
        if (!found && mustFind) {
            return -1;
        }
        return offset;
    }

    protected boolean putInternal(byte[] fixedKeyBytes, Integer hashcode,
                                  byte keyLength, Long value) {
        int offset = getHashBucket(hashcode);
        indexBuffer.position(offset);
        indexBuffer.mark();
        byte occupied = indexBuffer.get();
        if (occupied == 0) {
            // found a free slot, go back to the beginning of it
            indexBuffer.reset();
        } else {
            collisions++;

            // When there's a collision, walk the table until a
            // free slot is found
            offset = findBucket(hashcode, offset, false);

            // found a free slot, seek to it
            indexBuffer.position(offset);
        }

        // Write the data
        //
        indexBuffer.put(keyLength);
        indexBuffer.putInt(hashcode); // hashcode is faster for resolving collisions then comparing strings
        if (KEY_SIZE > 0 && fixedKeyBytes != null && fixedKeyBytes.length > 0) {
            // Make sure we copy *at most* KEY_SIZE bytes for the key
            indexBuffer.put(fixedKeyBytes,
                    0, Math.min(KEY_SIZE, fixedKeyBytes.length));
        }
        indexBuffer.putLong(value); // indexed record location

        bucketsUsed++;

        return true;
    }

    @Override
    public boolean put(String key, Long value) {
        //
        // Entry:
        // 1 (byte)  key length
        // 4 (int)   key hashcode
        // 0 (bytes) key size
        // 8 (long)  record location
        //
        try {
            // Check load to see if Index needs to be enlarged
            //
            if (getLoad() > LOAD_THRESHOLD) {
                enlargeIndex();
            }

            byte keylen = (byte) key.length();
            byte[] fixedKeyBytes = null;
            if (KEY_SIZE > 0) {
                fixedKeyBytes = new byte[KEY_SIZE];
                System.arraycopy(key.getBytes(),
                        0, fixedKeyBytes,
                        0, Math.min(KEY_SIZE, keylen));
            }

            return putInternal(fixedKeyBytes, key.hashCode(), keylen, value);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    protected int getHashBucketOffset(String key) {
        int offset = -1;

        try {
            offset = getHashBucket(key.hashCode());
            indexBuffer.position(offset);
            byte occupied = indexBuffer.get();
            if (occupied > 0) {
                offset = findBucket(key.hashCode(), offset, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return offset;
    }

    @Override
    public Long get(String key) {
        int offset = getHashBucketOffset(key);
        if (offset == -1) {
            // key not found
            return -1L;
        }

        // Return the location of the data record
        return indexBuffer.getLong();
    }

    @Override
    public void remove(String key) {
        int offset = getHashBucketOffset(key);
        if (offset == -1) {
            // key not found
            return;
        }

        offset = findBucket(key.hashCode(), offset, true);
        if (offset != -1) {
            // Simply zero out the occupied slot, but need to rewind first
            int currPos = indexBuffer.position();
            currPos -= (Integer.BYTES + 1);
            indexBuffer.position(currPos);
            indexBuffer.put((byte) 0);
        }
    }

    @Override
    public int getCollisions() {
        return collisions;
    }

    @Override
    public void outputStats() {
        System.out.println("Index " + journalPath + " Stats:");
        System.out.println(" -size: " + size());
        System.out.println(" -load: " + getLoad());
        System.out.println(" -entries: " + entries());
        System.out.println(" -capacity: " + capacity());
        System.out.println(" -available: " + available());
        System.out.println(" -collisions: " + getCollisions());

    }

    public long size() {
        return sizeInBytes;
    }

    public int entries() {
        return bucketsUsed;
    }

    public int capacity() {
        return totalBuckets;
    }

    public int available() {
        return capacity() - entries();
    }

    public int getLoad() {
        int used = entries();
        int capac = capacity();
        float f = (float) used / (float) capac;
        int load = (int) (f * 100);
        return load; // percentage
    }
}
