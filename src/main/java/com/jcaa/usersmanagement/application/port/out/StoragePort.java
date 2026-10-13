package com.jcaa.usersmanagement.application.port.out;

public interface StoragePort {
    void uploadAvatar(String userId, byte[] fileBytes, String contentType);
    String getAvatarSignedUrl(String userId);
}