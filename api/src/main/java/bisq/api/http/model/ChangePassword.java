package bisq.api.http.model;

public class ChangePassword {

    public String newPassword;
    public String oldPassword;

    @SuppressWarnings("unused")
    public ChangePassword() {
    }

    public ChangePassword(String newPassword, String oldPassword) {
        this.newPassword = newPassword;
        this.oldPassword = oldPassword;
    }
}
