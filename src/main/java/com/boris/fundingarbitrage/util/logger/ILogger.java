package com.boris.fundingarbitrage.util.logger;

import com.boris.fundingarbitrage.util.coinvector.CoinVector;

public interface ILogger {
    void log(String message);

    void warn(String message);

    void error(String message);

    <T> void logCoinVector(CoinVector<T> coinVector);
}
