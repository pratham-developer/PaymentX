package com.pratham.paymentx.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class EmailTemplateLoader {

    private static final String TEMPLATE_BASE_PATH = "templates/email/";

    public String load(String templateFileName, Map<String, String> placeholders) {
        String templatePath = TEMPLATE_BASE_PATH + templateFileName;
        String html = readTemplate(templatePath);
        return resolve(html, placeholders);
    }

    private String readTemplate(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load email template: {}", path, e);
            throw new IllegalStateException("Email template not found: " + path, e);
        }
    }

    private String resolve(String html, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            html = html.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return html;
    }
}