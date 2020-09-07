package net.nodium.coreprotecttologblock;

import net.nodium.coreprotecttologblock.coreprotectdb.CPTableBlocks;
import net.nodium.coreprotecttologblock.logblockdb.LBTableBlocks;

import java.io.*;
import java.sql.*;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    public static final int CP_ACTION_DESTROY = 0;
    public static final int CP_ACTION_PLACE = 1;

    public static int ids = 0;

    public static void main(String[] args) throws Exception {
//        String cpDb = null;
        String cpDb = "database_painting.db";
        String lbIp = null;
        int lbPort = 3306;
        String lbPassword = null;
        String lbDb = null;
//        String lbTable = null;
        String lbTable = "lb-recreation-blocks";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--cpdb")) {
                cpDb = args[i + 1];
            }
            if (arg.equals("--lbip")) {
                lbIp = args[i + 1];
            }
            if (arg.equals("--lbport")) {
                lbPort = Integer.parseInt(args[i + 1]);
            }
            if (arg.equals("--lbpassword")) {
                lbPassword = args[i + 1];
            }
            if (arg.equals("--lbdb")) {
                lbDb = args[i + 1];
            }
            if (arg.equals("--lbtable")) {
                lbTable = args[i + 1];
            }
        }

//        if (cpDb == null || lbIp == null || lbPassword == null || lbDb == null || lbTable == null) {
//            System.out.println("not enough arguments");
//            System.exit(1);
//        }

        Connection connCoreProtect = DriverManager.getConnection("jdbc:sqlite:" + cpDb);
        System.out.println("opened SQLite db");

        Connection connLogBlock = getConnection("mysql", "192.168.0.3", 23026, "logblock", "lasersteampantent%X1", "minecraft");
        System.out.println("connected to MySQL db");

        String queryCp;
        Statement stmtCp;
        ResultSet rsCp;

        queryCp = "SELECT count(*) FROM main.co_block";
//        queryCp = "SELECT * FROM sqlite_master;";
//        SqueryCp = "PRAGMA database_list;";
        stmtCp = connCoreProtect.createStatement();
        rsCp = stmtCp.executeQuery(queryCp);

        System.out.println("got number of rows on SQLite db");

        int cpRows = rsCp.getInt(1);
        int currentRow = 1;

        queryCp = "SELECT * FROM main.co_block;";
        stmtCp = connCoreProtect.createStatement();
        rsCp = stmtCp.executeQuery(queryCp);

        System.out.println("executed query on SQLite db");

        String queryLb = "USE minecraft;";
        Statement stmtLb = connLogBlock.createStatement();
        stmtLb.execute(queryLb);

        while (rsCp.next()) {
            CPTableBlocks cpTableBlocks = new CPTableBlocks();
            cpTableBlocks.time = Integer.parseInt(rsCp.getString("time"));
            cpTableBlocks.user = Integer.parseInt(rsCp.getString("user"));
            cpTableBlocks.wid = Integer.parseInt(rsCp.getString("wid"));
            cpTableBlocks.x = Integer.parseInt(rsCp.getString("x"));
            cpTableBlocks.y = Integer.parseInt(rsCp.getString("y"));
            cpTableBlocks.z = Integer.parseInt(rsCp.getString("z"));
            cpTableBlocks.type = Integer.parseInt(rsCp.getString("type"));
            cpTableBlocks.action = Integer.parseInt(rsCp.getString("action"));

            LBTableBlocks lbTableBlocks = cpToLb(cpTableBlocks);

            queryLb = String.format(
                    "INSERT INTO `%s` (date, playerid, replaced, replaceddata, type, typedata, x, y, z) VALUES (FROM_UNIXTIME(%d), %d, %d, %s, %d, %s, %d, %d, %d)",
                    lbTable,
                    cpTableBlocks.time,
                    0,
                    lbTableBlocks.replaced,
                    "0",
                    lbTableBlocks.type,
                    "0",
                    lbTableBlocks.x,
                    lbTableBlocks.y,
                    lbTableBlocks.z
            );
            stmtLb = connLogBlock.createStatement();
            stmtLb.execute(queryLb);
            if (currentRow % 10000 == 0) {
                System.out.printf("%.2f percent done\n", currentRow / ((double) cpRows));
            }
            currentRow++;
        }

        System.out.println("done");
        System.exit(0);
    }

    public static LBTableBlocks cpToLb(CPTableBlocks cpTableBlocks) {
        LBTableBlocks lbTableBlocks = new LBTableBlocks();

        lbTableBlocks.id = ids++;
        lbTableBlocks.date = new Timestamp(cpTableBlocks.time * 1000).toString();
        lbTableBlocks.playerId = 1; // null player for now
        if (cpTableBlocks.action == CP_ACTION_PLACE) {
            lbTableBlocks.replaced = 0;
            lbTableBlocks.type = cpTableBlocks.type;
        } else if (cpTableBlocks.action == CP_ACTION_DESTROY) {
            lbTableBlocks.replaced = cpTableBlocks.type;
            lbTableBlocks.type = 0;
        }
        lbTableBlocks.x = cpTableBlocks.x;
        lbTableBlocks.y = cpTableBlocks.y;
        lbTableBlocks.z = cpTableBlocks.z;

        return lbTableBlocks;
    }

    public static Connection getConnection(String dbms, String serverName, int portNumber, String userName, String password, String dbName) throws SQLException {
        Connection conn = null;
        Properties connectionProps = new Properties();
        connectionProps.put("user", userName);
        connectionProps.put("password", password);

        if (dbms.equals("mysql")) {
            conn = DriverManager.getConnection(
                    "jdbc:" + dbms + "://" +
                            serverName +
                            ":" + portNumber + "/",
                    connectionProps);
        } else if (dbms.equals("derby")) {
            conn = DriverManager.getConnection(
                    "jdbc:" + dbms + ":" +
                            dbName +
                            ";create=true",
                    connectionProps);
        }
        return conn;
    }
}
