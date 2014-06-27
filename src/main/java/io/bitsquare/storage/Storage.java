package io.bitsquare.storage;

import io.bitsquare.BitSquare;
import io.bitsquare.util.Utilities;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple storage solution for serialized data
 */
public class Storage
{
    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    //TODO save in users preferences location
    private final String preferencesFileName = BitSquare.ID + "_pref" + ".ser";
    private final String storageFile;
    private Map<String, Object> dict;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Storage()
    {
        storageFile = Utilities.getRootDir() + preferencesFileName;

        dict = readDataVO();
        if (dict == null)
        {
            dict = new HashMap<>();
            writeDataVO(dict);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void write(String key, Object value)
    {
        //log.info("Write object with key = " + key + " / value = " + value);
        dict.put(key, value);
        writeDataVO(dict);
    }

    public Object read(String key)
    {
        dict = readDataVO();
        return dict.get(key);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void writeDataVO(Map<String, Object> dict)
    {
        try
        {
            FileOutputStream fileOut = new FileOutputStream(storageFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(dict);
            out.close();
            fileOut.close();
        } catch (IOException i)
        {
            i.printStackTrace();
        }
    }

    private Map<String, Object> readDataVO()
    {
        Map<String, Object> dict = null;
        File file = new File(storageFile);
        if (file.exists())
        {
            try
            {
                FileInputStream fileIn = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                Object object = in.readObject();
                if (object instanceof Map)
                    dict = (Map<String, Object>) object;
                in.close();
                fileIn.close();
            } catch (IOException | ClassNotFoundException i)
            {
                i.printStackTrace();
            }
        }
        return dict;
    }
}
