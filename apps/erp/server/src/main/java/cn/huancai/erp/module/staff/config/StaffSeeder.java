package cn.huancai.erp.module.staff.config;

import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class StaffSeeder {

    private static final String DEMO_PHONE = "18602477659";
    private static final String DEMO_PASSWORD = "781112";

    @Bean
    public ApplicationRunner seedAdmin(StaffMapper staffMapper) {
        return args -> {
            Long exists = staffMapper.selectCount(
                    new LambdaQueryWrapper<Staff>().eq(Staff::getPhone, DEMO_PHONE));
            if (exists != null && exists > 0) {
                return;
            }
            Staff admin = new Staff();
            admin.setName("管理员");
            admin.setPhone(DEMO_PHONE);
            admin.setAccount("admin");
            admin.setPasswordHash(new BCryptPasswordEncoder().encode(DEMO_PASSWORD));
            admin.setRole("admin");
            admin.setTitle("校长");
            admin.setStatus(1);
            staffMapper.insert(admin);
        };
    }
}
