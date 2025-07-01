package com.cdwater.ticketbox.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/", "/login", "/register", "/events/**", "/performances/**", "/orders/**"})
    public String web() {
        return "forward:/index.html";
    }

    @GetMapping({"/admin", "/admin/", "/admin/login", "/admin/events/**", "/admin/orders", "/admin/check-in"})
    public String admin() {
        return "forward:/admin/index.html";
    }
}
