# API Di Chuyển Hàng Loạt (Batch Move)

## Endpoint
```
POST /api/batch-move
```

## Mô tả
API này cho phép di chuyển nhiều file và folder cùng lúc đến một folder đích. Nếu một số item di chuyển thất bại, API vẫn tiếp tục di chuyển các item còn lại và trả về kết quả chi tiết.

## Request Body
```json
{
  "fileIds": [
    "uuid-file-1",
    "uuid-file-2"
  ],
  "folderIds": [
    "uuid-folder-1",
    "uuid-folder-2"
  ],
  "destinationFolderId": "uuid-destination-folder"
}
```

**Lưu ý:**
- `fileIds` và `folderIds` đều là optional (có thể null hoặc empty array)
- `destinationFolderId` có thể là null (nghĩa là di chuyển về root)
- Có thể chỉ gửi `fileIds` hoặc chỉ `folderIds` hoặc cả hai

## Response
```json
{
  "code": 200,
  "message": "Batch move completed",
  "data": {
    "totalRequested": 4,
    "successCount": 3,
    "failureCount": 1,
    "successfulFileIds": [
      "uuid-file-1",
      "uuid-file-2"
    ],
    "successfulFolderIds": [
      "uuid-folder-1"
    ],
    "failedItems": [
      {
        "id": "uuid-folder-2",
        "type": "folder",
        "reason": "Permission denied"
      }
    ]
  }
}
```

## Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `totalRequested` | int | Tổng số item yêu cầu di chuyển |
| `successCount` | int | Số item di chuyển thành công |
| `failureCount` | int | Số item di chuyển thất bại |
| `successfulFileIds` | UUID[] | Danh sách ID các file di chuyển thành công |
| `successfulFolderIds` | UUID[] | Danh sách ID các folder di chuyển thành công |
| `failedItems` | FailedItem[] | Danh sách các item di chuyển thất bại và lý do |

### FailedItem Object
```json
{
  "id": "uuid",
  "type": "file" | "folder",
  "reason": "Error message"
}
```

## Quyền truy cập
Yêu cầu quyền: `DOC_UPDATE`

## Ví dụ sử dụng

### Di chuyển files đến folder đích
```bash
curl -X POST http://localhost:8080/api/batch-move \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "fileIds": ["file-id-1", "file-id-2"],
    "destinationFolderId": "destination-folder-id"
  }'
```

### Di chuyển folders đến folder đích
```bash
curl -X POST http://localhost:8080/api/batch-move \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "folderIds": ["folder-id-1", "folder-id-2"],
    "destinationFolderId": "destination-folder-id"
  }'
```

### Di chuyển cả files và folders đến root
```bash
curl -X POST http://localhost:8080/api/batch-move \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "fileIds": ["file-id-1"],
    "folderIds": ["folder-id-1"],
    "destinationFolderId": null
  }'
```

### Di chuyển cả files và folders
```bash
curl -X POST http://localhost:8080/api/batch-move \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "fileIds": ["file-id-1", "file-id-2"],
    "folderIds": ["folder-id-1"],
    "destinationFolderId": "destination-folder-id"
  }'
```

## Lưu ý quan trọng

1. **Quyền sở hữu**: Chỉ có thể di chuyển file/folder mà bạn là chủ sở hữu
2. **Quyền đích**: Bạn phải có quyền ghi (write permission) vào folder đích
3. **Kiểm tra vòng lặp**: Hệ thống tự động kiểm tra và ngăn chặn việc di chuyển folder vào chính nó hoặc các subfolder của nó (circular reference)
4. **Không rollback**: Nếu một số item di chuyển thất bại, các item đã di chuyển thành công sẽ KHÔNG được rollback
5. **Di chuyển về root**: Để di chuyển item về root, set `destinationFolderId` = `null`

## Error Codes

| Code | Message | Description |
|------|---------|-------------|
| `FILE_NOT_FOUND` | File not found | File không tồn tại |
| `FOLDER_NOT_FOUND` | Folder not found | Folder không tồn tại |
| `PERMISSION_DENIED` | Permission denied | Không có quyền di chuyển item này hoặc không có quyền ghi vào folder đích |
| `INVALID_REQUEST` | Invalid request | Di chuyển folder sẽ tạo circular reference |
| `WRITE_PERMISSION_REQUIRED` | Bạn chỉ có quyền xem. Cần quyền chỉnh sửa để thực hiện thao tác này | Không có quyền ghi vào folder đích |

## Use Cases

### 1. Tổ chức lại file
Di chuyển nhiều file vào folder "Đã hoàn thành" sau khi xong việc:
```json
{
  "fileIds": ["doc1-id", "doc2-id", "doc3-id"],
  "destinationFolderId": "completed-folder-id"
}
```

### 2. Sắp xếp lại cấu trúc thư mục
Di chuyển nhiều folder con vào folder cha mới:
```json
{
  "folderIds": ["project-a-id", "project-b-id"],
  "destinationFolderId": "2024-projects-id"
}
```

### 3. Dọn dẹp workspace
Di chuyển cả file và folder không cần thiết về root hoặc folder lưu trữ:
```json
{
  "fileIds": ["old-doc-id"],
  "folderIds": ["old-project-id"],
  "destinationFolderId": "archive-folder-id"
}
```
