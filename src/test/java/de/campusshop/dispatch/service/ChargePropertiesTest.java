package de.campusshop.dispatch.service;

import de.campusshop.dispatch.model.Tariff;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class ChargePropertiesTest {
    private static final long SEED = 20261126L;
    private final ChargeCalculator calculator = new ChargeCalculator();

    @Test void thousandChargesMatchAnIndependentIntegerRationalOracle() {
        Random inputs = new Random(SEED);
        for (int i = 0; i < 1000; i++) {
            int baseCents = inputs.nextInt(100001), priceCents = inputs.nextInt(10001);
            int grams = 1 + inputs.nextInt(100000), fuelMillis = inputs.nextInt(1001);
            long numerator = ((long) baseCents * 1000 + (long) priceCents * grams) * (1000 + fuelMillis);
            long roundedCents = (numerator + 500000) / 1000000;
            var tariff = tariff(baseCents, priceCents, fuelMillis);
            assertThat(calculator.charge(tariff, BigDecimal.valueOf(grams, 3)))
                    .as("integer oracle case %s, seed %s", i, SEED).isEqualTo(BigDecimal.valueOf(roundedCents, 2));
        }
    }

    @Test void chargeIsMonotoneInWeightForNonnegativeTariffs() {
        Random inputs = new Random(SEED);
        for (int i = 0; i < 1000; i++) {
            var tariff = tariff(inputs.nextInt(100001), inputs.nextInt(10001), inputs.nextInt(1001));
            BigDecimal weight = BigDecimal.valueOf(1 + inputs.nextInt(100000), 3);
            assertThat(calculator.charge(tariff, weight.add(BigDecimal.valueOf(1 + inputs.nextInt(10000), 3))))
                    .isGreaterThanOrEqualTo(calculator.charge(tariff, weight));
        }
    }

    @Test void chargeIsInvariantToTrailingZeroRepresentation() {
        Random inputs = new Random(SEED);
        for (int i = 0; i < 1000; i++) {
            var tariff = tariff(inputs.nextInt(100001), inputs.nextInt(10001), inputs.nextInt(1001));
            BigDecimal weight = BigDecimal.valueOf(1 + inputs.nextInt(100000), 3);
            assertThat(calculator.charge(tariff, weight.setScale(8))).isEqualTo(calculator.charge(tariff, weight));
        }
    }

    @Test void surchargeCannotReduceANonnegativeCharge() {
        Random inputs = new Random(SEED);
        for (int i = 0; i < 1000; i++) {
            int base = inputs.nextInt(100001), price = inputs.nextInt(10001);
            BigDecimal weight = BigDecimal.valueOf(1 + inputs.nextInt(100000), 3);
            assertThat(calculator.charge(tariff(base, price, inputs.nextInt(1001)), weight))
                    .isGreaterThanOrEqualTo(calculator.charge(tariff(base, price, 0), weight));
        }
    }

    private static Tariff tariff(int baseCents, int priceCents, int fuelMillis) {
        return new Tariff("GENERATED", "Generated", BigDecimal.valueOf(baseCents, 2),
                BigDecimal.valueOf(priceCents, 2), BigDecimal.valueOf(fuelMillis, 3));
    }
}
