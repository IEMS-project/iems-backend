# Task Service

Task Service là một microservice quản lý các nhiệm vụ (tasks) trong hệ thống IEMS. Service này hỗ trợ đầy đủ các use case liên quan đến quản lý nhiệm vụ theo yêu cầu của hệ thống.

## Các Use Case Được Hỗ Trợ

### UC23: Tạo Nhiệm vụ
- **Endpoint**: `POST /tasks`
- **Mô tả**: Tạo một nhiệm vụ mới và gán cho một thành viên
- **Quyền**: Quản lý dự án, Thành viên dự án
- **Headers**: `X-User-Id: {userId}`

### UC24: Gán Nhiệm vụ
- **Endpoint**: `PUT /tasks/{id}/assign?newAssigneeId={userId}`
- **Mô tả**: Gán hoặc phân công lại nhiệm vụ cho một thành viên khác
- **Quyền**: Quản lý dự án
- **Headers**: `X-User-Id: {userId}`

### UC25: Xem Danh sách Nhiệm vụ Được giao
- **Endpoint**: `GET /tasks/my-tasks`
- **Mô tả**: Xem danh sách các nhiệm vụ được giao cho mình
- **Quyền**: Người dùng đã đăng nhập
- **Headers**: `X-User-Id: {userId}`

### UC26: Cập nhật Trạng thái Nhiệm vụ
- **Endpoint**: `PUT /tasks/{id}/status?newStatus={status}&comment={comment}`
- **Mô tả**: Cập nhật trạng thái của nhiệm vụ
- **Quyền**: Người được gán nhiệm vụ
- **Headers**: `X-User-Id: {userId}`

### UC27: Thiết lập Ngày và Mức ưu tiên Nhiệm vụ
- **Endpoint**: `PUT /tasks/{id}/priority-date`
- **Mô tả**: Cập nhật ngày bắt đầu, ngày hết hạn và mức ưu tiên
- **Quyền**: Người tạo hoặc được gán nhiệm vụ
- **Headers**: `X-User-Id: {userId}`

## API Endpoints

### Tạo Nhiệm vụ
```http
POST /tasks
Content-Type: application/json
X-User-Id: {userId}

{
  "projectId": "uuid",
  "title": "Tên nhiệm vụ",
  "description": "Mô tả nhiệm vụ",
  "assignedTo": "uuid",
  "priority": "HIGH",
  "startDate": "2024-01-01",
  "dueDate": "2024-01-31"
}
```

### Gán Nhiệm vụ
```http
PUT /tasks/{taskId}/assign?newAssigneeId={newUserId}
X-User-Id: {userId}
```

### Cập nhật Trạng thái
```http
PUT /tasks/{taskId}/status?newStatus=IN_PROGRESS&comment=Đang thực hiện
X-User-Id: {userId}
```

### Cập nhật Ưu tiên và Ngày
```http
PUT /tasks/{taskId}/priority-date
Content-Type: application/json
X-User-Id: {userId}

{
  "priority": "MEDIUM",
  "startDate": "2024-01-15",
  "dueDate": "2024-02-15",
  "comment": "Điều chỉnh thời gian"
}
```

## Trạng thái Nhiệm vụ

- **TO_DO**: Chưa bắt đầu
- **IN_PROGRESS**: Đang thực hiện
- **COMPLETED**: Hoàn thành

## Mức Ưu tiên

- **LOW**: Thấp
- **MEDIUM**: Trung bình
- **HIGH**: Cao

## Quy tắc Chuyển Trạng thái

- `TO_DO` → `IN_PROGRESS`
- `IN_PROGRESS` → `COMPLETED` hoặc `TO_DO`
- `COMPLETED` → `IN_PROGRESS`

## Tính năng Bổ sung

### Lịch sử Trạng thái
- Mọi thay đổi trạng thái đều được ghi lại
- Hỗ trợ comment cho mỗi thay đổi
- Endpoint: `GET /tasks/{id}/history`

### Lọc và Tìm kiếm
- Lọc theo dự án: `GET /tasks/project/{projectId}`
- Lọc nhiệm vụ cá nhân: `GET /tasks/my-tasks/filter?status=IN_PROGRESS&priority=HIGH`

### Validation
- Kiểm tra ngày hợp lệ (startDate ≤ dueDate)
- Kiểm tra quyền thực hiện thao tác
- Validate trạng thái chuyển đổi

## Cấu trúc Dữ liệu

### Task Entity
- ID (UUID)
- Project ID
- Title, Description
- Assigned To, Created By
- Status, Priority
- Start Date, Due Date
- Timestamps

### TaskStatusHistory Entity
- ID (UUID)
- Task ID
- Status
- Updated By
- Comment
- Updated At

## Ghi chú

- Service sử dụng UUID cho tất cả ID
- Hỗ trợ transaction để đảm bảo tính nhất quán dữ liệu
- Có thể mở rộng để tích hợp với các service khác (User, Project)
- Hỗ trợ CORS cho frontend integration
