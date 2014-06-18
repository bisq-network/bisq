package io.bitsquare.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import javafx.animation.AnimationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.util.function.Function;

public class Utilities
{
    private static final Logger log = LoggerFactory.getLogger(Utilities.class);

    public static String getRootDir()
    {
        return Utilities.class.getProtectionDomain().getCodeSource().getLocation().getFile() + "/../";
    }


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
            ByteInputStream byteInputStream = new ByteInputStream();
            byteInputStream.setBuf(com.google.bitcoin.core.Utils.parseAsHexOrBase58(serializedHexString));
            ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);

            try
            {
                result = objectInputStream.readObject();
            } catch (ClassNotFoundException e)
            {
                e.printStackTrace();
            } finally
            {
                byteInputStream.close();
                objectInputStream.close();
            }

        } catch (IOException i)
        {
            i.printStackTrace();
        }
        return result;
    }

    public static String serializeObjectToHexString(Object serializable)
    {
        String result = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
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

    private static long lastTimeStamp = System.currentTimeMillis();

    public static void printElapsedTime(String msg)
    {
        if (msg.length() > 0)
            msg += " / ";
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


   /* public static ArrayList<BankAccountTypeInfo.BankAccountType> getAllBankAccountTypeEnums()
    {
        ArrayList<BankAccountTypeInfo.BankAccountType> bankAccountTypes = new ArrayList<>();
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.SEPA);
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.WIRE);
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.INTERNATIONAL);
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.OK_PAY);
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.NET_TELLER);
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.PERFECT_MONEY);
        bankAccountTypes.add(BankAccountTypeInfo.BankAccountType.OTHER);
        return bankAccountTypes;
    }  */

    public static AnimationTimer setTimeout(int delay, Function<AnimationTimer, Void> callback)
    {
        AnimationTimer animationTimer = new AnimationTimer()
        {
            long lastTimeStamp = System.currentTimeMillis();

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
