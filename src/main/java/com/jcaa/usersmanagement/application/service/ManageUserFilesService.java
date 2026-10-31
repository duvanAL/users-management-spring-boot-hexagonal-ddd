package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.in.ManageUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.out.FileStoragePort;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;

public final class ManageUserFilesService implements ManageUserFilesUseCase {
  private final GetUserByIdUseCase users;
  private final FileStoragePort files;

  public ManageUserFilesService(final GetUserByIdUseCase users, final FileStoragePort files) {
    this.users = users;
    this.files = files;
  }

  @Override
  public void requireUser(final String actorId, final boolean administrator, final String userId) {
    if (!administrator && !userId.equals(actorId)) throw new FileAccessDeniedException();
    users.execute(new GetUserByIdQuery(userId));
  }

  @Override
  public FileStoragePort.Result execute(final String actorId, final boolean administrator,
      final FileStoragePort.Request request) {
    requireUser(actorId, administrator, request.userId());
    return files.execute(request);
  }

  public static final class FileAccessDeniedException extends RuntimeException {
    public FileAccessDeniedException() { super("Access to this user's files is forbidden."); }
  }
}
