package exchange;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AllExchangesWsStabilityTest {
	private static final Duration WAIT_DURATION = Duration.ofMinutes(5);
	private static final String testCoin = "SOL";

	@Test
	@Tag("integration")
	public void testAllExchangesWebsocketStability() throws Exception {
		ArrayList<BaseExchange> exchanges = Instances.getExchangeArray();
		for (BaseExchange exchange : exchanges) {
			exchange.publicWsClient.subscribeMarkPrice(testCoin, (_) -> {});
			exchange.privateWsClient.subscribeDeposits((_) -> {});
		}
		Logger.log("Starting websocket stability test for " + exchanges.size() + " exchanges.");

		try {
			TimeUnit.MILLISECONDS.sleep(WAIT_DURATION.toMillis());
		} finally {
			for (BaseExchange exchange : exchanges) {
				exchange.publicWsClient.close();
				exchange.privateWsClient.close();
			}
			Logger.log("Websocket stability test finished. Closed all exchange clients.");
		}
	}
}
