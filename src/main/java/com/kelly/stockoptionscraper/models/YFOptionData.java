package com.kelly.stockoptionscraper.models;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.*;
import org.apache.commons.statistics.distribution.*;
import org.springframework.data.annotation.Id;

@Entity
@Table(name="option_data")
public class YFOptionData {

    @Id
    private String contractName;
    private String expirationDate;
    private String optionType;
    private String lastTradeDate;
    private Float strikePrice;
    private Float lastPrice;
    private Float bidPrice;
    private Float askPrice;
    private Float change;
    private Float percentChange;
    private Integer volume;
    private Integer openInterest;
    private Float impliedVolatilityPercent;
    private Float timeToExpirationYears;

    private Float gamma;
    private Float delta;
    private Float gex;

    public YFOptionData() { }

    public YFOptionData(String contractName, String expirationDate, String optionType,
                        String lastTradeDate, Float strikePrice, Float lastPrice,
                        Float bidPrice, Float askPrice, Float change, Float percentChange,
                        Integer volume, Integer openInterest, Float impliedVolatilityPercent) {
        this.contractName = contractName;
        this.expirationDate = expirationDate;
        this.optionType = optionType;
        this.lastTradeDate = lastTradeDate;
        this.strikePrice = strikePrice;
        this.lastPrice = lastPrice;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.change = change;
        this.percentChange = percentChange;
        this.volume = volume;
        this.openInterest = openInterest;
        this.impliedVolatilityPercent = impliedVolatilityPercent;
        this.timeToExpirationYears = getTimeToExpireYears();
    }

    public String getContractName() { return contractName; }
    public void setContractName(String value) { contractName = value; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String value) { expirationDate = value; }
    public String getOptionType() { return optionType; }
    public void setOptionType(String value) { optionType = value; }
    public String getLastTradeDate() { return lastTradeDate; }
    public void setLastTradeDate(String value) { lastTradeDate = value; }

    public Float getStrikePrice() { return strikePrice; }
    public void setStrikePrice(Float value) { strikePrice = value; }
    public Float getLastPrice() { return lastPrice; }
    public void setLastPrice(Float value) { lastPrice = value; }
    public Float getBidPrice() { return bidPrice; }
    public void setBidPrice(Float value) { bidPrice = value; }
    public Float getAskPrice() { return askPrice; }
    public void setAskPrice(Float value) { askPrice = value; }
    public Float getChange() { return change; }
    public void setChange(Float value) { change = value; }
    public Float getPercentChange() { return percentChange; }
    public void setPercentChange(Float value) { percentChange = value; }

    public Integer getVolume() { return volume; }
    public void setVolume(Integer value) { volume = value; }
    public Integer getOpenInterest() { return openInterest; }
    public void setOpenInterest(Integer value) { openInterest = value; }

    public Float getImpliedVolatilityPercent() { return impliedVolatilityPercent; }
    public void setImpliedVolatilityPercent(Float value) { impliedVolatilityPercent = value; }

    public boolean isCall() {
        return getOptionType().equals("C");
    }

    public boolean isPut() {
        return getOptionType().equals("P");
    }

    public String getOptionTypeName() {
        if (isCall())
            return "CALL";
        else if (isPut())
            return "PUT";
        else
            throw new IllegalStateException("optionType value is invalid: " + optionType);
    }

    public void computeGreeks(Float stockPrice, Float interestRate) {
        var d1 = getBlackScholesValue(stockPrice, interestRate);
        var normalDist = NormalDistribution.of(0.0, 1.0);

        //delta = isCall() ? normalCDFFn(d1) : normalCDFFn(d1) - 1f;
        delta = (float) (isCall() ? normalDist.cumulativeProbability(d1) : normalDist.cumulativeProbability(d1) - 1.0);

        var iv = impliedVolatilityPercent / 100;
        //gamma = (float) (normalPDFFn(d1) / (stockPrice * iv * Math.sqrt(timeToExpirationYears)));
        gamma = (float)(normalDist.density(d1) / (stockPrice * iv * Math.sqrt(timeToExpirationYears)));

        var openInterest = (float) getOpenInterest();
        var gexValue = gamma * openInterest * stockPrice * stockPrice * 0.01f;
        gex = isCall() ? gexValue : -gexValue;
    }

    public Float getTimeToExpireYears() {
        var currentDate = LocalDate.now();
        var expireDate = LocalDate.parse(expirationDate);
        var days = ChronoUnit.DAYS.between(currentDate, expireDate);

        if (currentDate.isAfter(expireDate))
            days *= -1;

        return (float)(days / 365.25f) + 0.001f;
    }

    public Float getDelta() { return delta; }

    public Float getGamma() { return gamma; }

    public Float getGex() { return gex; }

    private Float getBlackScholesValue(Float stockPrice, Float interestRate) {
        var iv = impliedVolatilityPercent / 100;
        var numer = Math.log(stockPrice / strikePrice) + ((interestRate + ((iv * iv) / 2)) * timeToExpirationYears);
        var denom = iv * Math.sqrt(timeToExpirationYears);
        return (float) (numer / denom);
    }

    // Import org.apache.commons.math3.distribution.NormalDistribution  (commons-math3-3.x.x.jar)
    private Float normalCDFFn(Float x) {
        var t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        var y = 1.0 - (((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t * Math.exp(-x * x)));
        return (float) ((x < 0) ? 0.5 * (1.0 - y) : 0.5 * (1.0 + y));
    }

    private Float normalPDFFn(Float x) {
        return (float) ((1.0 / Math.sqrt(2.0 * Math.PI)) * Math.exp(-0.5 * x * x));
    }
}
