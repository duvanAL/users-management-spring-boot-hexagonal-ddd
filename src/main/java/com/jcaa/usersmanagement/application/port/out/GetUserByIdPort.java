package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserId;

import java.lang.ScopedValue;
import java.util.Optional;

public interface GetUserByIdPort {
  Optional<UserModel> getById(UserId userId);

    <T> ScopedValue<T> getUserById(String userId);
}
