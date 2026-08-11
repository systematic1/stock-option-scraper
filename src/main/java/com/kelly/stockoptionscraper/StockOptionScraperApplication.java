package com.kelly.stockoptionscraper;

import com.kelly.stockoptionscraper.models.OptionGexData;
import com.kelly.stockoptionscraper.models.StrikeOptionData;
import com.kelly.stockoptionscraper.models.YFOptionData;
//import com.kelly.stockoptionscraper.services.OptionDataService;
//import com.kelly.stockoptionscraper.services.OptionGexDataService;
import com.kelly.stockoptionscraper.services.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.sql.Array;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import jakarta.annotation.PostConstruct;

import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableJpaRepositories(basePackages = "com.kelly.stockoptionscraper.services")
public class StockOptionScraperApplication {

    private OptionDataService optionDataService;
    private OptionGexDataService optionGexDataService;
    private StrikeOptionService strikeOptionService;

    private static StockOptionScraperApplication appInstance;
    private final YFOptionChainParser optionDataParser;
    private final FedResEFFRParser rateDataParser;
    private static ArrayList<String> symbols;
    private ArrayList<YFOptionData> callOptionChain;
    private ArrayList<YFOptionData> putOptionChain;
    private ArrayList<StrikeOptionData> strikeOptions;
    private Float stockPrice;

    // @Value("${application.properties-variable-name}") only works on instance variables
    @Value("${app.yf-option-scraper.url}")
    public String optionScraperUrl;

    @Value("${app.rate-scraper.url}")
    public String rateScraperUrl;

    @Value("${app.override-time-frame}")
    public boolean overrideTimeFrame;    // true allows running at any time

    @Value("${app.symbol.default}")
    public String defaultSymbol;

    @Value("${app.run-interval.minutes}")
    public long runIntervalPeriod;

    @Value("${app.output-console-log-verbose}")
    public boolean canOutputVerboseLog;

    @Value("${app.max-strike-range}")
    public int maxStrikeRange;

    public Float interestRate = 0.30f;   // default value

    static void main(String[] args) {
        symbols = new ArrayList<>();
        Collections.addAll(symbols, args);

        SpringApplication.run(StockOptionScraperApplication.class, args);
    }

    public StockOptionScraperApplication(OptionDataService optionDataSvc,
                                         OptionGexDataService gexDataSvc,
                                         StrikeOptionService strikeOptSvc) {
        optionDataParser = new YFOptionChainParser();
        rateDataParser = new FedResEFFRParser();

        optionDataService = optionDataSvc;
        optionGexDataService = gexDataSvc;
        strikeOptionService = strikeOptSvc;

        callOptionChain = new ArrayList<>();
        putOptionChain = new ArrayList<>();
    }

    // This is needed in order access bound @Value variables after construction
    @PostConstruct
    public void init() {
        if (symbols.isEmpty())
            symbols.add(defaultSymbol);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable task = this::checkTimeAndRun;

        long initialDelay = 0;
        scheduler.scheduleAtFixedRate(task, initialDelay, runIntervalPeriod, TimeUnit.MINUTES);

        System.out.println(String.format("Scheduler set for %d minute intervals", runIntervalPeriod));
        System.out.println();
    }

    public void checkTimeAndRun() {
        var now = LocalDateTime.now();
        var startTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 30)); // 9:30am
        var endTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(16, 0));   // 4:00pm

        if (overrideTimeFrame || now.isEqual(startTime) || now.isEqual(endTime) || (now.isAfter(startTime) && now.isBefore(endTime))) {
            System.out.println(String.format("******************** Process starting at %d:%02d *****************", now.getHour(), now.getMinute()));
            System.out.println();

            try {
                runProcess();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void runProcess()
            throws IOException, InterruptedException {

        loadRateData();
        System.out.println(String.format("Using Effective Federal Funds Rate (interest rate) of %6.4f", interestRate));

        for (var symbol : symbols) {
            System.out.println("====================================================================================");
            System.out.println(String.format("Requesting and analyzing symbol: %s ...", symbol));
            System.out.println();

            loadOptionData(symbol);
            strikeOptions = new ArrayList<>();

            if (canOutputVerboseLog)
                System.out.println(String.format("Current spot price: %10.2f", stockPrice));

            if (callOptionChain.isEmpty() || putOptionChain.isEmpty()) {
                System.out.println("- No option chains were found for symbol. Skipping...");
                continue;
            }

            for (var callOption : callOptionChain)
                callOption.computeGreeks(stockPrice, interestRate);

            for (var putOption : putOptionChain)
                putOption.computeGreeks(stockPrice, interestRate);

            var expDate = callOptionChain.getFirst().getExpirationDate();

            // Write Option Chain to the database
            for (var callOption : callOptionChain)
                optionDataService.save(callOption);

            for (var putOption : putOptionChain)
                optionDataService.save(putOption);

            // Create list of strikes with option data for the strike
            for (var callOption : callOptionChain) {
                var strike = callOption.getStrikePrice();
                var putOption = putOptionChain.stream()
                        .filter(opt -> opt.getStrikePrice().equals(strike))
                        .findFirst();

                var strikeOption = new StrikeOptionData(symbol, expDate, strike, LocalDateTime.now(),
                        callOption, putOption.orElse(new YFOptionData()));
                strikeOptions.add(strikeOption);
            }

            // Write Strike-Option data to the database
            for (var strikeOption : strikeOptions)
                strikeOptionService.save(strikeOption);

            // Compute GEX overall data
            var gex = new OptionGexData(symbol, LocalDateTime.now(), strikeOptions);

            // Write GEX data to the database
            optionGexDataService.save(gex);

            if (canOutputVerboseLog) {
                Float midPrice;
                var headerText = "Strike,  Expiration,     Ask,      Bid,      Mid  || Volume,  Open Int, IV %   || Delta,   Gamma,          Gex,        Dex";
                var dataFormat = "%7.1f, %10s, %8.2f, %8.2f, %8.2f || %7d, %7d, %7.2f || %6.4f, %6.4f, %14.4f, %11.2f";
                // format string: "%-20s" = left-align string with 20 character reserved
                //              "%20s" = right-align string with 20 chars reserved
                //              "%10d" = right-align integer with 15 chars reserved
                //              "%12.2f" = right-align float with 12 chars reserved and 2 decimal places

                System.out.println();
                System.out.println("====================== CALLS =======================");
                System.out.println();

                System.out.println(headerText);
                System.out.println();

                for (var opt : callOptionChain) {
                    midPrice = (opt.getAskPrice() + opt.getBidPrice()) / 2f;
                    System.out.println(String.format(dataFormat,
                            opt.getStrikePrice(), opt.getExpirationDate(), opt.getAskPrice(), opt.getBidPrice(), midPrice,
                            opt.getVolume(), opt.getOpenInterest(), opt.getImpliedVolatilityPercent(),
                            opt.getDelta(), opt.getGamma(), opt.getGex(), opt.getDex()));
                }

                System.out.println();
                System.out.println("====================== PUTS ========================");
                System.out.println();

                System.out.println(headerText);
                System.out.println();

                for (var opt : putOptionChain) {
                    midPrice = (opt.getAskPrice() + opt.getBidPrice()) / 2f;
                    System.out.println(String.format(dataFormat,
                            opt.getStrikePrice(), opt.getExpirationDate(), opt.getAskPrice(), opt.getBidPrice(), midPrice,
                            opt.getVolume(), opt.getOpenInterest(), opt.getImpliedVolatilityPercent(),
                            opt.getDelta(), opt.getGamma(), opt.getGex(), opt.getDex()));
                }

                System.out.println();
                System.out.println("===================== GEX DATA =====================");
                System.out.println();

                System.out.println("Strike, Net Gex, Absolute Gex");
                System.out.println();

                for (var data : strikeOptions) {
                    System.out.println(String.format("%7.1f, %12.2f, %12.2f",
                            data.getStrikePrice(), data.getNetGex(), data.getAbsoluteGex()));
                }

                System.out.println();
                System.out.println(String.format("Call Wall:  Strike = %7.1f, GEX Value = %13.2f",
                        gex.getCallWallStrike(), gex.getCallWallGex()));
                System.out.println(String.format("Put Wall:   Strike = %7.1f, GEX Value = %13.2f",
                        gex.getPutWallStrike(), gex.getPutWallGex()));
                System.out.println(String.format("Net Call Gex Value = %12.3f", gex.getNetCallGex()));
                System.out.println(String.format("Net Put  Gex Value = %12.3f", gex.getNetPutGex()));

                System.out.println(String.format("Call Gex Hedge = %12.2f (M)", gex.getNetCallGex() / 1000000f));
                System.out.println(String.format("Put  Gex Hedge = %12.2f (M)", gex.getNetPutGex() / 1000000f));

                System.out.println(String.format("Net TOTAL Gex Value = %13.3f", gex.getNetTotalGex()));

                System.out.println(gex.isPositive() ? "Positive (+) GEX" : "Negative (-) GEX");
                System.out.println(gex.isCallWallHigherGex() ? "Call wall is higher" : "Put wall is higher");

                System.out.println();
                System.out.println("***************************************************************************");
                System.out.println();
            }
        }
    }

    private void loadOptionData(String symbol)
            throws IOException, InterruptedException {

        callOptionChain.clear();
        putOptionChain.clear();

        Long unixEpoch = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        symbol = symbol.replace("^", "%5E");

        var finalUrl = optionScraperUrl
                .replace("{symbol}", symbol)
                .replace("{epoch}", unixEpoch.toString());

        System.out.println(String.format("Making http call to options url: %s", finalUrl));

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

        Float atmStrike = 0f;

        var tempCallOptions = new ArrayList<YFOptionData>();
        var tempPutOptions = new ArrayList<YFOptionData>();

        for (Element row : parsedOptionRows) {
            var optionChainData = optionDataParser.parseOptionRowData(row);

            if (optionChainData.isCall())
                tempCallOptions.add(optionChainData);
            else if (optionChainData.isPut())
                tempPutOptions.add(optionChainData);

            if (atmStrike == 0f && optionChainData.getStrikePrice() >= stockPrice)
                atmStrike = optionChainData.getStrikePrice();
        }

        //  Narrow list of Calls and Puts to within 25 strikes above and below stock price
        final Float atmStrikeFinal = atmStrike;
        var atmCallOption = tempCallOptions
                .stream()
                .filter(option -> option.getStrikePrice().equals(atmStrikeFinal))
                .findFirst();
        var atmPutOption = tempPutOptions
                .stream()
                .filter(option -> option.getStrikePrice().equals(atmStrikeFinal))
                .findFirst();
        var callOptIndex = tempCallOptions.indexOf(atmCallOption.orElseThrow());
        var putOptIndex = tempPutOptions.indexOf(atmPutOption.orElseThrow());

        for (var index = Math.max(0, callOptIndex - maxStrikeRange); index < Math.min(tempCallOptions.size(), callOptIndex + maxStrikeRange); index++)
            callOptionChain.add(tempCallOptions.get(index));

        for (var index = Math.max(0, putOptIndex - maxStrikeRange); index < Math.min(tempPutOptions.size(), putOptIndex + maxStrikeRange); index++)
            putOptionChain.add(tempPutOptions.get(index));

        tempCallOptions.clear();
        tempPutOptions.clear();

        System.out.println(String.format("- Parsed %d call options and %d put options", callOptionChain.size(), putOptionChain.size()));
    }

    private void loadRateData()
            throws IOException, InterruptedException {
        System.out.println(String.format("- Making http call to EFFR url: %s", rateScraperUrl));

        // Use HttpClient to call to Federal Reserve API
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(rateScraperUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate")
                .build();

        String stringResponse = null;

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                var responseStream = response.body();

                var contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
                if ("gzip".equalsIgnoreCase(contentEncoding))
                    responseStream = new GZIPInputStream(responseStream);

                stringResponse = new String(responseStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            else {
                System.err.println(String.format("- Called failed with status code: %d", response.statusCode()));
                throw new HttpServerErrorException(HttpStatusCode.valueOf(response.statusCode()));
            }

            var document = rateDataParser.parseRawHtml(stringResponse);
            var intRate = rateDataParser.extractCurrentRate(document);

            interestRate = rateDataParser.convertToFloat(intRate) / 100f;

            System.out.println(String.format("--- Received rate value: %s", intRate));
        }
        catch (HttpTimeoutException htoex) {
            System.err.println("* Timed out requesting EFFR. Using default value for interest rate.");
            interestRate = 0.0363f;
        }
    }
}

