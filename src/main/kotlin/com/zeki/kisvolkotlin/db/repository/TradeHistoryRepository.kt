package com.zeki.kisvolkotlin.db.repository

import com.zeki.kisvolkotlin.db.entity.TradeHistory
import org.springframework.data.jpa.repository.JpaRepository

interface TradeHistoryRepository : JpaRepository<TradeHistory, Long>