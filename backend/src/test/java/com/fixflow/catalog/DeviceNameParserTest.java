package com.fixflow.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceNameParserTest {

    @Test
    void splitsSamsungRadioVariant() {
        DeviceNameParser.ParsedDeviceName fourG = DeviceNameParser.parse("Samsung A32 4g");
        assertThat(fourG.brand()).isEqualTo("Samsung");
        assertThat(fourG.name()).isEqualTo("A32");
        assertThat(fourG.variant()).isEqualTo("4G");

        DeviceNameParser.ParsedDeviceName fiveG = DeviceNameParser.parse("Samsung A32 5G");
        assertThat(fiveG.name()).isEqualTo("A32");
        assertThat(fiveG.variant()).isEqualTo("5G");
        assertThat(fourG.display()).isEqualTo("Samsung A32 4G");
    }

    @Test
    void keepsRedmiAsItsOwnBrand() {
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse("Redmi 9A");
        assertThat(parsed.brand()).isEqualTo("Redmi");
        assertThat(parsed.name()).isEqualTo("9A");
        assertThat(parsed.variant()).isNull();
    }

    @Test
    void treatsIphoneAsAppleButKeepsTheModelName() {
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse("iPhone 11");
        assertThat(parsed.brand()).isEqualTo("Apple");
        assertThat(parsed.name()).isEqualTo("iPhone 11");
    }

    @Test
    void treatsGalaxyAsSamsungAndKeepsTheToken() {
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse("Galaxy S21");
        assertThat(parsed.brand()).isEqualTo("Samsung");
        assertThat(parsed.name()).isEqualTo("Galaxy S21");
    }

    @Test
    void correctsCommonTypos() {
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse("samaung a10");
        assertThat(parsed.brand()).isEqualTo("Samsung");
        assertThat(parsed.name()).isEqualTo("a10");
    }

    @Test
    void shopBrandsWinOverTheDictionary() {
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse(
                "Star Phone X", List.of("Star Phone"));
        assertThat(parsed.brand()).isEqualTo("Star Phone");
        assertThat(parsed.name()).isEqualTo("X");
    }

    @Test
    void leavesUnknownNamesForTheCallerDefaultBrand() {
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse("Narzo 20");
        assertThat(parsed.brand()).isNull();
        assertThat(parsed.name()).isEqualTo("Narzo 20");
    }

    @Test
    void displayJoinsBrandNameAndVariant() {
        assertThat(DeviceLabels.display("Samsung", "A32", "4G")).isEqualTo("Samsung A32 4G");
        assertThat(DeviceLabels.display("Apple", "iPhone 11", null)).isEqualTo("Apple iPhone 11");
    }
}
