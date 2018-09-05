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

import java.io.File;
import java.io.IOException;

import java.util.List;

import java.lang.management.ManagementFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Borrowed from: https://dzone.com/articles/programmatically-restart-java
public class RestartUtil {
    private static final Logger log = LoggerFactory.getLogger(RestartUtil.class);

    /**
     * Sun property pointing the main class and its arguments.
     * Might not be defined on non Hotspot VM implementations.
     */
    public static final String SUN_JAVA_COMMAND = "sun.java.command";

    public static void restartApplication(String logPath) throws IOException {
        try {
            String java = System.getProperty("java.home") + "/bin/java";
            List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            StringBuilder vmArgsOneLine = new StringBuilder();
            // if it's the agent argument : we ignore it otherwise the
// address of the old application and the new one will be in conflict
            vmArguments.stream().filter(arg -> !arg.contains("-agentlib")).forEach(arg -> {
                vmArgsOneLine.append(arg);
                vmArgsOneLine.append(" ");
            });
            // init the command to execute, add the vm args
            final StringBuilder cmd = new StringBuilder(java + " " + vmArgsOneLine);

            // program main and program arguments
            String[] mainCommand = System.getProperty(SUN_JAVA_COMMAND).split(" ");
            // program main is a jar
            if (mainCommand[0].endsWith(".jar")) {
                // if it's a jar, add -jar mainJar
                cmd.append("-jar ").append(new File(mainCommand[0]).getPath());
            } else {
                // else it's a .class, add the classpath and mainClass
                cmd.append("-cp \"").append(System.getProperty("java.class.path")).append("\" ").append(mainCommand[0]);
            }
            // finally add program arguments
            for (int i = 1; i < mainCommand.length; i++) {
                cmd.append(" ");
                cmd.append(mainCommand[i]);
            }

            try {
                final String command = "nohup " + cmd.toString() + " >/dev/null 2>" + logPath + " &";
                log.warn("\n\n############################################################\n" +
                                "Executing cmd for restart: {}" +
                                "\n############################################################\n\n",
                        command);
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            throw new IOException("Error while trying to restart the application", e);
        }
    }
}
