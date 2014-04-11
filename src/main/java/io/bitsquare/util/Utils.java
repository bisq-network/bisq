package io.bitsquare.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;

public class Utils
{
    public static String convertToJson(Object object)
    {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
        return gson.toJson(object);
    }

    public static Object copy(Object orig)
    {
        Object obj = null;
        try
        {
            // Write the object out to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(orig);
            out.flush();
            out.close();

            // Make an input stream from the byte array and read
            // a copy of the object back in.
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
            obj = in.readObject();
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ClassNotFoundException cnfe)
        {
            cnfe.printStackTrace();
        }
        return obj;
    }

}
