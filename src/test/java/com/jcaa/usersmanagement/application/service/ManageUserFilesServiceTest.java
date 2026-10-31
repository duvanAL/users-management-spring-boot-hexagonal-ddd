package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.out.FileStoragePort;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import org.junit.jupiter.api.Test;

class ManageUserFilesServiceTest {
  private final GetUserByIdUseCase users = mock(GetUserByIdUseCase.class);
  private final FileStoragePort storage = mock(FileStoragePort.class);
  private final ManageUserFilesService service = new ManageUserFilesService(users, storage);

  @Test void rejectsAnotherOwnerBeforeCheckingExistenceOrCallingStorage() {
    assertThrows(ManageUserFilesService.FileAccessDeniedException.class,
        () -> service.requireUser("other", false, "owner"));
    verifyNoInteractions(users, storage);
  }

  @Test void ownerAndAdminCanConfirmExistence() {
    service.requireUser("owner", false, "owner");
    service.requireUser("admin", true, "another");
    verify(users).execute(new GetUserByIdQuery("owner"));
    verify(users).execute(new GetUserByIdQuery("another"));
    verifyNoInteractions(storage);
  }

  @Test void missingUserNeverCallsFileStorage() {
    when(users.execute(any())).thenThrow(new IllegalArgumentException("absent"));
    assertThrows(IllegalArgumentException.class, () -> service.requireUser("owner", false, "owner"));
    verifyNoInteractions(storage);
  }

  @Test void forwardsRequestAfterUserCheck() {
    final FileStoragePort.Request request = new FileStoragePort.Request(FileStoragePort.Operation.LIST,
        "owner", null, null, null, null, null, null, 10, null);
    service.execute("owner", false, request);
    verify(users).execute(new GetUserByIdQuery("owner"));
    verify(storage).execute(request);
  }
}
