package execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.execution.OptimalWithdrawer;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.assetops.Withdrawal;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptimalWithdrawerTest {
	private TestExchange longExchange;
	private TestExchange shortExchange;
	private TestExchange sourceExchangeA;
	private TestExchange sourceExchangeB;
	private TestExchange sourceExchangeC;
	private TestExchange sourceExchangeD;
	private ArrayList<BaseExchange> allExchanges;
	private MockedStatic<Instances> instancesMock;

	@BeforeEach
	void setUp() {
		longExchange = new TestExchange(ExchangeName.BINANCE).setSpotUsdtBalance(20)
						.setFuturesUsdtBalance(0)
						.setSupportedChains(List.of(SupportedChain.TRX), List.of())
						.setWalletAddress(SupportedChain.TRX, "long-trx-address");

		shortExchange = new TestExchange(ExchangeName.OKX).setSpotUsdtBalance(100)
						.setFuturesUsdtBalance(0)
						.setSupportedChains(List.of(SupportedChain.TRX), List.of())
						.setWalletAddress(SupportedChain.TRX, "short-trx-address");

		sourceExchangeA = new TestExchange(ExchangeName.BYBIT).setSpotUsdtBalance(150)
						.setFuturesUsdtBalance(0)
						.setSupportedChains(List.of(), List.of(new WithdrawChain(SupportedChain.TRX, 1.0, 10.0)));

		sourceExchangeB = new TestExchange(ExchangeName.GATE).setSpotUsdtBalance(60)
						.setFuturesUsdtBalance(0)
						.setSupportedChains(List.of(), List.of(new WithdrawChain(SupportedChain.TRX, 0.5, 10.0)));

		sourceExchangeC = new TestExchange(ExchangeName.GATE).setSpotUsdtBalance(60)
						.setFuturesUsdtBalance(0)
						.setSupportedChains(List.of(), List.of(new WithdrawChain(SupportedChain.TRX, 0.5, 10.0)));

		sourceExchangeD = new TestExchange(ExchangeName.GATE).setSpotUsdtBalance(60)
						.setFuturesUsdtBalance(0)
						.setSupportedChains(List.of(), List.of(new WithdrawChain(SupportedChain.TRX, 0.5, 10.0)));

		allExchanges = new ArrayList<>(List.of(longExchange, shortExchange, sourceExchangeA, sourceExchangeB));
		instancesMock = Mockito.mockStatic(Instances.class);
		instancesMock.when(Instances::getExchangeArray).thenReturn(allExchanges);
	}

	@AfterEach
	void tearDown() {
		instancesMock.close();
	}

	@Test
	void withdrawsDirectlyFromOneSourceExchangeWhenItHasEnoughBalance() {
		OptimalWithdrawer withdrawer = new OptimalWithdrawer(longExchange, shortExchange, 100);
		withdrawer.withdrawUsdtToExchanges().join();

		List<Withdrawal> aWithdrawals = sourceExchangeA.getWithdrawals();
		List<Withdrawal> bWithdrawals = sourceExchangeB.getWithdrawals();

		assertEquals(1, aWithdrawals.size());
		assertEquals(0, bWithdrawals.size());
		assertEquals(80, aWithdrawals.get(0).amount());
		assertEquals(SupportedChain.TRX, aWithdrawals.get(0).address().chain());
		assertEquals("long-trx-address", aWithdrawals.get(0).address().address());
	}
}
