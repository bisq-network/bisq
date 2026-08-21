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

package bisq.core.dao.burningman;

import bisq.common.config.Config;

import com.google.gson.Gson;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public class BurningManAddressListService {
    public static final String RESOURCE_DIR = "burningman";
    public static final String FILE_NAME_PREFIX = "bm-addresses-v";
    public static final String FILE_NAME_SUFFIX = ".json";

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(FILE_NAME_PREFIX + "(\\d{4,})\\" + FILE_NAME_SUFFIX);
    private static final Gson GSON = new Gson();

    @Getter
    private final NavigableMap<Integer, BurningManAddressList> addressListsByVersion;

    @Inject
    public BurningManAddressListService() {
        NavigableMap<Integer, BurningManAddressList> loadedAddressListsByVersion = loadAddressLists();
        String currentNetwork = Config.baseCurrencyNetwork().name();
        NavigableMap<Integer, BurningManAddressList> currentNetworkAddressListsByVersion = loadedAddressListsByVersion.entrySet().stream()
                .filter(entry -> currentNetwork.equals(entry.getValue().getNetwork()))
                .collect(Collectors.toMap(Entry::getKey,
                        Entry::getValue,
                        (first, second) -> first,
                        TreeMap::new));

        if (!currentNetworkAddressListsByVersion.isEmpty()) {
            addressListsByVersion = Collections.unmodifiableNavigableMap(currentNetworkAddressListsByVersion);
        } else {
            checkArgument(!Config.baseCurrencyNetwork().isMainnet(),
                    "No Burning Man address list resources found for network " + currentNetwork);
            log.warn("No Burning Man address list resources found for network {}. " +
                    "Using bundled versions without applying address filtering.", currentNetwork);
            addressListsByVersion = Collections.unmodifiableNavigableMap(loadedAddressListsByVersion);
        }

        log.info("Loaded Burning Man address list versions {}", addressListsByVersion.keySet());
    }

    public List<Integer> getSupportedVersions() {
        return new ArrayList<>(addressListsByVersion.keySet());
    }

    public int getLatestVersion() {
        checkArgument(!addressListsByVersion.isEmpty(), "No Burning Man address list versions are available");
        return addressListsByVersion.lastKey();
    }

    public int getNextVersion() {
        return getLatestVersion() + 1;
    }

    public BurningManAddressList getAddressList(int version) {
        BurningManAddressList addressList = addressListsByVersion.get(version);
        checkArgument(addressList != null, "Burning Man address list version %s is not supported", version);
        return addressList;
    }

    public int selectHighestCommonVersion(Collection<Integer> peerVersions) {
        List<Integer> checkedPeerVersions = getValidatedSupportedVersions(peerVersions);
        Set<Integer> supportedVersions = new TreeSet<>(addressListsByVersion.keySet());
        return checkedPeerVersions.stream()
                .filter(supportedVersions::contains)
                .max(Integer::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("No common Burning Man address list version. " +
                        "localVersions=" + supportedVersions + ", peerVersions=" + checkedPeerVersions));
    }

    public static List<Integer> getValidatedSupportedVersions(Collection<Integer> versions) {
        checkNotNull(versions, "Burning Man address list versions must not be null");
        checkArgument(!versions.isEmpty(), "Burning Man address list versions must not be empty");

        List<Integer> sortedDistinctVersions = new ArrayList<>(new TreeSet<>(versions));
        checkArgument(sortedDistinctVersions.size() == versions.size(),
                "Burning Man address list versions must not contain duplicates");
        checkArgument(sortedDistinctVersions.stream().allMatch(version -> version > 0),
                "Burning Man address list versions must be positive");
        checkArgument(sortedDistinctVersions.equals(new ArrayList<>(versions)),
                "Burning Man address list versions must be sorted");
        return sortedDistinctVersions;
    }

    private NavigableMap<Integer, BurningManAddressList> loadAddressLists() {
        TreeMap<Integer, BurningManAddressList> result = new TreeMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = BurningManAddressListService.class.getClassLoader();
        }

        try {
            Enumeration<URL> resourceDirs = classLoader.getResources(RESOURCE_DIR);
            while (resourceDirs.hasMoreElements()) {
                URL resourceDir = resourceDirs.nextElement();
                loadAddressListsFromResourceDir(resourceDir, result);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Burning Man address list resources", e);
        }

        checkArgument(!result.isEmpty(), "No Burning Man address list resources found");
        return result;
    }

    private void loadAddressListsFromResourceDir(URL resourceDir,
                                                 TreeMap<Integer, BurningManAddressList> result) {
        try {
            if ("file".equals(resourceDir.getProtocol())) {
                loadAddressListsFromFileSystem(Paths.get(resourceDir.toURI()), result);
            } else if ("jar".equals(resourceDir.getProtocol())) {
                loadAddressListsFromJar(resourceDir, result);
            } else {
                log.warn("Unsupported Burning Man address list resource protocol {} at {}",
                        resourceDir.getProtocol(), resourceDir);
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Could not load Burning Man address list resources from " + resourceDir, e);
        }
    }

    private void loadAddressListsFromFileSystem(Path resourceDir,
                                                TreeMap<Integer, BurningManAddressList> result) throws IOException {
        try (Stream<Path> paths = Files.list(resourceDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> loadAddressList(path, result));
        }
    }

    private void loadAddressList(Path path,
                                 TreeMap<Integer, BurningManAddressList> result) {
        String fileName = path.getFileName().toString();
        if (!FILE_NAME_PATTERN.matcher(fileName).matches()) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loadAddressList(fileName, reader, result);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Burning Man address list resource " + path, e);
        }
    }

    private void loadAddressListsFromJar(URL resourceDir,
                                         TreeMap<Integer, BurningManAddressList> result) throws IOException {
        JarURLConnection connection = (JarURLConnection) resourceDir.openConnection();
        connection.setUseCaches(false);
        String resourceEntryName = connection.getEntryName();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String entryName = jarEntry.getName();
                if (jarEntry.isDirectory() ||
                        !entryName.startsWith(resourceEntryName + "/") ||
                        entryName.indexOf('/', resourceEntryName.length() + 1) >= 0) {
                    continue;
                }

                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (!FILE_NAME_PATTERN.matcher(fileName).matches()) {
                    continue;
                }

                try (Reader reader = new InputStreamReader(jarFile.getInputStream(jarEntry), StandardCharsets.UTF_8)) {
                    loadAddressList(fileName, reader, result);
                }
            }
        }
    }

    private void loadAddressList(String fileName,
                                 Reader reader,
                                 TreeMap<Integer, BurningManAddressList> result) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
        checkArgument(matcher.matches(), "Invalid Burning Man address list file name " + fileName);
        int versionFromFileName = Integer.parseInt(matcher.group(1));
        BurningManAddressList addressList = GSON.fromJson(reader, BurningManAddressList.class);
        validateAddressList(fileName, versionFromFileName, addressList);
        BurningManAddressList previous = result.put(addressList.getListVersion(), addressList);
        checkArgument(previous == null,
                "Duplicate Burning Man address list version %s in %s", addressList.getListVersion(), fileName);
    }

    private void validateAddressList(String fileName,
                                     int versionFromFileName,
                                     BurningManAddressList addressList) {
        checkNotNull(addressList, "Burning Man address list must not be null");
        checkArgument(addressList.getSchemaVersion() == BurningManAddressList.SCHEMA_VERSION,
                "Invalid schemaVersion in %s", fileName);
        checkArgument(addressList.getListVersion() == versionFromFileName,
                "listVersion in %s must match file name version", fileName);
        checkArgument(!isBlank(addressList.getNetwork()), "network must not be blank in %s", fileName);
        checkArgument(addressList.getChainHeight() > 0, "chainHeight must be positive in %s", fileName);
        checkArgument(addressList.getBurningManSelectionHeight() > 0,
                "burningManSelectionHeight must be positive in %s", fileName);
        checkArgument(!isBlank(addressList.getLegacyBurningManAddress()),
                "legacyBurningManAddress must not be blank in %s", fileName);
        checkArgument(addressList.getEntries() != null && !addressList.getEntries().isEmpty(),
                "entries must not be empty in %s", fileName);

        List<String> receiverAddresses = addressList.getEntries().stream()
                .map(BurningManAddressList.Entry::getReceiverAddress)
                .collect(Collectors.toList());
        checkArgument(receiverAddresses.stream().noneMatch(BurningManAddressListService::isBlank),
                "receiverAddress must not be blank in %s", fileName);
        checkArgument(new TreeSet<>(receiverAddresses).size() == receiverAddresses.size(),
                "receiverAddress values must be unique in %s", fileName);
        checkArgument(addressList.getEntries().stream()
                        .allMatch(entry -> Double.isFinite(entry.getCappedBurnAmountShare()) &&
                                entry.getCappedBurnAmountShare() >= 0),
                "cappedBurnAmountShare must be finite and not negative in %s", fileName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
