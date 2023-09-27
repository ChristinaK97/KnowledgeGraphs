package org.example.A_InputPoint.SQLdb.SQLconnector;

public class Credentials {
    private String url = "jdbc:mysql://localhost:3306/epibank";
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
