package cn.huancai.erp.module.staff.mapper;

import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.vo.StaffSummaryVO;
import cn.huancai.erp.module.staff.vo.StaffVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StaffMapper extends BaseMapper<Staff> {

    List<StaffVO> selectStaffList(@Param("campusId") Long campusId,
                                  @Param("keyword") String keyword,
                                  @Param("role") String role,
                                  @Param("status") Integer status);

    StaffSummaryVO selectSummary(@Param("campusId") Long campusId,
                                 @Param("keyword") String keyword,
                                 @Param("role") String role,
                                 @Param("status") Integer status);
}
