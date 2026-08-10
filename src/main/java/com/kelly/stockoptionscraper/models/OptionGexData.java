package com.kelly.stockoptionscraper.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "option_gex")
public class OptionGexData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private LocalDateTime dateTime;
    private String symbol;
    private List<StrikeOptionData> strikeOptions;

    private Float callWallStrike;
    private Float putWallStrike;
    private Float callWallGex;
    private Float putWallGex;
    private Float netCallGex;
    private Float netPutGex;
    private Float absoluteGex;

    public OptionGexData(String symbol, LocalDateTime dateTime, List<StrikeOptionData> strikeOptions) {
        this.symbol = symbol;
        this.dateTime = dateTime;
        this.strikeOptions = strikeOptions;

        computeGexData();
    }

    public Integer getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDateTime getDateTime() { return dateTime; }

    public boolean isPositive() {
        return getNetTotalGex() >= 0f;
    }
    public boolean isCallWallHigherGex() {
        return Math.abs(callWallGex) > Math.abs(putWallGex);
    }
    public Float getCallWallStrike() { return callWallStrike; }
    public Float getPutWallStrike() { return putWallStrike; }
    public Float getCallWallGex() { return callWallGex; }
    public Float getPutWallGex() { return putWallGex; }
    public Float getNetCallGex() { return netCallGex; }
    public Float getNetPutGex() { return netPutGex; }
    public Float getAbsoluteGex() { return absoluteGex; }
    public Float getNetTotalGex() { return netCallGex + netPutGex; }

    private void computeGexData() {
        callWallGex = 0f;
        putWallGex = 0f;
        callWallStrike = 0f;
        putWallStrike = 0f;
        netCallGex = 0f;
        netPutGex = 0f;
        absoluteGex = 0f;

        // Discover the call wall and put wall and their gex values
        for (var optionData : strikeOptions) {
            var callGex = optionData.getCallOption().getGex();
            var putGex = optionData.getPutOption().getGex();

            netCallGex += callGex;
            netPutGex += putGex;
            absoluteGex += optionData.getAbsoluteGex();

            if (callGex > callWallGex) {
                callWallGex = callGex;
                callWallStrike = optionData.getStrikePrice();
            }

            if (putGex < putWallGex) {
                putWallGex = putGex;
                putWallStrike = optionData.getStrikePrice();
            }
        }
    }
}
