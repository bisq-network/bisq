package io.bitsquare.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    @Nullable
    public static Object deserializeHexStringToObject(String serializedHexString)
    {
        @Nullable Object result = null;
        try
        {
            @NotNull ByteInputStream byteInputStream = new ByteInputStream();
            byteInputStream.setBuf(com.google.bitcoin.core.Utils.parseAsHexOrBase58(serializedHexString));

            try (@NotNull ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream))
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

    @Nullable
    public static String serializeObjectToHexString(Serializable serializable)
    {
        @Nullable String result = null;
        @NotNull ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try
        {
            @NotNull ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(serializable);

            result = com.google.bitcoin.core.Utils.bytesToHexString(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.close();
            objectOutputStream.close();

        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static void printElapsedTime(@NotNull String msg)
    {
        if (!msg.isEmpty())
            msg += " / ";
        long timeStamp = System.currentTimeMillis();
        log.debug(msg + "Elapsed: " + String.valueOf(timeStamp - lastTimeStamp));
        lastTimeStamp = timeStamp;
    }

    public static void printElapsedTime()
    {
        printElapsedTime("");
    }


    public static void openURL(@NotNull String url) throws Exception
    {
        Desktop.getDesktop().browse(new URI(url));
    }


    @Nullable
    public static Object copy(Serializable orig)
    {
        @Nullable Object obj = null;
        try
        {
            // Write the object out to a byte array
            @NotNull ByteArrayOutputStream bos = new ByteArrayOutputStream();
            @NotNull ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(orig);
            out.flush();
            out.close();

            // Make an input stream from the byte array and read
            // a copy of the object back in.
            @NotNull ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
            obj = in.readObject();
        } catch (@NotNull IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        return obj;
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    public static AnimationTimer setTimeout(int delay, @NotNull Function<AnimationTimer, Void> callback)
    {
        @NotNull AnimationTimer animationTimer = new AnimationTimer()
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
    @NotNull
    public static AnimationTimer setInterval(int delay, @NotNull Function<AnimationTimer, Void> callback)
    {
        @NotNull AnimationTimer animationTimer = new AnimationTimer()
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
