package com.apps.searchandpagination.persistance.repository;

import com.apps.searchandpagination.persistance.entity.TradeData;
import com.apps.searchandpagination.persistance.entity.TradeDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeDetailsRepository extends JpaRepository<TradeDetails, String>, JpaSpecificationExecutor<TradeDetails> {
    public TradeDetails findByTradeData(TradeData tradeData);

    @Query("select distinct td.domain from TradeDetails td")
    List<String> findAllDomains();

    @Query(value = "select td.SYMBOL as label, sum(td.AMOUNT) as value, sum(td.VAL) as volume " +
            " from trade_data td inner " +
            "     join trade_details te on td.ID = te.TRADE_DATA_ID " +
            " where te.DOMAIN = ?1 " +
            " GROUP BY td.SYMBOL " +
            " order by value desc " +
            " LIMIT ?2 ",
            nativeQuery = true)
    List<AnalysedData> findAggregatedSymbolsData (String domain, Integer size);

    public static interface AnalysedData {

        String getLabel();

        String getValue();

        String getVolume();

    }

}
