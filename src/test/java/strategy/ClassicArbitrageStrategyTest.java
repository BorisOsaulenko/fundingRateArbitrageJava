package strategy;

import com.boris.fundingarbitrage.strategy.pretradestrategy.ClassicPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.CrossPreTradeStrategy;

public class ClassicArbitrageStrategyTest extends PreTradeArbitrageStrategyTest {
	private final CrossPreTradeStrategy strat = new ClassicPreTradeStrategy();

	@Override
	protected CrossPreTradeStrategy strategy() {
		return strat;
	}
}

