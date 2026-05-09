package com.ying.tech.community.web.auth;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ying.tech.community.core.common.CursorPageResult;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.service.ArticleCategoryService;
import com.ying.tech.community.service.article.service.ArticleSearchService;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleCategoryVO;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import com.ying.tech.community.service.comment.service.CommentService;
import com.ying.tech.community.service.comment.vo.CommentArticlePageVO;
import com.ying.tech.community.service.comment.vo.CommentReplyPageVO;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.service.user.vo.UserCurrentVO;
import com.ying.tech.community.service.user.vo.UserProfileVO;
import com.ying.tech.community.web.config.WebConfig;
import com.ying.tech.community.web.controller.ArticleCategoryController;
import com.ying.tech.community.web.controller.ArticleController;
import com.ying.tech.community.web.controller.CommentController;
import com.ying.tech.community.web.controller.UserController;
import com.ying.tech.community.web.global.GlobalExceptionHandler;
import com.ying.tech.community.web.hook.SaTokenUserContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = SaTokenAuthWebMvcTest.TestApplication.class,
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
class SaTokenAuthWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;
    @MockBean
    private ArticleService articleService;
    @MockBean
    private ArticleCategoryService articleCategoryService;
    @MockBean
    private ArticleDetailService articleDetailService;
    @MockBean
    private ArticleSearchService articleSearchService;
    @MockBean
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        Mockito.lenient()
                .when(articleSearchService.searchWithHighlight(anyString(), anyInt(), anyInt()))
                .thenReturn(new Page<>());
        Mockito.lenient()
                .when(articleService.getArticleList(anyLong(), anyInt(), nullable(Long.class)))
                .thenReturn(new CursorPageResult<>(null, Collections.emptyList()));
        Mockito.lenient()
                .when(articleCategoryService.listEnabledCategories())
                .thenReturn(List.of(buildCategory(1L, "后端", 10)));
        Mockito.lenient()
                .when(articleDetailService.getArticleDetailById(anyLong()))
                .thenReturn(new ArticleDetailVO());
        Mockito.lenient()
                .when(commentService.getArticleCommentList(anyLong(), anyInt(), anyInt()))
                .thenReturn(emptyArticleCommentPage());
        Mockito.lenient()
                .when(commentService.getCommentReplies(anyLong(), anyInt(), anyInt()))
                .thenReturn(emptyReplyCommentPage());
        Mockito.lenient()
                .when(userService.getUserProfile(anyLong()))
                .thenReturn(buildProfile(1001L, "alice"));
        Mockito.lenient()
                .when(userService.getUserArticlePage(anyLong(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(0L, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (SaTokenContextException ignored) {
            // The request-scoped Sa-Token context is not available after MockMvc completes.
        }
    }

    @Test
    void loginShouldReturnTokenString() throws Exception {
        Mockito.when(userService.login(eq("alice"), eq("123456")))
                .thenAnswer(invocation -> {
                    StpUtil.login(1001L);
                    return StpUtil.getTokenValue();
                });

        mockMvc.perform(post("/user/login")
                        .param("username", "alice")
                        .param("password", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void loginFailureShouldKeepExistingBusinessCode() throws Exception {
        Mockito.when(userService.login(eq("alice"), eq("wrong")))
                .thenThrow(new BusinessException(StatusEnum.USER_PWD_ERROR));

        mockMvc.perform(post("/user/login")
                        .param("username", "alice")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusEnum.USER_PWD_ERROR.getCode()));
    }

    @Test
    void anonymousReadEndpointsShouldRemainAccessible() throws Exception {
        mockMvc.perform(get("/article/search").param("keyWord", "sa-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/article/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("后端"));

        mockMvc.perform(get("/article/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/comment/article/1/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/comment/1/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/user/1001/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1001L))
                .andExpect(jsonPath("$.data.username").value("alice"));

        mockMvc.perform(get("/user/1001/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void protectedEndpointShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/user/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusEnum.AUTH_REQUIRED.getCode()));

        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusEnum.AUTH_REQUIRED.getCode()));
    }

    @Test
    void protectedEndpointShouldAcceptValidSatokenHeader() throws Exception {
        Mockito.when(userService.login(eq("alice"), eq("123456")))
                .thenAnswer(invocation -> {
                    StpUtil.login(1001L);
                    return StpUtil.getTokenValue();
                });
        Mockito.when(userService.getCurrentUser(1001L)).thenReturn(buildUser(1001L, "alice"));

        String token = loginAndGetToken();

        mockMvc.perform(get("/user/current")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1001L))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void logoutShouldInvalidateExistingToken() throws Exception {
        Mockito.when(userService.login(eq("alice"), eq("123456")))
                .thenAnswer(invocation -> {
                    StpUtil.login(1001L);
                    return StpUtil.getTokenValue();
                });
        Mockito.when(userService.getCurrentUser(1001L)).thenReturn(buildUser(1001L, "alice"));

        String token = loginAndGetToken();

        mockMvc.perform(post("/user/logout")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/user/current")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusEnum.AUTH_REQUIRED.getCode()));
    }

    private String loginAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/user/login")
                        .param("username", "alice")
                        .param("password", "123456"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").asText();
    }

    private UserCurrentVO buildUser(Long userId, String username) {
        UserCurrentVO user = new UserCurrentVO();
        user.setId(userId);
        user.setUsername(username);
        user.setUserRole(0);
        return user;
    }

    private UserProfileVO buildProfile(Long userId, String username) {
        UserProfileVO profile = new UserProfileVO();
        profile.setUserId(userId);
        profile.setUsername(username);
        profile.setUserRole(0);
        profile.setArticleCount(0L);
        profile.setFollowCount(0L);
        profile.setFanCount(0L);
        profile.setFollowed(Boolean.FALSE);
        profile.setSelf(Boolean.FALSE);
        return profile;
    }

    private ArticleCategoryVO buildCategory(Long id, String name, Integer sort) {
        ArticleCategoryVO categoryVO = new ArticleCategoryVO();
        categoryVO.setId(id);
        categoryVO.setName(name);
        categoryVO.setSort(sort);
        return categoryVO;
    }

    private CommentArticlePageVO emptyArticleCommentPage() {
        CommentArticlePageVO pageVO = new CommentArticlePageVO();
        pageVO.setPublicPage(new PageResult<>(0L, Collections.emptyList()));
        pageVO.setMine(Collections.emptyList());
        return pageVO;
    }

    private CommentReplyPageVO emptyReplyCommentPage() {
        CommentReplyPageVO pageVO = new CommentReplyPageVO();
        pageVO.setPublicPage(new PageResult<>(0L, Collections.emptyList()));
        pageVO.setMine(Collections.emptyList());
        return pageVO;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SaTokenTestConfig {
        @Bean
        @Primary
        SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            UserController.class,
            ArticleController.class,
            ArticleCategoryController.class,
            CommentController.class,
            WebConfig.class,
            GlobalExceptionHandler.class,
            SaTokenUserContextInterceptor.class,
            SaTokenAuthWebMvcTest.SaTokenTestConfig.class
    })
    static class TestApplication {
    }
}
