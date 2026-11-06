# Authenticated web endpoints

GET /api/me returns the profile for the verified JWT subject. All authenticated roles may access their own profile; query parameters cannot select another user. Uses the existing input port and response mapper; passwords are not returned. Existing role restrictions for user administration remain in force. No new secret or database migration is required.
