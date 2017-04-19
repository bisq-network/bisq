package io.bisq.common.util;

import lombok.Getter;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class FunctionalReadWriteLock {
    @Getter
    private final Lock readLock;
    @Getter
    private final Lock writeLock;

    public FunctionalReadWriteLock(boolean isFair) {
        this(new ReentrantReadWriteLock(isFair));
    }

    public FunctionalReadWriteLock(ReadWriteLock lock) {
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    public <T> T read(Supplier<T> block) {
        readLock.lock();
        try {
            return block.get();
        } finally {
            readLock.unlock();
        }
    }

    public void read(Runnable block) {
        readLock.lock();
        try {
            block.run();
        } finally {
            readLock.unlock();
        }
    }

    public void write1(Callable block) throws Exception {
        readLock.lock();
        try {
            block.call();
        } finally {
            readLock.unlock();
        }
    }

    public <T> T write(Supplier<T> block) {
        writeLock.lock();
        try {
            return block.get();
        } finally {
            writeLock.unlock();
        }
    }

    public void write(Runnable block) {
        writeLock.lock();
        try {
            block.run();
        } finally {
            writeLock.unlock();
        }
    }

    public void write2(Callable block) throws Exception {
        writeLock.lock();
        try {
            block.call();
        } finally {
            writeLock.unlock();
        }
    }
}
