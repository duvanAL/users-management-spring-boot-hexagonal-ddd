package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.application.port.out.FileStoragePort;

public interface ManageUserFilesUseCase {
  FileStoragePort.Result execute(String actorId, boolean administrator, FileStoragePort.Request request);
  void requireUser(String actorId, boolean administrator, String userId);
}
