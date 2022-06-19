package ua.kharole.snapshotpolling;

import java.math.BigDecimal;
import java.util.List;

public class Rate {
    private String symbol;
    private BigDecimal price_usd;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getPrice_usd() {
        return price_usd;
    }

    public void setPrice_usd(BigDecimal price_usd) {
        this.price_usd = price_usd;
    }
}
