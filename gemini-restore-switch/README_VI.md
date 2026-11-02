# Gemini Home Restore v4.4 — chỉ dành cho nút Home

Ứng dụng dành cho Xiaomi 13 Ultra (HyperOS 3 ROM Trung Quốc) và các máy đã xác nhận lệnh ADB dưới đây có tác dụng. Giữ nguyên package **com.ldd.geminirestore** để có thể nâng cấp.

## Chức năng còn lại

- Bật/tắt tự khôi phục nhấn giữ **Home → Gemini**.
- Kiểm tra giá trị `Settings.System.long_press_home_key`. Nếu đã là `launch_google_search`, không ghi lại.
- Thử sửa ngay và sau BOOT_COMPLETED; kiểm tra thêm tại 15, 45, 85 giây (dừng nếu đã quá 90 giây). Thời điểm thực tế phụ thuộc hạn chế chạy nền của HyperOS.
- Hiển thị trạng thái, mở màn hình cấp `WRITE_SETTINGS`, và sao chép lệnh ADB dự phòng.

## Đã bỏ

Không còn nút chuyển Gemini/MiCTS, đổi trợ lý mặc định, khôi phục nút nguồn, quyền `WRITE_SECURE_SETTINGS`, dữ liệu Google VoiceInteraction và các thao tác liên quan MiCTS. **Không gỡ Xiao AI.**

## Sử dụng

1. Cài bản v4.4, mở ứng dụng, bật **Tự khôi phục Home → Gemini**.
2. Cấp **Sửa cài đặt hệ thống** nếu được yêu cầu.
3. Trong HyperOS, bật **Tự khởi động**, đặt pin thành **Không hạn chế**.
4. Khởi động lại máy và thử nhấn giữ Home. Dùng **Kiểm tra & khôi phục ngay** để xem kết quả.

Nếu HyperOS chặn ứng dụng thường (ví dụ `IllegalArgumentException`), app **không thể bảo đảm tự phục hồi** dù ADB trên máy tính làm được. Lệnh dự phòng:

```bat
adb shell settings get system long_press_home_key
adb shell settings put system long_press_home_key launch_google_search
```

Bản mới được build và đưa lên mục Releases của repository. Nếu APK cũ không cùng chữ ký và báo xung đột, cần gỡ phiên bản cũ trước khi cài (làm mất cài đặt trong app).
