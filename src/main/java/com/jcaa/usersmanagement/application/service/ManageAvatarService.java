package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.ManageAvatarUseCase;
import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.out.StoragePort;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
public class ManageAvatarService implements ManageAvatarUseCase {

    private final StoragePort storagePort;
    private final GetUserByIdUseCase getUserByIdUseCase;

    public ManageAvatarService(StoragePort storagePort, GetUserByIdUseCase getUserByIdUseCase) {
        this.storagePort = storagePort;
        this.getUserByIdUseCase = getUserByIdUseCase;
    }

    @Override
    public void uploadAvatar(String userId, byte[] fileBytes, String contentType) {
        getUserByIdUseCase.execute(new GetUserByIdQuery(userId));
        storagePort.uploadAvatar(userId, fileBytes, contentType);
    }

    @Override
    public String getAvatarUrl(String userId) {
        getUserByIdUseCase.execute(new GetUserByIdQuery(userId));
        return storagePort.getAvatarSignedUrl(userId);
    }
}
