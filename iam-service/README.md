# IAM Service

IAM Service is a microservice that manages user accounts, roles, and permissions in the IEMS system.

## Supported Use Cases

### UC01: Create Permission
- **Endpoint**: `POST /api/iam/permissions`
- **Description**: Create a new permission

### UC02: List Permissions
- **Endpoint**: `GET /api/iam/permissions`
- **Description**: Get list of permissions

### UC03: Permission Details
- **Endpoint**: `GET /api/iam/permissions/{id}`
- **Description**: Get detailed permission information

### UC04: Update Permission
- **Endpoint**: `PUT /api/iam/permissions/{id}`
- **Description**: Update permission name

### UC05: Delete Permission
- **Endpoint**: `DELETE /api/iam/permissions/{id}`
- **Description**: Delete permission

### UC06: Create Role
- **Endpoint**: `POST /api/iam/roles`
- **Description**: Create role and assign list of permissions by code

### UC07: List Roles
- **Endpoint**: `GET /api/iam/roles`
- **Description**: Get list of roles

### UC08: Role Details
- **Endpoint**: `GET /api/iam/roles/{id}`
- **Description**: Get detailed role information

### UC09: Update Role
- **Endpoint**: `PUT /api/iam/roles/{id}`
- **Description**: Update role name and permissions

### UC10: Delete Role
- **Endpoint**: `DELETE /api/iam/roles/{id}`
- **Description**: Delete role

### UC11: Create User Account
- **Endpoint**: `POST /api/iam/users`
- **Description**: Create account, encrypt password and assign roles

### UC12: List User Accounts
- **Endpoint**: `GET /api/iam/users`
- **Description**: Get list of user accounts

### UC13: User Account Details
- **Endpoint**: `GET /api/iam/users/{id}`
- **Description**: Get detailed user account information

### UC14: Update User Account
- **Endpoint**: `PUT /api/iam/users/{id}`
- **Description**: Update email, status and roles

### UC15: Delete User Account
- **Endpoint**: `DELETE /api/iam/users/{id}`
- **Description**: Delete user account

## API Endpoints (HTTP Examples)

### Create Permission
```http
POST /api/iam/permissions
Content-Type: application/json

{
  "code": "USER_READ",
  "name": "Read users"
}
```

### List Permissions
```http
GET /api/iam/permissions
```

### Permission Details
```http
GET /api/iam/permissions/1
```

### Update Permission
```http
PUT /api/iam/permissions/1
Content-Type: application/json

{
  "name": "Read user accounts"
}
```

### Delete Permission
```http
DELETE /api/iam/permissions/1
```

### Create Role
```http
POST /api/iam/roles
Content-Type: application/json

{
  "code": "ADMIN",
  "name": "Administrator",
  "permissionCodes": ["USER_READ"]
}
```

### List Roles
```http
GET /api/iam/roles
```

### Role Details
```http
GET /api/iam/roles/1
```

### Update Role
```http
PUT /api/iam/roles/1
Content-Type: application/json

{
  "name": "Admin",
  "permissionCodes": ["USER_READ"]
}
```

### Delete Role
```http
DELETE /api/iam/roles/1
```

### Create User Account
```http
POST /api/iam/users
Content-Type: application/json

{
  "username": "alice",
  "email": "alice@example.com",
  "password": "123456",
  "roleCodes": ["ADMIN"]
}
```

### List User Accounts
```http
GET /api/iam/users
```

### User Account Details
```http
GET /api/iam/users/1
```

### Update User Account
```http
PUT /api/iam/users/1
Content-Type: application/json

{
  "email": "alice.new@example.com",
  "enabled": true,
  "roleCodes": ["ADMIN"]
}
```

### Delete User Account
```http
DELETE /api/iam/users/1
```

## Status and Conventions

- Standard response format:
```json
{
  "status": "success|error",
  "message": "short description",
  "data": {}
}
```
- Passwords are encrypted with BCrypt (via PasswordEncoder).
- Relationships: User ⟷ Role (n-n), Role ⟷ Permission (n-n).

## Running the Service

```bash
# Windows PowerShell
cd iam-service
./mvnw.cmd -q -DskipTests spring-boot:run
```

- Base URL: `http://localhost:8086`
- Currently all endpoints are permitAll (can be tightened later).
