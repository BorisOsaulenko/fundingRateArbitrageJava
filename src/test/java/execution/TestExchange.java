package execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.assetops.Withdrawal;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TestExchange extends BaseExchange {
	private final PrivateHttpClient privateHttpClientMock;
	private final Map<SupportedChain, WalletAddress> walletAddresses = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<Withdrawal> withdrawals = new CopyOnWriteArrayList<>();

	private volatile double spotUsdtBalance = 0.0;
	private volatile double futuresUsdtBalance = 0.0;
	private volatile ExchangeChains exchangeChains = new ExchangeChains(List.of(), List.of());

	public TestExchange(ExchangeName name) {
		this(name, Mockito.mock(PrivateHttpClient.class));
	}

	private TestExchange(ExchangeName name, PrivateHttpClient privateHttpClientMock) {
		super(
						name,
						Mockito.mock(PublicWsClient.class),
						Mockito.mock(PrivateWsClient.class),
						Mockito.mock(PublicHttpClient.class),
						privateHttpClientMock
		);
		this.privateHttpClientMock = privateHttpClientMock;
		initStubs();
	}

	private void initStubs() {
		when(privateHttpClientMock.getSpotUsdtBalance()).thenAnswer(_ ->
						CompletableFuture.completedFuture(spotUsdtBalance));
		when(privateHttpClientMock.getFuturesUsdtBalance()).thenAnswer(_ ->
						CompletableFuture.completedFuture(futuresUsdtBalance));
		when(privateHttpClientMock.getSupportedChains()).thenAnswer(_ ->
						CompletableFuture.completedFuture(exchangeChains));
		when(privateHttpClientMock.getUsdtWalletAddress(any(SupportedChain.class))).thenAnswer(invocation -> {
			SupportedChain chain = invocation.getArgument(0);
			WalletAddress walletAddress = walletAddresses.get(chain);
			if (walletAddress == null) {
				return CompletableFuture.failedFuture(new IllegalStateException(
								"Wrong test data: no wallet address configured for chain: " + chain));
			}
			return CompletableFuture.completedFuture(walletAddress);
		});
		when(privateHttpClientMock.withdrawUsdt(any(Withdrawal.class))).thenAnswer(invocation -> {
			Withdrawal withdrawal = invocation.getArgument(0);
			withdrawals.add(withdrawal);
			return CompletableFuture.completedFuture(null);
		});
	}

	public TestExchange setSpotUsdtBalance(double spotUsdtBalance) {
		this.spotUsdtBalance = spotUsdtBalance;
		return this;
	}

	public TestExchange setFuturesUsdtBalance(double futuresUsdtBalance) {
		this.futuresUsdtBalance = futuresUsdtBalance;
		return this;
	}

	public TestExchange setSupportedChains(
					List<SupportedChain> depositableChains,
					List<WithdrawChain> withdrawableChains
	) {
		this.exchangeChains = new ExchangeChains(depositableChains, withdrawableChains);
		return this;
	}

	public TestExchange setWalletAddress(WalletAddress walletAddress) {
		this.walletAddresses.put(walletAddress.chain(), walletAddress);
		return this;
	}

	public TestExchange setWalletAddress(SupportedChain chain, String address) {
		return setWalletAddress(new WalletAddress(chain, address, null));
	}

	public TestExchange clearWithdrawals() {
		this.withdrawals.clear();
		return this;
	}

	public List<Withdrawal> getWithdrawals() {
		return List.copyOf(withdrawals);
	}
}
