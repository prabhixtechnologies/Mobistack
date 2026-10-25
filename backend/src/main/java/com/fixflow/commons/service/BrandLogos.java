package com.fixflow.commons.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small brand marks carried inside the catalog snapshot.
 *
 * <p>The phone draws {@code logo} from the graph it already saved. It does not request
 * {@code logoUrl}. A stored value is used only when it is itself a short SVG; a remote address
 * stays on the server. Brands with no mark keep the letter on the phone.
 *
 * <p>The files under {@code catalog/brand-logos} are Simple Icons (MIT). Brand names remain
 * trademarks of their owners.
 */
@Component
public class BrandLogos {

    static final int MAX_BYTES = 4096;

    private final Map<String, String> packed;

    public BrandLogos() {
        this.packed = load();
    }

    /**
     * The SVG to store on the brand row, or null when the phone should draw a letter.
     */
    public String mark(String name, String logoUrl) {
        String inline = inline(logoUrl);
        if (inline != null) {
            return inline;
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        return packed.get(key(name));
    }

    static String inline(String logoUrl) {
        if (logoUrl == null) {
            return null;
        }
        String trimmed = logoUrl.trim();
        if (!trimmed.startsWith("<svg") || trimmed.length() > MAX_BYTES || trimmed.contains("<script")) {
            return null;
        }
        return trimmed;
    }

    /**
     * Same folding as {@code fixflow_normalize}, then the spaces are removed so "One Plus" and
     * "OnePlus" share one file.
     */
    static String key(String name) {
        StringBuilder key = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                key.append(c);
            }
        }
        return key.toString();
    }

    private static Map<String, String> load() {
        Map<String, String> found = new HashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:catalog/brand-logos/*.svg");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".svg")) {
                    continue;
                }
                String svg = read(resource);
                if (svg == null) {
                    continue;
                }
                found.put(filename.substring(0, filename.length() - 4).toLowerCase(Locale.ROOT), svg);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Brand marks could not be read", ex);
        }
        return Map.copyOf(found);
    }

    private static String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            String svg = StreamUtils.copyToString(in, StandardCharsets.UTF_8).trim();
            if (!svg.startsWith("<svg") || svg.length() > MAX_BYTES || svg.contains("<script")) {
                return null;
            }
            return svg;
        }
    }
}
