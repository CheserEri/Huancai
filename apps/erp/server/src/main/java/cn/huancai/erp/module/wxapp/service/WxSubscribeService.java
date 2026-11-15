package cn.huancai.erp.module.wxapp.service;

import cn.huancai.erp.module.wxapp.entity.WxParent;
import cn.huancai.erp.module.wxapp.mapper.WxParentMapper;
import cn.huancai.erp.module.wxapp.mapper.WxSubscribeQuotaMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订阅消息：家长在小程序里授权一次即获得一次推送机会（一次性订阅），
 * 后端按额度发送，发送失败会退还次数；开发态 openid（dev: 前缀）跳过发送。
 */
@Service
public class WxSubscribeService {

    private static final Logger LOG = LoggerFactory.getLogger(WxSubscribeService.class);
    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";
    private static final String SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token={token}";
    private static final String ACTIVITY_PAGE = "pages/activities/activities";
    private static final int MAX_THING = 20;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WxParentMapper parentMapper;
    private final WxSubscribeQuotaMapper quotaMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${wx.miniapp.appid:}")
    private String appid;
    @Value("${wx.miniapp.secret:}")
    private String secret;
    @Value("${wx.miniapp.subscribe.activity-template-id:}")
    private String activityTemplateId;

    private volatile String accessToken;
    private volatile long accessTokenExpireAt;

    public WxSubscribeService(WxParentMapper parentMapper,
                              WxSubscribeQuotaMapper quotaMapper,
                              ObjectMapper objectMapper) {
        this.parentMapper = parentMapper;
        this.quotaMapper = quotaMapper;
        this.objectMapper = objectMapper;
    }

    /** 家长在活动页授权成功后退还一次额度。 */
    public int grantActivity(Long parentId) {
        String templateId = templateId();
        if (parentId == null || templateId == null) {
            return 0;
        }
        quotaMapper.grant(parentId, templateId, 1);
        return remainActivity(parentId);
    }

    public int remainActivity(Long parentId) {
        String templateId = templateId();
        if (parentId == null || templateId == null) {
            return 0;
        }
        Integer remain = quotaMapper.remainCount(parentId, templateId);
        return remain == null ? 0 : remain;
    }

    /**
     * 活动海报推送后给家长发订阅消息，没有额度或发送失败只跳过，不影响海报推送本身。
     *
     * @param place 活动地点/范围文案，为空时使用校区通用文案
     */
    public void sendActivityPoster(Collection<Long> parentIds, String activityName, String place) {
        String templateId = templateId();
        if (parentIds == null || parentIds.isEmpty() || templateId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", value(cut(activityName, MAX_THING)));
        data.put("time2", value(LocalDateTime.now().format(TIME_FORMAT)));
        data.put("thing3", value(cut(place == null || place.isBlank() ? "幻彩学堂各校区" : place, MAX_THING)));
        data.put("thing4", value("点击查看活动海报"));
        for (Long parentId : parentIds) {
            try {
                send(parentId, templateId, data);
            } catch (Exception ex) {
                LOG.warn("活动海报订阅消息发送异常：parentId={}", parentId, ex);
            }
        }
    }

    private void send(Long parentId, String templateId, Map<String, Object> data) throws Exception {
        WxParent parent = parentMapper.selectById(parentId);
        if (parent == null || parent.getOpenid() == null || parent.getOpenid().isBlank()) {
            return;
        }
        // 开发态账号没有真实 openid，微信接口必然失败，直接跳过
        if (parent.getOpenid().startsWith("dev:")) {
            return;
        }
        if (quotaMapper.consume(parentId, templateId) <= 0) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", parent.getOpenid());
        body.put("template_id", templateId);
        body.put("page", ACTIVITY_PAGE);
        body.put("data", data);
        boolean sent = false;
        try {
            // 必须按 UTF-8 字节发送：RestClient 默认用 ISO-8859-1 写 String body，中文会变成问号
            byte[] payload = objectMapper.writeValueAsBytes(body);
            String raw = restClient.post()
                    .uri(SEND_URL, accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> result = parse(raw);
            String errcode = String.valueOf(result.getOrDefault("errcode", "0"));
            sent = "0".equals(errcode);
            if (!sent) {
                LOG.warn("活动海报订阅消息发送失败：parentId={}, errcode={}, errmsg={}",
                        parentId, errcode, result.get("errmsg"));
                if ("40001".equals(errcode) || "40014".equals(errcode) || "42001".equals(errcode)) {
                    accessToken = null;
                }
            }
        } finally {
            if (!sent) {
                // 没发出去就不占用家长的授权次数
                quotaMapper.grant(parentId, templateId, 1);
            }
        }
    }

    private String accessToken() throws Exception {
        String token = accessToken;
        if (token != null && System.currentTimeMillis() < accessTokenExpireAt) {
            return token;
        }
        synchronized (this) {
            token = accessToken;
            if (token != null && System.currentTimeMillis() < accessTokenExpireAt) {
                return token;
            }
            if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
                throw new IllegalStateException("未配置小程序 AppID / AppSecret");
            }
            String raw = restClient.get()
                    .uri(TOKEN_URL, appid, secret)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> body = parse(raw);
            Object value = body.get("access_token");
            if (value == null) {
                throw new IllegalStateException("获取微信 access_token 失败：" + body.get("errmsg"));
            }
            long seconds = body.get("expires_in") == null ? 7000L : Long.parseLong(String.valueOf(body.get("expires_in")));
            accessToken = String.valueOf(value);
            // 提前 5 分钟过期，避免临界失效
            accessTokenExpireAt = System.currentTimeMillis() + Math.max(seconds - 300L, 60L) * 1000L;
            return accessToken;
        }
    }

    private Map<String, Object> parse(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
        });
    }

    private String templateId() {
        return activityTemplateId == null || activityTemplateId.isBlank() ? null : activityTemplateId.trim();
    }

    private Map<String, String> value(String text) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("value", text);
        return item;
    }

    private String cut(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
