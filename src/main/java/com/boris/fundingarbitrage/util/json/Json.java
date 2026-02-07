package com.boris.fundingarbitrage.util.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public final class Json {
	private Json() {}

	public static void requireCodeOk(String code, String expected, String msg) {
		if (!expected.equals(code)) {
			throw new IllegalStateException("Error code: " + code + ", message: " + msg);
		}
	}

	public static JsonNode requireObject(JsonNode node, String field) {
		JsonNode child = requireField(node, field);
		if (!child.isObject()) {
			throw new IllegalStateException("Expected object for field: " + field);
		}
		return child;
	}

	public static JsonNode requireArray(JsonNode node, String field) {
		JsonNode child = requireField(node, field);
		if (!child.isArray()) {
			throw new IllegalStateException("Expected array for field: " + field);
		}
		return child;
	}

	public static JsonNode requireField(JsonNode node, String field) {
		if (node == null || !node.has(field) || node.get(field).isNull()) {
			throw new IllegalStateException("Missing field: " + field);
		}
		return node.get(field);
	}

	public static String requireText(JsonNode node, String field) {
		String text = requireField(node, field).asText();
		if (text == null || text.isEmpty()) {
			throw new IllegalStateException("Missing field: " + field);
		}
		return text;
	}

	public static long requireLong(JsonNode node, String field) {
		String text = requireText(node, field);
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException ex) {
			throw new IllegalStateException("Invalid long for field: " + field, ex);
		}
	}

	public static double requireDouble(JsonNode node, String field) {
		String text = requireText(node, field);
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException ex) {
			throw new IllegalStateException("Invalid number for field: " + field, ex);
		}
	}

	public static boolean requireBoolean(JsonNode node, String field) {
		JsonNode child = requireField(node, field);
		if (!child.isBoolean()) {
			throw new IllegalStateException("Invalid boolean for field: " + field);
		}
		return child.asBoolean();
	}

	public static Instant toInstantMillisOrNanos(long ts) {
		if (ts > 1_000_000_000_000_000L) {
			return Instant.ofEpochMilli(ts / 1_000_000L);
		}
		return Instant.ofEpochMilli(ts);
	}
}
