package com.boris.fundingarbitrage.coinParser;

import com.boris.fundingarbitrage.util.logger.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class UAInvestPageParser implements ICoinParser {
	private final String url = "https://uainvest.com.ua/arbitrage";

	@Override
	public Set<String> getCoinsSync() {
		try {
			// Basic fetching of the page
			Document doc = Jsoup.connect(url)
							.userAgent(
											"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
							.timeout(10000)
							.get();

			Logger.log(doc.toString());

			Elements tableRows = doc.select("tr[data-v-832563fd]");
			Logger.log("Found " + tableRows.size() + " rows on page");
			return tableRows.stream()
							.map(row -> row.selectFirst("strong[data-v-832563fd]"))
							.filter(Objects::nonNull)
							.map(Element::text)
							.collect(Collectors.toSet());
		} catch (IOException e) {
			Logger.error("Failed to fetch or parse page from url: " + url + ": " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
