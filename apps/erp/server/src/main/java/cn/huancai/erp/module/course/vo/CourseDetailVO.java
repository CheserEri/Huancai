package cn.huancai.erp.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CourseDetailVO {

    private Long id;
    private String name;
    private String category;
    private String subject;
    private String lessonMode;
    private String campusScope;
    private Long campusId;
    private String campusName;
    private Integer lessonCharge;
    private Integer timeCharge;
    private Integer periodCharge;
    private BigDecimal materialFee;
    private String remark;
    private Integer status;
    private LocalDateTime createdAt;
    private List<CoursePriceVO> prices;
    private List<CourseClassVO> classes;
}
