package com.github.ltsopensource.admin.web.filter;

import com.github.ltsopensource.admin.access.domain.Account;
import com.github.ltsopensource.admin.cluster.BackendAppContext;
import com.github.ltsopensource.admin.request.AccountReq;
import com.github.ltsopensource.admin.support.AppConfigurer;
import com.github.ltsopensource.admin.web.support.SpringContextHolder;
import com.github.ltsopensource.core.commons.utils.Base64;
import com.github.ltsopensource.core.commons.utils.StringUtils;
import com.github.ltsopensource.admin.support.ThreadLocalUtil;
import org.springframework.util.AntPathMatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by ztajy on 2015-11-11.
 *
 * @author ztajy
 * @author Robert HG (254963746@qq.com)
 */
public class LoginAuthFilter implements Filter {
    private static final String AUTH_PREFIX = "Basic ";
    private AntPathMatcher pathMatcher = new AntPathMatcher();

    private String username = "admin";

    private String password = "admin";

    private String[] excludedURLArray;

    private BackendAppContext appContext;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        username = AppConfigurer.getProperty("console.username", username);
        password = AppConfigurer.getProperty("console.password", password);

        String excludedURLs = filterConfig.getInitParameter("excludedURLs");
        if (StringUtils.isNotEmpty(excludedURLs)) {
            String[] arr = excludedURLs.split(",");
            excludedURLArray = new String[arr.length];
            for (int i = 0; i < arr.length; i++) {
                excludedURLArray[i] = StringUtils.trim(arr[i]);
            }
        }
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        ThreadLocalUtil.setAttr("authority",false);//默认不具备管理员权限

        if(appContext == null){
            appContext = SpringContextHolder.getBean(BackendAppContext.class);
        }

        if (isExclude(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String authorization = httpRequest.getHeader("authorization");
        if (null != authorization && authorization.length() > AUTH_PREFIX.length()) {
            authorization = authorization.substring(AUTH_PREFIX.length(), authorization.length());

            // Owen Jia at 20190319，修改登陆体系，增加帐户表
            String usernameAndPassword = new String(Base64.decodeFast(authorization));
            String username1 = usernameAndPassword.split(":")[0];
            ThreadLocalUtil.setAttr("username",username1);//登录名称

            if(!username1.equals(username)){
                //表中账户，非系统管理员
                AccountReq accountReq = new AccountReq();
                accountReq.setUsername(username1);
                Account account = appContext.getBackendAccountAccess().selectOne(accountReq);
                if(usernameAndPassword.equals(account.getUsername() + ":" + account.getPassword())){
                    authenticateSuccess(httpResponse);
                    chain.doFilter(httpRequest, httpResponse);
                } else {
                    needAuthenticate(httpRequest, httpResponse);
                }
            } else {
                if ((username + ":" + password).equals(usernameAndPassword)) {
                    //管理员账户
                    ThreadLocalUtil.setAttr("authority",true);
                    authenticateSuccess(httpResponse);
                    chain.doFilter(httpRequest, httpResponse);
                } else {
                    needAuthenticate(httpRequest, httpResponse);
                }
            }
        } else {
            needAuthenticate(httpRequest, httpResponse);
        }
    }

    private boolean isExclude(String path) {
        if (excludedURLArray != null) {
            for (String page : excludedURLArray) {
                //判断是否在过滤url中
                if (pathMatcher.match(page, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void authenticateSuccess(final HttpServletResponse response) {
        response.setStatus(200);
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-store");
        response.setDateHeader("Expires", 0);
    }

    private void needAuthenticate(final HttpServletRequest request, final HttpServletResponse response) {
        response.setStatus(401);
        response.setHeader("Cache-Control", "no-store");
        response.setDateHeader("Expires", 0);
        response.setHeader("WWW-authenticate", AUTH_PREFIX + "Realm=\"lts login need auth\"");
    }

    @Override
    public void destroy() {
    }
}
