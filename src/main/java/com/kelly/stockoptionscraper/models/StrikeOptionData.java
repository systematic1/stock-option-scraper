package com.kelly.stockoptionscraper.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "strike_gex")
public class StrikeOptionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private final Float strikePrice;
    private Float netGex;
    private Float absoluteGex;
    private String symbol;
    private String expirationDate;
    private LocalDateTime dateTime;

    @Transient
    private YFOptionData callOption;
    @Transient
    private YFOptionData putOption;

    public StrikeOptionData(String symbol, String expirationDate, Float strikePrice,
                            LocalDateTime dateTime, YFOptionData callOption, YFOptionData putOption) {
        this.strikePrice = strikePrice;
        this.symbol = symbol;
        this.expirationDate = expirationDate;
        this.dateTime = dateTime;
        this.callOption = callOption;
        this.putOption = putOption;

        this.netGex = callOption.getGex() + putOption.getGex();
        this.absoluteGex = Math.abs(callOption.getGex()) + Math.abs(putOption.getGex());
    }

    public YFOptionData getCallOption() { return callOption; }
    public YFOptionData getPutOption() { return putOption; }
    public Float getStrikePrice() { return strikePrice; }

    public Float getNetGex() { return netGex; }
    public Float getAbsoluteGex() { return absoluteGex; }
}
