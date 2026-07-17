package com.xbrowse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * x-browse 多媒体图片视频浏览系统启动类
 */
@SpringBootApplication
@EnableScheduling
public class XBrowseApplication {

    public static void main(String[] args) {
        SpringApplication.run(XBrowseApplication.class, args);
    }
}
