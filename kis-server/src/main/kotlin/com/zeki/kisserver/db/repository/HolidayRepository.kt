package com.zeki.kisserver.db.repository

import com.zeki.db.entity.Holiday
import com.zeki.kisserver.db.entity.Holiday
import com.zeki.kisvolkotlin.db.entity.Holiday
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate


interface HolidayRepository : JpaRepository<Holiday, Long> {
    fun findFirstByDate(availableDate: LocalDate): Holiday?

    @Suppress("SpringDataMethodInconsistencyInspection") // IsHoliday를 Holiday로 인식함
    fun findByDateBetweenAndIsHoliday(startDate: LocalDate, endDate: LocalDate, isHoliday: Boolean): List<Holiday>
}