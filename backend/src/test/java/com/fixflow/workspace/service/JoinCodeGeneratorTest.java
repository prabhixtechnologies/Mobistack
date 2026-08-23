package com.fixflow.workspace.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeGeneratorTest {

    @Test
    void stemsTheShopNameAndAddsARandomSuffix() {
        String code = JoinCodeGenerator.generate("Sharma Mobile");
        assertThat(code).matches("SHARMA-[A-Z2-9]{4}");
    }

    @Test
    void fallsBackWhenTheNameHasNoLetters() {
        assertThat(JoinCodeGenerator.generate("   ")).matches("SHOP-[A-Z2-9]{4}");
        assertThat(JoinCodeGenerator.generate(null)).matches("SHOP-[A-Z2-9]{4}");
    }
}
