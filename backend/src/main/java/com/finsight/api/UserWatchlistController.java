package com.finsight.api;

import com.finsight.application.UserWatchlistService;
import com.finsight.domain.model.UserWatchlistItem;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class UserWatchlistController {
    private final UserWatchlistService userWatchlistService;

    public UserWatchlistController(UserWatchlistService userWatchlistService) {
        this.userWatchlistService = userWatchlistService;
    }

    @GetMapping
    public List<UserWatchlistItem> list(@RequestHeader(value = "X-Finsight-User", required = false) String userId) {
        return userWatchlistService.list(userId);
    }

    @PostMapping("/{symbol}")
    public List<UserWatchlistItem> add(
            @RequestHeader(value = "X-Finsight-User", required = false) String userId,
            @PathVariable String symbol
    ) {
        return userWatchlistService.add(userId, symbol);
    }

    @DeleteMapping("/{symbol}")
    public List<UserWatchlistItem> remove(
            @RequestHeader(value = "X-Finsight-User", required = false) String userId,
            @PathVariable String symbol
    ) {
        return userWatchlistService.remove(userId, symbol);
    }
}
