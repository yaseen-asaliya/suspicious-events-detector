package com.freightos.suseventsdetector.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class HealthCheckService {
    @Autowired
    private DataSource ds;
    public boolean isReady() {
            try(Connection conn = ds.getConnection()){
                Statement stmt = conn.createStatement();
                stmt.execute("select email, ip, domain, uri, message from event");
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
    }
}
