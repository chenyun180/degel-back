import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 秒杀 100 并发压测：
 * 1) prep: 造 100 个测试用户 + 每人 1 条收货地址（幂等，已存在跳过）
 * 2) login: 并发登录拿 C 端 JWT
 * 3) burst: CountDownLatch 对齐，100 线程同时 POST /app/seckill/reserve
 * 4) order: reserve 成功者立即凭 token 下单（真实消耗库存）
 * 5) 汇总：各错误码计数 / 耗时统计 / 成单数
 */
public class SeckillLoad {

    static final String GW = "http://localhost:9999";
    static final String DB_URL = "jdbc:mysql://192.168.1.14:3306/degel_app?useSSL=false&serverTimezone=Asia/Shanghai";
    static final String SESSION_ID = "2099677023943475201";
    static final String SKU_ID = "84";
    static final int N = 100;
    static final String PHONE_BASE = "15500000101"; // 101..200
    static final String PWD_HASH = "$2a$10$8vtKsbWIBaTyCC8jFtoaU.Xnvym/cR95qtA9LDFzHVkbCljllUXRC"; // = admin123

    static final ConcurrentHashMap<String, Long> PHONE_UID = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, Long> PHONE_ADDR = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, String> PHONE_TOKEN = new ConcurrentHashMap<>();

    // 结果统计：code -> count
    static final ConcurrentHashMap<Integer, AtomicInteger> RESERVE_CODES = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer, AtomicInteger> ORDER_CODES = new ConcurrentHashMap<>();
    static final List<Long> reserveLatency = Collections.synchronizedList(new ArrayList<>());
    static final List<String> winners = Collections.synchronizedList(new ArrayList<>());
    static final ConcurrentHashMap<String, String> WINNER_TOKENS = new ConcurrentHashMap<>(); // phone -> reserve token

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        prep();
        login();
        burst();
        orders();
        report();
    }

    // ---------- 1. 造数 ----------
    static void prep() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "root", "123456")) {
            for (int i = 0; i < N; i++) {
                String phone = String.valueOf(Long.parseLong(PHONE_BASE) + i);
                Long uid = null;
                try (PreparedStatement q = c.prepareStatement("SELECT id FROM mall_user WHERE phone=?")) {
                    q.setString(1, phone);
                    ResultSet r = q.executeQuery();
                    if (r.next()) uid = r.getLong(1);
                }
                if (uid == null) {
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO mall_user(openid,nickname,phone,password,status,del_flag) VALUES (?,?,?,?,0,0)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        p.setString(1, "sk-load-" + phone);
                        p.setString(2, "秒杀压测" + (i + 1));
                        p.setString(3, phone);
                        p.setString(4, PWD_HASH);
                        p.executeUpdate();
                        ResultSet g = p.getGeneratedKeys();
                        g.next();
                        uid = g.getLong(1);
                    }
                }
                PHONE_UID.put(phone, uid);
                // 地址
                Long aid = null;
                try (PreparedStatement q = c.prepareStatement("SELECT id FROM mall_address WHERE user_id=? AND del_flag=0 LIMIT 1")) {
                    q.setLong(1, uid);
                    ResultSet r = q.executeQuery();
                    if (r.next()) aid = r.getLong(1);
                }
                if (aid == null) {
                    aid = (System.currentTimeMillis() << 12) + i; // 伪雪花，保证唯一
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO mall_address(id,user_id,name,phone,province,city,district,detail,is_default,del_flag) VALUES (?,?,?,?,?,?,?,?,1,0)")) {
                        p.setLong(1, aid);
                        p.setLong(2, uid);
                        p.setString(3, "压测收货人" + (i + 1));
                        p.setString(4, phone);
                        p.setString(5, "浙江省");
                        p.setString(6, "丽水市");
                        p.setString(7, "龙泉市");
                        p.setString(8, "锦溪镇压测" + (i + 1) + "号");
                        p.executeUpdate();
                    }
                }
                PHONE_ADDR.put(phone, aid);
            }
        }
        System.out.println("[prep] " + N + " users ready (phones " + PHONE_BASE + "~" + (Long.parseLong(PHONE_BASE) + N - 1) + ")");
    }

    // ---------- 2. 登录（服务端有 IP 限流，40027 时退避重试） ----------
    static void login() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch done = new CountDownLatch(N);
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) {
            final String phone = String.valueOf(Long.parseLong(PHONE_BASE) + i);
            pool.execute(() -> {
                try {
                    for (int retry = 0; retry < 20; retry++) {
                        String body = post(GW + "/app/auth/login",
                                "{\"phone\":\"" + phone + "\",\"password\":\"admin123\"}", null);
                        int code = codeOf(body);
                        if (code == 200) {
                            String token = extract(body, "\"token\":\"");
                            if (token != null) PHONE_TOKEN.put(phone, token);
                            break;
                        }
                        if (code != 40027) { // 40027=登录限流，退避后重试；其他错误直接失败
                            System.out.println("[login fail] " + phone + " code=" + code);
                            break;
                        }
                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    System.out.println("[login fail] " + phone + " " + e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();
        System.out.println("[login] " + PHONE_TOKEN.size() + "/" + N + " ok in " + (System.currentTimeMillis() - t0) + "ms");
        if (PHONE_TOKEN.size() < N) throw new IllegalStateException("有用户登录失败，中止");
    }

    // ---------- 3. 100 并发 reserve ----------
    static void burst() throws Exception {
        CountDownLatch ready = new CountDownLatch(N);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        for (int i = 0; i < N; i++) {
            final String phone = String.valueOf(Long.parseLong(PHONE_BASE) + i);
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                    long t0 = System.currentTimeMillis();
                    String body = post(GW + "/app/seckill/reserve",
                            "{\"sessionId\":\"" + SESSION_ID + "\",\"skuId\":\"" + SKU_ID + "\"}",
                            PHONE_TOKEN.get(phone));
                    long cost = System.currentTimeMillis() - t0;
                    reserveLatency.add(cost);
                    int code = codeOf(body);
                    RESERVE_CODES.computeIfAbsent(code, k -> new AtomicInteger()).incrementAndGet();
                    if (code != 200 && RESERVE_CODES.get(code).get() <= 3) {
                        System.out.println("[reserve body] " + phone + " -> " + body);
                    }
                    if (code == 200) {
                        String token = extract(body, "\"token\":\"");
                        WINNER_TOKENS.put(phone, token);
                        winners.add(phone + "(" + cost + "ms)");
                    }
                } catch (Exception e) {
                    RESERVE_CODES.computeIfAbsent(-1, k -> new AtomicInteger()).incrementAndGet();
                    System.out.println("[reserve exc] " + phone + " " + e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();          // 100 线程全部就位
        Thread.sleep(200);
        long t0 = System.currentTimeMillis();
        go.countDown();         // 鸣枪
        done.await();
        pool.shutdown();
        long wall = System.currentTimeMillis() - t0;

        List<Long> lat = new ArrayList<>(reserveLatency);
        Collections.sort(lat);
        System.out.println("[reserve] wall=" + wall + "ms  p50=" + lat.get(lat.size() / 2) + "ms  max=" + lat.get(lat.size() - 1) + "ms");
        System.out.println("[reserve] codes=" + RESERVE_CODES);
        System.out.println("[reserve] winners(" + winners.size() + "): " + winners);
    }

    // ---------- 4. 成功者下单（真实消耗库存） ----------
    static void orders() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, winners.size()));
        CountDownLatch done = new CountDownLatch(winners.size());
        long t0 = System.currentTimeMillis();
        for (String phone : WINNER_TOKENS.keySet()) {
            final String p = phone;
            pool.execute(() -> {
                try {
                    String body = post(GW + "/app/seckill/order",
                            "{\"token\":\"" + WINNER_TOKENS.get(p) + "\",\"addressId\":\"" + PHONE_ADDR.get(p) + "\"}",
                            PHONE_TOKEN.get(p));
                    ORDER_CODES.computeIfAbsent(codeOf(body), k -> new AtomicInteger()).incrementAndGet();
                } catch (Exception e) {
                    ORDER_CODES.computeIfAbsent(-1, k -> new AtomicInteger()).incrementAndGet();
                    System.out.println("[order exc] " + p + " " + e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();
        System.out.println("[order] wall=" + (System.currentTimeMillis() - t0) + "ms  codes=" + ORDER_CODES);
    }

    // ---------- 5. 报告 ----------
    static void report() throws Exception {
        System.out.println("\n===== 汇总 =====");
        int reserveOk = RESERVE_CODES.getOrDefault(200, new AtomicInteger()).get();
        int soldOut = RESERVE_CODES.getOrDefault(40022, new AtomicInteger()).get();
        int limit = RESERVE_CODES.getOrDefault(40023, new AtomicInteger()).get();
        int orderOk = ORDER_CODES.getOrDefault(200, new AtomicInteger()).get();
        System.out.println("reserve 200=" + reserveOk + " 40022已抢光=" + soldOut + " 40023限购=" + limit
                + " 其他=" + (N - reserveOk - soldOut - limit));
        System.out.println("order 200(成单)=" + orderOk);
        // 实时余量
        String s = get(GW + "/app/seckill/sessions");
        String remaining = extract(s, "\"remaining\":");
        System.out.println("after-burst remaining=" + (remaining == null ? "?" : remaining.replaceAll("[},].*", "")));
    }

    // ---------- HTTP 工具 ----------
    static String post(String url, String json, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return read(conn);
    }

    static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(10000);
        return read(conn);
    }

    static String read(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        Scanner sc = new Scanner(code < 400 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8").useDelimiter("\\A");
        String body = sc.hasNext() ? sc.next() : "";
        conn.disconnect();
        return body;
    }

    static int codeOf(String body) {
        int i = body == null ? -1 : body.indexOf("\"code\":");
        if (i < 0) return -1;
        String rest = body.substring(i + 7).replaceAll("[^0-9].*", "");
        try {
            return Integer.parseInt(rest);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 取 "key":"value" 的 value（截到下一个引号） */
    static String extract(String body, String key) {
        int i = body == null ? -1 : body.indexOf(key);
        if (i < 0) return null;
        String rest = body.substring(i + key.length());
        int end = rest.indexOf('"');
        return end >= 0 ? rest.substring(0, end) : rest;
    }
}
