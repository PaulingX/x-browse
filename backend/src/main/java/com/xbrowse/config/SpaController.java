package com.xbrowse.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由控制器
 * 将前端路由转发到 index.html
 */
@Controller
public class SpaController {

    @GetMapping(value = {"/admin", "/admin/**", "/browse/**", "/viewer/**", "/settings"})
    public String forward() {
        return "forward:/index.html";
    }
}
