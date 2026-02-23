package exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.impl.whitebit.WhitebitContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.WhitebitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import exchange.PrivateRestTest;

public class WhitebitPrivateRestTest extends PrivateRestTest {
	private static final WhitebitContext context = new WhitebitContext();
	private static final WhitebitPrivateHttpClient client = new WhitebitPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return client;
	}
}
