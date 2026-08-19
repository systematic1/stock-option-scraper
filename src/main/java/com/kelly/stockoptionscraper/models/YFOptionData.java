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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long optionId;

    @Column(length = 5)
    private String symbol;

    @Column(length = 20)
    private String contractName;
    private LocalDateTime dateTime;
    @Column(length = 10)
    private LocalDate expirationDate;
    @Column(length = 1)
    private String optionType;

    private Float strikePrice;
    private Float bidPrice;
    private Float askPrice;
    private Float midPrice;
    private Integer volume;
    private Integer openInterest;
    private Float impliedVolatilityPercent;
    private Float timeToExpirationYears;

    private Float gamma;
    private Float delta;
    private Float gex;
    private Float dex;

    public YFOptionData() { }

    public YFOptionData(String symbol, LocalDateTime dateTime, String contractName, LocalDate expirationDate,
                       String optionType, Float strikePrice, Float bidPrice, Float askPrice,
                       Integer volume, Integer openInterest, Float impliedVolatilityPercent) {
        this.optionId = null;
        this.symbol = symbol;
        this.contractName = contractName;
        this.dateTime = dateTime;
        this.expirationDate = expirationDate;
        this.optionType = optionType;
        this.strikePrice = strikePrice;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.volume = volume;
        this.openInterest = openInterest;
        this.impliedVolatilityPercent = impliedVolatilityPercent;
        this.timeToExpirationYears = getTimeToExpireYears();
        this.midPrice = (askPrice - bidPrice) / 2f;
    }

    public Long getOptionId() { return optionId; }
    public String getContractName() { return contractName; }
    public void setContractName(String value) { contractName = value; }
    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime value) { dateTime = value; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate value) { expirationDate = value; }
    public String getOptionType() { return optionType; }
    public void setOptionType(String value) { optionType = value; }
    public Float getStrikePrice() { return strikePrice; }
    public void setStrikePrice(Float value) { strikePrice = value; }
    public Float getBidPrice() { return bidPrice; }
    public void setBidPrice(Float value) { bidPrice = value; }
    public Float getAskPrice() { return askPrice; }
    public void setAskPrice(Float value) { askPrice = value; }

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

    public Float getMidPrice() { return midPrice; }
    public Float getDelta() { return Objects.requireNonNullElse(delta, 0f); }
    public Float getGamma() { return Objects.requireNonNullElse(gamma, 0f); }
    public Float getGex() { return Objects.requireNonNullElse(gex, 0f); }
    public Float getDex() { return Objects.requireNonNullElse(dex, 0f); }

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

        dex = delta * openInterest * 100f * (isCall() ? 1 : -1);
    }

    public Float getTimeToExpireYears() {
        var currentDate = LocalDate.now();
        var days = ChronoUnit.DAYS.between(currentDate, expirationDate);

        if (currentDate.isAfter(expirationDate))
            days *= -1;

        return ((float)days / 365.25f) + 0.001f;
    }

    private Float getBlackScholesValue(Float stockPrice, Float interestRate) {
        var iv = (impliedVolatilityPercent + 0.001) / 100;
        var numer = Math.log(stockPrice / strikePrice) + ((interestRate + ((iv * iv) / 2)) * timeToExpirationYears);
        var denom = iv * Math.sqrt(timeToExpirationYears);
        return (float) (numer / denom);
    }
}
