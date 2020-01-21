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

package bisq.core.app;

import bisq.common.config.BisqHelpFormatter;

import joptsimple.OptionParser;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class BisqHelpFormatterTest {

    @Test
    public void testHelpFormatter() throws IOException, URISyntaxException {

        OptionParser parser = new OptionParser();

        parser.formatHelpWith(new BisqHelpFormatter("Bisq Test", "bisq-test", "0.1.0"));

        parser.accepts("name",
                "The name of the Bisq node")
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("Bisq");

        parser.accepts("another-option",
                "This is a long description which will need to break over multiple linessssssssssss such " +
                        "that no line is longer than 80 characters in the help output.")
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("WAT");

        parser.accepts("exactly-72-char-description",
                "012345678911234567892123456789312345678941234567895123456789612345678971")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("exactly-72-char-description-with-spaces",
                " 123456789 123456789 123456789 123456789 123456789 123456789 123456789 1")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("90-char-description-without-spaces",
                "-123456789-223456789-323456789-423456789-523456789-623456789-723456789-823456789-923456789")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("90-char-description-with-space-at-char-80",
                "-123456789-223456789-323456789-423456789-523456789-623456789-723456789-823456789 923456789")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("90-char-description-with-spaces-at-chars-5-and-80",
                "-123 56789-223456789-323456789-423456789-523456789-623456789-723456789-823456789 923456789")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("90-char-description-with-space-at-char-73",
                "-123456789-223456789-323456789-423456789-523456789-623456789-723456789-8 3456789-923456789")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("1-char-description-with-only-a-space", " ")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("empty-description", "")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("no-description")
                .withRequiredArg()
                .ofType(String.class);

        parser.accepts("no-arg", "Some description");

        parser.accepts("optional-arg",
                "Option description")
                .withOptionalArg();

        parser.accepts("with-default-value",
                "Some option with a default value")
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("Wat");

        parser.accepts("data-dir",
                "Application data directory")
                .withRequiredArg()
                .ofType(File.class)
                .defaultsTo(new File("/Users/cbeams/Library/Application Support/Bisq"));

        parser.accepts("enum-opt",
                "Some option that accepts an enum value as an argument")
                .withRequiredArg()
                .ofType(AnEnum.class)
                .defaultsTo(AnEnum.foo);

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        String expected = new String(Files.readAllBytes(Paths.get(getClass().getResource("cli-output.txt").toURI())));
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Load the expected content from a different file for Windows due to different path separator
            // And normalize line endings to LF in case the file has CRLF line endings
            expected = new String(Files.readAllBytes(Paths.get(getClass().getResource("cli-output_windows.txt").toURI())))
                    .replaceAll("\\r\\n?", "\n");
        }

        parser.printHelpOn(new PrintStream(actual));
        assertThat(actual.toString(), equalTo(expected));
    }


    enum AnEnum {foo, bar, baz}
}
