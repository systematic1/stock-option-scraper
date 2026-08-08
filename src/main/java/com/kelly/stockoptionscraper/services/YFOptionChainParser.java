package com.kelly.stockoptionscraper.services;

import com.kelly.stockoptionscraper.models.YFOptionData;

import java.io.IOException;
import java.util.Objects;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class YFOptionChainParser {

    private final int IDX_CONTRACT = 0;
    private final int IDX_LAST_TRADE = 1;
    private final int IDX_STRIKE = 2;
    private final int IDX_LAST_PRICE = 3;
    private final int IDX_BID = 4;
    private final int IDX_ASK = 5;
    private final int IDX_CHANGE = 6;
    private final int IDX_PERC_CHANGE = 7;
    private final int IDX_VOLUME = 8;
    private final int IDX_OPEN_INTEREST = 9;
    private final int IDX_IV_PERC = 10;

    public Document parseRawHtml(String html) {
        return Jsoup.parse(html);
    }

    public String extractStockPrice(Document document)
                throws IOException {
        var element = document.selectFirst(".price .container .container .price.base");
        if (element == null)
            throw new IOException("Couldn't locate spot price in HTML.");

        return element.text();
    }

    public Elements extractOptionChainRows(Document document) {
        var e1 = document.select("#main-content-wrapper");
        var e1_1 = e1.select(".tableContainer");

        return document.select("#main-content-wrapper .tableContainer table tbody tr");
    }

    // use DOM querying to find the following elements:
    //  ".price .container .container .price.base"  ==> value is stock price
    //  ".options-list-table .optionsHeader h3" where text == "Calls"
    //  Starting at the next element beyond the .optionsHeader, ".tableContainer table tbody tr" contains list of table rows of call option chain data
    //  Iterate over all 'tr's extracting data from 'td' elements (*see below for structure)
    //  ".options-list-table .optionsHeader h3" where text == "Puts"
    //  Starting at the next element beyond the .optionsHeader, ".tableContainer table tbody tr" contains list of table rows of put option chain data
    //  Iterate over all 'tr's extracting data from 'td' elements (*see below for structure)
    //  row structure (td's)
    //      <0>  Contract Name : child is <a> with contract name string as its text
    //      <1>  Last Trade Date/Time
    //      <2>  Strike : child is <a> with strike price as its text
    //      <3>  Last Price
    //      <4>  Bid Price
    //      <5>  Ask Price
    //      <6>  Change
    //      <7>  % Change (number with %)
    //      <8>  Volume
    //      <9>  Open Interest
    //      <10> Implied Volatility (IV) as %

    // The Contract Name string is a fixed field string broken in this manner
    //  "[AAAA][YYMMDD][T][#####]000"
    //      AAAA    = Symbol name
    //      YYMMDD  = 2 digit year, 2 digit month, 2 digit day for expiration date
    //      T       = option type: "C"=call, "P"=put
    //      #####   = 5-digit, zero-padded strike price
    //      000     = unknown, always 3 zeros

    public YFOptionData parseOptionRowData(Element trElement) {
        var tdElements = trElement.select("td");

        var contractName = getChildAnchorText(tdElements.get(IDX_CONTRACT));
        var lastTrade = tdElements.get(IDX_LAST_TRADE).text();
        var strike = convertToFloat(tdElements.get(IDX_STRIKE).text());
        var lastPrice = convertToFloat(tdElements.get(IDX_LAST_PRICE).text());
        var bidPrice = convertToFloat(tdElements.get(IDX_BID).text());
        var askPrice = convertToFloat(tdElements.get(IDX_ASK).text());
        var change = convertToFloat(tdElements.get(IDX_CHANGE).text());
        var percentChange = convertToFloat(tdElements.get(IDX_PERC_CHANGE).text().replace("%", ""));
        var volume = convertToInteger(tdElements.get(IDX_VOLUME).text());
        var openInterest = convertToInteger(tdElements.get(IDX_OPEN_INTEREST).text());
        var impliedVolatilityPerc = convertToFloat(tdElements.get(IDX_IV_PERC).text().replace("%", ""));

        var conLength = contractName.length();
        var contractLast15 = (conLength > 15) ? contractName.substring(conLength - 15) : contractName;

        var expirationDate = String.format("20%s-%s-%s",
                contractLast15.substring(0, 2),
                contractLast15.substring(2, 4),
                contractLast15.substring(4, 6));
        var optionType = contractLast15.substring(6, 7);

        return new YFOptionData(contractName, expirationDate, optionType, lastTrade,
                strike, lastPrice, bidPrice, askPrice, change, percentChange, volume,
                openInterest, impliedVolatilityPerc);
    }

    private String getChildAnchorText(Element element) {
        var aElement = element.select("a");
        var result = "";
        if (!aElement.isEmpty())
            result = Objects.requireNonNull(aElement.first()).text();

        return result;
    }

    public Float convertToFloat(String numericText) {
        numericText = numericText.trim()
                .replace("$", "")
                .replace(",", "")
                .replace("%", "")
                .replace(" ", "");
        return !numericText.equals("-") ? Float.parseFloat(numericText) : 0f;
    }

    public Integer convertToInteger(String numericText) {
        numericText = numericText.trim()
                .replace("$", "")
                .replace(",", "")
                .replace("%", "")
                .replace(" ", "");
        return !numericText.equals("-") ? Integer.parseInt(numericText) : 0;
    }
}
