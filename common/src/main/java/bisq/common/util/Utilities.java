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

import org.bitcoinj.core.Utils;

import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.text.DecimalFormat;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Utilities {

    public static ExecutorService getSingleThreadExecutor(String name) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(true)
                .build();
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    public static ListeningExecutorService getSingleThreadListeningExecutor(String name) {
        return MoreExecutors.listeningDecorator(getSingleThreadExecutor(name));
    }

    public static ListeningExecutorService getListeningExecutorService(String name,
                                                                       int corePoolSize,
                                                                       int maximumPoolSize,
                                                                       long keepAliveTimeInSec) {
        return MoreExecutors.listeningDecorator(getThreadPoolExecutor(name, corePoolSize, maximumPoolSize, keepAliveTimeInSec));
    }

    public static ListeningExecutorService getListeningExecutorService(String name,
                                                                       int corePoolSize,
                                                                       int maximumPoolSize,
                                                                       long keepAliveTimeInSec,
                                                                       BlockingQueue<Runnable> workQueue) {
        return MoreExecutors.listeningDecorator(getThreadPoolExecutor(name, corePoolSize, maximumPoolSize, keepAliveTimeInSec, workQueue));
    }

    public static ThreadPoolExecutor getThreadPoolExecutor(String name,
                                                           int corePoolSize,
                                                           int maximumPoolSize,
                                                           long keepAliveTimeInSec) {
        return getThreadPoolExecutor(name, corePoolSize, maximumPoolSize, keepAliveTimeInSec,
                new ArrayBlockingQueue<>(maximumPoolSize));
    }

    private static ThreadPoolExecutor getThreadPoolExecutor(String name,
                                                            int corePoolSize,
                                                            int maximumPoolSize,
                                                            long keepAliveTimeInSec,
                                                            BlockingQueue<Runnable> workQueue) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(true)
                .build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTimeInSec,
                TimeUnit.SECONDS, workQueue, threadFactory);
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

    // TODO: Can some/all of the uses of this be replaced by guava MoreExecutors.shutdownAndAwaitTermination(..)?
    public static void shutdownAndAwaitTermination(ExecutorService executor, long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public static <V> FutureCallback<V> failureCallback(Consumer<Throwable> errorHandler) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(V result) {
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                errorHandler.accept(t);
            }
        };
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

    /**
     * @return True, if Bisq is running on a virtualized OS within Qubes, false otherwise
     */
    public static boolean isQubesOS() {
        // For Linux qubes, "os.version" looks like "4.19.132-1.pvops.qubes.x86_64"
        // The presence of the "qubes" substring indicates this Linux is running as a qube
        // This is the case for all 3 virtualization modes (PV, PVH, HVM)
        // In addition, this works for both simple AppVMs, as well as for StandaloneVMs
        // TODO This might not work for detecting Qubes virtualization for other OSes
        // like Windows
        return getOSVersion().contains("qubes");
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

    public static String getOSVersion() {
        return System.getProperty("os.version").toLowerCase(Locale.US);
    }

    /**
     * Returns the well-known "user data directory" for the current operating system.
     */
    public static File getUserDataDir() {
        if (Utilities.isWindows())
            return new File(System.getenv("APPDATA"));

        if (Utilities.isOSX())
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toFile();

        // *nix
        return Paths.get(System.getProperty("user.home"), ".local", "share").toFile();
    }

    public static int getMinorVersion() throws InvalidVersionException {
        String version = getOSVersion();
        String[] tokens = version.split("\\.");
        try {
            checkArgument(tokens.length > 1);
            return Integer.parseInt(tokens[1]);
        } catch (IllegalArgumentException e) {
            printSysInfo();
            throw new InvalidVersionException("Version is not in expected format. Version=" + version);
        }
    }

    public static int getMajorVersion() throws InvalidVersionException {
        String version = getOSVersion();
        String[] tokens = version.split("\\.");
        try {
            checkArgument(tokens.length > 0);
            return Integer.parseInt(tokens[0]);
        } catch (IllegalArgumentException e) {
            printSysInfo();
            throw new InvalidVersionException("Version is not in expected format. Version=" + version);
        }
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
        if (!DesktopUtil.browse(uri))
            throw new IOException("Failed to open URI: " + uri);
    }

    public static void openFile(File file) throws IOException {
        if (!DesktopUtil.open(file))
            throw new IOException("Failed to open file: " + file);
    }

    public static String getDownloadOfHomeDir() {
        File file = new File(getSystemHomeDirectory() + "/Downloads");
        if (file.exists())
            return file.getAbsolutePath();
        else
            return getSystemHomeDirectory();
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

    public static boolean isCtrlShiftPressed(KeyCode keyCode, KeyEvent keyEvent) {
        return new KeyCodeCombination(keyCode, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN).match(keyEvent);
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
        return ArrayUtils.addAll(array1, array2);
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

    public static String toTruncatedString(Object message) {
        return toTruncatedString(message, 200, true);
    }

    public static String toTruncatedString(Object message, int maxLength) {
        return toTruncatedString(message, maxLength, true);
    }

    public static String toTruncatedString(Object message, int maxLength, boolean removeLineBreaks) {
        if (message == null)
            return "null";


        String result = StringUtils.abbreviate(message.toString(), maxLength);
        if (removeLineBreaks)
            return result.replace("\n", "");

        return result;

    }

    public static List<String> toListOfWrappedStrings(String s, int wrapLength) {
        StringBuilder sb = new StringBuilder(s);
        int i = 0;
        while (i + wrapLength < sb.length() && (i = sb.lastIndexOf(" ", i + wrapLength)) != -1) {
            sb.replace(i, i + 1, "\n");
        }
        String[] splitLine = sb.toString().split("\n");
        return Arrays.asList(splitLine);
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

    public static byte[] copyRightAligned(byte[] src, int newLength) {
        byte[] dest = new byte[newLength];
        int srcPos = Math.max(src.length - newLength, 0);
        int destPos = Math.max(newLength - src.length, 0);
        System.arraycopy(src, srcPos, dest, destPos, newLength - destPos);
        return dest;
    }

    public static byte[] intsToBytesBE(int[] ints) {
        byte[] bytes = new byte[ints.length * 4];
        int i = 0;
        for (int v : ints) {
            bytes[i++] = (byte) (v >> 24);
            bytes[i++] = (byte) (v >> 16);
            bytes[i++] = (byte) (v >> 8);
            bytes[i++] = (byte) v;
        }
        return bytes;
    }

    public static int[] bytesToIntsBE(byte[] bytes) {
        int[] ints = new int[bytes.length / 4];
        for (int i = 0, j = 0; i < bytes.length / 4; i++) {
            ints[i] = Ints.fromBytes(bytes[j++], bytes[j++], bytes[j++], bytes[j++]);
        }
        return ints;
    }

    // Helper to filter unique elements by key
    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> map = new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    public static String readableFileSize(long size) {
        if (size <= 0) return "0";
        String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.###").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    // Substitute for FormattingUtils if there is no dependency to core
    public static String formatDurationAsWords(long durationMillis) {
        String format = "";
        String second = "second";
        String minute = "minute";
        String hour = "hour";
        String day = "day";
        String days = "days";
        String hours = "hours";
        String minutes = "minutes";
        String seconds = "seconds";

        if (durationMillis >= TimeUnit.DAYS.toMillis(1)) {
            format = "d\' " + days + ", \'";
        }

        format += "H\' " + hours + ", \'m\' " + minutes + ", \'s\'.\'S\' " + seconds + "\'";

        String duration = durationMillis > 0 ? DurationFormatUtils.formatDuration(durationMillis, format) : "";

        duration = StringUtils.replacePattern(duration, "^1 " + seconds + "|\\b1 " + seconds, "1 " + second);
        duration = StringUtils.replacePattern(duration, "^1 " + minutes + "|\\b1 " + minutes, "1 " + minute);
        duration = StringUtils.replacePattern(duration, "^1 " + hours + "|\\b1 " + hours, "1 " + hour);
        duration = StringUtils.replacePattern(duration, "^1 " + days + "|\\b1 " + days, "1 " + day);

        duration = duration.replace(", 0 seconds", "");
        duration = duration.replace(", 0 minutes", "");
        duration = duration.replace(", 0 hours", "");
        duration = StringUtils.replacePattern(duration, "^0 days, ", "");
        duration = StringUtils.replacePattern(duration, "^0 hours, ", "");
        duration = StringUtils.replacePattern(duration, "^0 minutes, ", "");
        duration = StringUtils.replacePattern(duration, "^0 seconds, ", "");

        String result = duration.trim();
        if (result.isEmpty()) {
            result = "0.000 seconds";
        }
        return result;
    }

    public static String cleanString(String string) {
        return string.replaceAll("[\\t\\n\\r]+", " ");
    }
}
