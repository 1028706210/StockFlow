package io.github.stockflow.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 买家与仓管复用同一份页面、会话和业务逻辑，通过入口路径选择布局。 */
@Controller
public class StorefrontController {
    @GetMapping({"/shop", "/shop/"})
    public String storefront() {
        return "forward:/index.html";
    }
}
