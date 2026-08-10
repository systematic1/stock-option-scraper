package com.kelly.stockoptionscraper.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.persistence.*;
import org.apache.commons.statistics.distribution.*;

@Entity
@Table(name="option_data")
public class YFOptionData {

    @Id
    private String contractName;
    private LocalDateTime dateTime;
    private String expirationDate;
    private String optionType;
    //private String lastTradeDate;
    private Float strikePrice;
    //private Float lastPrice;
    private Float bidPrice;
    private Float askPrice;
    //private Float change;
    //private Float percentChange;
    private Integer volume;
    private Integer openInterest;
    private Float impliedVolatilityPercent;
    private Float timeToExpirationYears;

    private Float gamma;
    private Float delta;
    private Float gex;

    public YFOptionData() { }

    /*public YFOptionData(LocalDateTime dateTime, String contractName, String expirationDate,
                        String optionType, String lastTradeDate, Float strikePrice, Float lastPrice,
                        Float bidPrice, Float askPrice, Float change, Float percentChange,
                        Integer volume, Integer openInterest, Float impliedVolatilityPercent) {*/
    public YFOptionData(LocalDateTime dateTime, String contractName, String expirationDate,
                       String optionType, Float strikePrice, Float bidPrice, Float askPrice,
                       Integer volume, Integer openInterest, Float impliedVolatilityPercent) {
        this.contractName = contractName;
        this.dateTime = dateTime;
        this.expirationDate = expirationDate;
        this.optionType = optionType;
        //this.lastTradeDate = lastTradeDate;
        this.strikePrice = strikePrice;
        //this.lastPrice = lastPrice;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        //this.change = change;
        //this.percentChange = percentChange;
        this.volume = volume;
        this.openInterest = openInterest;
        this.impliedVolatilityPercent = impliedVolatilityPercent;
        this.timeToExpirationYears = getTimeToExpireYears();
    }

    public String getContractName() { return contractName; }
    public void setContractName(String value) { contractName = value; }
    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime value) { dateTime = value; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String value) { expirationDate = value; }
    public String getOptionType() { return optionType; }
    public void setOptionType(String value) { optionType = value; }
    //public String getLastTradeDate() { return lastTradeDate; }
    //public void setLastTradeDate(String value) { lastTradeDate = value; }

    public Float getStrikePrice() { return strikePrice; }
    public void setStrikePrice(Float value) { strikePrice = value; }
    //public Float getLastPrice() { return lastPrice; }
    //public void setLastPrice(Float value) { lastPrice = value; }
    public Float getBidPrice() { return bidPrice; }
    public void setBidPrice(Float value) { bidPrice = value; }
    public Float getAskPrice() { return askPrice; }
    public void setAskPrice(Float value) { askPrice = value; }
    //public Float getChange() { return change; }
    //public void setChange(Float value) { change = value; }
    //public Float getPercentChange() { return percentChange; }
    //public void setPercentChange(Float value) { percentChange = value; }

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

    public Float getDelta() { return Objects.requireNonNullElse(delta, 0f); }
    public Float getGamma() { return Objects.requireNonNullElse(gamma, 0f); }
    public Float getGex() { return Objects.requireNonNullElse(gex, 0f); }

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

        delta = (float) (isCall() ? normalDist.cumulativeProbability(d1) : normalDist.cumulativeProbability(d1) - 1.0);

        var iv = (impliedVolatilityPercent + 0.001) / 100;
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

    private Float getBlackScholesValue(Float stockPrice, Float interestRate) {
        var iv = (impliedVolatilityPercent + 0.001) / 100;
        var numer = Math.log(stockPrice / strikePrice) + ((interestRate + ((iv * iv) / 2)) * timeToExpirationYears);
        var denom = iv * Math.sqrt(timeToExpirationYears);
        return (float) (numer / denom);
    }
}
