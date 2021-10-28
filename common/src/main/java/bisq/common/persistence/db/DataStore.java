package bisq.common.persistence.db;

public interface DataStore {
    public enum Storage {
        IN_MEMORY,
        PERSISTED
    }

    public static final byte INACTIVE_RECORD = 0;
    public static final byte ACTIVE_RECORD = 1;

    public static final byte EMPTY_RECORD_TYPE = -1;
    public static final byte OBJ_RECORD_TYPE = 1;
    public static final byte TEXT_RECORD_TYPE = 2;
    public static final byte LONG_RECORD_TYPE = 3;
    public static final byte INT_RECORD_TYPE = 4;
    public static final byte DOUBLE_RECORD_TYPE = 5;
    public static final byte FLOAT_RECORD_TYPE = 6;
    public static final byte SHORT_RECORD_TYPE = 7;
    public static final byte CHAR_RECORD_TYPE = 8;
    public static final byte BYTEARRAY_RECORD_TYPE = 9;


    // Get Journal stats
    public long getRecordCount();

    public long getEmptyCount();

    public String getName();

    public String getFolder();

    public long getFilesize();

    public boolean putInteger(String key, Integer val);

    public Integer getInteger(String key);

    public boolean putShort(String key, Short val);

    public Short getShort(String key);

    public boolean putLong(String key, Long val);

    public Long getLong(String key);

    public boolean putFloat(String key, Float val);

    public Float getFloat(String key);

    public boolean putDouble(String key, Double val);

    public Double getDouble(String key);

    public boolean putString(String key, String val);

    public String getString(String key);

    public boolean putObject(String key, Object msg);

    public Object getObject(String key); // key is the object ID

    boolean putBytes(String key, byte[] bytes);

    byte[] getBytes(String key);

    public boolean putChar(String key, char val);

    public char getChar(String key);

    public boolean remove(String key); // ID is the object hashs

    public Object iterateStart();

    public Object iterateNext();

    public void delete();
}
