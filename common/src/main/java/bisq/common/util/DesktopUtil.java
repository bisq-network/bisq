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

import java.net.URI;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

// Taken form https://stackoverflow.com/questions/18004150/desktop-api-is-not-supported-on-the-current-platform,
// originally net.mightypork.rpack.utils.DesktopApi
@Slf4j
class DesktopUtil {

    public static boolean browse(URI uri) {
        return openSystemSpecific(uri.toString());
    }


    public static boolean open(File file) {
        return openSystemSpecific(file.getPath());
    }


    public static boolean edit(File file) {
        // you can try something like
        // runCommand("gimp", "%s", file.getPath())
        // based on user preferences.
        return openSystemSpecific(file.getPath());
    }


    private static boolean openSystemSpecific(String what) {
        EnumOS os = getOs();
        if (os.isLinux()) {
            if (runCommand("kde-open", "%s", what)) return true;
            if (runCommand("gnome-open", "%s", what)) return true;
            if (runCommand("xdg-open", "%s", what)) return true;
        }

        if (os.isMac()) {
            if (runCommand("open", "%s", what)) return true;
        }

        if (os.isWindows()) {
            return runCommand("explorer", "%s", "\"" + what + "\"");
        }

        return false;
    }


    @SuppressWarnings("SameParameterValue")
    private static boolean runCommand(String command, String args, String file) {

        log.info("Trying to exec: cmd = {} args = {} file = {}", command, args, file);

        String[] parts = prepareCommand(command, args, file);

        try {
            Process p = Runtime.getRuntime().exec(parts);
            if (p == null) return false;

            try {
                int value = p.exitValue();
                if (value == 0) {
                    log.warn("Process ended immediately.");
                } else {
                    log.warn("Process crashed.");
                }
                return false;
            } catch (IllegalThreadStateException e) {
                log.info("Process is running.");
                return true;
            }
        } catch (IOException e) {
            log.warn("Error running command. {}", e.toString());
            return false;
        }
    }


    private static String[] prepareCommand(String command, String args, String file) {

        List<String> parts = new ArrayList<>();
        parts.add(command);

        if (args != null) {
            for (String s : args.split(" ")) {
                s = String.format(s, file); // put in the filename thing

                parts.add(s.trim());
            }
        }

        return parts.toArray(new String[parts.size()]);
    }

    public enum EnumOS {
        linux,
        macos,
        solaris,
        unknown,
        windows;

        public boolean isLinux() {

            return this == linux || this == solaris;
        }


        public boolean isMac() {

            return this == macos;
        }


        public boolean isWindows() {

            return this == windows;
        }
    }


    private static EnumOS getOs() {

        String s = System.getProperty("os.name").toLowerCase();

        if (s.contains("win")) {
            return EnumOS.windows;
        }

        if (s.contains("mac")) {
            return EnumOS.macos;
        }

        if (s.contains("solaris")) {
            return EnumOS.solaris;
        }

        if (s.contains("sunos")) {
            return EnumOS.solaris;
        }

        if (s.contains("linux")) {
            return EnumOS.linux;
        }

        if (s.contains("unix")) {
            return EnumOS.linux;
        } else {
            return EnumOS.unknown;
        }
    }
}
