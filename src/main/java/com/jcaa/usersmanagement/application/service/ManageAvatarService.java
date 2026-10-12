package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.ManageAvatarUseCase;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.StoragePort;
import org.springframework.stereotype.Service;
// Asegúrate de importar tu StoragePort y el puerto de búsqueda de usuario (ej: GetUserByIdPort)
// import com.tuproyecto.application.port.out.StoragePort;
// import com.tuproyecto.application.port.out.GetUserByIdPort;

@Service
public class ManageAvatarService implements ManageAvatarUseCase {

    private final StoragePort storagePort;
    // NOTA: Ajusta el nombre de "GetUserByIdPort" al que uses en tu proyecto para buscar usuarios.
    private final GetUserByIdPort getUserByIdPort;

    // Constructor para la inyección de dependencias
    public ManageAvatarService(StoragePort storagePort, GetUserByIdPort getUserByIdPort) {
        this.storagePort = storagePort;
        this.getUserByIdPort = getUserByIdPort;
    }

    @Override
    public void uploadAvatar(String userId, byte[] fileBytes, String contentType) {
        // 1. Verificamos que el usuario exista ANTES de operar con el archivo.
        // Si no lo encuentra, lanzamos una excepción (idealmente una personalizada como UserNotFoundException)
        getUserByIdPort.getUserById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Si existe, subimos la foto a Supabase a través del puerto de salida.
        storagePort.uploadAvatar(userId, fileBytes, contentType);
    }

    @Override
    public String getAvatarUrl(String userId) {
        // 1. Verificamos que el usuario exista
        getUserByIdPort.getUserById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Pedimos la URL temporal a Supabase
        return storagePort.getAvatarSignedUrl(userId);
    }
}
