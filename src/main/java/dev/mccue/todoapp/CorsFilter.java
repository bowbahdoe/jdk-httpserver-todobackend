package dev.mccue.todoapp;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import dev.mccue.jdk.httpserver.Body;
import dev.mccue.jdk.httpserver.HttpExchangeUtils;

import java.io.IOException;
import java.util.List;

public final class CorsFilter extends Filter {
    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.put("access-control-allow-origin", List.of("*"));
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            headers.put("access-control-allow-headers", List.of("*"));
            headers.put("access-control-allow-methods", List.of("*"));
            HttpExchangeUtils.sendResponse(exchange, 200, Body.empty());
        }
        else {
            chain.doFilter(exchange);
        }
    }

    @Override
    public String description() {
        return "Adds CORS headers to the request + handles options requests";
    }
}
