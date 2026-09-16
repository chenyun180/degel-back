import java.math.BigDecimal;
import java.sql.*;
import java.util.Calendar;

/**
 * e2e 回归测试造数脚本（平台看板 + 店铺工作台 + 商品审核页）。
 *
 * 用法（JDK8，degel-back 目录）：
 *   javac -d /tmp scripts/E2eSeedData.java
 *   java -cp "/tmp:$HOME/.m2/repository/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar" E2eSeedData
 *
 * 造数内容（与 docs/e2e回归测试方案.md 对应，可重复执行——先清后插）：
 * - 16 单 remark='平台看板测试数据' 订单（shop 4 十单 + shop 5 六单，状态 0-5 全覆盖，
 *   近 28 天分布；shop 4 含 4 单待发货供 S4/S5 发货操作）
 * - 每单 1 条 order_item 快照；金额最大的一单挂「牛仔短裤」（看板畅销 TOP5 断言）
 * - 2 条 product_spu 待审核商品（P2 断言）
 *
 * ⚠️ 累计总流水基准配平：e2e 断言平台看板"累计总流水"= 3,391.50（固定值）。
 * 本脚本先清旧种子、再读库中【其它】已支付流水 others，种子总额 = 3391.50 - others，
 * 按权重摊到各单（其中一单吸收配平尾差）——无论历史数据怎么漂，总值精确命中基准。
 */
public class E2eSeedData {

    /** 平台看板 e2e 断言的累计总流水基准（platform-dashboard.spec.ts 同步改） */
    private static final long BASELINE_TOTAL_CENTS = 339150L;
    private static final String SEED_REMARK = "平台看板测试数据";
    private static final String DB = "jdbc:mysql://192.168.1.14:3306/degel_order?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String DB_P = "jdbc:mysql://192.168.1.14:3306/degel_product?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASS = "123456";

    public static void main(String[] args) throws Exception {
        long userId = 1L;
        try (Connection oc = DriverManager.getConnection(DB, USER, PASS);
             Connection pc = DriverManager.getConnection(DB_P, USER, PASS)) {
            cleanOldSeeds(oc, pc);

            // 库中其它已支付流水（已支付口径 status IN (1,2,3,5)，同看板）
            long others;
            try (Statement s = oc.createStatement();
                 ResultSet r = s.executeQuery(
                    "SELECT IFNULL(SUM(pay_amount),0) FROM order_info WHERE del_flag=0 AND status IN (1,2,3,5)")) {
                r.next();
                others = r.getBigDecimal(1).movePointRight(2).longValue();
            }
            long budget = BASELINE_TOTAL_CENTS - others;
            System.out.println("其它已支付流水 = " + cents(others) + "，种子预算 = " + cents(budget));

            // shop5 固定 6 单共 600.00；shop4 摊剩余（7 单有权重 + 1 单配平 + 2 单不计流水）
            long shop5Fixed = 60000L;
            if (budget < shop5Fixed + 60000L) {
                throw new IllegalStateException("预算不足（" + cents(budget) + "）：库中其它流水太多，"
                    + "请先清理历史测试订单或同步调大 BASELINE_TOTAL_CENTS（e2e 断言需同步改）");
            }
            long shop4Budget = budget - shop5Fixed;

            // shop4 已支付 7 单权重（配平单另算）
            double[] w = {0.18, 0.10, 0.07, 0.14, 0.12, 0.09, 0.05};
            long[] shop4Amt = new long[7];
            long allocated = 0;
            for (int i = 0; i < 7; i++) {
                shop4Amt[i] = Math.round(shop4Budget * w[i] / 100.0) * 100L; // 取整到元，尾差归配平单
                allocated += shop4Amt[i];
            }
            long balancer = shop4Budget - allocated;

            Timestamp now = new Timestamp(System.currentTimeMillis());

            // ---- shop 4：10 单（O1-O3/O10 待发货1，O4 已发货2，O5/O6 已完成3，O7 售后中5，O8 待付款0，O9 已取消4）----
            // 明细统一「牛仔短裤」（spu_id=0 聚合后 ~shop4 总额，稳居畅销 TOP5——e2e 断言）
            // [status, daysAgo, amountCents, 商品快照名]
            Object[][] shop4Orders = {
                {1, 2, shop4Amt[0], "牛仔短裤"},
                {1, 3, shop4Amt[1], "牛仔短裤"},
                {1, 5, shop4Amt[2], "牛仔短裤"},
                {2, 8, shop4Amt[3], "牛仔短裤"},
                {3, 12, shop4Amt[4], "牛仔短裤"},
                {3, 20, shop4Amt[5], "牛仔短裤"},
                {5, 10, shop4Amt[6], "牛仔短裤"},
                {0, null, 9900L, "牛仔短裤"},    // 待付款：不计流水
                {4, null, 8800L, "牛仔短裤"},    // 已取消：不计流水
                {1, 1, balancer, "牛仔短裤"},     // 配平单（吸收尾差）
            };
            for (int i = 0; i < shop4Orders.length; i++) {
                insertOrder(oc, userId, 4L, "TEST2026-S4" + String.format("%02d", i + 1), shop4Orders[i], now);
            }

            // ---- shop 5：6 单固定金额共 600.00 ----
            Object[][] shop5Orders = {
                {2, 6, 20000L, "测试商品A"},
                {3, 15, 15000L, "测试商品B"},
                {5, 9, 10000L, "测试商品A"},
                {1, 4, 5000L, "测试商品B"},
                {2, 25, 4000L, "测试商品A"},
                {3, 28, 6000L, "测试商品B"},
            };
            for (int i = 0; i < shop5Orders.length; i++) {
                insertOrder(oc, userId, 5L, "TEST2026-S5" + String.format("%02d", i + 1), shop5Orders[i], now);
            }

            // ---- 2 条待审核商品（P2 断言：shop 4 / shop 5 各一条，命名 = 测试待审商品-{shopId}）----
            insertPendingSpu(pc, 4L, "测试待审商品-4");
            insertPendingSpu(pc, 5L, "测试待审商品-5");

            // ---- 对账 ----
            try (Statement s = oc.createStatement();
                 ResultSet r = s.executeQuery(
                    "SELECT IFNULL(SUM(pay_amount),0), COUNT(*) FROM order_info "
                    + "WHERE del_flag=0 AND status IN (1,2,3,5)")) {
                r.next();
                System.out.println("✔ 累计总流水（已支付口径）= " + r.getBigDecimal(1)
                    + "（基准 " + BigDecimal.valueOf(BASELINE_TOTAL_CENTS, 2) + "）");
                System.out.println("✔ 已支付订单总数 = " + r.getInt(2));
            }
            System.out.println("✔ shop4 待发货（TEST）= " + countBy(oc, 4L, 1)
                + " 单；shop5 全部种子 = " + countByRemark(oc, 5L) + " 单");
            System.out.println("✔ 待审商品 2 条已插入");
        }
    }

    private static void insertOrder(Connection c, long userId, long shopId, String orderNo, Object[] spec, Timestamp now) throws SQLException {
        int status = (Integer) spec[0];
        Integer daysAgo = (Integer) spec[1];
        long amount = (Long) spec[2];
        String item = (String) spec[3];

        Timestamp payTime = null, shipTime = null, receiveTime = null, cancelTime = null, createTime;
        if (daysAgo != null) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -daysAgo);
            cal.set(Calendar.HOUR_OF_DAY, 10 + (daysAgo % 8));
            cal.set(Calendar.MINUTE, 0);
            createTime = new Timestamp(cal.getTimeInMillis());
            payTime = createTime; // 已支付口径必须有 pay_time（看板按 pay_time 统计）
            if (status >= 2) shipTime = now;
            if (status >= 3) receiveTime = now;
        } else {
            createTime = now;
            if (status == 4) cancelTime = now;
        }

        PreparedStatement p = c.prepareStatement(
            "INSERT INTO order_info (order_no, user_id, shop_id, total_amount, freight_amount, discount_amount, "
            + "pay_amount, status, pay_time, ship_time, receive_time, cancel_time, receiver_name, receiver_phone, "
            + "receiver_address, remark, express_company, express_no, create_time, del_flag) "
            + "VALUES (?,?,?,?,0,0,?,?,?,?,?,?, '测试收货人','13800000000','测试地址',?, '','',?,0)",
            Statement.RETURN_GENERATED_KEYS);
        p.setString(1, orderNo);
        p.setLong(2, userId);
        p.setLong(3, shopId);
        p.setBigDecimal(4, cents(amount)); // total_amount
        p.setBigDecimal(5, cents(amount)); // pay_amount
        p.setInt(6, status);
        p.setTimestamp(7, payTime);
        p.setTimestamp(8, shipTime);
        p.setTimestamp(9, receiveTime);
        p.setTimestamp(10, cancelTime);
        p.setString(11, SEED_REMARK);
        p.setTimestamp(12, createTime);
        p.executeUpdate();
        long orderId;
        try (ResultSet g = p.getGeneratedKeys()) { g.next(); orderId = g.getLong(1); }

        PreparedStatement pi = c.prepareStatement(
            "INSERT INTO order_item (order_id, spu_id, sku_id, spu_name, sku_spec, price, quantity, total_amount, create_time, del_flag) "
            + "VALUES (?,?,0,?,'默认',?,2,?,?,0)");
        pi.setLong(1, orderId);
        pi.setLong(2, 0L); // 快照字段无外键；spu_id=0 表测试造数
        pi.setString(3, item);
        pi.setBigDecimal(4, cents(amount / 2)); // 单价（数量固定 2）
        pi.setBigDecimal(5, cents(amount));     // 明细总额
        pi.setTimestamp(6, createTime);
        pi.executeUpdate();
    }

    private static void insertPendingSpu(Connection c, long shopId, String name) throws SQLException {
        PreparedStatement p = c.prepareStatement(
            "INSERT INTO product_spu (shop_id, category_id, name, audit_status, status, del_flag, create_time) "
            + "VALUES (?,?,?,1,0,0,NOW())");
        p.setLong(1, shopId);
        p.setLong(2, 1L); // 类目表有 34 条初始数据，1 必存在
        p.setString(3, name);
        p.executeUpdate();
    }

    private static void cleanOldSeeds(Connection oc, Connection pc) throws SQLException {
        try (Statement s = oc.createStatement()) {
            int items = s.executeUpdate(
                "DELETE oi FROM order_item oi JOIN order_info o ON oi.order_id=o.id WHERE o.remark='" + SEED_REMARK + "'");
            int orders = s.executeUpdate("DELETE FROM order_info WHERE remark='" + SEED_REMARK + "'");
            System.out.println("清理旧种子：订单 " + orders + " 单 / 明细 " + items + " 条");
        }
        try (Statement s = pc.createStatement()) {
            int spus = s.executeUpdate("DELETE FROM product_spu WHERE name LIKE '测试待审商品-%'");
            System.out.println("清理旧待审商品 " + spus + " 条");
        }
    }

    private static int countBy(Connection c, long shopId, int status) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM order_info WHERE del_flag=0 AND shop_id=? AND status=? AND remark='" + SEED_REMARK + "'")) {
            p.setLong(1, shopId); p.setInt(2, status);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    private static int countByRemark(Connection c, long shopId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM order_info WHERE del_flag=0 AND shop_id=? AND remark='" + SEED_REMARK + "'")) {
            p.setLong(1, shopId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    private static BigDecimal cents(long c) { return BigDecimal.valueOf(c, 2); }
}
