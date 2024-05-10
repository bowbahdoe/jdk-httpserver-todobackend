package dev.mccue.todoapp;

import com.sun.net.httpserver.Request;
import dev.mccue.json.Json;
import dev.mccue.json.JsonEncodable;

public record TodoUrl(Request request, int id) implements JsonEncodable {
    @Override
    public Json toJson() {
        return Json.of("https://" + request.getRequestHeaders().getFirst("host") + "/" + id);
    }
}
