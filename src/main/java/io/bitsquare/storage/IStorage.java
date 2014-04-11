package io.bitsquare.storage;

public interface IStorage
{
    void write(String key, Object value);

    Object read(String key);
}
