/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.common.util;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.*;
import io.bisq.common.crypto.LimitedKeyStrengthException;
import io.bisq.common.io.LookAheadObjectInputStream;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;


/**
 * General utilities
 */
public class Utilities {
    private static final Logger log = LoggerFactory.getLogger(Utilities.class);
    private static long lastTimeStamp = System.currentTimeMillis();
    public static final String LB = System.getProperty("line.separator");

    // TODO check out Jackson lib
    public static String objectToJson(Object object) {
        Gson gson = new GsonBuilder()
                .setExclusionStrategies(new AnnotationExclusionStrategy())
                /*.excludeFieldsWithModifiers(Modifier.TRANSIENT)*/
              /*  .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)*/
                .setPrettyPrinting()
                .create();
        return gson.toJson(object);
    }

    public static ListeningExecutorService getListeningExecutorService(String name,
                                                                       int corePoolSize,
                                                                       int maximumPoolSize,
                                                                       long keepAliveTimeInSec) {
        return MoreExecutors.listeningDecorator(getThreadPoolExecutor(name, corePoolSize, maximumPoolSize, keepAliveTimeInSec));
    }

    public static ThreadPoolExecutor getThreadPoolExecutor(String name,
                                                           int corePoolSize,
                                                           int maximumPoolSize,
                                                           long keepAliveTimeInSec) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(true)
                .build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTimeInSec,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(maximumPoolSize), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler((r, e) -> {
            log.debug("RejectedExecutionHandler called");
        });
        return executor;
    }


    public static ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor(String name,
                                                                             int corePoolSize,
                                                                             int maximumPoolSize,
                                                                             long keepAliveTimeInSec) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(true)
                .setPriority(Thread.MIN_PRIORITY)
                .build();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
        executor.setKeepAliveTime(keepAliveTimeInSec, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        executor.setMaximumPoolSize(maximumPoolSize);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRejectedExecutionHandler((r, e) -> {
            log.debug("RejectedExecutionHandler called");
        });
        return executor;
    }

    public static boolean isUnix() {
        return isOSX() || isLinux() || getOSName().contains("freebsd");
    }

    public static boolean isWindows() {
        return getOSName().contains("win");
    }

    public static boolean isOSX() {
        return getOSName().contains("mac") || getOSName().contains("darwin");
    }

    public static boolean isLinux() {
        return getOSName().contains("linux");
    }

    private static String getOSName() {
        return System.getProperty("os.name").toLowerCase(Locale.US);
    }

    public static String getOSArchitecture() {
        String osArch = System.getProperty("os.arch");
        if (isWindows()) {
            // See: Like always windows needs extra treatment
            // https://stackoverflow.com/questions/20856694/how-to-find-the-os-bit-type
            String arch = System.getenv("PROCESSOR_ARCHITECTURE");
            String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");
            return arch.endsWith("64")
                    || wow64Arch != null && wow64Arch.endsWith("64")
                    ? "64" : "32";
        } else if (osArch.contains("arm")) {
            // armv8 is 64 bit, armv7l is 32 bit
            return osArch.contains("64") || osArch.contains("v8") ? "64" : "32";
        } else if (isLinux()) {
            return osArch.startsWith("i") ? "32" : "64";
        } else {
            return osArch.contains("64") ? "64" : osArch;
        }
    }

    public static void printSysInfo() {
        log.info("os.name: " + System.getProperty("os.name"));
        log.info("os.version: " + System.getProperty("os.version"));
        log.info("os.arch: " + System.getProperty("os.arch"));
        log.info("sun.arch.data.model: " + getJVMArchitecture());
        log.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
        log.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.name", "-") + ")");
    }

    public static String getJVMArchitecture() {
        return System.getProperty("sun.arch.data.model");
    }

    public static boolean isCorrectOSArchitecture() {
        boolean result = getOSArchitecture().endsWith(getJVMArchitecture());
        if (!result) {
            log.warn("System.getProperty(\"os.arch\") " + System.getProperty("os.arch"));
            log.warn("System.getenv(\"ProgramFiles(x86)\") " + System.getenv("ProgramFiles(x86)"));
            log.warn("System.getenv(\"PROCESSOR_ARCHITECTURE\")" + System.getenv("PROCESSOR_ARCHITECTURE"));
            log.warn("System.getenv(\"PROCESSOR_ARCHITEW6432\") " + System.getenv("PROCESSOR_ARCHITEW6432"));
            log.warn("System.getProperty(\"sun.arch.data.model\") " + System.getProperty("sun.arch.data.model"));
        }
        return result;
    }

    public static void openURI(URI uri) throws IOException {
        if (!isLinux()
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            // Maybe Application.HostServices works in those cases?
            // HostServices hostServices = getHostServices();
            // hostServices.showDocument(uri.toString());

            // On Linux Desktop is poorly implemented.
            // See https://stackoverflow.com/questions/18004150/desktop-api-is-not-supported-on-the-current-platform
            if (!DesktopUtil.browse(uri))
                throw new IOException("Failed to open URI: " + uri.toString());
        }
    }

    public static void openDirectory(File directory) throws IOException {
        if (!isLinux()
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(directory);
        } else {
            // Maybe Application.HostServices works in those cases?
            // HostServices hostServices = getHostServices();
            // hostServices.showDocument(uri.toString());

            // On Linux Desktop is poorly implemented.
            // See https://stackoverflow.com/questions/18004150/desktop-api-is-not-supported-on-the-current-platform
            if (!DesktopUtil.open(directory))
                throw new IOException("Failed to open directory: " + directory.toString());
        }
    }

    public static void printSystemLoad() {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.freeMemory() / 1024 / 1024;
        long total = runtime.totalMemory() / 1024 / 1024;
        long used = total - free;
        log.info("System load (no. threads/used memory (MB)): " + Thread.activeCount() + "/" + used);
    }

    public static void copyToClipboard(String content) {
        try {
            if (content != null && content.length() > 0) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(content);
                clipboard.setContent(clipboardContent);
            }
        } catch (Throwable e) {
            log.error("copyToClipboard failed " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static byte[] concatByteArrays(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        byte[] result = new byte[totalLength];
        int currentIndex = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, currentIndex, array.length);
            currentIndex += array.length;
        }
        return result;
    }

    public static <T> T jsonToObject(String jsonString, Class<T> classOfT) {
        Gson gson =
                new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).setPrettyPrinting().create();
        return gson.fromJson(jsonString, classOfT);
    }


/*    public static Object deserializeHexStringToObject(String serializedHexString) {
        Object result = null;
        try {
            ByteArrayInputStream byteInputStream =
                    new ByteArrayInputStream(org.bitcoinj.core.Utils.parseAsHexOrBase58(serializedHexString));

            try (ObjectInputStream objectInputStream = new LookAheadObjectInputStream(byteInputStream)) {
                result = objectInputStream.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                byteInputStream.close();

            }

        } catch (IOException i) {
            i.printStackTrace();
        }
        return result;
    }


    public static String serializeObjectToHexString(Serializable serializable) {
        String result = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(serializable);

            result = org.bitcoinj.core.Utils.HEX.encode(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.close();
            objectOutputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }*/

    public static <T extends Serializable> T deserialize(byte[] data) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        Object result = null;
        try {
            in = new LookAheadObjectInputStream(bis, true);
            result = in.readObject();
            if (!(result instanceof Serializable))
                throw new RuntimeException("Object not of type Serializable");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                bis.close();
            } catch (IOException ex) {
                // ignore close exception
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
        }
        return (T) result;
    }

    public static byte[] serialize(Serializable object) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        byte[] result = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(object);
            out.flush();
            result = bos.toByteArray().clone();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignore) {
            }
            try {
                bos.close();
            } catch (IOException ignore) {
            }
        }
        return result;
    }

    private static void printElapsedTime(String msg) {
        if (!msg.isEmpty()) {
            msg += " / ";
        }
        long timeStamp = System.currentTimeMillis();
        log.debug(msg + "Elapsed: " + String.valueOf(timeStamp - lastTimeStamp));
        lastTimeStamp = timeStamp;
    }

    public static void printElapsedTime() {
        printElapsedTime("");
    }


    public static Object copy(Serializable orig) throws IOException, ClassNotFoundException {
        try {
            // Write the object out to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(orig);
            out.flush();
            out.close();

            // Make an input stream from the byte array and read
            // a copy of the object back in.
            ObjectInputStream in = new LookAheadObjectInputStream(new ByteArrayInputStream(bos.toByteArray()), true);
            Object obj = in.readObject();
            return obj;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static String readTextFileFromServer(String url, String userAgent) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
        connection.addRequestProperty("User-Agent", userAgent);
        connection.connect();
        try (InputStream inputStream = connection.getInputStream()) {
            return CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static void setThreadName(String name) {
        Thread.currentThread().setName(name + "-" + new Random().nextInt(10000));
    }

    public static void overwriteWithRandomBytes(byte[] bytes) {
        Random random = new Random();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) random.nextInt(Integer.MAX_VALUE);
        }
    }

    public static boolean isDirectory(String path) {
        return new File(path).isDirectory();
    }

    public static String getSystemHomeDirectory() {
        return Utilities.isWindows() ? System.getenv("USERPROFILE") : System.getProperty("user.home");
    }

    private static class AnnotationExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getAnnotation(JsonExclude.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }

    public static void checkCryptoPolicySetup() throws NoSuchAlgorithmException, LimitedKeyStrengthException {
        if (Cipher.getMaxAllowedKeyLength("AES") > 128)
            log.debug("Congratulations, you have unlimited key length support!");
        else
            throw new LimitedKeyStrengthException();
    }

    public static String toTruncatedString(Object message, int maxLength) {
        if (Objects.nonNull(message)) {
            return StringUtils.abbreviate(message.toString(), maxLength).replace("\n", "");
        }
        return "NULL";
    }

    public static String toTruncatedString(Object message) {
        return toTruncatedString(message, 200);
    }

    public static String getRandomPrefix(int minLength, int maxLength) {
        int length = minLength + new Random().nextInt(maxLength - minLength + 1);
        String result;
        switch (new Random().nextInt(3)) {
            case 0:
                result = RandomStringUtils.randomAlphabetic(length);
                break;
            case 1:
                result = RandomStringUtils.randomNumeric(length);
                break;
            case 2:
            default:
                result = RandomStringUtils.randomAlphanumeric(length);
        }

        switch (new Random().nextInt(3)) {
            case 0:
                result = result.toUpperCase();
                break;
            case 1:
                result = result.toLowerCase();
                break;
            case 2:
            default:
        }

        return result;
    }

    public static String getShortId(String id) {
        return getShortId(id, "-");
    }

    public static String getShortId(String id, String sep) {
        String[] chunks = id.split(sep);
        if (chunks.length > 0)
            return chunks[0];
        else
            return id.substring(0, Math.min(8, id.length()));
    }
}
