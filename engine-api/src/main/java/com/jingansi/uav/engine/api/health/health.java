package com.jingansi.uav.engine.api.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author yzwang
 * @date 2026-04-03 18:35
 */
@RestController
@RequestMapping("/health")
public class health {
    @GetMapping()
    public String health() {
        return "OK";
    }
}
