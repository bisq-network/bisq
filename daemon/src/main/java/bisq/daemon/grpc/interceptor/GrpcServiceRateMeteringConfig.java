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

package bisq.daemon.grpc.interceptor;

import io.grpc.ServerInterceptor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.common.annotations.VisibleForTesting;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.file.FileUtil.deleteFileIfExists;
import static bisq.common.file.FileUtil.renameFile;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

@VisibleForTesting
@Slf4j
public class GrpcServiceRateMeteringConfig {

    public static final String RATE_METERS_CONFIG_FILENAME = "ratemeters.json";

    private static final String KEY_GRPC_SERVICE_CLASS_NAME = "grpcServiceClassName";
    private static final String KEY_METHOD_RATE_METERS = "methodRateMeters";
    private static final String KEY_ALLOWED_CALL_PER_TIME_WINDOW = "allowedCallsPerTimeWindow";
    private static final String KEY_TIME_UNIT = "timeUnit";
    private static final String KEY_NUM_TIME_UNITS = "numTimeUnits";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final List<Map<String, GrpcCallRateMeter>> methodRateMeters;
    private final String grpcServiceClassName;

    public GrpcServiceRateMeteringConfig(String grpcServiceClassName) {
        this(grpcServiceClassName, new ArrayList<>());
    }

    public GrpcServiceRateMeteringConfig(String grpcServiceClassName,
                                         List<Map<String, GrpcCallRateMeter>> methodRateMeters) {
        this.grpcServiceClassName = grpcServiceClassName;
        this.methodRateMeters = methodRateMeters;
    }

    @SuppressWarnings("unused")
    public GrpcServiceRateMeteringConfig addMethodCallRateMeter(String methodName,
                                                                int maxCalls,
                                                                TimeUnit timeUnit) {
        return addMethodCallRateMeter(methodName, maxCalls, timeUnit, 1);
    }

    public GrpcServiceRateMeteringConfig addMethodCallRateMeter(String methodName,
                                                                int maxCalls,
                                                                TimeUnit timeUnit,
                                                                int numTimeUnits) {
        methodRateMeters.add(new LinkedHashMap<>() {{
            put(methodName, new GrpcCallRateMeter(maxCalls, timeUnit, numTimeUnits));
        }});
        return this;
    }

    public boolean isConfigForGrpcService(Class<?> clazz) {
        return isConfigForGrpcService(clazz.getSimpleName());
    }

    public boolean isConfigForGrpcService(String grpcServiceClassSimpleName) {
        return this.grpcServiceClassName.equals(grpcServiceClassSimpleName);
    }

    @Override
    public String toString() {
        return "GrpcServiceRateMeteringConfig{" + "\n" +
                "  grpcServiceClassName='" + grpcServiceClassName + '\'' + "\n" +
                ", methodRateMeters=" + methodRateMeters + "\n" +
                '}';
    }

    public static Optional<ServerInterceptor> getCustomRateMeteringInterceptor(File installationDir,
                                                                               Class<?> grpcServiceClass) {
        File configFile = new File(installationDir, RATE_METERS_CONFIG_FILENAME);
        return configFile.exists()
                ? toServerInterceptor(configFile, grpcServiceClass)
                : Optional.empty();
    }

    public static Optional<ServerInterceptor> toServerInterceptor(File configFile, Class<?> grpcServiceClass) {
        // From a global rate metering config file, create a specific gRPC service
        // interceptor configuration in the form of an interceptor constructor argument,
        // a map<method-name, rate-meter>.
        // Transforming json into the List<Map<String, GrpcCallRateMeter>> is a bit
        // convoluted due to Gson's loss of generic type information during deserialization.
        Optional<GrpcServiceRateMeteringConfig> grpcServiceConfig = getAllDeserializedConfigs(configFile)
                .stream().filter(x -> x.isConfigForGrpcService(grpcServiceClass)).findFirst();
        if (grpcServiceConfig.isPresent()) {
            Map<String, GrpcCallRateMeter> serviceCallRateMeters = new HashMap<>();
            for (Map<String, GrpcCallRateMeter> methodToRateMeterMap : grpcServiceConfig.get().methodRateMeters) {
                Map.Entry<String, GrpcCallRateMeter> entry = methodToRateMeterMap.entrySet().stream().findFirst().orElseThrow(()
                        -> new IllegalStateException("Gson deserialized a method rate meter configuration into an empty map."));
                serviceCallRateMeters.put(entry.getKey(), entry.getValue());
            }
            return Optional.of(new CallRateMeteringInterceptor(serviceCallRateMeters));
        } else {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, GrpcCallRateMeter>> getMethodRateMetersMap(Map<String, Object> gsonMap) {
        List<Map<String, GrpcCallRateMeter>> rateMeters = new ArrayList<>();
        // Each gsonMap is a Map<String, Object> with a single entry:
        // {getVersion={allowedCallsPerTimeUnit=8.0, timeUnit=SECONDS, callsCount=0.0, isRunning=false}}
        // Convert it to a multiple entry Map<String, GrpcCallRateMeter>, where the key
        // is a method name.
        for (Map<String, Object> singleEntryRateMeterMap : (List<Map<String, Object>>) gsonMap.get(KEY_METHOD_RATE_METERS)) {
            log.debug("Gson's single entry {} {}<String, Object> = {}",
                    gsonMap.get(KEY_GRPC_SERVICE_CLASS_NAME),
                    singleEntryRateMeterMap.getClass().getSimpleName(),
                    singleEntryRateMeterMap);
            Map.Entry<String, Object> entry = singleEntryRateMeterMap.entrySet().stream().findFirst().orElseThrow(()
                    -> new IllegalStateException("Gson deserialized a method rate meter configuration into an empty map."));
            String methodName = entry.getKey();
            GrpcCallRateMeter rateMeter = getGrpcCallRateMeter(entry);
            rateMeters.add(new LinkedHashMap<>() {{
                put(methodName, rateMeter);
            }});
        }
        return rateMeters;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<GrpcServiceRateMeteringConfig> deserialize(File configFile) {
        verifyConfigFile(configFile);
        List<GrpcServiceRateMeteringConfig> serviceMethodConfigurations = new ArrayList<>();
        // Gson cannot deserialize a json string to List<GrpcServiceRateMeteringConfig>
        // so easily for us, so we do it here before returning the list of configurations.
        List rawConfigList = gson.fromJson(toJson(configFile), ArrayList.class);
        // Gson gave us a list of maps with keys grpcServiceClassName, methodRateMeters:
        //          String grpcServiceClassName
        //          List<Map> methodRateMeters
        for (Object rawConfig : rawConfigList) {
            Map<String, Object> gsonMap = (Map<String, Object>) rawConfig;
            String grpcServiceClassName = (String) gsonMap.get(KEY_GRPC_SERVICE_CLASS_NAME);
            List<Map<String, GrpcCallRateMeter>> rateMeters = getMethodRateMetersMap(gsonMap);
            serviceMethodConfigurations.add(new GrpcServiceRateMeteringConfig(grpcServiceClassName, rateMeters));
        }
        return serviceMethodConfigurations;
    }

    @SuppressWarnings("unchecked")
    private static GrpcCallRateMeter getGrpcCallRateMeter(Map.Entry<String, Object> gsonEntry) {
        Map<String, Object> valueMap = (Map<String, Object>) gsonEntry.getValue();
        int allowedCallsPerTimeWindow = ((Number) valueMap.get(KEY_ALLOWED_CALL_PER_TIME_WINDOW)).intValue();
        TimeUnit timeUnit = TimeUnit.valueOf((String) valueMap.get(KEY_TIME_UNIT));
        int numTimeUnits = ((Number) valueMap.get(KEY_NUM_TIME_UNITS)).intValue();
        return new GrpcCallRateMeter(allowedCallsPerTimeWindow, timeUnit, numTimeUnits);
    }

    private static void verifyConfigFile(File configFile) {
        if (configFile == null)
            throw new IllegalStateException("Cannot read null json config file.");

        if (!configFile.exists())
            throw new IllegalStateException(format("cannot find json config file %s", configFile.getAbsolutePath()));
    }

    private static String toJson(File configFile) {
        try {
            return new String(readAllBytes(Paths.get(configFile.getAbsolutePath())));
        } catch (IOException ex) {
            throw new IllegalStateException(format("Cannot read json string from file %s.",
                    configFile.getAbsolutePath()));
        }
    }

    private static List<GrpcServiceRateMeteringConfig> allDeserializedConfigs;

    private static List<GrpcServiceRateMeteringConfig> getAllDeserializedConfigs(File configFile) {
        // We deserialize once, not for each gRPC service wanting an interceptor.
        if (allDeserializedConfigs == null)
            allDeserializedConfigs = deserialize(configFile);

        return allDeserializedConfigs;
    }

    @VisibleForTesting
    public static class Builder {
        private final List<GrpcServiceRateMeteringConfig> rateMeterConfigs = new ArrayList<>();

        public void addCallRateMeter(String grpcServiceClassName,
                                     String methodName,
                                     int maxCalls,
                                     TimeUnit timeUnit) {
            addCallRateMeter(grpcServiceClassName,
                    methodName,
                    maxCalls,
                    timeUnit,
                    1);
        }

        public void addCallRateMeter(String grpcServiceClassName,
                                     String methodName,
                                     int maxCalls,
                                     TimeUnit timeUnit,
                                     int numTimeUnits) {
            log.info("Adding call rate metering definition {}.{} ({}/{}ms).",
                    grpcServiceClassName,
                    methodName,
                    maxCalls,
                    timeUnit.toMillis(1) * numTimeUnits);
            rateMeterConfigs.stream().filter(c -> c.isConfigForGrpcService(grpcServiceClassName))
                    .findFirst().ifPresentOrElse(
                    (config) -> config.addMethodCallRateMeter(methodName, maxCalls, timeUnit, numTimeUnits),
                    () -> rateMeterConfigs.add(new GrpcServiceRateMeteringConfig(grpcServiceClassName)
                            .addMethodCallRateMeter(methodName, maxCalls, timeUnit, numTimeUnits)));
        }

        public File build() {
            File tmpFile = serializeRateMeterDefinitions();
            File configFile = Paths.get(getProperty("java.io.tmpdir"), "ratemeters.json").toFile();
            try {
                deleteFileIfExists(configFile);
                renameFile(tmpFile, configFile);
            } catch (IOException ex) {
                throw new IllegalStateException(format("Could not create config file %s.",
                        configFile.getAbsolutePath()), ex);
            }
            return configFile;
        }

        private File serializeRateMeterDefinitions() {
            String json = gson.toJson(rateMeterConfigs);
            File file = createTmpFile();
            try (OutputStreamWriter outputStreamWriter =
                         new OutputStreamWriter(new FileOutputStream(checkNotNull(file), false), UTF_8)) {
                outputStreamWriter.write(json);
            } catch (Exception ex) {
                throw new IllegalStateException(format("Cannot write file for json string %s.", json), ex);
            }
            return file;
        }

        private File createTmpFile() {
            File file;
            try {
                file = File.createTempFile("ratemeters_",
                        ".tmp",
                        Paths.get(getProperty("java.io.tmpdir")).toFile());
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot create tmp ratemeters json file.", ex);
            }
            return file;
        }
    }
}
