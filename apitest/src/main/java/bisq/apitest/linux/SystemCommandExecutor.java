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
import java.io.InputStream;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * This class can be used to execute a system command from a Java application.
 * See the documentation for the public methods of this class for more
 * information.
 *
 * Documentation for this class is available at this URL:
 *
 * http://devdaily.com/java/java-processbuilder-process-system-exec
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
class SystemCommandExecutor {
    private final List<String> cmdOptions;
    private ThreadedStreamHandler inputStreamHandler;
    private ThreadedStreamHandler errorStreamHandler;

    public SystemCommandExecutor(final List<String> cmdOptions) {
        if (cmdOptions.isEmpty())
            throw new IllegalStateException("No command params specified.");

        if (cmdOptions.contains("sudo"))
            throw new IllegalStateException("'sudo' commands are prohibited.");

        log.trace("System cmd options {}", cmdOptions);
        this.cmdOptions = cmdOptions;
    }

    // Execute a system command and return its status code (0 or 1).
    // The system command's output (stderr or stdout) can be accessed from accessors.
    public int exec() throws IOException, InterruptedException {
        return exec(true);
    }

    // Execute a system command and return its status code (0 or 1).
    // The system command's output (stderr or stdout) can be accessed from accessors
    // if the waitOnErrStream flag is true, else the method will not wait on (join)
    // the error stream handler thread.
    public int exec(boolean waitOnErrStream) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmdOptions).start();

        // I'm currently doing these on a separate line here in case i need to set them to null
        // to get the threads to stop.
        // see http://java.sun.com/j2se/1.5.0/docs/guide/misc/threadPrimitiveDeprecation.html
        InputStream inputStream = process.getInputStream();
        InputStream errorStream = process.getErrorStream();

        // These need to run as java threads to get the standard output and error from the command.
        // the inputstream handler gets a reference to our stdOutput in case we need to write
        // something to it.
        inputStreamHandler = new ThreadedStreamHandler(inputStream);
        errorStreamHandler = new ThreadedStreamHandler(errorStream);

        inputStreamHandler.start();
        errorStreamHandler.start();

        int exitStatus = process.waitFor();

        inputStreamHandler.interrupt();
        errorStreamHandler.interrupt();

        inputStreamHandler.join();
        if (waitOnErrStream)
            errorStreamHandler.join();

        return exitStatus;
    }

    // Get the standard error from an executed system command.
    public StringBuilder getStandardErrorFromCommand() {
        return errorStreamHandler.getOutputBuffer();
    }

    // Get the standard output from an executed system command.
    public StringBuilder getStandardOutputFromCommand() {
        return inputStreamHandler.getOutputBuffer();
    }
}
