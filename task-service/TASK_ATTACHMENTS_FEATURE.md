# Task Attachments Feature

## Tổng quan

Tính năng mới cho phép đính kèm file vào task khi tạo hoặc cập nhật task. File sẽ được upload lên Document Service và URL được lưu vào database.

## Các thay đổi

### 1. Entity mới

- **TaskAttachment**: Entity lưu thông tin file đính kèm
  - `id`: UUID (Primary Key)
  - `taskId`: UUID (Foreign Key to tasks)
  - `fileName`: Tên file
  - `fileUrl`: URL công khai của file
  - `fileType`: Loại file (MIME type)
  - `uploadedAt`: Thời gian upload
  - `uploadedBy`: UUID người upload

### 2. Repository mới

- **TaskAttachmentRepository**: Repository để quản lý task attachments

### 3. Feign Client mới

- **DocumentServiceFeignClient**: Feign client để gọi Document Service upload files

### 4. DTOs mới

- **TaskAttachmentDto**: DTO trả về thông tin attachment
- **SimpleFileResponse**: DTO nhận response từ Document Service

### 5. Service updates

- **TaskService.createTask(CreateTaskDto, MultipartFile[])**: Tạo task với files
- **TaskService.updateTask(UUID, UpdateTaskDto, MultipartFile[])**: Cập nhật task với files
- **uploadTaskAttachments()**: Helper method upload files và lưu vào DB

### 6. Controller updates

- **POST /tasks**: Nhận multipart/form-data với `task` (JSON) và `files` (files)
- **PATCH /tasks/{id}**: Nhận multipart/form-data với `task` (JSON) và `files` (files)

### 7. Configuration

- Thêm Feign Form encoder để hỗ trợ multipart upload
- Dependencies: feign-form và feign-form-spring

## Cách sử dụng

### Tạo task với file đính kèm

```bash
curl -X POST "http://localhost:8084/tasks" \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -F "task={\"projectId\":\"...\",\"title\":\"Task with files\",\"assignedTo\":\"...\",\"priority\":\"HIGH\",\"taskType\":\"TASK\",\"dueDate\":\"2025-12-31\"};type=application/json" \\
  -F "files=@/path/to/file1.pdf" \\
  -F "files=@/path/to/file2.png"
```

### Cập nhật task với file đính kèm

```bash
curl -X PATCH "http://localhost:8084/tasks/{taskId}" \\
  -H "Authorization: Bearer YOUR_TOKEN" \\
  -F "task={\"title\":\"Updated task\",\"description\":\"New description\"};type=application/json" \\
  -F "files=@/path/to/file3.docx"
```

### Response format

TaskResponseDto giờ sẽ bao gồm field `attachments`:

```json
{
  "id": "...",
  "title": "Task with files",
  "description": "...",
  "attachments": [
    {
      "id": "...",
      "fileName": "file1.pdf",
      "fileUrl": "https://storage.example.com/files/...",
      "fileType": "application/pdf",
      "uploadedAt": "2025-12-06T10:00:00",
      "uploadedBy": "..."
    }
  ],
  ...
}
```

## Database Migration

Chạy script SQL để tạo bảng `task_attachments`:

```sql
-- Xem file: database-migration-attachments.sql
```

## Dependencies

Thêm vào `pom.xml`:

```xml
<dependency>
    <groupId>io.github.openfeign.form</groupId>
    <artifactId>feign-form</artifactId>
    <version>3.8.0</version>
</dependency>
<dependency>
    <groupId>io.github.openfeign.form</groupId>
    <artifactId>feign-form-spring</artifactId>
    <version>3.8.0</version>
</dependency>
```

## Lưu ý

1. Files được upload thông qua Document Service endpoint `/api/files/upload-batch`
2. Nếu upload files thất bại, task vẫn được tạo/cập nhật thành công (không fail toàn bộ operation)
3. Files parameter là optional - có thể tạo/cập nhật task không cần files
4. Khi update task với files mới, các file cũ không bị xóa - chỉ thêm file mới
5. Attachments được tự động load khi query task thông qua `convertToResponseDto()`
