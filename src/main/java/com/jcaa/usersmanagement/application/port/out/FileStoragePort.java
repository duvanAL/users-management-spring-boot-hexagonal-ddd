package com.jcaa.usersmanagement.application.port.out;

public interface FileStoragePort {
  Result execute(Request request);

  enum Operation { LIST, GET, DOWNLOAD, UPLOAD, REPLACE, DELETE }

  record Request(Operation operation, String userId, String fileId, String idempotencyKey,
      String ifMatch, String fileName, String contentType, byte[] content, Integer limit, String after) {}

  record Result(int status, String json) {}
}
