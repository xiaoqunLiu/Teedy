package com.sismics.docs.core.constant;

/**
 * Configuration types.
 */
public enum ConfigType {
    DEFAULT_LANGUAGE("default_language"),
    GUEST_LOGIN("guest_login"),
    OCR_ENABLED("ocr_enabled"),
    SMTP_HOSTNAME("smtp_hostname"),
    SMTP_PORT("smtp_port"),
    SMTP_USERNAME("smtp_username"),
    SMTP_PASSWORD("smtp_password"),
    SMTP_FROM("smtp_from"),
    INBOX_ENABLED("inbox_enabled"),
    INBOX_AUTOMATIC_TAGS("inbox_automatic_tags"),
    INBOX_DELETE_IMPORTED("inbox_delete_imported"),
    INBOX_HOSTNAME("inbox_hostname"),
    INBOX_PORT("inbox_port"),
    INBOX_STARTTLS("inbox_starttls"),
    INBOX_USERNAME("inbox_username"),
    INBOX_PASSWORD("inbox_password"),
    INBOX_FOLDER("inbox_folder"),
    INBOX_TAG("inbox_tag"),
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
    THEME("theme"),
    LUCENE_DIRECTORY_STORAGE("lucene_directory_storage"),
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