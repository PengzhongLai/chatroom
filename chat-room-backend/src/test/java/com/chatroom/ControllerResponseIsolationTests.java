package com.chatroom;

import com.chatroom.controller.ChannelController;
import com.chatroom.controller.AuthController;
import com.chatroom.controller.SearchController;
import com.chatroom.controller.PrivateChatController;
import com.chatroom.controller.UserController;
import com.chatroom.controller.UserSearchController;
import com.chatroom.service.ChannelViewService;
import com.chatroom.service.MessageService;
import com.chatroom.service.PrivateChatService;
import com.chatroom.service.PrivateChatViewService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerResponseIsolationTests {

    @Test
    void restControllersDoNotDeclareJpaEntitiesOrSpringPagesInResponses() {
        List<Class<?>> controllers = List.of(
                ChannelController.class,
                PrivateChatController.class,
                UserController.class,
                UserSearchController.class
        );

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                Type responseType = method.getGenericReturnType();
                String typeName = responseType.getTypeName();
                assertThat(typeName)
                        .as("%s#%s response type", controller.getSimpleName(), method.getName())
                        .doesNotContain("com.chatroom.entity.")
                        .doesNotContain("org.springframework.data.domain.Page<");
            }
        }
    }

    @Test
    void restRequestBodiesUseTypedDtosInsteadOfMaps() {
        List<Class<?>> controllers = List.of(
                AuthController.class,
                ChannelController.class,
                PrivateChatController.class,
                SearchController.class,
                UserController.class,
                UserSearchController.class
        );

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getAnnotation(RequestBody.class) != null) {
                        assertThat(parameter.getType())
                                .as("%s#%s request body", controller.getSimpleName(), method.getName())
                                .isNotEqualTo(Map.class);
                    }
                }
            }
        }
    }

    @Test
    void lazyEntityMappingsStayInsideDeclaredTransactions() throws NoSuchMethodException {
        assertTransactional(ChannelViewService.class, "create", String.class, String.class, boolean.class);
        assertTransactional(ChannelViewService.class, "list", String.class, int.class, int.class);
        assertTransactional(ChannelViewService.class, "detail", Long.class);
        assertTransactional(ChannelViewService.class, "update", Long.class, String.class, String.class);
        assertTransactional(ChannelViewService.class, "join", Long.class);
        assertTransactional(ChannelViewService.class, "joinByInviteCode", String.class);
        assertTransactional(
                ChannelViewService.class,
                "invite",
                Long.class,
                Long.class,
                com.chatroom.enums.HistoryLevel.class,
                Integer.class
        );
        assertTransactional(ChannelViewService.class, "toggleMute", Long.class);
        assertTransactional(ChannelViewService.class, "members", Long.class);
        assertTransactional(ChannelViewService.class, "myChannels");
        assertTransactional(PrivateChatViewService.class, "initiate", Long.class);
        assertTransactional(PrivateChatViewService.class, "accept", Long.class);
        assertTransactional(PrivateChatService.class, "getChats");
        assertTransactional(PrivateChatService.class, "getMessages", Long.class, int.class, int.class);
        assertTransactional(MessageService.class, "getMessages", Long.class, int.class, int.class);
    }

    private void assertTransactional(Class<?> type, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(Transactional.class))
                .as("%s#%s transaction boundary", type.getSimpleName(), methodName)
                .isNotNull();
    }
}
