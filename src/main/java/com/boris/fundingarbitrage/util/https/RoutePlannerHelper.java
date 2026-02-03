package com.boris.fundingarbitrage.util.https;

import java.net.InetAddress;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

public class RoutePlannerHelper {
    public static HttpRoutePlanner getForIp(InetAddress address) {
        return new DefaultRoutePlanner(null) {
            @Override
            protected InetAddress determineLocalAddress(final HttpHost firstHop, final HttpContext context) {
                return address; // ✅ bind source IP
            }
        };
    }
}
