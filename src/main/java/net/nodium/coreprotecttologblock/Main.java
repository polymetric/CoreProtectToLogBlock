package net.nodium.coreprotecttologblock;

import net.nodium.coreprotecttologblock.coreprotectdb.CPTableBlocks;
import net.nodium.coreprotecttologblock.logblockdb.LBTableBlocks;

import java.sql.*;
import java.util.Scanner;

public class Main {
    public static final int CP_ACTION_DESTROY = 0;
    public static final int CP_ACTION_PLACE = 1;

    public static int ids = 0;

    public static void main(String[] args) throws Exception {
        // default args
//        String cpDb = null;
        String cpDb = "database_painting.db";
//        String cpDb = "database.db";
        String lbIp = null;
        int lbPort = 3306;
        String lbPassword = null;
        String lbDb = null;
//        String world = null;
        String world = "recreation";
//        String world = "world";

        // parse args
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
            if (arg.equals("--world")) {
                world = args[i + 1];
            }
        }

        // exit if not enough args
//        if (cpDb == null || lbIp == null || lbPassword == null || lbDb == null || lbTable == null) {
//            System.out.println("not enough arguments");
//            System.exit(1);
//        }

        System.out.printf("WARNING:\n" +
                        "This operation will COMPLETELY DESTROY the following SQL tables in the specified server:\n" +
                        "\tminecraft.lb-materials\n" +
                        "\tminecraft.lb-%s-blocks\n" +
                        "Type \"yes\" to continue: ",
                world
        );

        Scanner scanner = new Scanner(System.in);
        String response = scanner.nextLine();

        if (!(response.equalsIgnoreCase("yes"))) {
            System.err.println("Exiting");
            System.exit(1);
        }

        // create coreprotect db connection
        Connection connCoreProtect = DriverManager.getConnection("jdbc:sqlite:" + cpDb);
        System.out.println("opened SQLite db");

        // create logblock db connection
        Connection connLogBlock = DriverManager.getConnection("jdbc:mysql://localhost:3306/minecraft?user=root");
        System.out.println("connected to MySQL db");

        // declare and initialize coreprotect sql variables
        String queryCp;
        Statement stmtCp;
        ResultSet rsCp;

        // get the coreprotect world id
        queryCp = "SELECT * FROM main.co_world";
        stmtCp = connCoreProtect.createStatement();
        rsCp = stmtCp.executeQuery(queryCp);

        int worldId = -1;

        while (rsCp.next()) {
            if (rsCp.getString("world").equals(world)) {
                worldId = rsCp.getInt("id");
                System.out.printf("coreprotect world id is: %d\n", worldId);
                break;
            }
        }
        if (worldId == -1) {
            System.err.println("Couldn't find air material in CoreProtect database.");
            System.exit(1);
        }

        // get the number of rows
        queryCp = String.format("SELECT count(*) FROM main.co_block WHERE wid = %d", worldId);
        stmtCp = connCoreProtect.createStatement();
        rsCp = stmtCp.executeQuery(queryCp);

        int cpRows = rsCp.getInt(1);
        int currentRow = 1;

        System.out.printf("CoreProtect table has %d rows\n", cpRows);

        // set the logblock sql db to minecraft
        String queryLb = "USE minecraft;";
        Statement stmtLb = connLogBlock.createStatement();
        stmtLb.execute(queryLb);

        // drop the table if it exists
        queryLb = String.format("DROP TABLE IF EXISTS `lb-%s-blocks`;", world);
        stmtLb = connLogBlock.createStatement();
        stmtLb.execute(queryLb);

        // create the blocks table
        queryLb = String.format("CREATE TABLE IF NOT EXISTS `lb-%s-blocks` %s", world, "(id INT UNSIGNED NOT NULL AUTO_INCREMENT, date DATETIME NOT NULL, playerid INT UNSIGNED NOT NULL, replaced SMALLINT UNSIGNED NOT NULL, replacedData SMALLINT NOT NULL, type SMALLINT UNSIGNED NOT NULL, typeData SMALLINT NOT NULL, x MEDIUMINT NOT NULL, y SMALLINT UNSIGNED NOT NULL, z MEDIUMINT NOT NULL, PRIMARY KEY (id), KEY coords (x, z, y), KEY date (date), KEY playerid (playerid))");
        stmtLb = connLogBlock.createStatement();
        stmtLb.execute(queryLb);

        // copy the materials table
        queryCp = "SELECT * FROM main.co_material_map";
        stmtCp = connCoreProtect.createStatement();
        rsCp = stmtCp.executeQuery(queryCp);

        // drop the table if it exists
        queryLb = "DROP TABLE IF EXISTS `lb-materials`;";
        stmtLb = connLogBlock.createStatement();
        stmtLb.execute(queryLb);

        // create the blocks table
        queryLb = "CREATE TABLE IF NOT EXISTS `lb-materials` (id INT UNSIGNED NOT NULL, name VARCHAR(255) NOT NULL, PRIMARY KEY (id)) DEFAULT CHARSET utf8;";
        stmtLb = connLogBlock.createStatement();
        stmtLb.execute(queryLb);

        int airId = -1;

        while (rsCp.next()) {
//            System.out.println(rsCp.getInt("id"));
//            System.out.println(rsCp.getString("material"));
            queryLb = String.format("INSERT IGNORE INTO `lb-materials` (id, name) VALUES (%d, '%s');", rsCp.getInt("id"), rsCp.getString("material"));
            stmtLb = connLogBlock.createStatement();
            stmtLb.execute(queryLb);

            // get the air block ID
            // we have to do this because air isn't just 0 anymore, it's at an arbitrary position in the materials table
            // also coreprotect has different actions for "placed" and "destroyed" but logblock does it differently,
            // instead when you place a block it says you replaced "air" with "<block>" and vice versa if you destroyed it
            if (rsCp.getString("material").equals("minecraft:air")) {
                airId = rsCp.getInt("id");
                System.out.printf("air material index is: %d\n", airId);
            }
        }
        if (airId == -1) {
            System.err.println("Couldn't find air material in CoreProtect database.");
            System.exit(1);
        }
        System.out.println("copied materials table");

        // mark the time that the process was started to see how long it takes
        long timeStart = System.nanoTime();

        // batch prepared statements
        PreparedStatement psCp = connCoreProtect.prepareStatement("SELECT * FROM main.co_block LIMIT ? OFFSET ?");
        PreparedStatement psLb = connLogBlock.prepareStatement(String.format("INSERT INTO `lb-%s-blocks` (date, playerid, replaced, replaceddata, type, typedata, x, y, z) VALUES (FROM_UNIXTIME(?), ?, ?, ?, ?, ?, ?, ?, ?)", world));

        // batch block size parameters
        final int batchReadSize = 1000;
        final int batchWriteSize = 10000;
        final int progressInterval = 10000;

        boolean writtenBatch = false;

        // actual read/write loop
        // TODO may miss the last read batch
        for (int i = 0; i < (cpRows / batchReadSize) + 1; i++) {
            writtenBatch = false;
            psCp.clearParameters();

            psCp.setInt(1, batchReadSize);
            psCp.setInt(2, i * batchReadSize);

            rsCp = psCp.executeQuery();

            while (rsCp.next()) {
                if (rsCp.getInt("wid") != worldId) {
                    continue;
                }

                CPTableBlocks cpTableBlocks = new CPTableBlocks();
                cpTableBlocks.time = rsCp.getInt("time");
                cpTableBlocks.user = rsCp.getInt("user");
                cpTableBlocks.wid = rsCp.getInt("wid");
                cpTableBlocks.x = rsCp.getInt("x");
                cpTableBlocks.y = rsCp.getInt("y");
                cpTableBlocks.z = rsCp.getInt("z");
                cpTableBlocks.type = rsCp.getInt("type");
                cpTableBlocks.action = rsCp.getInt("action");

                LBTableBlocks lbTableBlocks = cpToLb(cpTableBlocks, airId);


                psLb.clearParameters();

                psLb.setInt(1, cpTableBlocks.time);
                psLb.setInt(2, 1);
                psLb.setInt(3, lbTableBlocks.replaced);
                psLb.setInt(4, -1);
                psLb.setInt(5, lbTableBlocks.type);
                psLb.setInt(6, -1);
                psLb.setInt(7, lbTableBlocks.x);
                psLb.setInt(8, clamp(lbTableBlocks.y, 0, 65535));
                psLb.setInt(9, lbTableBlocks.z);

                psLb.addBatch();


                if (currentRow % batchWriteSize == 0) {
                    psLb.executeBatch();
                    writtenBatch = true;
                }
                if (currentRow % progressInterval == 1) {
                    printProgress(cpRows, currentRow, timeStart);
                }
                currentRow++;
            }
        }
        if (!writtenBatch) {
            psLb.executeBatch();
        }

        printProgress(cpRows, currentRow, timeStart);
        System.exit(0);
    }

    public static LBTableBlocks cpToLb(CPTableBlocks cpTableBlocks, int airId) {
        LBTableBlocks lbTableBlocks = new LBTableBlocks();

        lbTableBlocks.id = ids++;
        lbTableBlocks.date = new Timestamp(cpTableBlocks.time * 1000).toString();
        lbTableBlocks.playerId = 1; // null player for now
        if (cpTableBlocks.action == CP_ACTION_PLACE) {
            lbTableBlocks.replaced = airId;
            lbTableBlocks.type = cpTableBlocks.type;
        } else if (cpTableBlocks.action == CP_ACTION_DESTROY) {
            lbTableBlocks.replaced = cpTableBlocks.type;
            lbTableBlocks.type = airId;
        }
        lbTableBlocks.x = cpTableBlocks.x;
        lbTableBlocks.y = cpTableBlocks.y;
        lbTableBlocks.z = cpTableBlocks.z;

        return lbTableBlocks;
    }

    public static int clamp(int i, int lower, int upper) {
        return Math.max(lower, Math.min(upper, i));
    }

    public static void printProgress(int cpRows, int currentRow, long timeStart) {
        // subtract 1 from current row because SQL is 1 indexed?!?
        currentRow--;
        System.out.printf("%.3f percent done, (%d/%d rows) running for %.0fs\n", (currentRow / ((double) cpRows)) * 100d, currentRow, cpRows, (System.nanoTime() - timeStart) / 1e9D);
    }
}
