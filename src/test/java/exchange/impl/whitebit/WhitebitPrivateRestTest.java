package exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.impl.whitebit.WhitebitContext;
import exchange.PrivateRestTest;
import impl.whitebit.privaterest.WhitebitPrivateHttpClient;
import privatehttp.PrivateHttpClient;

public class WhitebitPrivateRestTest extends PrivateRestTest {
	private static final WhitebitContext context = new WhitebitContext();
	private static final WhitebitPrivateHttpClient client = new WhitebitPrivateHttpClient(context);

	@Override
	protected PrivateHttpClient privateRest() {
		return client;
	}
}
