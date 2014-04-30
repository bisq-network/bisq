package io.bitsquare.storage;

import io.bitsquare.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple storage solution for serialized data
 */
public class Storage
{

    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    private final String preferencesFileName = "preferences.ser";
    private final String storageFile;
    private DataVO dataVO;

    public Storage()
    {
        storageFile = Storage.class.getProtectionDomain().getCodeSource().getLocation().getFile() + "/" + preferencesFileName;

        dataVO = readDataVO();
        if (dataVO == null)
        {
            dataVO = new DataVO();
            dataVO.dict = new HashMap<String, Object>();
            writeDataVO(dataVO);
        }
    }


    public void updateUserFromStorage(User user)
    {
        User savedUser = (User) read(user.getClass().getName());
        if (savedUser != null)
            user.updateFromStorage(savedUser);
    }

    public void write(String key, Object value)
    {
        //log.info("Write object with key = " + key + " / value = " + value);
        dataVO.dict.put(key, value);
        writeDataVO(dataVO);
    }

    public Object read(String key)
    {
        dataVO = readDataVO();
        Object result = dataVO.dict.get(key);
        //log.info("Read object with key = " + key + " result = " + result);
        return result;
    }

    private void writeDataVO(DataVO dataVO)
    {
        try
        {
            FileOutputStream fileOut = new FileOutputStream(storageFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(dataVO);
            out.close();
            fileOut.close();
        } catch (IOException i)
        {
            i.printStackTrace();
        }
    }

    private DataVO readDataVO()
    {
        DataVO dataVO = null;
        File file = new File(storageFile);
        if (file.exists())
        {
            try
            {
                FileInputStream fileIn = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                dataVO = (DataVO) in.readObject();
                in.close();
                fileIn.close();
            } catch (IOException i)
            {
                i.printStackTrace();
            } catch (ClassNotFoundException c)
            {
                c.printStackTrace();
            }
        }
        return dataVO;
    }


}

class DataVO implements Serializable
{

    private static final long serialVersionUID = -1127046445783201376L;

    public Map<String, Object> dict;
}