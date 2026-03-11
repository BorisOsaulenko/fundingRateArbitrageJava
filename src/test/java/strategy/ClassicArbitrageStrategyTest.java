package strategy;

import com.boris.fundingarbitrage.strategy.ClassicPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;

public class ClassicArbitrageStrategyTest extends PreTradeArbitrageStrategyTest {
	private final PreTradeStrategy strat = new ClassicPreTradeStrategy();

	@Override
	protected PreTradeStrategy strategy() {
		return strat;
	}
}

