package com.zero.system.controller;

import com.zero.system.entity.user.User;
import com.zero.system.entity.user.UserReward;
import com.zero.system.service.UserRewardService;
import com.zero.system.service.UserService;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpOAuth2AccessToken;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/wechat")
public class WechatController {

    @Autowired
    private WxMpService wxMpService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRewardService userRewardService;

    /**
     * 请求微信服务器认证授权
     *
     * @param returnUrl 微信认证授权时的state参数
     * @return
     */
    @GetMapping("/authorize")
    public String authorize(@RequestParam("returnUrl") String returnUrl) {
        // 用户授权完成后的重定向链接，441aca54.ngrok.io为使用ngrok代理后的公网域名，该域名需要替换为在微信公众号后台设置的域名，否则请求微信服务器不成功，一般都是采用将本地服务代理映射到一个可以访问的公网进行开发调试
        String url = "http://hd.exuhigg.cn/wechat/userInfo";
        String redirectURL = wxMpService.oauth2buildAuthorizationUrl(url, WxConsts.OAuth2Scope.SNSAPI_USERINFO, URLEncoder.encode(returnUrl));
        log.info("【微信网页授权】获取code,redirectURL={}", redirectURL);
        return "redirect:" + redirectURL;
    }

    /**
     * 认证授权成功后返回微信用户信息
     *
     * @param code      微信授权成功后重定向地址后的code参数
     * @param returnUrl 请求微信授权时携带的state参数
     * @return
     * @throws Exception
     */
    @GetMapping("/userInfo")
    public String userInfo(@RequestParam("code") String code,
                           @RequestParam("state") String returnUrl, HttpServletRequest request) throws Exception {
        log.info("【微信网页授权】code={}", code);
        log.info("【微信网页授权】state={}", returnUrl);
        WxMpOAuth2AccessToken wxMpOAuth2AccessToken;
        try {
            // 获取accessToken
            wxMpOAuth2AccessToken = wxMpService.oauth2getAccessToken(code);
        } catch (WxErrorException e) {
            log.error("【微信网页授权】{}", e);
            throw new Exception(e.getError().getErrorMsg());
        }
        // 根据accessToken获取openId
        String openId = wxMpOAuth2AccessToken.getOpenId();
        log.info("【微信网页授权】openId={}", openId);
        // 获取用户信息
        WxMpUser wxMpUser = wxMpService.oauth2getUserInfo(wxMpOAuth2AccessToken, null);
        if(null!= wxMpUser){
            User user1 = userService.selectByOpenId(wxMpUser.getOpenId());
            if(user1 == null){
                List<UserReward> userRewardList = userRewardService.selectList();
                User user = new User();
                user.setOpenId(wxMpUser.getOpenId());
                user.setUserName(wxMpUser.getNickname());
                user.setImage(wxMpUser.getHeadImgUrl());
                user.setSex(wxMpUser.getSexDesc());
                user.setRewardId(userRewardList.get(0).getId());
                user.setBalance("0");
                user.setWithdrawal("0");
                user.setStart(0);
                userService.userAdd(user);

            }

            request.getSession().setAttribute("wxMpUser",wxMpUser);
        }else {

        }
        log.info("【微信网页授权】wxMpUser={}", wxMpUser);
        // 刷新accessToken
        wxMpService.oauth2refreshAccessToken(wxMpOAuth2AccessToken.getRefreshToken());
        // 验证accessToken是否有效
        wxMpService.oauth2validateAccessToken(wxMpOAuth2AccessToken);
            return "redirect:" + returnUrl + "?openid=" + openId;

    }
}
