package bisq.api.http.service.auth;

import bisq.api.http.exceptions.UnauthorizedException;

import bisq.core.app.BisqEnvironment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import com.github.javafaker.Faker;

public class ApiPasswordManagerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private String dataDir;
    private BisqEnvironment bisqEnvironmentMock;
    private TokenRegistry tokenRegistryMock;
    private ApiPasswordManager apiPasswordManager;

    private static String getRandomPasswordDifferentThan(String otherPassword) {
        String newPassword;
        do {
            newPassword = Faker.instance().internet().password();
        } while (otherPassword.equals(newPassword));
        return newPassword;
    }

    @Before
    public void setUp() throws Exception {
        this.bisqEnvironmentMock = mock(BisqEnvironment.class);
        this.tokenRegistryMock = new TokenRegistry();
        this.dataDir = createTempDirectory();
        when(bisqEnvironmentMock.getAppDataDir()).thenReturn(dataDir);
        assertPasswordFileDoesNotExist();
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(new File(this.dataDir));
    }

    @Test
    public void constructor_passwordFileNotReadable_throwsException() {
        //        Given
        File invalidPasswordFile = getPasswordFile();
        assertTrue(invalidPasswordFile.mkdir());
        invalidPasswordFile.deleteOnExit();
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Unable to read api password file");

        //        When
        new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
    }

    @Test
    public void constructor_passwordFileContainsMoreThan2Lines_doesNotSetPassword() throws IOException {
        //        Given
        writePasswordFile("a:b\nd:e");

        //        When
        ApiPasswordManager apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        boolean passwordSet = apiPasswordManager.isPasswordSet();

        //        Then
        assertFalse(passwordSet);
    }

    @Test
    public void constructor_passwordFileContainsMoreThan2Separators_doesNotSetPassword() throws IOException {
        //        Given
        writePasswordFile("a:b:e");

        //        When
        ApiPasswordManager apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        boolean passwordSet = apiPasswordManager.isPasswordSet();

        //        Then
        assertFalse(passwordSet);
    }

    @Test
    public void isPasswordSet_noPasswordFile_returnsFalse() {
        //        Given

        //        When
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);

        //        Then
        assertFalse(apiPasswordManager.isPasswordSet());
    }

    @Test
    public void isPasswordSet_passwordFileExists_returnsTrue() {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String newPassword = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, newPassword);

        //        When
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);

        //        Then
        assertTrue(this.apiPasswordManager.isPasswordSet());
        assertTrue(anotherPasswordManager.isPasswordSet());
    }

    @Test
    public void authenticate_noPasswordFile_throwsUnauthorizedException() {
        //        Given
        expectedException.expect(UnauthorizedException.class);
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        assertPasswordFileDoesNotExist();

        //        When
        apiPasswordManager.authenticate(getRandomPasswordDifferentThan(""));
    }

    @Test
    public void authenticate_passwordDoesNotMatch_throwsUnauthorizedException() {
        //        Given
        expectedException.expect(UnauthorizedException.class);
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);

        //        When
        apiPasswordManager.authenticate(getRandomPasswordDifferentThan(password));
    }

    @Test
    public void authenticate_passwordDoesNotMatchAndDifferentPasswordManagerInstance_throwsUnauthorizedException() {
        //        Given
        expectedException.expect(UnauthorizedException.class);
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);

        //        When
        anotherPasswordManager.authenticate(getRandomPasswordDifferentThan(password));
    }

    @Test
    public void authenticate_passwordMatches_returnsTokenFromTokenRegistry() {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);

        //        When
        String token = anotherPasswordManager.authenticate(password);

        //        Then
        assertNotNull(token);
        assertTrue(tokenRegistryMock.isValidToken(token));
    }

    @Test
    public void changePassword_noPasswordFileAndNewPasswordSet_returnsTokenFromRegistry() {
        //        Given
        assertPasswordFileDoesNotExist();
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");

        //        When
        String token = apiPasswordManager.changePassword(null, password);

        //        Then
        assertNotNull(token);
        assertTrue(tokenRegistryMock.isValidToken(token));
        assertNotNull(apiPasswordManager.authenticate(password));
    }

    @Test
    public void changePassword_noPasswordFileAndNewPasswordSet_changesThePassword() {
        //        Given
        assertPasswordFileDoesNotExist();
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");

        //        When
        apiPasswordManager.changePassword(null, password);
        String token = apiPasswordManager.authenticate(password);

        //        Then
        assertTrue(tokenRegistryMock.isValidToken(token));
    }

    @Test
    public void changePassword_noPasswordFileAndNewPasswordSet_storesThePasswordInPasswordFile() {
        //        Given
        assertPasswordFileDoesNotExist();
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");

        //        When
        apiPasswordManager.changePassword(null, password);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String token = anotherPasswordManager.authenticate(password);

        //        Then
        assertPasswordFileExists();
        assertTrue(tokenRegistryMock.isValidToken(token));
    }

    @Test
    public void changePassword_passwordDoesNotMatch_throwsUnauthorizedException() {
        //        Given
        expectedException.expect(UnauthorizedException.class);
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String invalidPassword = getRandomPasswordDifferentThan(password);
        String newPassword = getRandomPasswordDifferentThan(password);

        //        When
        anotherPasswordManager.changePassword(invalidPassword, newPassword);
    }

    @Test
    public void changePassword_oldPasswordIsNull_throwsUnauthorizedException() {
        //        Given
        expectedException.expect(UnauthorizedException.class);
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String newPassword = getRandomPasswordDifferentThan(password);

        //        When
        anotherPasswordManager.changePassword(null, newPassword);
    }

    @Test
    public void changePassword_oldPasswordMatchesTheOneInPasswordFileAndNewPasswordSet_changesThePasswordInPasswordFile() {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        String newPassword = getRandomPasswordDifferentThan(password);

        //        When
        apiPasswordManager.changePassword(password, newPassword);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String token = anotherPasswordManager.authenticate(newPassword);

        //        Then
        assertTrue(tokenRegistryMock.isValidToken(token));
    }

    @Test
    public void changePassword_oldPasswordMatchesTheOneInPasswordFileAndNewPasswordSet_oldPasswordBecomesInvalid() {
        //        Given
        expectedException.expect(UnauthorizedException.class);
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        String newPassword = getRandomPasswordDifferentThan(password);

        //        When
        apiPasswordManager.changePassword(password, newPassword);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        anotherPasswordManager.authenticate(password);
    }

    @Test
    public void changePassword_oldPasswordMatchesTheOneInPasswordFileAndNewPasswordIsNull_unSetsPassword() {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);

        //        When
        apiPasswordManager.changePassword(password, null);
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);

        //        Then
        assertFalse(apiPasswordManager.isPasswordSet());
        assertFalse(anotherPasswordManager.isPasswordSet());
        expectedException.expect(UnauthorizedException.class);
        anotherPasswordManager.authenticate(password);
    }

    @Test
    public void changePassword_oldPasswordMatchesTheOneInPasswordFileAndNewPasswordIsEmptyString_unSetsPassword() {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);

        //        When
        apiPasswordManager.changePassword(password, "");
        ApiPasswordManager anotherPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);

        //        Then
        assertFalse(apiPasswordManager.isPasswordSet());
        assertFalse(anotherPasswordManager.isPasswordSet());
        expectedException.expect(UnauthorizedException.class);
        anotherPasswordManager.authenticate(password);
    }

    @Test
    public void changePassword_newPasswordNullButPasswordFileNotWritable_throwsException() throws IOException {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        File passwordFile = getPasswordFile();
        assertTrue(passwordFile.delete());
        assertTrue(passwordFile.mkdir());
        assertNotNull(File.createTempFile("bisq", "api", passwordFile));
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Unable to remove password file: " + passwordFile.getAbsolutePath());

        //        When
        apiPasswordManager.changePassword(password, null);
    }

    @Test
    public void changePassword_passwordFileNotWritable_throwsException() throws IOException {
        //        Given
        apiPasswordManager = new ApiPasswordManager(bisqEnvironmentMock, tokenRegistryMock);
        String password = getRandomPasswordDifferentThan("");
        apiPasswordManager.changePassword(null, password);
        File passwordFile = getPasswordFile();
        assertTrue(passwordFile.delete());
        assertTrue(passwordFile.mkdir());
        assertNotNull(File.createTempFile("bisq", "api", passwordFile));
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Unable to write password file: " + passwordFile.getAbsolutePath());

        //        When
        apiPasswordManager.changePassword(password, getRandomPasswordDifferentThan(password));
    }

    private boolean passwordFileExists() {
        return getPasswordFile().exists();
    }

    private void assertPasswordFileDoesNotExist() {
        assertFalse(passwordFileExists());
    }

    private void assertPasswordFileExists() {
        assertTrue(passwordFileExists());
    }

    private String createTempDirectory() throws IOException {
        File tempFile = File.createTempFile("bisq", "api");
        if (!tempFile.delete()) {
            throw new RuntimeException("Unable to create temporary directory: " + tempFile.getAbsolutePath());
        }
        if (!tempFile.mkdir()) {
            throw new RuntimeException("Unable to create temporary directory: " + tempFile.getAbsolutePath());
        }
        return tempFile.getAbsolutePath();
    }

    @NotNull
    private File getPasswordFile() {
        return new File(this.dataDir, "apipasswd");
    }

    private void writePasswordFile(String data) throws IOException {
        File passwordFile = getPasswordFile();
        passwordFile.deleteOnExit();
        FileUtils.write(passwordFile, data, "UTF-8");
    }
}
