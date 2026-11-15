package cn.huancai.erp.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 全局订单号生成：办理中心订单与教材订单共用同一套纯数字序列。 */
@Component
public class OrderNoGenerator {

    private final JdbcTemplate jdbcTemplate;

    public OrderNoGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 接续已有纯数字最大值；历史订单为 QB-E-xxx 时从 33661 起。 */
    public synchronized String next() {
        Long max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(n), 0) FROM ("
                        + "SELECT CAST(order_no AS UNSIGNED) AS n FROM sales_order WHERE order_no REGEXP '^[0-9]+$' "
                        + "UNION ALL "
                        + "SELECT CAST(ext_order_no AS UNSIGNED) AS n FROM material_buy_record "
                        + "WHERE ext_order_no REGEXP '^[0-9]+$'"
                        + ") t", Long.class);
        long next = (max == null || max < 33660L ? 33660L : max) + 1;
        return String.valueOf(next);
    }
}
