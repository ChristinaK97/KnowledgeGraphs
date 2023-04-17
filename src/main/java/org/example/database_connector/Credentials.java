package org.example.database_connector;

public class Credentials {
    private String url = "jdbc:mysql://localhost:3306/test_efs";
    private String user = "root";
    private String password = "admin";

    public String getUrl() {
        return url;
    }
    public String getUser() {
        return user;
    }
    public String getPassword() {
        return password;
    }
}
