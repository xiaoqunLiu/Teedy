package com.sismics.docs.core.constant;

/**
 * Configuration parameters.
 *
 * @author jtremeaux
 */
public enum ConfigType {
    /**
     * Lucene directory storage type.
     */
    LUCENE_DIRECTORY_STORAGE("lucene_directory_storage"),
    /**
     * Theme configuration.
     */
    THEME("theme"),

    /**
     * Guest login.
     */
    GUEST_LOGIN("guest_login"),

    /**
     * OCR enabled.
     */
    OCR_ENABLED("ocr_enabled"),

    /**
     * Default language.
     */
    DEFAULT_LANGUAGE("default_language"),

    /**
     * SMTP server configuration.
     */
    SMTP_HOSTNAME("smtp_hostname"),
    SMTP_PORT("smtp_port"),
    SMTP_FROM("smtp_from"),
    SMTP_USERNAME("smtp_username"),
    SMTP_PASSWORD("smtp_password"),

    /**
     * Inbox scanning configuration.
     */
    INBOX_ENABLED("inbox_enabled"),
    INBOX_HOSTNAME("inbox_hostname"),
    INBOX_PORT("inbox_port"),
    INBOX_STARTTLS("inbox_starttls"),
    INBOX_USERNAME("inbox_username"),
    INBOX_PASSWORD("inbox_password"),
    INBOX_FOLDER("inbox_folder"),
    INBOX_TAG("inbox_tag"),
    INBOX_AUTOMATIC_TAGS("inbox_automatic_tags"),
    INBOX_DELETE_IMPORTED("inbox_delete_imported"),

    /**
     * LDAP connection.
     */
    LDAP_ENABLED("ldap_enabled"),
    LDAP_HOST("ldap_host"),
    LDAP_PORT("ldap_port"),
    LDAP_USESSL("ldap_usessl"),
    LDAP_ADMIN_DN("ldap_admin_dn"),
    LDAP_ADMIN_PASSWORD("ldap_admin_password"),
    LDAP_BASE_DN("ldap_base_dn"),
    LDAP_FILTER("ldap_filter"),
    LDAP_DEFAULT_EMAIL("ldap_default_email"),
    LDAP_DEFAULT_STORAGE("ldap_default_storage"),

    /**
     * Youdao Translation API configuration.
     */
    YOUDAO_APP_KEY("youdao.app_key"),
    YOUDAO_APP_SECRET("youdao.app_secret");

    private final String key;

    ConfigType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
