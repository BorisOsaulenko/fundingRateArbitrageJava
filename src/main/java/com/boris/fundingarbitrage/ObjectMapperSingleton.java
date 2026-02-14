package com.boris.fundingarbitrage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

public class ObjectMapperSingleton {
	@Getter
	private static final ObjectMapper instance = new ObjectMapper().configure(
					DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
					false
	);

}
