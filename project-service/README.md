# Project Service

Project Service là một microservice trong hệ thống IEMS (Integrated Enterprise Management System) chịu trách nhiệm quản lý dự án và thành viên dự án.

## Tính năng chính

### 1. Quản lý Dự án (UC13, UC14, UC16, UC17)
- **Tạo dự án mới**: Tạo dự án với thông tin cơ bản và gán quản lý dự án
- **Cập nhật dự án**: Chỉnh sửa thông tin dự án
- **Gán quản lý dự án**: Thay đổi quản lý dự án (chỉ Admin)
- **Xem tiến độ dự án**: Hiển thị tỷ lệ hoàn thành và trạng thái nhiệm vụ
- **Tìm kiếm dự án**: Tìm kiếm theo tên, trạng thái, quản lý

### 2. Quản lý Thành viên Dự án (UC19, UC20, UC21, UC22)
- **Thêm thành viên**: Thêm nhân viên vào dự án với vai trò cụ thể
- **Xem danh sách thành viên**: Hiển thị tất cả thành viên trong dự án
- **Cập nhật vai trò**: Thay đổi vai trò của thành viên
- **Loại bỏ thành viên**: Xóa thành viên khỏi dự án

## API Endpoints

### Projects
- `POST /api/projects` - Tạo dự án mới
- `PUT /api/projects/{projectId}` - Cập nhật dự án
- `GET /api/projects/{projectId}` - Lấy thông tin dự án
- `GET /api/projects` - Tìm kiếm dự án
- `GET /api/projects/my-projects` - Lấy dự án của người dùng
- `GET /api/projects/{projectId}/progress` - Lấy tiến độ dự án
- `PUT /api/projects/{projectId}/assign-manager` - Gán quản lý dự án

### Project Members
- `POST /api/projects/{projectId}/members` - Thêm thành viên
- `GET /api/projects/{projectId}/members` - Lấy danh sách thành viên
- `GET /api/projects/{projectId}/members/role/{role}` - Lấy thành viên theo vai trò
- `PUT /api/projects/{projectId}/members/{userId}/role` - Cập nhật vai trò
- `DELETE /api/projects/{projectId}/members/{userId}` - Xóa thành viên

## Cấu hình

### Database
- PostgreSQL database: `iems_project_db`
- Port: 5432
- Username: postgres
- Password: password

### Service Configuration
- Port: 8084
- Eureka Server: http://localhost:8761
- Swagger UI: http://localhost:8084/swagger-ui.html

## Chạy ứng dụng

1. **Cài đặt dependencies**:
   ```bash
   mvn clean install
   ```

2. **Chạy ứng dụng**:
   ```bash
   mvn spring-boot:run
   ```

3. **Truy cập Swagger UI**:
   ```
   http://localhost:8084/swagger-ui.html
   ```

## Database Schema

### Projects Table
- `id` (Primary Key, UUID)
- `name` (Unique)
- `description`
- `start_date`
- `end_date`
- `status` (PLANNING, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED)
- `manager_id` (UUID)
- `created_by` (UUID)
- `created_at`
- `updated_at`

### Project Members Table
- `id` (Primary Key, UUID)
- `project_id` (Foreign Key, UUID)
- `user_id` (UUID)
- `role` (PROJECT_MANAGER, DEVELOPER, TESTER, REVIEWER, ANALYST, DESIGNER)
- `joined_at`
- `assigned_by` (UUID)
- `created_at`
- `updated_at`

## Tích hợp với các Service khác

### User Service
- Lấy thông tin người dùng
- Kiểm tra quyền admin

### Task Service
- Lấy thông tin nhiệm vụ để tính tiến độ dự án
- Kiểm tra nhiệm vụ đang hoạt động khi xóa thành viên

## Bảo mật

- Tất cả API endpoints yêu cầu header `X-User-ID` để xác định người dùng hiện tại
- Kiểm tra quyền truy cập dựa trên vai trò người dùng trong dự án
- Chỉ quản lý dự án hoặc admin mới có thể thực hiện các thao tác quản lý

## Swagger Documentation

Tất cả API endpoints đều có documentation chi tiết với:
- Mô tả chức năng
- Tham số đầu vào
- Response codes và messages
- Schema của request/response objects

Truy cập Swagger UI tại: `http://localhost:8084/swagger-ui.html`
