module dev.mccue.todoapp {
    requires org.xerial.sqlitejdbc;
    requires org.slf4j.simple;
    requires jdk.httpserver;
    requires dev.mccue.jdk.httpserver.json;
    requires dev.mccue.jdk.httpserver;
    requires dev.mccue.jdk.httpserver.regexrouter;
    requires dev.mccue.json;

    exports dev.mccue.todoapp;
}