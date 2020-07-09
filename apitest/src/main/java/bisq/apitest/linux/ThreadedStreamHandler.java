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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * This class is intended to be used with the SystemCommandExecutor
 * class to let users execute system commands from Java applications.
 *
 * This class is based on work that was shared in a JavaWorld article
 * named "When System.exec() won't". That article is available at this
 * url:
 *
 * http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html
 *
 * Documentation for this class is available at this URL:
 *
 * http://devdaily.com/java/java-processbuilder-process-system-exec
 *
 *
 * Copyright 2010 alvin j. alexander, devdaily.com.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Please ee the following page for the LGPL license:
 * http://www.gnu.org/licenses/lgpl.txt
 *
 */
@Slf4j
class ThreadedStreamHandler extends Thread {
    final InputStream inputStream;
    String adminPassword;
    @SuppressWarnings("unused")
    OutputStream outputStream;
    PrintWriter printWriter;
    final StringBuilder outputBuffer = new StringBuilder();
    private boolean sudoIsRequested = false;

    /**
     * A simple constructor for when the sudo command is not necessary.
     * This constructor will just run the command you provide, without
     * running sudo before the command, and without expecting a password.
     *
     * @param inputStream InputStream
     */
    ThreadedStreamHandler(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    /**
     * Use this constructor when you want to invoke the 'sudo' command.
     * The outputStream must not be null. If it is, you'll regret it. :)
     *
     * TODO this currently hangs if the admin password given for the sudo command is wrong.
     *
     * @param inputStream InputStream
     * @param outputStream OutputStream
     * @param adminPassword String
     */
    ThreadedStreamHandler(InputStream inputStream, OutputStream outputStream, String adminPassword) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.printWriter = new PrintWriter(outputStream);
        this.adminPassword = adminPassword;
        this.sudoIsRequested = true;
    }

    public void run() {
        // On mac os x 10.5.x, the admin password needs to be written immediately.
        if (sudoIsRequested) {
            printWriter.println(adminPassword);
            printWriter.flush();
        }

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null)
                outputBuffer.append(line).append("\n");

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    private void doSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    public StringBuilder getOutputBuffer() {
        return outputBuffer;
    }
}

