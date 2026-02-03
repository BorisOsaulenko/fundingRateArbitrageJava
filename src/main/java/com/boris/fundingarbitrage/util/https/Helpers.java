package com.boris.fundingarbitrage.util.https;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Helpers {
    public static URI sortParamsAlphabetically(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return uri;
        }

        List<String> keys = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            keys.add(keyValue[0]);
            params.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
        }

        keys.sort(String.CASE_INSENSITIVE_ORDER);

        StringBuilder sortedQuery = new StringBuilder();
        for (String key : keys) {
            if (!sortedQuery.isEmpty()) {
                sortedQuery.append("&");
            }
            sortedQuery.append(key).append("=").append(params.get(key));
        }

        try {
            return new URI(
                    uri.getScheme(), uri.getAuthority(), uri.getPath(), sortedQuery.toString(), uri.getFragment());
        } catch (Exception e) {
            throw new RuntimeException("Error constructing sorted URI", e);
        }
    }
}
