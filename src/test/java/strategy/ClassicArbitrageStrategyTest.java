package strategy;

import com.boris.fundingarbitrage.strategy.ArbitrageStrategy;
import com.boris.fundingarbitrage.strategy.ClassicArbitrageStrategy;

public class ClassicArbitrageStrategyTest extends ArbitrageStrategyTest {
	private final ArbitrageStrategy strat = new ClassicArbitrageStrategy();
	@Override
	protected ArbitrageStrategy strategy() {
		return strat;
	}
}

