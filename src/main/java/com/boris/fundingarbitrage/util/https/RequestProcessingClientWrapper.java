package com.boris.fundingarbitrage.util.https;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class RequestProcessingClientWrapper {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private final PrettyHttpClient client;

	public RequestProcessingClientWrapper(PrettyHttpClient client) {
		this.client = client;
	}

	public <U> CompletableFuture<U> getResponse(SimpleHttpRequest req, Class<U> responseClass) {
		return client.send(req).thenApply((response) -> {
			try {
				return mapper.readValue(response.getBodyBytes(), responseClass);
			} catch (Exception e) {
				Logger.error(String.format("Error parsing public rest response: %s", e.getMessage()));
				throw new RuntimeException("Failed to process request", e);
			}
		});
	}

	public <T, U> CompletableFuture<U> processRequest(
					SimpleHttpRequest request,
					Class<T> responseClass,
					Function<T, U> parser
	) {
		return getResponse(request, responseClass).thenApply(parser);
	}

	public <T extends PaginatedResponse> CompletableFuture<Void> processPaginatedRequest(
					Function<String, SimpleHttpRequest> getRequestWithPagination,
					Class<T> responseClass,
					Consumer<T> parser,
					String paginationIndex
	) {
		SimpleHttpRequest currentRequest = getRequestWithPagination.apply(paginationIndex);
		return getResponse(currentRequest, responseClass).thenCompose(res -> {
			parser.accept(res);
			if (res.getPaginationIndex().isEmpty()) return CompletableFuture.completedFuture(null);
			return processPaginatedRequest(getRequestWithPagination, responseClass, parser, res.getPaginationIndex());
		});
	}
}
