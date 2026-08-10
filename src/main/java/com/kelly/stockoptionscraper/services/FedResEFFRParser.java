package com.kelly.stockoptionscraper.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class FedResEFFRParser {

    public Document parseRawHtml(String html) {
        return Jsoup.parse(html);
    }

    public String extractCurrentRate(Document document)
                throws IOException {
        //var element = document.selectFirst(".series-meta-observation-value"); // St Louis Fed
        var element = document.selectFirst(".data-table table tbody tr:first-child td:last-child");
        if (element == null)
            throw new IOException("Couldn't locate EFFR rate in HTML.");

        return element.text();
    }

    public Float convertToFloat(String numericText) {
        numericText = numericText.trim()
                .replace("$", "")
                .replace(",", "")
                .replace("%", "")
                .replace(" ", "");
        return !numericText.equals("-") ? Float.parseFloat(numericText) : 0f;
    }
}
