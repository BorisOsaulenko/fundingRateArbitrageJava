package exchange;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AllExchangesWsStabilityTest {
	private static final Logger log = LoggerFactory.getLogger(AllExchangesWsStabilityTest.class);
	private static final Duration WAIT_DURATION = Duration.ofMinutes(5);
	private static final String testCoin = "SOL";

	@Test
	@Tag("integration")
	@Tag("manual")
	public void testAllExchangesWebsocketStability() throws Exception {
		ArrayList<BaseExchange> exchanges = Instances.getExchangeArray();
		for (BaseExchange exchange : exchanges) {
			exchange.publicWsClient().subscribeFuturesMarkPrice(
							testCoin, (_) -> {
							}
			);
		}
		log.info("Starting websocket stability test for {} exchanges.", exchanges.size());

		try {
			TimeUnit.MILLISECONDS.sleep(WAIT_DURATION.toMillis());
		} finally {
			for (BaseExchange exchange : exchanges) exchange.publicWsClient().close();
			log.info("Websocket stability test finished. Closed all exchange clients.");
		}
	}
}
