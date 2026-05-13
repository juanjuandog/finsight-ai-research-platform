package com.finsight.domain.repository;

import com.finsight.domain.model.UserWatchlistItem;

import java.util.List;

public interface UserWatchlistRepository {
    void add(String userId, String companySymbol);

    void remove(String userId, String companySymbol);

    List<UserWatchlistItem> findByUserId(String userId);
}
