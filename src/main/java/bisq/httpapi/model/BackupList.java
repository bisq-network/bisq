package bisq.httpapi.model;

import java.util.List;

public class BackupList {

    public List<String> backups;

    public BackupList(List<String> backups) {
        this.backups = backups;
    }
}
