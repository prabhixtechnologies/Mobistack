package com.fixflow.workspace.service;

import com.fixflow.common.util.TextNormalizer;

import java.security.SecureRandom;

/**
 * Builds codes such as {@code SHARMA-7K2P}. The suffix is random so two shops
 * named "Sharma Mobile" do not collide.
 */
public final class JoinCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodeGenerator() {
    }

    public static String generate(String workspaceName) {
        String stem = TextNormalizer.toCode(workspaceName).replace("_", "");
        if (stem.length() > 6) {
            stem = stem.substring(0, 6);
        }
        if (stem.isBlank()) {
            stem = "SHOP";
        }
        return stem + "-" + randomSuffix(4);
    }

    private static String randomSuffix(int length) {
        StringBuilder suffix = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return suffix.toString();
    }
}
