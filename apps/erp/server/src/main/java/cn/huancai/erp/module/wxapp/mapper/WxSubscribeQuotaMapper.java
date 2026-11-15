package cn.huancai.erp.module.wxapp.mapper;

import cn.huancai.erp.module.wxapp.entity.WxSubscribeQuota;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/** 订阅消息额度：授权累加、发送扣减。 */
public interface WxSubscribeQuotaMapper extends BaseMapper<WxSubscribeQuota> {

    int grant(@Param("parentId") Long parentId,
              @Param("templateId") String templateId,
              @Param("count") int count);

    /** 扣减一次，返回受影响行数（0 表示没有可用次数）。 */
    int consume(@Param("parentId") Long parentId, @Param("templateId") String templateId);

    Integer remainCount(@Param("parentId") Long parentId, @Param("templateId") String templateId);
}
