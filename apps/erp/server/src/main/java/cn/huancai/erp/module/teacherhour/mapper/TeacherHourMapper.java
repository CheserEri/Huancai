package cn.huancai.erp.module.teacherhour.mapper;

import cn.huancai.erp.module.teacherhour.vo.TeacherHourRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TeacherHourMapper {

    /** 查询区间内已上课的课次，带上课人数、主要年级与手工调整金额。 */
    List<TeacherHourRecordVO> selectRecords(@Param("start") LocalDate start,
                                            @Param("end") LocalDate end,
                                            @Param("teacherId") Long teacherId,
                                            @Param("campusId") Long campusId);
}
