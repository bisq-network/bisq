/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.setup;

import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.ChainFileLockedException;
import org.bitcoinj.utils.*;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.locks.*;

import static com.google.common.base.Preconditions.*;

/**
 * NonMMappedSPVBlockStore is like SPVBlockStore but it does not use a memory mapped file
 * to access the file systen. Memory mapped file has problems on windows on restore from
 * seed because of problems releasing control of mmapped files.
 * We proposed a similar solution on bitcoinj upstream, so we hopefully will be able to
 * remove this class and use the upstream version.
 * See:
 * <a href="http://www.mapdb.org/blog/mmap_files_alloc_and_jvm_crash/">Mapdb on mmapped files</a>
 * <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4715154">Java bug 1</a>
 * <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4724038">Java bug 2</a>
 * <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6359560">Java bug 3</a>
 */
public class NonMMappedSPVBlockStore implements BlockStore {
    private static final Logger log = LoggerFactory.getLogger(NonMMappedSPVBlockStore.class);

    /** The default number of headers that will be stored in the ring buffer. */
    public static final int DEFAULT_NUM_HEADERS = 5000;
    public static final String HEADER_MAGIC = "SPVB";

    protected int numHeaders;
    protected NetworkParameters params;

    protected ReentrantLock lock = Threading.lock("SPVBlockStore");

    protected LinkedHashMap<Sha256Hash, StoredBlock> blockCache = new LinkedHashMap<Sha256Hash, StoredBlock>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, StoredBlock> entry) {
            return size() > 2050;  // Slightly more than the difficulty transition period.
        }
    };
    // Use a separate cache to track get() misses. This is to efficiently handle the case of an unconnected block
    // during chain download. Each new block will do a get() on the unconnected block so if we haven't seen it yet we
    // must efficiently respond.
    //
    // We don't care about the value in this cache. It is always notFoundMarker. Unfortunately LinkedHashSet does not
    // provide the removeEldestEntry control.
    protected static final Object notFoundMarker = new Object();
    protected LinkedHashMap<Sha256Hash, Object> notFoundCache = new LinkedHashMap<Sha256Hash, Object>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, Object> entry) {
            return size() > 100;  // This was chosen arbitrarily.
        }
    };
    // Used to stop other applications/processes from opening the store.
    protected FileLock fileLock = null;
    protected RandomAccessFile randomAccessFile = null;
    private final String fileAbsolutePath;

    /**
     * Creates and initializes an SPV block store. Will create the given file if it's missing. This operation
     * will block on disk.
     */
    public NonMMappedSPVBlockStore(NetworkParameters params, File file) throws BlockStoreException {
        checkNotNull(file);
        fileAbsolutePath = file.getAbsolutePath();
        this.params = checkNotNull(params);
        try {
            this.numHeaders = DEFAULT_NUM_HEADERS;
            boolean exists = file.exists();
            // Set up the backing file.
            randomAccessFile = new RandomAccessFile(file, "rw");
            long fileSize = getFileSize();
            if (!exists) {
                log.info("Creating new SPV block chain file " + file);
                randomAccessFile.setLength(fileSize);
            } else if (randomAccessFile.length() != fileSize) {
                throw new BlockStoreException("File size on disk does not match expected size: " +
                        randomAccessFile.length() + " vs " + fileSize);
            }

            FileChannel channel = randomAccessFile.getChannel();
            fileLock = channel.tryLock();
            if (fileLock == null)
                throw new ChainFileLockedException("Store file is already locked by another process");

            // Check or initialize the header bytes to ensure we don't try to open some random file.
            byte[] header;
            if (exists) {
                header = new byte[4];
                randomAccessFile.read(header);
                if (!new String(header, "US-ASCII").equals(HEADER_MAGIC))
                    throw new BlockStoreException("Header bytes do not equal " + HEADER_MAGIC);
            } else {
                initNewStore(params);
            }
        } catch (Exception e) {
            try {
                if (randomAccessFile != null) randomAccessFile.close();
            } catch (IOException e2) {
                throw new BlockStoreException(e2);
            }
            throw new BlockStoreException(e);
        }
    }

    private void initNewStore(NetworkParameters params) throws Exception {
        byte[] header;
        header = HEADER_MAGIC.getBytes("US-ASCII");
        randomAccessFile.write(header);
        // Insert the genesis block.
        lock.lock();
        try {
            setRingCursor(randomAccessFile, FILE_PROLOGUE_BYTES);
        } finally {
            lock.unlock();
        }
        Block genesis = params.getGenesisBlock().cloneAsHeader();
        StoredBlock storedGenesis = new StoredBlock(genesis, genesis.getWork(), 0);
        put(storedGenesis);
        setChainHead(storedGenesis);
    }

    /** Returns the size in bytes of the file that is used to store the chain with the current parameters. */
    public final int getFileSize() {
        return RECORD_SIZE * numHeaders + FILE_PROLOGUE_BYTES /* extra kilobyte for stuff */;
    }

    @Override
    public void put(StoredBlock block) throws BlockStoreException {
        final RandomAccessFile randomAccessFile = this.randomAccessFile;
        if (randomAccessFile == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            int cursor = getRingCursor(randomAccessFile);
            if (cursor == getFileSize()) {
                // Wrapped around.
                cursor = FILE_PROLOGUE_BYTES;
            }
            randomAccessFile.seek(cursor);
            Sha256Hash hash = block.getHeader().getHash();
            notFoundCache.remove(hash);
            randomAccessFile.write(hash.getBytes());
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            block.serializeCompact(buffer);
            buffer.flip();
            FileChannel channel = randomAccessFile.getChannel();
            channel.write(buffer);
            setRingCursor(randomAccessFile, (int) randomAccessFile.getFilePointer());
            blockCache.put(hash, block);
        } catch (IOException ioException) {
            throw new BlockStoreException(ioException);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Nullable
    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        final RandomAccessFile randomAccessFile = this.randomAccessFile;
        if (randomAccessFile == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            StoredBlock cacheHit = blockCache.get(hash);
            if (cacheHit != null)
                return cacheHit;
            if (notFoundCache.get(hash) != null)
                return null;

            // Starting from the current tip of the ring work backwards until we have either found the block or
            // wrapped around.
            int cursor = getRingCursor(randomAccessFile);
            final int startingPoint = cursor;
            final int fileSize = getFileSize();
            final byte[] targetHashBytes = hash.getBytes();
            byte[] scratch = new byte[32];
            do {
                cursor -= RECORD_SIZE;
                if (cursor < FILE_PROLOGUE_BYTES) {
                    // We hit the start, so wrap around.
                    cursor = fileSize - RECORD_SIZE;
                }
                // Cursor is now at the start of the next record to check, so read the hash and compare it.
                randomAccessFile.seek(cursor);
                randomAccessFile.read(scratch);
                if (Arrays.equals(scratch, targetHashBytes)) {
                    // Found the target.
                    ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
                    FileChannel channel = randomAccessFile.getChannel();
                    channel.read(buffer);
                    buffer.flip();
                    StoredBlock storedBlock = StoredBlock.deserializeCompact(params, buffer);
                    blockCache.put(hash, storedBlock);
                    return storedBlock;
                }
            } while (cursor != startingPoint);
            // Not found.
            notFoundCache.put(hash, notFoundMarker);
            return null;
        } catch (ProtocolException e) {
            throw new RuntimeException(e);  // Cannot happen.
        } catch (IOException ioException) {
            throw new BlockStoreException(ioException);
        } finally { lock.unlock(); }
    }

    protected StoredBlock lastChainHead = null;

    @Override
    public StoredBlock getChainHead() throws BlockStoreException {
        final RandomAccessFile randomAccessFile = this.randomAccessFile;
        if (randomAccessFile == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            if (lastChainHead == null) {
                byte[] headHash = new byte[32];
                randomAccessFile.seek(8);
                randomAccessFile.read(headHash);
                Sha256Hash hash = Sha256Hash.wrap(headHash);
                StoredBlock block = get(hash);
                if (block == null) 
                    throw new BlockStoreException("Corrupted block store: could not find chain head: " + hash 
                                                          +"\nFile path: "+ fileAbsolutePath);
                lastChainHead = block;
            }
            return lastChainHead;
        } catch (IOException ioException) {
            throw new BlockStoreException(ioException);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        final RandomAccessFile randomAccessFile = this.randomAccessFile;
        if (randomAccessFile == null) throw new BlockStoreException("Store closed");

        lock.lock();
        try {
            lastChainHead = chainHead;
            byte[] headHash = chainHead.getHeader().getHash().getBytes();
            randomAccessFile.seek(8);
            randomAccessFile.write(headHash);
        } catch (IOException ioException) {
            throw new BlockStoreException(ioException);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws BlockStoreException {
        try {
            randomAccessFile.close();
            randomAccessFile = null; // Set to null to avoid trying to use a closed file.
        } catch (IOException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }

    protected static final int RECORD_SIZE = 32 /* hash */ + StoredBlock.COMPACT_SERIALIZED_SIZE;

    // File format:
    //   4 header bytes = "SPVB"
    //   4 cursor bytes, which indicate the offset from the first kb where the next block header should be written.
    //   32 bytes for the hash of the chain head
    //
    // For each header (128 bytes)
    //   32 bytes hash of the header
    //   12 bytes of chain work
    //    4 bytes of height
    //   80 bytes of block header data
    protected static final int FILE_PROLOGUE_BYTES = 1024;

    /** Returns the offset from the file start where the latest block should be written (end of prev block). */
    private int getRingCursor(RandomAccessFile randomAccessFile) throws IOException {
        long filePointer = randomAccessFile.getFilePointer();
        randomAccessFile.seek(4);
        int c = randomAccessFile.readInt();
        randomAccessFile.seek(filePointer);
        checkState(c >= FILE_PROLOGUE_BYTES, "Integer overflow");
        return c;
    }

    private void setRingCursor(RandomAccessFile randomAccessFile, int newCursor) throws IOException {
        checkArgument(newCursor >= 0);
        long filePointer = randomAccessFile.getFilePointer();
        randomAccessFile.seek(4);
        randomAccessFile.writeInt(newCursor);
        randomAccessFile.seek(filePointer);
    }

}
