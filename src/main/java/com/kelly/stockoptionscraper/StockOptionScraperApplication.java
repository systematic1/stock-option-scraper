package com.kelly.stockoptionscraper;

import com.kelly.stockoptionscraper.models.OptionGexData;
import com.kelly.stockoptionscraper.models.YFOptionData;
import com.kelly.stockoptionscraper.services.OptionDataService;
import com.kelly.stockoptionscraper.services.OptionGexDataService;
import com.kelly.stockoptionscraper.services.YFOptionChainParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.*;

import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;

/**************
 *   Remove "(exclude = {..})" from @SprintBootApplication to enable database access
 **************/
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class StockOptionScraperApplication {

    private static StockOptionScraperApplication appInstance;
    private final YFOptionChainParser optionDataParser;
    private final OptionDataService optionDataService;
    private final OptionGexDataService optionGexDataService;
    private static ArrayList<String> symbols;
    private ArrayList<YFOptionData> callOptionChain;
    private ArrayList<YFOptionData> putOptionChain;
    private Float stockPrice;

    @Value("${app.scraper.url}")
    public static String sourceUrl;

    @Value("${app.override-time-frame}")
    public static boolean overrideTimeFrame;    // true allows running at any time

    @Value("${app.symbol.default}")
    public static String defaultSymbol;

    @Value("${app.run-interval.minutes}")
    public static long runIntervalPeriod;

    public static Float interestRate = 1f;   // at 0 DTE, the e^(-rT) approaches one so just use constant here

    static void main(String[] args) {
        symbols = new ArrayList<>();
        Collections.addAll(symbols, args);

        if (symbols.isEmpty())
            symbols.add(defaultSymbol);

        var context = SpringApplication.run(StockOptionScraperApplication.class, args);
        appInstance = context.getBean(StockOptionScraperApplication.class);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable task = () -> appInstance.checkTimeAndRun();

        long initialDelay = 0;
        scheduler.scheduleAtFixedRate(task, initialDelay, runIntervalPeriod, TimeUnit.MINUTES);

        System.out.println(String.format("Scheduler set for %d minute intervals", runIntervalPeriod));
        System.out.println();
    }

    public StockOptionScraperApplication(OptionDataService optionSvc, OptionGexDataService optionGexSvc) {
        optionDataParser = new YFOptionChainParser();
        optionDataService = optionSvc;
        optionGexDataService = optionGexSvc;

        callOptionChain = new ArrayList<>();
        putOptionChain = new ArrayList<>();
    }

    public void checkTimeAndRun() {
        var now = LocalDateTime.now();
        var startTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 30)); // 9:30am
        var endTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(16, 0));   // 4:00pm

        if (overrideTimeFrame || now.isEqual(startTime) || now.isEqual(endTime) || (now.isAfter(startTime) && now.isBefore(endTime))) {
            System.out.println(String.format("** Process starting at %d:%2d", now.getHour(), now.getMinute()));

            try {
                runProcess();
            }
            catch (Exception ex) {
                System.err.println("!! EXCEPTION:");
                System.err.println(String.format("Message: %s -> %s", ex.getClass().getName(), ex.getMessage()));
            }
        }
    }

    private void runProcess()
            throws IOException, InterruptedException {

        for (var symbol : symbols) {
            System.out.println(String.format("Requesting and analyzing symbol: %s ...", symbol));

            loadOptionData(symbol);

            if (callOptionChain.isEmpty() || putOptionChain.isEmpty()) {
                System.out.println("- No option chains were found for symbol. Skipping...");
                continue;
            }

            for (var callOption : callOptionChain) {
                callOption.computeGreeks(stockPrice, interestRate);
            }

            for (var putOption : putOptionChain) {
                putOption.computeGreeks(stockPrice, interestRate);
            }

            // Write to the database
            for (var callOption : callOptionChain) {
                optionDataService.save(callOption);
            }

            for (var putOption : putOptionChain) {
                optionDataService.save(putOption);
            }

            // Compute GEX overall data
            var gex = new OptionGexData(symbol, callOptionChain, putOptionChain);

            // Write to the database
            optionGexDataService.save(gex);

            Float midPrice;
            var headerText = "Strike, Expiration, Ask, Bid, Mid || Last Trade, Last Price, Change, % Change || Volume, Open Int, IV % || Delta, Gamma, Gex";
            var dataFormat = "%.2f, %s, %.2f, %.2f, %.2f || %s, %.2f, %.2f, %.3f || %d, %d, %.2f || %.4f, %.4f, %.0f";
            // format string: "%-20s" = left-align string with 20 character reserved
            //              "%20s" = right-align string with 20 chars reserved
            //              "%10d" = right-align integer with 15 chars reserved
            //              "%12.2f" = right-align float with 12 chars reserved and 2 decimal places

            System.out.println();
            System.out.println("================== CALLS ===================");

            System.out.println(headerText);
            System.out.println();

            for (var opt : callOptionChain) {
                midPrice = (opt.getAskPrice() + opt.getBidPrice()) / 2f;
                System.out.println(String.format(dataFormat,
                        opt.getStrikePrice(), opt.getExpirationDate(), opt.getAskPrice(), opt.getBidPrice(), midPrice,
                        opt.getLastTradeDate(), opt.getLastPrice(), opt.getChange(), opt.getPercentChange(),
                        opt.getVolume(), opt.getOpenInterest(), opt.getImpliedVolatilityPercent(),
                        opt.getDelta(), opt.getGamma(), opt.getGex()));
            }

            System.out.println();
            System.out.println("================== PUTS ====================");

            System.out.println(headerText);
            System.out.println();

            for (var opt : putOptionChain) {
                midPrice = (opt.getAskPrice() + opt.getBidPrice()) / 2f;
                System.out.println(String.format(dataFormat,
                        opt.getStrikePrice(), opt.getExpirationDate(), opt.getAskPrice(), opt.getBidPrice(), midPrice,
                        opt.getLastTradeDate(), opt.getLastPrice(), opt.getChange(), opt.getPercentChange(),
                        opt.getVolume(), opt.getOpenInterest(), opt.getImpliedVolatilityPercent(),
                        opt.getDelta(), opt.getGamma(), opt.getGex()));
            }

            System.out.println();
            System.out.println("================= GEX DATA =================");
            System.out.println();

            System.out.println(String.format("Call Wall:  Strike = %f, GEX Value = %f",
                    gex.getCallWallStrike(), gex.getCallWallGex()));
            System.out.println(String.format("Put Wall:   Strike = %f, GEX Value = %f",
                    gex.getPutWallStrike(), gex.getPutWallGex()));
            System.out.println(String.format("Net Call Gex Value = %f", gex.getNetCallGex()));
            System.out.println(String.format("Net Put  Gex Value = %f", gex.getNetPutGex()));

            System.out.println(String.format("Net TOTAL Gex Value = %f", gex.getNetTotalGex()));
            System.out.println(String.format("Aggregate Gex Value = %f", gex.getAggregateTotalGex()));

            System.out.println(gex.isPositive() ? "Positive (+) GEX" : "Negative (-) GEX");
            System.out.println(gex.isCallWallHigherGex() ? "Call wall is higher" : "Put wall is higher");

            System.out.println();
            System.out.println("**********************************************************");
            System.out.println();
        }
    }

    private void loadOptionData(String symbol)
            throws IOException, InterruptedException {

        callOptionChain.clear();
        putOptionChain.clear();

        Long unixEpoch = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        symbol = symbol.replace("^", "%5E");

        var finalUrl = sourceUrl
                .replace("{symbol}", symbol)
                .replace("{date}", unixEpoch.toString());

        System.out.println(String.format("Making http call to url: %s", finalUrl));

        // Use HttpClient to call to Yahoo finance API
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .GET()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();

        String stringResponse = null;

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200)
            stringResponse = response.body();
        else {
            System.err.println(String.format("- Called failed with status code: %d", response.statusCode()));
            throw new HttpServerErrorException(HttpStatusCode.valueOf(response.statusCode()));
        }

        System.out.println(String.format("- Parsing HTML content (bytes length = %d)...", stringResponse.length()));

        var document = optionDataParser.parseRawHtml(stringResponse);
        var parsedOptionRows = optionDataParser.extractOptionChainRows(document);
        var parsedStockPrice = optionDataParser.extractStockPrice(document);

        stockPrice = optionDataParser.convertToFloat(parsedStockPrice);

        System.out.println(String.format("- Parsed stock price: %f", stockPrice));

        for (Element row : parsedOptionRows) {
            var optionChainData = optionDataParser.parseOptionRowData(row);

            if (optionChainData.isCall())
                callOptionChain.add(optionChainData);
            else if (optionChainData.isPut())
                putOptionChain.add(optionChainData);
        }

        System.out.println(String.format("- Parsed %d call options and %d put options", callOptionChain.size(), putOptionChain.size()));
    }
}

