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

import org.bitcoinj.core.Block;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.ChainFileLockedException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.Threading;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * ClearableSPVBlockStore fixes some issues on SPVBlockStore that made
 * "restore from seed" to fail on windows:
 * - It allows the blockstore file to be cleared (as a replacement for deleting
 * and recreating the file).
 * - It overrides close() to avoid invoking WindowsMMapHack on windows
 * which fails on jdk10.
 * See https://github.com/bisq-network/bisq/issues/2402
 */
public class ClearableSPVBlockStore extends SPVBlockStore {

    /**
     * Creates and initializes an SPV block store. Will create the given file if it's missing. This operation
     * will block on disk.
     * @param params
     * @param file
     */
    public ClearableSPVBlockStore(NetworkParameters params, File file) throws BlockStoreException {
        super(params, file);
    }

    public void clear() throws Exception {
        lock.lock();
        try {
            // Clear caches
            blockCache.clear();
            notFoundCache.clear();
            // Clear file content
            buffer.position(0);
            long fileLength = randomAccessFile.length();
            for (int i = 0; i < fileLength; i++) {
                buffer.put((byte)0);
            }
            // Initialize store again
            buffer.position(0);
            initNewStoreCopy(params);
        } finally { lock.unlock(); }
    }

    // Copy of SPVBlockStore.initNewStore() that can not be used here because it is private
    private void initNewStoreCopy(NetworkParameters params) throws Exception {
        byte[] header;
        header = HEADER_MAGIC.getBytes("US-ASCII");
        buffer.put(header);
        // Insert the genesis block.
        lock.lock();
        try {
            setRingCursorCopy(buffer, FILE_PROLOGUE_BYTES);
        } finally {
            lock.unlock();
        }
        Block genesis = params.getGenesisBlock().cloneAsHeader();
        StoredBlock storedGenesis = new StoredBlock(genesis, genesis.getWork(), 0);
        put(storedGenesis);
        setChainHead(storedGenesis);
    }

    // Copy of SPVBlockStore.setRingCursor() that can not be used here because it is private
    private void setRingCursorCopy(ByteBuffer buffer, int newCursor) {
        checkArgument(newCursor >= 0);
        buffer.putInt(4, newCursor);
    }

    // Override close() to avoid invoking WindowsMMapHack on windows which fails on jdk10
    @Override
    public void close() throws BlockStoreException {
        try {
            buffer.force();
            buffer = null;  // Allow it to be GCd and the underlying file mapping to go away.
            randomAccessFile.close();
        } catch (IOException e) {
            throw new BlockStoreException(e);
        }
    }
}
