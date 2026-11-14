package cn.huancai.erp.module.material.vo;

import java.math.BigDecimal;
import java.util.List;

/** 教材模块写操作的下拉选项。 */
public record MaterialOptionsVO(List<MaterialOption> materials, List<String> rooms, List<String> categories) {

    public record MaterialOption(Long id, String name, String category, String status, BigDecimal salePrice) {
    }
}
