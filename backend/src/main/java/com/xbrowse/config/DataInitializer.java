package com.xbrowse.config;

import com.xbrowse.entity.User;
import com.xbrowse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 数据初始化类
 * 首次启动时创建管理员账户
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${xbrowse.admin-password:admin123}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            // 检查是否已存在管理员账户
            Optional<User> adminOpt = userRepository.findByUsername("admin");
            if (adminOpt.isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                admin.setDisplayName("管理员");
                admin.setAdmin(true);
                admin.setEnabled(true);
                admin.setLastLoginTime(LocalDateTime.now());

                User saved = userRepository.save(admin);
                log.info("已创建默认管理员账户: admin, id={}", saved.getId());
            } else {
                log.info("管理员账户已存在, id={}", adminOpt.get().getId());
            }
        } catch (Exception e) {
            log.error("数据初始化失败", e);
        }
    }
}
