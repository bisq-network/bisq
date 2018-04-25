package io.bisq.api;

import io.restassured.http.ContentType;
import org.arquillian.cube.ContainerObjectFactory;
import org.arquillian.cube.CubeController;
import org.arquillian.cube.HostPort;
import org.arquillian.cube.containerobject.Cube;
import org.arquillian.cube.containerobject.Environment;
import org.arquillian.cube.containerobject.Image;
import org.arquillian.cube.containerobject.Volume;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.arquillian.cube.spi.CubeOutput;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.bisq.api.RegexMatcher.matchesRegex;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class BackupResourceIT {

    private static final String APP_DIR_VOLUME_NAME = "alice-app-dir";
    private static final String APP_DIR_VOLUME_HOST_PATH = "/root/.local/share/Bisq";

    @ArquillianResource
    private CubeController cubeController;

    @ArquillianResource
    private ContainerObjectFactory factory;

    @DockerContainer
    private Container alice = ContainerFactory.createApiContainerBuilder("alice", "8081->8080", 3333, false, false)
            .withVolume(APP_DIR_VOLUME_NAME, APP_DIR_VOLUME_HOST_PATH)
            .build();

    private static String backupPath;

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
        removeAllBackups();
    }

    @InSequence(1)
    @Test
    public void createBackup_always_returns200() throws Exception {
        backupPath = given().
                port(getAlicePort()).
//
        when().
                        post("/api/v1/backups").
//
        then().
                        statusCode(200).
                        and().body("path", isA(String.class)).
                        extract().path("path");

    }

    @InSequence(2)
    @Test
    public void getBackupList_always_returnsListOfBackupFilenames() {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/backups").
//
        then().
                statusCode(200).
                and().body("backups", isA(List.class)).
                and().body("backups.size()", equalTo(1)).
                and().body("backups[0]", equalTo(backupPath))
        ;
    }

    @InSequence(3)
    @Test
    public void getBackup_backupDoesNotExist_returns404() throws Exception {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/backups/xyz").
//
        then().
                statusCode(404)
        ;
    }

    @InSequence(3)
    @Test
    public void getBackup_backupExist_returns200() throws Exception {
//        Make sure there is more than one backup
        createBackup_always_returns200();
        assertNumberOfBackups(2);

        final InputStream inputStream = given().
                port(getAlicePort()).
//
        when().
                        get("/api/v1/backups/" + backupPath).
//
        then().
                        statusCode(200).
                        and().contentType(ContentType.BINARY.toString()).
                        extract().asInputStream();
        final Set<String> zipEntries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry nextEntry;
            while (null != (nextEntry = zip.getNextEntry())) {
                zipEntries.add(nextEntry.getName());
            }
        }
        final List<String> expectedEntries = Arrays.asList("bisq.log",
                "btc_regtest/wallet/bisq_BTC.wallet",
                "btc_regtest/wallet/bisq_BSQ.wallet",
                "btc_regtest/wallet/bisq.spvchain",
                "btc_regtest/db/UserPayload",
                "btc_regtest/db/AddressEntryList",
                "btc_regtest/db/PreferencesPayload",
                "btc_regtest/keys/enc.key",
                "btc_regtest/keys/sig.key",
                "bisq.properties"
        );
        for (String expectedEntry : expectedEntries)
            Assert.assertThat(zipEntries, hasItem(expectedEntry));
//        Backup should not contain "backup" directory
        Assert.assertThat(zipEntries, not(hasItem(matchesRegex("^backup/?.*"))));
    }

    @InSequence(4)
    @Test
    public void removeBackup_backupDoesNotExist_returns404() throws Exception {
        given().
                port(getAlicePort()).
//
        when().
                delete("/api/v1/backups/xyz").
//
        then().
                statusCode(404)
        ;
    }

    @InSequence(5)
    @Test
    public void removeBackup_backupExist_returns204() throws Exception {
        given().
                port(getAlicePort()).
//
        when().
                delete("/api/v1/backups/" + backupPath).
//
        then().
                statusCode(204)
        ;
        assertNumberOfBackups(1);
    }

    @InSequence(6)
    @Test
    public void restore() throws Exception {
        final int alicePort = getAlicePort();
        final String walletAddress = given().
                port(alicePort).
//
        when().
                        post("/api/v1/wallet/addresses").
//
        then().
                        statusCode(200).
                        extract().path("address");

        createBackup_always_returns200();

        ApiTestHelper.waitForP2PMsgPropagation();  //this is just some wait, not necessarily for p2p

        given().
                port(alicePort).
//
        when().
                post("/api/v1/backups/" + backupPath + "/restore").
//
        then().
                statusCode(204);

        final ApiContainer apiContainer = factory.createContainerObject(ApiContainer.class);
        try {
            ApiTestHelper.waitForAllServicesToBeReady();

            given().
                    port(apiContainer.port).
                    queryParam("purpose", "RECEIVE_FUNDS").
//
        when().
                    get("/api/v1/wallet/addresses").
//
        then().
                    statusCode(200).
                    and().body("walletAddresses[0].address", equalTo(walletAddress));
        } finally {
            cubeController.stop(ApiContainer.CUBE_ID);
            ApiTestHelper.waitForAllServicesToBeReady();
            cubeController.destroy(ApiContainer.CUBE_ID);
        }
    }

    private void assertNumberOfBackups(int numberOfBackups) {
        given().
                port(getAlicePort()).
//
        when().
                get("/api/v1/backups").
//
        then().
                statusCode(200).
                and().body("backups", isA(List.class)).
                and().body("backups.size()", equalTo(numberOfBackups))
        ;
    }

    private void removeAllBackups() {
        final CubeOutput cubeOutput = alice.exec("rm", APP_DIR_VOLUME_HOST_PATH + "/backup", "-rf");
        assertEquals("Command 'rm backup/*' should succeed", "", cubeOutput.getError());
    }


    private int getAlicePort() {
        return alice.getBindPort(8080);
    }


    @Environment(key = ContainerFactory.ENV_USE_LOCALHOST_FOR_P2P_KEY, value = ContainerFactory.ENV_USE_LOCALHOST_FOR_P2P_VALUE)
    @Environment(key = ContainerFactory.ENV_BASE_CURRENCY_NETWORK_KEY, value = ContainerFactory.ENV_BASE_CURRENCY_NETWORK_VALUE)
    @Environment(key = ContainerFactory.ENV_BITCOIN_REGTEST_HOST_KEY, value = ContainerFactory.ENV_BITCOIN_REGTEST_HOST_VALUE)
    @Environment(key = ContainerFactory.ENV_BTC_NODES_KEY, value = ContainerFactory.ENV_BTC_NODES_VALUE)
    @Environment(key = ContainerFactory.ENV_LOG_LEVEL_KEY, value = ContainerFactory.ENV_LOG_LEVEL_VALUE)
    @Environment(key = ContainerFactory.ENV_BISQ_API_HOST_KEY, value = ContainerFactory.ENV_BISQ_API_HOST_VALUE)
    @Cube(value = ApiContainer.CUBE_ID, portBinding = "8080")
    @Volume(hostPath = ContainerFactory.M2_VOLUME_NAME, containerPath = ContainerFactory.M2_VOLUME_CONTAINER_PATH)
    @Volume(hostPath = APP_DIR_VOLUME_NAME, containerPath = APP_DIR_VOLUME_HOST_PATH)
    @Image(ContainerFactory.API_IMAGE)
    public static class ApiContainer {

        public static final String CUBE_ID = "bisq-api-alice-backup";

        @HostPort(8080)
        public int port;
    }
}
