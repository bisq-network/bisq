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

package bisq.network.p2p.network;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.nio.charset.StandardCharsets;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



import org.tukaani.xz.XZInputStream;

/**
 * The tor-binary artifacts that netlayer bundles declare a Tor version as their Maven version, while
 * the Tor executable inside them is copied from a Tor Browser release. Nothing else checks that the
 * two agree: netlayer 0d4dc4b6 shipped tor-binary 0.4.9.13 containing Tor 0.4.9.12. This test reads
 * the version that each bundled executable reports and compares it with the declared version.
 */
class BundledTorBinaryVersionTest {
    private static final String POM_PROPERTIES = "META-INF/maven/com.github.bisq-network.tor-binary/%s/pom.properties";
    // Tor compiles " (on Tor <version> <git revision>)" into every build. Other strings in the binary,
    // for example "Tor 0.1.2.17 and later", name unrelated versions.
    private static final Pattern BUILD_VERSION = Pattern.compile("\\(on Tor (\\d+\\.\\d+\\.\\d+\\.\\d+(?:-[0-9a-z]+)?) ");

    static Stream<Arguments> bundledTorBinaries() {
        return Stream.of(
                Arguments.of("tor-binary-linux32", "native/linux/x86/tor.tar.xz", "tor"),
                Arguments.of("tor-binary-linux64", "native/linux/x64/tor.tar.xz", "tor"),
                Arguments.of("tor-binary-macos", "native/osx/x64/tor.tar.xz", "tor"),
                Arguments.of("tor-binary-macos-aarch64", "native/osx/aarch64/tor.tar.xz", "tor"),
                Arguments.of("tor-binary-windows", "native/windows/x86/tor.tar.xz", "tor.exe"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bundledTorBinaries")
    void bundledTorBinaryHasTheDeclaredVersion(String artifactId,
                                               String archive,
                                               String executable) throws IOException {
        String declaredVersion = declaredVersion(artifactId);
        String buildVersion = buildVersion(archive, executable);

        assertEquals(declaredVersion, buildVersion,
                artifactId + " declares Tor " + declaredVersion + ", but " + archive + " contains Tor " + buildVersion);
    }

    private static String declaredVersion(String artifactId) throws IOException {
        String resource = String.format(POM_PROPERTIES, artifactId);
        try (InputStream inputStream = resource(resource)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version");
            assertNotNull(version, resource + " has no version");
            return version;
        }
    }

    private static String buildVersion(String archive, String executable) throws IOException {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new XZInputStream(new BufferedInputStream(resource(archive))))) {
            for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                if (entry.isFile() && executable.equals(entry.getName().replaceFirst("^\\./", ""))) {
                    return buildVersion(archive, tar.readAllBytes());
                }
            }
        }
        throw new AssertionError(archive + " does not contain " + executable);
    }

    private static String buildVersion(String archive, byte[] executable) {
        Matcher matcher = BUILD_VERSION.matcher(new String(executable, StandardCharsets.ISO_8859_1));
        Set<String> versions = new TreeSet<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        assertEquals(1, versions.size(), archive + " must report exactly one Tor build version: " + versions);
        return versions.iterator().next();
    }

    private static InputStream resource(String name) {
        InputStream inputStream = BundledTorBinaryVersionTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(inputStream, name + " is not on the classpath");
        return inputStream;
    }
}
