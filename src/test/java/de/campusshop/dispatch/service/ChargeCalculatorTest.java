package de.campusshop.dispatch.service;

import de.campusshop.dispatch.model.Tariff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static de.campusshop.dispatch.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ChargeCalculatorTest {
    private final ChargeCalculator calculator = new ChargeCalculator();
    @Test void pricesTheWorkedExample() {
        assertThat(calculator.charge(STANDARD, dec("2.5"))).isEqualTo(dec("8.25"));
        assertThat(calculator.charge(EXPRESS, dec("0.8"))).isEqualTo(dec("8.74"));
    }
    @ParameterizedTest
    @CsvSource({"1.004, 1.00", "1.005, 1.01", "1.006, 1.01", "0.005, 0.01", "0, 0.00"})
    void roundsHalfUp(String base, String expected) {
        var tariff = new Tariff("T", "Test", dec(base), dec("0"), dec("0"));
        assertThat(calculator.charge(tariff, dec("1"))).isEqualTo(dec(expected));
    }
    @Test void preservesPrecisionUntilTheFinalRounding() {
        var tariff = new Tariff("T", "Test", dec("0"), dec("0.3333"), dec("0.10"));
        assertThat(calculator.charge(tariff, dec("3"))).isEqualTo(dec("1.10"));
        var large = new Tariff("T", "Test", dec("9007199254740993.005"), dec("0"), dec("0"));
        assertThat(calculator.charge(large, dec("1"))).isEqualTo(dec("9007199254740993.01"));
    }
}
