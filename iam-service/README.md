# IAM Service

IAM Service là một microservice quản lý tài khoản người dùng, vai trò (roles) và quyền (permissions) trong hệ thống IEMS.

## Các Use Case Được Hỗ Trợ

### UC01: Tạo Quyền (Permission)
- **Endpoint**: `POST /api/iam/permissions`
- **Mô tả**: Tạo một quyền mới

### UC02: Danh sách Quyền
- **Endpoint**: `GET /api/iam/permissions`
- **Mô tả**: Lấy danh sách quyền

### UC03: Chi tiết Quyền
- **Endpoint**: `GET /api/iam/permissions/{id}`
- **Mô tả**: Lấy thông tin chi tiết của quyền

### UC04: Cập nhật Quyền
- **Endpoint**: `PUT /api/iam/permissions/{id}`
- **Mô tả**: Cập nhật tên quyền

### UC05: Xóa Quyền
- **Endpoint**: `DELETE /api/iam/permissions/{id}`
- **Mô tả**: Xóa quyền

### UC06: Tạo Vai trò (Role)
- **Endpoint**: `POST /api/iam/roles`
- **Mô tả**: Tạo vai trò và gán danh sách quyền theo mã

### UC07: Danh sách Vai trò
- **Endpoint**: `GET /api/iam/roles`
- **Mô tả**: Lấy danh sách vai trò

### UC08: Chi tiết Vai trò
- **Endpoint**: `GET /api/iam/roles/{id}`
- **Mô tả**: Lấy thông tin chi tiết của vai trò

### UC09: Cập nhật Vai trò
- **Endpoint**: `PUT /api/iam/roles/{id}`
- **Mô tả**: Cập nhật tên và quyền của vai trò

### UC10: Xóa Vai trò
- **Endpoint**: `DELETE /api/iam/roles/{id}`
- **Mô tả**: Xóa vai trò

### UC11: Tạo Tài khoản (User)
- **Endpoint**: `POST /api/iam/users`
- **Mô tả**: Tạo tài khoản, mã hóa mật khẩu và gán vai trò

### UC12: Danh sách Tài khoản
- **Endpoint**: `GET /api/iam/users`
- **Mô tả**: Lấy danh sách tài khoản

### UC13: Chi tiết Tài khoản
- **Endpoint**: `GET /api/iam/users/{id}`
- **Mô tả**: Lấy thông tin chi tiết tài khoản

### UC14: Cập nhật Tài khoản
- **Endpoint**: `PUT /api/iam/users/{id}`
- **Mô tả**: Cập nhật email, trạng thái và vai trò

### UC15: Xóa Tài khoản
- **Endpoint**: `DELETE /api/iam/users/{id}`
- **Mô tả**: Xóa tài khoản

## API Endpoints (HTTP Examples)

### Tạo Quyền
```http
POST /api/iam/permissions
Content-Type: application/json

{
  "code": "USER_READ",
  "name": "Read users"
}
```

### Danh sách Quyền
```http
GET /api/iam/permissions
```

### Chi tiết Quyền
```http
GET /api/iam/permissions/1
```

### Cập nhật Quyền
```http
PUT /api/iam/permissions/1
Content-Type: application/json

{
  "name": "Read user accounts"
}
```

### Xóa Quyền
```http
DELETE /api/iam/permissions/1
```

### Tạo Vai trò
```http
POST /api/iam/roles
Content-Type: application/json

{
  "code": "ADMIN",
  "name": "Administrator",
  "permissionCodes": ["USER_READ"]
}
```

### Danh sách Vai trò
```http
GET /api/iam/roles
```

### Chi tiết Vai trò
```http
GET /api/iam/roles/1
```

### Cập nhật Vai trò
```http
PUT /api/iam/roles/1
Content-Type: application/json

{
  "name": "Admin",
  "permissionCodes": ["USER_READ"]
}
```

### Xóa Vai trò
```http
DELETE /api/iam/roles/1
```

### Tạo Tài khoản
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

### Danh sách Tài khoản
```http
GET /api/iam/users
```

### Chi tiết Tài khoản
```http
GET /api/iam/users/1
```

### Cập nhật Tài khoản
```http
PUT /api/iam/users/1
Content-Type: application/json

{
  "email": "alice.new@example.com",
  "enabled": true,
  "roleCodes": ["ADMIN"]
}
```

### Xóa Tài khoản
```http
DELETE /api/iam/users/1
```

## Trạng thái và Quy ước

- Phản hồi chuẩn dạng:
```json
{
  "status": "success|error",
  "message": "mô tả ngắn",
  "data": {}
}
```
- Mật khẩu được mã hóa với BCrypt (theo PasswordEncoder).
- Quan hệ: User ⟷ Role (n-n), Role ⟷ Permission (n-n).

## Chạy Service

```bash
# Windows PowerShell
cd iam-service
./mvnw.cmd -q -DskipTests spring-boot:run
```

- Base URL: `http://localhost:8086`
- Hiện tại tất cả endpoint đều permitAll (có thể siết chặt sau).
