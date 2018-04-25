package io.bisq.api;

import io.restassured.http.ContentType;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.DockerContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
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

@RunWith(Arquillian.class)
public class BackupResourceIT {

    @DockerContainer
    Container alice = ContainerFactory.createApiContainer("alice", "8081->8080", 3333, false, false);

    private static String backupPath;

    @InSequence
    @Test
    public void waitForAllServicesToBeReady() throws InterruptedException {
        ApiTestHelper.waitForAllServicesToBeReady();
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

    private int getAlicePort() {
        return alice.getBindPort(8080);
    }
}
