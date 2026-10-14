package com.tradingframework.indicator;

import com.tradingframework.TestBars;
import com.tradingframework.model.BarSeries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorsTest {

    private final BarSeries s = TestBars.flat("X", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    @Test
    void sma() {
        assertTrue(Double.isNaN(Indicators.sma(s, 3, 1)));
        assertEquals(2.0, Indicators.sma(s, 3, 2), 1e-9);
        assertEquals(9.0, Indicators.sma(s, 3, 9), 1e-9);
    }

    @Test
    void emaSeedsWithSmaThenSmooths() {
        assertEquals(2.0, Indicators.ema(s, 3, 2), 1e-9);
        assertEquals(0.5 * 4 + 0.5 * 2.0, Indicators.ema(s, 3, 3), 1e-9);
    }

    @Test
    void rsiIsHundredWhenOnlyRisingAndFiftyWhenFlat() {
        assertEquals(100.0, Indicators.rsi(s, 5, 9), 1e-9);
        assertEquals(50.0, Indicators.rsi(TestBars.flat("F", 5, 5, 5, 5, 5, 5, 5), 3, 6), 1e-9);
        BarSeries mixed = TestBars.flat("M", 10, 11, 10, 11, 10, 11, 10);
        double r = Indicators.rsi(mixed, 4, 6);
        assertTrue(r > 0 && r < 100, "rsi=" + r);
    }

    @Test
    void channelExcludesCurrentBar() {
        assertEquals(9.0, Indicators.highestHigh(s, 3, 9), 1e-9);
        assertEquals(7.0, Indicators.lowestLow(s, 3, 9), 1e-9);
        assertTrue(Double.isNaN(Indicators.highestHigh(s, 3, 2)));
    }

    @Test
    void averageVolumeAndPercentChange() {
        assertEquals(1_000_000, Indicators.averageVolume(s, 5, 9), 1e-9);
        assertEquals(1.0, Indicators.percentChange(s, 1, 1), 1e-9);
    }
}
