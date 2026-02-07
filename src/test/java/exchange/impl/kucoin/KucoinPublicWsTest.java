package exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.util.wss.publicmessagehandler.FundingSettlementViaRest;
import exchange.PublicWsTest;

public class KucoinPublicWsTest extends PublicWsTest<FundingSettlementViaRest<KucoinPublicMessageHandler>> {
	private static final KucoinContext context = new KucoinContext();
	private static final PublicHttpClient publicHttp = new KucoinPublicHttpClient(context);
	private static final KucoinPublicMessageHandler messageHandler = new KucoinPublicMessageHandler(context, publicHttp);
	private static final KucoinPublicWsClient publicWsClient = new KucoinPublicWsClient(context, messageHandler);

	@Override
	protected PublicWsClient<FundingSettlementViaRest<KucoinPublicMessageHandler>> publicWsClient() {
		return publicWsClient;
	}
}
