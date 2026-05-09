package com.ying.tech.community.web.auth;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.admin.service.AdminDashboardService;
import com.ying.tech.community.service.admin.vo.AdminDashboardSummaryVO;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.web.config.WebConfig;
import com.ying.tech.community.web.controller.UserController;
import com.ying.tech.community.web.controller.admin.AdminDashboardController;
import com.ying.tech.community.web.global.GlobalExceptionHandler;
import com.ying.tech.community.web.hook.SaTokenUserContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = AdminRoleAuthWebMvcTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.thymeleaf.check-template-location=false",
                "sa-token.token-name=satoken",
                "sa-token.is-read-header=true",
                "sa-token.is-read-cookie=false"
        }
)
@AutoConfigureMockMvc
class AdminRoleAuthWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;
    @MockBean
    private AdminDashboardService adminDashboardService;

    @AfterEach
    void tearDown() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (SaTokenContextException ignored) {
            // ignore request scoped context cleanup failure
        }
    }

    @Test
    void adminEndpointShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusEnum.AUTH_REQUIRED.getCode()));
    }

    @Test
    void adminEndpointShouldRejectNormalUser() throws Exception {
        Mockito.when(userService.login(eq("alice"), eq("123456")))
                .thenAnswer(invocation -> {
                    StpUtil.login(1001L);
                    return StpUtil.getTokenValue();
                });

        String token = loginAndGetToken("alice", "123456");

        mockMvc.perform(get("/admin/dashboard/summary").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusEnum.AUTH_FORBIDDEN.getCode()));
    }

    @Test
    void adminEndpointShouldAcceptAdminUser() throws Exception {
        Mockito.when(userService.login(eq("admin"), eq("admin")))
                .thenAnswer(invocation -> {
                    StpUtil.login(2001L);
                    return StpUtil.getTokenValue();
                });
        Mockito.when(adminDashboardService.getSummary()).thenReturn(new AdminDashboardSummaryVO());

        String token = loginAndGetToken("admin", "admin");

        mockMvc.perform(get("/admin/dashboard/summary").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/user/login")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminSaTokenTestConfig {
        @Bean
        @Primary
        SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }

        @Bean
        @Primary
        StpInterface stpInterface() {
            return new StpInterface() {
                @Override
                public List<String> getPermissionList(Object loginId, String loginType) {
                    return Collections.emptyList();
                }

                @Override
                public List<String> getRoleList(Object loginId, String loginType) {
                    return "2001".equals(String.valueOf(loginId)) ? List.of("admin") : Collections.emptyList();
                }
            };
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            UserController.class,
            AdminDashboardController.class,
            WebConfig.class,
            GlobalExceptionHandler.class,
            SaTokenUserContextInterceptor.class,
            AdminSaTokenTestConfig.class
    })
    static class TestApplication {
    }
}
