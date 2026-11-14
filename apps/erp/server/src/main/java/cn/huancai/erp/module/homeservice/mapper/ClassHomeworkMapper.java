package cn.huancai.erp.module.homeservice.mapper;

import cn.huancai.erp.module.homeservice.entity.ClassHomework;
import cn.huancai.erp.module.homeservice.vo.HomeworkVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface ClassHomeworkMapper extends BaseMapper<ClassHomework> {

    IPage<HomeworkVO> selectHomeworkPage(IPage<HomeworkVO> page,
                                         @Param("classId") Long classId,
                                         @Param("keyword") String keyword,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);
}
