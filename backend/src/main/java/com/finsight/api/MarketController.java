package com.finsight.api;

import com.finsight.market.MarketDataService;
import com.finsight.market.MarketQuote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private final MarketDataService marketDataService;

    public MarketController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/quotes/{symbol}")
    public MarketQuote quote(@PathVariable String symbol) {
        return marketDataService.quote(symbol);
    }
}
