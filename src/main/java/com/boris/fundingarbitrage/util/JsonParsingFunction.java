package com.boris.fundingarbitrage.util;

import com.fasterxml.jackson.core.JsonProcessingException;

public @FunctionalInterface interface JsonParsingFunction<R> {
    R apply(String value) throws JsonProcessingException;
}
