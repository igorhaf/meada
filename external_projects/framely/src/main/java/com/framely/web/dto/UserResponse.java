package com.framely.web.dto;

import com.framely.user.User;

public record UserResponse(Long id, String name, String email, Long telegramChatId) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getTelegramChatId());
    }
}
