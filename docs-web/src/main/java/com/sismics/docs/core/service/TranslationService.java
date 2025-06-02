package com.sismics.docs.core.service;

import com.google.common.base.Strings;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.constant.ConfigType;
import com.sismics.util.context.ThreadLocalContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Translation service using Youdao API.
 */
public class TranslationService {
    private static final String YOUDAO_API_URL = "https://openapi.youdao.com/api";
    private static final String YOUDAO_APP_KEY = ConfigUtil.getConfigStringValue(ConfigType.YOUDAO_APP_KEY);
    private static final String YOUDAO_APP_SECRET = ConfigUtil.getConfigStringValue(ConfigType.YOUDAO_APP_SECRET);

    public TranslationService() {
        // System.out.println("[TranslationService] YOUDAO_APP_KEY=" + YOUDAO_APP_KEY);
        // System.out.println("[TranslationService] YOUDAO_APP_SECRET=" + YOUDAO_APP_SECRET);
        System.out.println("[TranslationService] TranslationService initialized");
    }

    /**
     * Translate text using Youdao API.
     *
     * @param content Text to translate
     * @param from Source language
     * @param to Target language
     * @return Translated text
     * @throws IOException If an error occurs during translation
     */
    public String translate(String content, String from, String to) throws IOException {
        if (content == null || content.isEmpty()) {
            throw new IOException("Content is empty");
        }
        int maxLen = 4000;
        StringBuilder translated = new StringBuilder();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxLen, content.length());
            String part = content.substring(start, end);
            String partResult = translateSingle(part, from, to);
            translated.append(partResult);
            start = end;
        }
        return translated.toString();
    }

    // 单段调用
    private String translateSingle(String content, String from, String to) throws IOException {
        if (Strings.isNullOrEmpty(content)) {
            return content;
        }

        String salt = UUID.randomUUID().toString();
        String curtime = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = DigestUtils.sha256Hex(YOUDAO_APP_KEY + truncate(content) + salt + curtime + YOUDAO_APP_SECRET);

        // 使用 application/x-www-form-urlencoded 方式提交参数
        java.util.List<org.apache.http.NameValuePair> params = new java.util.ArrayList<>();
        params.add(new org.apache.http.message.BasicNameValuePair("q", content));
        params.add(new org.apache.http.message.BasicNameValuePair("from", from));
        params.add(new org.apache.http.message.BasicNameValuePair("to", to));
        params.add(new org.apache.http.message.BasicNameValuePair("appKey", YOUDAO_APP_KEY));
        params.add(new org.apache.http.message.BasicNameValuePair("salt", salt));
        params.add(new org.apache.http.message.BasicNameValuePair("sign", sign));
        params.add(new org.apache.http.message.BasicNameValuePair("signType", "v3"));
        params.add(new org.apache.http.message.BasicNameValuePair("curtime", curtime));

        try (org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
            org.apache.http.client.methods.HttpPost httpPost = new org.apache.http.client.methods.HttpPost(YOUDAO_API_URL);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setEntity(new org.apache.http.client.entity.UrlEncodedFormEntity(params, java.nio.charset.StandardCharsets.UTF_8));

            try (org.apache.http.client.methods.CloseableHttpResponse response = httpClient.execute(httpPost)) {
                org.apache.http.HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String result = org.apache.http.util.EntityUtils.toString(entity);
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(result);

                    // 输出完整响应
                    // System.out.println("[Youdao API] response: " + jsonResponse.toString());

                    // 检查 errorCode
                    if (jsonResponse.has("errorCode") && !"0".equals(jsonResponse.getString("errorCode"))) {
                        throw new IOException("Translation failed, errorCode=" + jsonResponse.getString("errorCode")
                                + ", msg=" + jsonResponse.optString("msg"));
                    }

                    if (jsonResponse.has("translation") && jsonResponse.getJSONArray("translation").length() > 0) {
                        return jsonResponse.getJSONArray("translation").getString(0);
                    }
                }
            }
        }

        throw new IOException("Translation failed: empty response or no translation field");
    }

    /**
     * Truncate text to 20 characters for Youdao API.
     */
    private String truncate(String text) {
        if (text.length() <= 20) {
            return text;
        }
        return text.substring(0, 10) + text.length() + text.substring(text.length() - 10);
    }
} 