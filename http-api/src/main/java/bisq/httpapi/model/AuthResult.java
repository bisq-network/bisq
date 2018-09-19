package bisq.httpapi.model;

public class AuthResult {

    public String token;

    public AuthResult() {
    }

    public AuthResult(String token) {
        this.token = token;
    }
}
