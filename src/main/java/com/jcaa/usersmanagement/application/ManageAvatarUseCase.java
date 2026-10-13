package com.jcaa.usersmanagement.application;

public interface ManageAvatarUseCase {
    void uploadAvatar(String userId, byte[] fileBytes, String contentType);
    String getAvatarUrl(String userId);
}
