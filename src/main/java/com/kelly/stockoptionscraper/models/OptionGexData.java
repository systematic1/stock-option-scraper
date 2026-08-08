package com.kelly.stockoptionscraper.models;

import java.util.Date;
import java.util.List;

public class OptionGexData {

    private String symbol;
    private Date dateTime;
    private List<YFOptionData> callOptionChain;
    private List<YFOptionData> putOptionChain;

    private Float callWallStrike;
    private Float putWallStrike;
    private Float callWallGex;
    private Float putWallGex;
    private Float netCallGex;
    private Float netPutGex;

    public OptionGexData(String symbol, Date dateTime, List<YFOptionData> callOptionChain, List<YFOptionData> putOptionChain) {
        this.symbol = symbol;
        this.dateTime = dateTime;
        this.callOptionChain = callOptionChain;
        this.putOptionChain = putOptionChain;

        computeGexData();
    }

    public boolean isPositive() {
        return getNetTotalGex() >= 0f;
    }

    public boolean isCallWallHigherGex() {
        return callWallGex > putWallGex;
    }

    public Float getCallWallStrike() { return callWallStrike; }
    public Float getPutWallStrike() { return putWallStrike; }
    public Float getCallWallGex() { return callWallGex; }
    public Float getPutWallGex() { return putWallGex; }
    public Float getNetCallGex() { return netCallGex; }
    public Float getNetPutGex() { return netPutGex; }
    public Float getNetTotalGex() { return netCallGex - netPutGex; }
    public Float getAggregateTotalGex() { return netCallGex + netPutGex; }

    private void computeGexData() {
        callWallGex = 0f;
        putWallGex = 0f;
        callWallStrike = 0f;
        putWallStrike = 0f;
        netCallGex = 0f;
        netPutGex = 0f;

        // Discover the call wall and put wall and their gex values
        for (var callOption : callOptionChain) {
            netCallGex += callOption.getGex();

            if (callOption.getGex() > callWallGex) {
                callWallGex = callOption.getGex();
                callWallStrike = callOption.getStrikePrice();
            }
        }

        for (var putOption : putOptionChain) {
            netCallGex += putOption.getGex();

            if (putOption.getGex() < putWallGex) {
                putWallGex = putOption.getGex();
                putWallStrike = putOption.getStrikePrice();
            }
        }
    }
}
