package com.zeki.kisserver.domain.data_go.stock_code

import com.zeki.common.em.StockMarket
import com.zeki.exception.ResponseCode
import com.zeki.kisserver.db.repository.StockCodeJoinRepository
import com.zeki.kisserver.db.repository.StockCodeRepository
import com.zeki.kisserver.domain._common.webclient.WebClientConnector
import com.zeki.kisserver.domain.data_go.holiday.HolidayDateService
import com.zeki.kisserver.domain.data_go.stock_code.dto.DataGoStockCodeResDto
import com.zeki.kisserver.domain.data_go.stock_code.dto.StockCodeItem
import com.zeki.kisserver.exception.ResponseCode
import com.zeki.kisvolkotlin.domain._common.util.CustomUtils.toStringDate
import com.zeki.kisvolkotlin.exception.ResponseCode
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import java.time.LocalDate
import java.time.LocalTime

@Service
class StockCodeService(
    private val stockCodeRepository: StockCodeRepository,
    private val stockCodeJoinRepository: StockCodeJoinRepository,

    private val holidayDateService: HolidayDateService,

    private val webClientConnector: WebClientConnector
) {

    @Transactional
    fun upsertStockCode(
        standardDate: LocalDate = LocalDate.now(),
        standardTime: LocalTime = LocalTime.now(),
        standardDeltaDate: Int = 10
    ) {
        val stockCodeSaveList = mutableListOf<com.zeki.kisserver.db.entity.StockCode>()
        val stockCodeUpdateList = mutableListOf<com.zeki.kisserver.db.entity.StockCode>()
        val stockCodeDeleteSet = mutableSetOf<com.zeki.kisserver.db.entity.StockCode>()

        val dataGoStockCodeItemList =
            this.getStockCodesFromDataGo(
                standardDate = standardDate,
                standardTime = standardTime,
                standardDeltaDate = standardDeltaDate
            )

        val stockCodeMap = stockCodeRepository.findAll()
            .associateBy { it.code }
            .toMutableMap()

        for (item in dataGoStockCodeItemList) {
            val stockCode = item.srtnCd.substring(1)
            val stockName = item.itmsNm
            val stockMarket = item.mrktCtg

            when (val stockCodeEntity = stockCodeMap[stockCode]) {
                null -> {
                    stockCodeSaveList.add(
                        com.zeki.kisserver.db.entity.StockCode(
                            code = stockCode,
                            name = stockName,
                            market = StockMarket.valueOf(stockMarket)
                        )
                    )
                }

                else -> {
                    val isUpdate = stockCodeEntity.updateStockCode(
                        name = stockName,
                        market = StockMarket.valueOf(stockMarket)
                    )
                    if (isUpdate) {
                        stockCodeUpdateList.add(stockCodeEntity)
                    }
                    stockCodeMap.remove(stockCode)
                }
            }
        }

        stockCodeDeleteSet.addAll(stockCodeMap.values)

        stockCodeJoinRepository.bulkInsert(stockCodeSaveList)
        stockCodeJoinRepository.bulkUpdate(stockCodeUpdateList)
        stockCodeRepository.deleteAllInBatch(stockCodeDeleteSet)
    }


    fun getStockCodesFromDataGo(
        standardDate: LocalDate = LocalDate.now(),
        standardTime: LocalTime = LocalTime.now(),
        standardDeltaDate: Int = 10
    ): List<StockCodeItem> {
        var pageNo = 1
        var totalCount = Int.MAX_VALUE

        val batchSize = 1000
        val deltaOfToday: String =
            holidayDateService.getAvailableDate(
                standardDate = standardDate,
                standardTime = standardTime,
                standardDeltaDate = standardDeltaDate
            ).toStringDate()

        val queryParams = LinkedMultiValueMap<String, String>()
            .apply {
                add("resultType", "json")
                add("numOfRows", batchSize.toString())
                add("basDt", deltaOfToday)
                add("pageNo", pageNo.toString())
            }

        val dataGoStockCodeItemList = mutableListOf<StockCodeItem>()
        while ((pageNo - 1) * batchSize < totalCount) {
            queryParams["pageNo"] = pageNo.toString()

            val responseDatas = webClientConnector.connect<Unit, DataGoStockCodeResDto>(
                WebClientConnector.WebClientType.DATA_GO,
                HttpMethod.GET,
                "1160100/service/GetKrxListedInfoService/getItemInfo",
                requestParams = queryParams,
                responseClassType = DataGoStockCodeResDto::class.java,
                retryCount = 3,
                retryDelay = 510
            )

            val dataGoStockCodeResDto =
                responseDatas?.body ?: throw com.zeki.kisserver.exception.ApiException(
                    ResponseCode.INTERNAL_SERVER_WEBCLIENT_ERROR,
                    "통신에러 queryParams: $queryParams"
                )

            totalCount = dataGoStockCodeResDto.response.body.totalCount
            pageNo += 1

            dataGoStockCodeItemList.addAll(dataGoStockCodeResDto.response.body.items.item)
        }

        return dataGoStockCodeItemList
    }
}