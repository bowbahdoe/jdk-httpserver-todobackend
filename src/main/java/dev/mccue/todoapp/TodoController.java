package dev.mccue.todoapp;

import com.sun.net.httpserver.HttpExchange;
import dev.mccue.jdk.httpserver.HttpExchangeUtils;
import dev.mccue.jdk.httpserver.json.JsonBody;
import dev.mccue.jdk.httpserver.regexrouter.RegexRouter;
import dev.mccue.jdk.httpserver.regexrouter.RouteParams;
import dev.mccue.json.Json;
import dev.mccue.json.JsonDecoder;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.nio.file.Files.delete;

public final class TodoController {
    private final DataSource db;

    public TodoController(DataSource db) {
        this.db = db;
    }

    public void register(RegexRouter.Builder router) {
        router
                .get(Pattern.compile("/"), this::getAllTodos)
                .get(Pattern.compile("/(?<id>.+)"), this::getTodo)
                .patch(Pattern.compile("/(?<id>.+)"), this::patchTodo)
                .post(Pattern.compile("/"), this::postTodo)
                .delete(Pattern.compile("/(?<id>.+)"), this::deleteTodo)
                .delete(Pattern.compile("/"), this::deleteAllTodos);
    }

    public void getAllTodos(HttpExchange exchange) throws IOException {
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT id, title, completed, "order"
                     FROM todo
                     """)) {
            var rs = stmt.executeQuery();
            var arrayBuilder = Json.arrayBuilder();

            while (rs.next()) {
                arrayBuilder.add(
                        Json.objectBuilder()
                                .put("title", rs.getString("title"))
                                .put("completed", rs.getBoolean("completed"))
                                .put("url", new TodoUrl(exchange, rs.getInt("id")))
                                .put("order", rs.getInt("order"))
                );
            }

            HttpExchangeUtils.sendResponse(exchange, 200, JsonBody.of(arrayBuilder));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void getTodo(HttpExchange exchange) throws IOException {
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT id, title, completed, "order"
                     FROM todo
                     WHERE id = ?
                     """)) {
            var routeParams = RouteParams.get(exchange);
            stmt.setInt(1, Integer.parseInt(routeParams.param("id").orElseThrow()));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                HttpExchangeUtils.sendResponse(
                        exchange,
                        200,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("title", rs.getString("title"))
                                        .put("completed", rs.getBoolean("completed"))
                                        .put("url", new TodoUrl(exchange, rs.getInt("id")))
                                        .put("order", rs.getInt("order"))
                        )
                );
            }
            else {
                HttpExchangeUtils.sendResponse(exchange, 200, JsonBody.of(Json.ofNull()));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    record PatchTodoRequest(
            Optional<String> title,
            Optional<Boolean> completed,
            Optional<Integer> order
    ) {
        static PatchTodoRequest fromJson(Json json) {
            return new PatchTodoRequest(
                    JsonDecoder.optionalField(json, "title", JsonDecoder::string),
                    JsonDecoder.optionalField(json, "completed", JsonDecoder::boolean_),
                    JsonDecoder.optionalField(json, "order", JsonDecoder::int_)
            );
        }
    }


    public void patchTodo(HttpExchange exchange) throws IOException {
        var routeParams = RouteParams.get(exchange);
        var id = Integer.parseInt(routeParams.param("id").orElseThrow());
        var json = Json.readString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        var body = PatchTodoRequest.fromJson(json);

        try (var conn = db.getConnection()) {
            conn.setAutoCommit(false);

            var title = body.title.orElse(null);
            if (title != null) {
                try (var stmt = conn.prepareStatement("""
                    UPDATE todo
                       SET title = ?
                    WHERE id = ?
                    """)) {
                    stmt.setString(1, title);
                    stmt.setInt(2, id);
                    stmt.execute();
                }
            }


            var completed = body.completed.orElse(null);
            if (completed != null) {
                try (var stmt = conn.prepareStatement("""
                    UPDATE todo
                       SET completed = ?
                    WHERE id = ?
                    """)) {
                    stmt.setBoolean(1, completed);
                    stmt.setInt(2, id);
                    stmt.execute();
                }
            }

            var order = body.order.orElse(null);
            if (order != null) {
                try (var stmt = conn.prepareStatement("""
                    UPDATE todo
                       SET "order" = ?
                    WHERE id = ?
                    """)) {
                    stmt.setInt(1, order);
                    stmt.setInt(2, id);
                    stmt.execute();
                }
            }

            conn.commit();

            try (var stmt = conn.prepareStatement("""
                     SELECT id, title, completed, "order"
                     FROM todo
                     WHERE id = ?
                     """)) {
                stmt.setInt(1, Integer.parseInt(routeParams.param("id").orElseThrow()));
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    HttpExchangeUtils.sendResponse(
                            exchange,
                            200,
                            JsonBody.of(
                                    Json.objectBuilder()
                                            .put("title", rs.getString("title"))
                                            .put("completed", rs.getBoolean("completed"))
                                            .put("url", new TodoUrl(exchange, rs.getInt("id")))
                                            .put("order", rs.getInt("order"))
                            )
                    );
                }
                else {
                    HttpExchangeUtils.sendResponse(
                            exchange,
                            200,
                            JsonBody.of(Json.ofNull())
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    record PostTodoRequest(String title, Optional<Integer> order) {
        static PostTodoRequest fromJson(Json json) {
            return new PostTodoRequest(
                    JsonDecoder.field(json, "title", JsonDecoder::string),
                    JsonDecoder.optionalField(json, "order", JsonDecoder::int_)
            );
        }
    }

    public void postTodo(HttpExchange exchange) throws IOException {
        var json = Json.readString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        var todo = PostTodoRequest.fromJson(json);

        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO todo(title, "order")
                     VALUES (?, ?)
                     RETURNING id, title, completed, "order"
                     """)) {
            stmt.setString(1, todo.title);
            stmt.setInt(2, todo.order.orElse(0));
            var rs = stmt.executeQuery();
            rs.next();
            HttpExchangeUtils.sendResponse(exchange, 200, JsonBody.of(
                    Json.objectBuilder()
                            .put("title", rs.getString("title"))
                            .put("completed", rs.getBoolean("completed"))
                            .put("url", new TodoUrl(exchange, rs.getInt("id")))
                            .put("order", rs.getInt("order"))
            ));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void deleteAllTodos(HttpExchange exchange) throws IOException {
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM todo
                     """)) {
            stmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        HttpExchangeUtils.sendResponse(exchange,200, JsonBody.of(Json.of(List.of())));
    }

    public void deleteTodo(HttpExchange exchange) throws IOException {
        var routeParams = RouteParams.get(exchange);
        var id = Integer.parseInt(routeParams.param("id").orElseThrow());
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM todo
                     WHERE id = ?
                     """)) {
            stmt.setInt(1, id);
            stmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        HttpExchangeUtils.sendResponse(exchange, 200, JsonBody.of(Json.ofNull()));
    }
}
