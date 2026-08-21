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

package bisq.cli.opts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Wrapper for an array of command line arguments.
 *
 * Used to extract CLI connection and authentication arguments, or method arguments
 * before parsing CLI or method opts
 *
 */
public class ArgumentList {

    private final Predicate<String> isCliOpt = (o) ->
            o.startsWith("--password") || o.startsWith("-password")
                    || o.startsWith("--port") || o.startsWith("-port")
                    || o.startsWith("--host") || o.startsWith("-host");


    // The method name is the only positional opt in a command (easy to identify).
    // If the positional argument does not match a Method, or there are more than one
    // positional arguments, the joptsimple parser or CLI will fail as expected.
    private final Predicate<String> isMethodNameOpt = (o) -> !o.startsWith("-");

    private final Predicate<String> isHelpOpt = (o) -> o.startsWith("--help") || o.startsWith("-help");

    private final String[] arguments;
    private int currentIndex;

    public ArgumentList(String... arguments) {
        this.arguments = arguments.clone();
    }

    /**
     * Returns only the CLI connection & authentication, and method name args
     * (--password, --host, --port, --help, method name) contained in the original
     * String[] args; excludes the method specific arguments.
     *
     * If String[] args contains both a method name (the only positional opt) and a help
     * argument (--help, -help), it is assumed the user wants method help, not CLI help,
     * and the help argument is not included in the returned String[].
     */
    public String[] getCLIArguments() {
        currentIndex = 0;
        Optional<String> methodNameArgument = Optional.empty();
        Optional<String> helpArgument = Optional.empty();
        List<String> prunedArguments = new ArrayList<>();

        while (hasMore()) {
            String arg = peek();
            if (isMethodNameOpt.test(arg)) {
                methodNameArgument = Optional.of(arg);
                prunedArguments.add(arg);
            }

            if (isCliOpt.test(arg))
                prunedArguments.add(arg);

            if (isHelpOpt.test(arg))
                helpArgument = Optional.of(arg);

            next();
        }

        // Include the saved CLI help argument if the positional method name argument
        // was not found.
        if (!methodNameArgument.isPresent() && helpArgument.isPresent())
            prunedArguments.add(helpArgument.get());

        return prunedArguments.toArray(new String[0]);
    }

    /**
     * Returns only the method args contained in the original String[] args;  excludes the
     * CLI connection & authentication opts (--password, --host, --port), plus the
     * positional method name arg.
     */
    public String[] getMethodArguments() {
        List<String> prunedArguments = new ArrayList<>();
        currentIndex = 0;
        while (hasMore()) {
            String arg = peek();
            if (!isCliOpt.test(arg) && !isMethodNameOpt.test(arg)) {
                prunedArguments.add(arg);
            }
            next();
        }
        return prunedArguments.toArray(new String[0]);
    }


    boolean hasMore() {
        return currentIndex < arguments.length;
    }

    @SuppressWarnings("UnusedReturnValue")
    String next() {
        return arguments[currentIndex++];
    }

    String peek() {
        return arguments[currentIndex];
    }
}
