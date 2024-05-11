package dev.mccue.todoapp;

import com.sun.net.httpserver.HttpServer;
import dev.mccue.jdk.httpserver.HttpExchangeUtils;
import dev.mccue.jdk.httpserver.json.JsonBody;
import dev.mccue.jdk.httpserver.regexrouter.RegexRouter;
import dev.mccue.json.Json;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class Main {
    static DataSource db() throws Exception {
        var db = new SQLiteDataSource();
        db.setUrl("jdbc:sqlite:todos.db");

        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS todo(
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        completed BOOLEAN NOT NULL DEFAULT false,
                        "order" INTEGER NOT NULL DEFAULT 0
                     )
                     """)) {
            stmt.execute();
        }

        return db;
    }

    public static void main(String[] args) throws Exception {
        var db = db();

        var router = RegexRouter.builder();

        router.notFoundHandler(exchange -> {
            HttpExchangeUtils.sendResponse(exchange, 404, JsonBody.of(Json.of("Not Found")));
        });
        router.errorHandler((t, exchange) -> {
            t.printStackTrace(System.err);
            HttpExchangeUtils.sendResponse(exchange, 500, JsonBody.of(Json.of("Internal Server Error")));
        });

        new TodoController(db).register(router);

        int port = 7777;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException ignored) {
        }

        var server = HttpServer.create(
                new InetSocketAddress(port),
                0,
                "/",
                router.build(),
                new CorsFilter()
        );

        // server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }
}
