package io.bitsquare.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utilities
{
    private static final Logger log = LoggerFactory.getLogger(Utilities.class);
    private static long lastTimeStamp = System.currentTimeMillis();

    public static String objectToJson(Object object)
    {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).setPrettyPrinting().create();
        return gson.toJson(object);
    }

    public static <T> T jsonToObject(String jsonString, Class<T> classOfT)
    {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).setPrettyPrinting().create();
        return gson.fromJson(jsonString, classOfT);
    }


    public static Object deserializeHexStringToObject(String serializedHexString)
    {
        Object result = null;
        try
        {
            ByteArrayInputStream byteInputStream =
                    new ByteArrayInputStream(com.google.bitcoin.core.Utils.parseAsHexOrBase58(serializedHexString));

            try (ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream))
            {
                result = objectInputStream.readObject();
            } catch (ClassNotFoundException e)
            {
                e.printStackTrace();
            } finally
            {
                byteInputStream.close();

            }

        } catch (IOException i)
        {
            i.printStackTrace();
        }
        return result;
    }


    public static String serializeObjectToHexString(Serializable serializable)
    {
        String result = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(serializable);

            result = com.google.bitcoin.core.Utils.HEX.encode(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.close();
            objectOutputStream.close();

        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static void printElapsedTime(String msg)
    {
        if (!msg.isEmpty())
        {
            msg += " / ";
        }
        long timeStamp = System.currentTimeMillis();
        log.debug(msg + "Elapsed: " + String.valueOf(timeStamp - lastTimeStamp));
        lastTimeStamp = timeStamp;
    }

    public static void printElapsedTime()
    {
        printElapsedTime("");
    }


    public static void openURL(String url) throws Exception
    {
        Desktop.getDesktop().browse(new URI(url));
    }


    public static Object copy(Serializable orig)
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
        } catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        return obj;
    }

    @SuppressWarnings("SameParameterValue")

    public static AnimationTimer setTimeout(int delay, Function<AnimationTimer, Void> callback)
    {
        AnimationTimer animationTimer = new AnimationTimer()
        {
            final long lastTimeStamp = System.currentTimeMillis();

            @Override
            public void handle(long arg0)
            {
                if (System.currentTimeMillis() > delay + lastTimeStamp)
                {
                    callback.apply(this);
                    this.stop();
                }
            }
        };
        animationTimer.start();
        return animationTimer;
    }

    @SuppressWarnings("SameParameterValue")

    public static AnimationTimer setInterval(int delay, Function<AnimationTimer, Void> callback)
    {
        AnimationTimer animationTimer = new AnimationTimer()
        {
            long lastTimeStamp = System.currentTimeMillis();

            @Override
            public void handle(long arg0)
            {
                if (System.currentTimeMillis() > delay + lastTimeStamp)
                {
                    lastTimeStamp = System.currentTimeMillis();
                    callback.apply(this);
                }
            }
        };
        animationTimer.start();
        return animationTimer;
    }
}
