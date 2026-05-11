package com.finsight.market;

public interface MarketDataClient {
    MarketQuote quote(String symbol);
}
