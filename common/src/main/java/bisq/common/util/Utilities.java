/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.util;

import bisq.common.crypto.LimitedKeyStrengthException;

import org.bitcoinj.core.Utils;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import javax.crypto.Cipher;

import java.security.NoSuchAlgorithmException;

import java.net.URI;
import java.net.URISyntaxException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.awt.Desktop.Action;
import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;

@Slf4j
public class Utilities {
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

    public static ListeningExecutorService getListeningSingleThreadExecutor(String name) {
        return MoreExecutors.listeningDecorator(getSingleThreadExecutor(name));
    }

    public static ListeningExecutorService getSingleThreadExecutor(String name) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(true)
                .build();
        return MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(threadFactory));
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
        executor.setRejectedExecutionHandler((r, e) -> log.debug("RejectedExecutionHandler called"));
        return executor;
    }


    @SuppressWarnings("SameParameterValue")
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
        executor.setRejectedExecutionHandler((r, e) -> log.debug("RejectedExecutionHandler called"));
        return executor;
    }

    /**
     * @return true if <code>defaults read -g AppleInterfaceStyle</code> has an exit status of <code>0</code> (i.e. _not_ returning "key not found").
     */
    public static boolean isMacMenuBarDarkMode() {
        try {
            // check for exit status only. Once there are more modes than "dark" and "default", we might need to analyze string contents..
            Process process = Runtime.getRuntime().exec(new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"});
            process.waitFor(100, TimeUnit.MILLISECONDS);
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException | IllegalThreadStateException ex) {
            // IllegalThreadStateException thrown by proc.exitValue(), if process didn't terminate
            return false;
        }
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

    public static boolean isDebianLinux() {
        return isLinux() && new File("/etc/debian_version").isFile();
    }

    public static boolean isRedHatLinux() {
        return isLinux() && new File("/etc/redhat-release").isFile();
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
        log.info("System info: os.name={}; os.version={}; os.arch={}; sun.arch.data.model={}; JRE={}; JVM={}",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                getJVMArchitecture(),
                (System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")"),
                (System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.name", "-") + ")")
        );
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
                && isDesktopSupported()
                && getDesktop().isSupported(Action.BROWSE)) {
            getDesktop().browse(uri);
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

    public static void openFile(File file) throws IOException {
        if (!isLinux()
                && isDesktopSupported()
                && getDesktop().isSupported(Action.OPEN)) {
            getDesktop().open(file);
        } else {
            // Maybe Application.HostServices works in those cases?
            // HostServices hostServices = getHostServices();
            // hostServices.showDocument(uri.toString());

            // On Linux Desktop is poorly implemented.
            // See https://stackoverflow.com/questions/18004150/desktop-api-is-not-supported-on-the-current-platform
            if (!DesktopUtil.open(file))
                throw new IOException("Failed to open file: " + file.toString());
        }
    }

    public static String getTmpDir() {
        return System.getProperty("java.io.tmpdir");
    }

    public static String getDownloadOfHomeDir() {
        File file = new File(getSystemHomeDirectory() + "/Downloads");
        if (file.exists())
            return file.getAbsolutePath();
        else
            return getSystemHomeDirectory();
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
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).setPrettyPrinting().create();
        return gson.fromJson(jsonString, classOfT);
    }

    public static <T extends Serializable> T deserialize(byte[] data) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        Object result = null;
        try {
            in = new ObjectInputStream(bis);
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
        //noinspection unchecked,ConstantConditions
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

    public static <T extends Serializable> T cloneObject(Serializable object) {
        return deserialize(serialize(object));
    }

    @SuppressWarnings("SameParameterValue")
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

    public static void setThreadName(String name) {
        Thread.currentThread().setName(name + "-" + new Random().nextInt(10000));
    }

    public static boolean isDirectory(String path) {
        return new File(path).isDirectory();
    }

    public static String getSystemHomeDirectory() {
        return Utilities.isWindows() ? System.getenv("USERPROFILE") : System.getProperty("user.home");
    }

    public static String encodeToHex(@Nullable byte[] bytes, boolean allowNullable) {
        if (allowNullable)
            return bytes != null ? Utils.HEX.encode(bytes) : "null";
        else
            return Utils.HEX.encode(checkNotNull(bytes, "bytes must not be null at encodeToHex"));
    }

    public static String bytesAsHexString(@Nullable byte[] bytes) {
        return encodeToHex(bytes, true);
    }

    public static String encodeToHex(@Nullable byte[] bytes) {
        return encodeToHex(bytes, false);
    }

    public static byte[] decodeFromHex(String encoded) {
        return Utils.HEX.decode(encoded);
    }

    public static boolean isAltOrCtrlPressed(KeyCode keyCode, KeyEvent keyEvent) {
        return isAltPressed(keyCode, keyEvent) || isCtrlPressed(keyCode, keyEvent);
    }

    public static boolean isCtrlPressed(KeyCode keyCode, KeyEvent keyEvent) {
        return new KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN).match(keyEvent) ||
                new KeyCodeCombination(keyCode, KeyCombination.CONTROL_DOWN).match(keyEvent);
    }

    public static boolean isAltPressed(KeyCode keyCode, KeyEvent keyEvent) {
        return new KeyCodeCombination(keyCode, KeyCombination.ALT_DOWN).match(keyEvent);
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
        return ArrayUtils.addAll(array1, array2);
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2, byte[] array3) {
        return ArrayUtils.addAll(array1, ArrayUtils.addAll(array2, array3));
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2, byte[] array3, byte[] array4) {
        return ArrayUtils.addAll(array1, ArrayUtils.addAll(array2, ArrayUtils.addAll(array3, array4)));
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2, byte[] array3, byte[] array4, byte[] array5) {
        return ArrayUtils.addAll(array1, ArrayUtils.addAll(array2, ArrayUtils.addAll(array3, ArrayUtils.addAll(array4, array5))));
    }

    public static Date getUTCDate(int year, int month, int dayOfMonth) {
        GregorianCalendar calendar = new GregorianCalendar(year, month, dayOfMonth);
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        return calendar.getTime();
    }

    /**
     * @param stringList      String of comma separated tokens.
     * @param allowWhitespace If white space inside the list tokens is allowed. If not the token will be ignored.
     * @return Set of tokens
     */
    public static Set<String> commaSeparatedListToSet(String stringList, boolean allowWhitespace) {
        if (stringList != null) {
            return Splitter.on(",")
                    .splitToList(allowWhitespace ? stringList : StringUtils.deleteWhitespace(stringList))
                    .stream()
                    .filter(e -> !e.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            return new HashSet<>();
        }
    }

    public static String getPathOfCodeSource() throws URISyntaxException {
        return new File(Utilities.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
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

    public static String toTruncatedString(Object message) {
        return toTruncatedString(message, 200, true);
    }

    public static String toTruncatedString(Object message, int maxLength) {
        return toTruncatedString(message, maxLength, true);
    }

    public static String toTruncatedString(Object message, boolean removeLinebreaks) {
        return toTruncatedString(message, 200, removeLinebreaks);
    }

    public static String toTruncatedString(Object message, int maxLength, boolean removeLinebreaks) {
        if (message == null)
            return "null";


        String result = StringUtils.abbreviate(message.toString(), maxLength);
        if (removeLinebreaks)
            return result.replace("\n", "");

        return result;

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

    @SuppressWarnings("SameParameterValue")
    public static String getShortId(String id, String sep) {
        String[] chunks = id.split(sep);
        if (chunks.length > 0)
            return chunks[0];
        else
            return id.substring(0, Math.min(8, id.length()));
    }

    public static String collectionToCSV(Collection<String> collection) {
        return collection.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    public static byte[] integerToByteArray(int intValue, int numBytes) {
        byte[] bytes = new byte[numBytes];
        for (int i = numBytes - 1; i >= 0; i--) {
            bytes[i] = ((byte) (intValue & 0xFF));
            intValue >>>= 8;
        }
        return bytes;
    }

    public static int byteArrayToInteger(byte[] bytes) {
        int result = 0;
        for (byte aByte : bytes) {
            result = result << 8 | aByte & 0xff;
        }
        return result;
    }
}
