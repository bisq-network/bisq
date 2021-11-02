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

package bisq.apitest.linux;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.apitest.config.ApiTestConfig.BASH_PATH_VALUE;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;

@Slf4j
public class BashCommand {

    private int exitStatus = -1;
    @Nullable
    private String output;
    @Nullable
    private String error;

    private final String command;
    private final int numResponseLines;

    public BashCommand(String command) {
        this(command, 0);
    }

    public BashCommand(String command, int numResponseLines) {
        this.command = command;
        this.numResponseLines = numResponseLines; // only want the top N lines of output
    }

    public BashCommand run() throws IOException, InterruptedException {
        SystemCommandExecutor commandExecutor = new SystemCommandExecutor(tokenizeSystemCommand());
        exitStatus = commandExecutor.exec();
        processOutput(commandExecutor);
        return this;
    }

    public BashCommand runInBackground() throws IOException, InterruptedException {
        SystemCommandExecutor commandExecutor = new SystemCommandExecutor(tokenizeSystemCommand());
        exitStatus = commandExecutor.exec(false);
        processOutput(commandExecutor);
        return this;
    }

    private void processOutput(SystemCommandExecutor commandExecutor) {
        // Get the error status and stderr from system command.
        StringBuilder stderr = commandExecutor.getStandardErrorFromCommand();
        if (stderr.length() > 0)
            error = stderr.toString();

        if (exitStatus != 0)
            return;

        // Format and cache the stdout from system command.
        StringBuilder stdout = commandExecutor.getStandardOutputFromCommand();
        String[] rawLines = stdout.toString().split("\n");
        StringBuilder truncatedLines = new StringBuilder();
        int limit = numResponseLines > 0 ? Math.min(numResponseLines, rawLines.length) : rawLines.length;
        for (int i = 0; i < limit; i++) {
            String line = rawLines[i].length() >= 220 ? rawLines[i].substring(0, 220) + " ..." : rawLines[i];
            truncatedLines.append(line).append((i < limit - 1) ? "\n" : "");
        }
        output = truncatedLines.toString();
    }

    public String getCommand() {
        return this.command;
    }

    public int getExitStatus() {
        return this.exitStatus;
    }

    // TODO return Optional<String>
    @Nullable
    public String getOutput() {
        return this.output;
    }

    // TODO return Optional<String>
    public String getError() {
        return this.error;
    }

    private List<String> tokenizeSystemCommand() {
        return new ArrayList<>() {{
            add(BASH_PATH_VALUE);
            add("-c");
            add(command);
        }};
    }

    @SuppressWarnings("unused")
    // Convenience method for getting system load info.
    public static String printSystemLoadString(Exception tracingException) throws IOException, InterruptedException {
        StackTraceElement[] stackTraceElement = tracingException.getStackTrace();
        StringBuilder stackTraceBuilder = new StringBuilder(tracingException.getMessage()).append("\n");
        int traceLimit = Math.min(stackTraceElement.length, 4);
        for (int i = 0; i < traceLimit; i++) {
            stackTraceBuilder.append(stackTraceElement[i]).append("\n");
        }
        stackTraceBuilder.append("...");
        log.info(stackTraceBuilder.toString());
        BashCommand cmd = new BashCommand("ps -aux --sort -rss --headers", 2).run();
        return cmd.getOutput() + "\n"
                + "System load: Memory (MB): " + getUsedMemoryInMB() + " / No. of threads: " + Thread.activeCount()
                + " JVM uptime (ms): " + getRuntimeMXBean().getUptime();
    }

    public static long getUsedMemoryInMB() {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.freeMemory() / 1024 / 1024;
        long total = runtime.totalMemory() / 1024 / 1024;
        return total - free;
    }

    public static long getPid(String processName) throws IOException, InterruptedException {
        String psCmd = "ps aux | pgrep " + processName + " | grep -v grep";
        String psCmdOutput = new BashCommand(psCmd).run().getOutput();
        if (psCmdOutput == null || psCmdOutput.isEmpty())
            return -1;

        return Long.parseLong(psCmdOutput);
    }

    @SuppressWarnings("unused")
    public static BashCommand grep(String processName) throws IOException, InterruptedException {
        String c = "ps -aux | grep " + processName + " | grep -v grep";
        return new BashCommand(c).run();
    }

    public static boolean isAlive(long pid) throws IOException, InterruptedException {
        String isAliveScript = "if ps -p " + pid + " > /dev/null; then echo true; else echo false; fi";
        return new BashCommand(isAliveScript).run().getOutput().equals("true");
    }
}
