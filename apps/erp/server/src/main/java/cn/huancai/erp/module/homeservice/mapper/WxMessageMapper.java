package cn.huancai.erp.module.homeservice.mapper;

import cn.huancai.erp.module.homeservice.entity.WxMessage;
import cn.huancai.erp.module.homeservice.vo.MessageParentVO;
import cn.huancai.erp.module.homeservice.vo.MessageReceiverVO;
import cn.huancai.erp.module.homeservice.vo.MessageStudentVO;
import cn.huancai.erp.module.homeservice.vo.MessageVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface WxMessageMapper extends BaseMapper<WxMessage> {

    IPage<MessageVO> selectMessagePage(IPage<MessageVO> page,
                                       @Param("keyword") String keyword,
                                       @Param("msgType") String msgType,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    IPage<MessageReceiverVO> selectReceiverPage(IPage<MessageReceiverVO> page,
                                                @Param("messageId") Long messageId,
                                                @Param("readFlag") Integer readFlag);

    List<String> selectClassNames(@Param("classIds") List<Long> classIds);

    List<MessageStudentVO> selectClassStudents(@Param("classIds") List<Long> classIds);

    List<MessageStudentVO> selectAllBoundStudents();

    List<MessageStudentVO> selectStudentsByIds(@Param("ids") List<Long> ids);

    List<MessageParentVO> selectParentsByStudentIds(@Param("ids") List<Long> ids);
}
