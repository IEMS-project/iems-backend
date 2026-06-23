# API Xóa Hàng Loạt (Batch Delete)

## Endpoint
```
POST /api/batch-delete
```

## Mô tả
API này cho phép xóa nhiều file và folder cùng lúc. Nếu một số item xóa thất bại, API vẫn tiếp tục xóa các item còn lại và trả về kết quả chi tiết.

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
  ]
}
```

**Lưu ý:**
- `fileIds` và `folderIds` đều là optional (có thể null hoặc empty array)
- Có thể chỉ gửi `fileIds` hoặc chỉ `folderIds` hoặc cả hai
- Folder sẽ được xóa đệ quy (bao gồm tất cả file và subfolder bên trong)

## Response
```json
{
  "code": 200,
  "message": "Batch delete completed",
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
| `totalRequested` | int | Tổng số item yêu cầu xóa |
| `successCount` | int | Số item xóa thành công |
| `failureCount` | int | Số item xóa thất bại |
| `successfulFileIds` | UUID[] | Danh sách ID các file xóa thành công |
| `successfulFolderIds` | UUID[] | Danh sách ID các folder xóa thành công |
| `failedItems` | FailedItem[] | Danh sách các item xóa thất bại và lý do |

### FailedItem Object
```json
{
  "id": "uuid",
  "type": "file" | "folder",
  "reason": "Error message"
}
```

## Quyền truy cập
Yêu cầu quyền: `DOC_DELETE`

## Ví dụ sử dụng

### Xóa chỉ files
```bash
curl -X POST http://localhost:8080/api/batch-delete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "fileIds": ["file-id-1", "file-id-2", "file-id-3"]
  }'
```

### Xóa chỉ folders
```bash
curl -X POST http://localhost:8080/api/batch-delete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "folderIds": ["folder-id-1", "folder-id-2"]
  }'
```

### Xóa cả files và folders
```bash
curl -X POST http://localhost:8080/api/batch-delete \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "fileIds": ["file-id-1", "file-id-2"],
    "folderIds": ["folder-id-1"]
  }'
```

## Lưu ý quan trọng

1. **Quyền sở hữu**: Chỉ có thể xóa file/folder mà bạn là chủ sở hữu
2. **Xóa đệ quy**: Khi xóa folder, tất cả file và subfolder bên trong cũng sẽ bị xóa
3. **Không rollback**: Nếu một số item xóa thất bại, các item đã xóa thành công sẽ KHÔNG được rollback
4. **Xóa khỏi storage**: File cũng sẽ bị xóa khỏi MinIO storage, không chỉ database

## Error Codes

| Code | Message | Description |
|------|---------|-------------|
| `FILE_NOT_FOUND` | File not found | File không tồn tại |
| `FOLDER_NOT_FOUND` | Folder not found | Folder không tồn tại |
| `PERMISSION_DENIED` | Permission denied | Không có quyền xóa item này |
