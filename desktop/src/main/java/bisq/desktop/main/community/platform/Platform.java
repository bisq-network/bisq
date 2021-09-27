package bisq.desktop.main.community.platform;

public class Platform {
    private final String title;
    private final String description;
    private final String url;
    private final String iconClass;

    public Platform(String title, String description, String url, String iconClass) {
        this.title = title;
        this.description = description;
        this.iconClass = iconClass;
        this.url = url;
    }
    public Platform(String title, String description, String url) {
        this(title, description, url, null);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getIconClass() {
        return iconClass;
    }
}
