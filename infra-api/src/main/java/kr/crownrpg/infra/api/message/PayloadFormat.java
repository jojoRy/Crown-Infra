package kr.crownrpg.infra.api.message;

/**
 * Supported payload formats for infra messages.
 */
public enum PayloadFormat {
    JSON("json"),
    TEXT("text");

    private final String wireName;

    PayloadFormat(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static PayloadFormat fromWireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return JSON;
        }
        for (PayloadFormat format : values()) {
            if (format.wireName.equalsIgnoreCase(name.trim())) {
                return format;
            }
        }
        return JSON;
    }
}
