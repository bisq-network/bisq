package network.bisq.api.model;

public class ChangePassword {

    public String newPassword;
    public String oldPassword;

    public ChangePassword() {
    }

    public ChangePassword(String newPassword, String oldPassword) {
        this.newPassword = newPassword;
        this.oldPassword = oldPassword;
    }
}
