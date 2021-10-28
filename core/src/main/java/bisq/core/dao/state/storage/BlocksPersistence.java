/*
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

package bisq.core.dao.state.storage;

import bisq.common.file.FileUtil;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import protobuf.BaseBlock;

import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class BlocksPersistence {
    // 10000->Writing 130014 blocks took 1658 msec
    // 1000-> Writing 130014 blocks took 1685 msec  Mapping blocks from DaoStateStore took 2250 ms
    public static final int BUCKET_SIZE = 1000; // results in about 1 MB files and about 1 new file per week

    private final File storageDir;
    private final String fileName;
    private final PersistenceProtoResolver persistenceProtoResolver;
    private Path usedTempFilePath;

    public BlocksPersistence(File storageDir, String fileName, PersistenceProtoResolver persistenceProtoResolver) {
        this.storageDir = storageDir;
        this.fileName = fileName;
        this.persistenceProtoResolver = persistenceProtoResolver;

       /* if (!storageDir.exists()) {
            storageDir.mkdir();
        }*/
    }

    public void writeBlocks(List<BaseBlock> protobufBlocks) {
        long ts = System.currentTimeMillis();
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }
        List<BaseBlock> temp = new ArrayList<>();
        int bucketIndex = 0;
        for (BaseBlock block : protobufBlocks) {
            temp.add(block);

            int height = block.getHeight();
            bucketIndex = height / BUCKET_SIZE;
            int remainder = height % BUCKET_SIZE;
            boolean isLastBucketItem = remainder == 0;
            if (isLastBucketItem) {
                int first = bucketIndex * BUCKET_SIZE - BUCKET_SIZE + 1;
                int last = bucketIndex * BUCKET_SIZE;
                File storageFile = new File(storageDir, fileName + "_" + first + "-" + last);
                //  log.error("addAll height={} items={}", height, temp.stream().map(e -> e.getHeight() + ", ").collect(Collectors.toList()));
                writeToDisk(storageFile, new BsqBlockStore(temp), null);
                temp = new ArrayList<>();
            }
        }
        if (!temp.isEmpty()) {
            bucketIndex++;
            int first = bucketIndex * BUCKET_SIZE - BUCKET_SIZE + 1;
            int last = bucketIndex * BUCKET_SIZE;
            File storageFile = new File(storageDir, fileName + "_" + first + "-" + last);
            // log.error("items={}", temp.stream().map(e -> e.getHeight()).collect(Collectors.toList()));
            writeToDisk(storageFile, new BsqBlockStore(temp), null);

        }
        log.error("Write {} blocks to disk took {} msec", protobufBlocks.size(), System.currentTimeMillis() - ts);
    }

    public void removeBlocksDirectory() {
        if (storageDir.exists()) {
            try {
                FileUtil.deleteDirectory(storageDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<BaseBlock> readBlocks(int from, int to) {
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }

        //  log.error("getBlocks {}-{}", from, to);
        long ts = System.currentTimeMillis();
        // from = Math.max(571747, from);
        List<BaseBlock> buckets = new ArrayList<>();
        int start = from / BUCKET_SIZE + 1;
        int end = to / BUCKET_SIZE + 1;
        for (int bucketIndex = start; bucketIndex <= end; bucketIndex++) {
            List<BaseBlock> bucket = readBucket(bucketIndex);
            //  log.error("read bucketIndex {},  items={}", bucketIndex, bucket.stream().map(e -> e.getHeight() + ", ").collect(Collectors.toList()));
            buckets.addAll(bucket);
        }
        log.error("Reading {} blocks took {} msec", buckets.size(), System.currentTimeMillis() - ts);
        // System.exit(0);
        return buckets;
    }


    private List<BaseBlock> readBucket(int bucketIndex) {
        int first = bucketIndex * BUCKET_SIZE - BUCKET_SIZE + 1;
        int last = bucketIndex * BUCKET_SIZE;

       /* int first = bucketIndex * BUCKET_SIZE + 1;
        int last = first + BUCKET_SIZE - 1;*/
        String child = fileName + "_" + first + "-" + last;
        // log.error("getBlocksOfBucket {}", child);
        File storageFile = new File(storageDir, child);
        if (!storageFile.exists()) {
            // log.error("storageFile not existing {}", storageFile.getName());
            return new ArrayList<>();
        }

        try (FileInputStream fileInputStream = new FileInputStream(storageFile)) {
            protobuf.PersistableEnvelope proto = protobuf.PersistableEnvelope.parseDelimitedFrom(fileInputStream);
            BsqBlockStore bsqBlockStore = (BsqBlockStore) persistenceProtoResolver.fromProto(proto);
            return bsqBlockStore.getBlocksAsProto();
        } catch (Throwable t) {
            log.error("Reading {} failed with {}.", fileName, t.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeToDisk(File storageFile,
                             BsqBlockStore bsqBlockStore,
                             @Nullable Runnable completeHandler) {
        long ts = System.currentTimeMillis();
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        try {
            tempFile = usedTempFilePath != null
                    ? FileUtil.createNewFile(usedTempFilePath)
                    : File.createTempFile("temp_" + fileName, null, storageDir);

            // Don't use a new temp file path each time, as that causes the delete-on-exit hook to leak memory:
            tempFile.deleteOnExit();

            fileOutputStream = new FileOutputStream(tempFile);
            bsqBlockStore.toProtoMessage().writeDelimitedTo(fileOutputStream);

            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            fileOutputStream.flush();
            fileOutputStream.getFD().sync();

            // Close resources before replacing file with temp file because otherwise it causes problems on windows
            // when rename temp file
            fileOutputStream.close();

            FileUtil.renameFile(tempFile, storageFile);
            usedTempFilePath = tempFile.toPath();
        } catch (Throwable t) {
            // If an error occurred, don't attempt to reuse this path again, in case temp file cleanup fails.
            usedTempFilePath = null;
            log.error("Error at saveToFile, storageFile={}", fileName, t);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save. We will delete it now. storageFile={}", fileName);
                if (!tempFile.delete()) {
                    log.error("Cannot delete temp file.");
                }
            }

            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Cannot close resources." + e.getMessage());
            }
            // log.info("Writing the serialized {} completed in {} msec", fileName, System.currentTimeMillis() - ts);
            if (completeHandler != null) {
                completeHandler.run();
            }
        }
    }
}
