package com.sg.shopping.controller;

import com.sg.shopping.common.utils.*;
import com.sg.shopping.pojo.UserInfo;
import com.sg.shopping.pojo.bo.UserInfoBO;
import com.sg.shopping.pojo.vo.UserInfoVO;
import com.sg.shopping.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Controller
public class SsoController {
    public static final String REDIS_USER_TOKEN = "redis_user_token";
    public static final String REDIS_USER_TICKET = "redis_user_ticket";
    public static final String REDIS_TEMP_TICKET = "redis_temp_ticket";
    public static final String COOKIE_USER_TICKET = "cookie_user_ticket";

    @Autowired
    private UserService userService;

    @Autowired
    private RedisOperator redisOperator;

    @GetMapping("/login")
    public String login(String returnUrl, Model model,
                        HttpServletRequest request,
                        HttpServletResponse response) throws NoSuchAlgorithmException {
        model.addAttribute("returnUrl", returnUrl);

        // 获取userTicket，如果cookie中能够获取到，证明用户登录过，此时再签发一个临时票据
        String userTicket = getCookie(request, COOKIE_USER_TICKET);
        if (verifyUserTicket(userTicket)) {
            String tempTicket = generateTempTicket();
            return "redirect:" + returnUrl + "?tmpTicket=" + tempTicket;
        }

        return "login"; // this will redirect to login.html resource file.
    }

    private boolean verifyUserTicket(String userTicket) {
        if (StringUtils.isBlank(userTicket)) {
            return false;
        }

        String userId = redisOperator.get(REDIS_USER_TICKET + ":" + userTicket);
        if (StringUtils.isBlank(userId)) {
            return false;
        }

        // 验证门票对应的用户会话是否存在
        String userSession = redisOperator.get(REDIS_USER_TOKEN + ":" + userId);
        if (StringUtils.isBlank(userSession)) {
            return false;
        }

        return true;
    }

    @PostMapping("/doLogin")
    public String doLogin(String username,
                            String password,
                            String returnUrl,
                            Model model,
                            HttpServletRequest request,
                            HttpServletResponse response) throws NoSuchAlgorithmException {
        if (StringUtils.isBlank(username) ||
                StringUtils.isBlank(password)) {
            model.addAttribute("errmsg", "password cannot be null");
            return "login";
        }

        // 1. 验证登录
        UserInfo userInfo = userService.login(username, MD5Utils.getMd5String(password));

        if (userInfo == null) {
            model.addAttribute("errmsg", "user or password incorrect");
            return "login";
        }

        // 2. 在redis保存会话
        String uniqueToken = UUID.randomUUID().toString().trim();
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(userInfo, userInfoVO);
        userInfoVO.setUserUniqueToken(uniqueToken);
        redisOperator.set(REDIS_USER_TOKEN + ":" + userInfo.getId(), JsonUtils.objectToJson(userInfoVO));

        // 3. 生成userTicket门票，全局门票，代表用户在CAS端登录过，并通过Cookie返回
        String userTicket = UUID.randomUUID().toString().trim();
        setCookie(COOKIE_USER_TICKET, userTicket, response);

        // 4. userTicket关联用户id，并且存放到redis
        redisOperator.set(REDIS_USER_TICKET + ":" + userTicket, userInfo.getId());

        // 5. 生产临时门票tempTicket用于回跳验证
        String tempTicket = generateTempTicket();

        return "redirect:" + returnUrl + "?tmpTicket=" + tempTicket;
    }

    @PostMapping("/verifyTmpTicket")
    @ResponseBody
    public JsonResult verifyTmpTicket(String tmpTicket,
                        HttpServletRequest request,
                        HttpServletResponse response) throws NoSuchAlgorithmException {
        // 验证redis临时票据
        String tmpTicketValue = redisOperator.get(REDIS_TEMP_TICKET + ":" + tmpTicket);
        if (StringUtils.isBlank(tmpTicketValue)) {
            return JsonResult.errorUserTicket("Temp ticket error");
        }
        if (!tmpTicketValue.equals(MD5Utils.getMd5String(tmpTicket))) {
            return JsonResult.errorUserTicket("Temp ticket error");
        }

        // 销毁临时票据
        redisOperator.del(REDIS_TEMP_TICKET + ":" + tmpTicket);

        // 验证并获取用户的userTicket
        String userTicket = getCookie(request, COOKIE_USER_TICKET);
        String userId = redisOperator.get(REDIS_USER_TICKET + ":" + userTicket);
        if (StringUtils.isBlank(userId)) {
            return JsonResult.errorUserTicket("Ticket is not exist");
        }

        // 验证门票对应的用户会话是否存在
        String userSession = redisOperator.get(REDIS_USER_TOKEN + ":" + userId);
        if (StringUtils.isBlank(userSession)) {
            return JsonResult.errorUserTicket("Session is not exist");
        }

        // 验证成功，返回用户会话
        return JsonResult.ok(JsonUtils.jsonToPojo(userSession, UserInfoVO.class));
    }

    private String generateTempTicket() throws NoSuchAlgorithmException {
        String tempTicket = UUID.randomUUID().toString().trim();
        redisOperator.set(REDIS_TEMP_TICKET + ":" + tempTicket, MD5Utils.getMd5String(tempTicket), 600);
        return tempTicket;
    }

    private void setCookie(String key, String value, HttpServletResponse response) {
        Cookie cookie = new Cookie(key, value);
        cookie.setDomain("sso.com");
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private String getCookie(HttpServletRequest request, String key) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || StringUtils.isBlank(key)) {
            return null;
        }

        String cookieValue = null;
        for (int i = 0; i < cookies.length; i++) {
            if (cookies[i].getName().equals(key)) {
                cookieValue = cookies[i].getValue();
                break;
            }
        }
        return cookieValue;
    }

}
