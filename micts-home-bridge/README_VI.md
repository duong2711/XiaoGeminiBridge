# MiCTS Home Bridge v1.0 — thử nghiệm dành cho Xiaomi HyperOS

**Mục tiêu:** giữ phím Home (không phải nhấn Home một lần) để **mở ứng dụng MiCTS y hệt bấm biểu tượng app**, không tự đổi None ↔ Google, không dùng nút nguồn, không cần root/Accessibility/Shizuku.

## Cách thực hiện

- APK đăng ký một activity nhận `android.intent.action.ASSIST`, yêu cầu người dùng chủ động chọn APK làm **Default digital assistant app** qua RoleManager.
- Khi hệ thống gửi ACTION_ASSIST (nếu HyperOS có dùng assistant role cho giữ Home), Activity sẽ lấy launcher intent của `com.parallelc.micts` và mở MiCTS.
- APK thử duy trì `Settings.System.long_press_home_key=launch_google_search` sau boot; đây là **lệnh gọi trợ lý**, không phải `launch_micts`. Nếu Xiaomi hardcode vào Google thay vì gọi Assistant role, cầu nối sẽ không nhận được sự kiện.
- Tất cả thay đổi có thể hoàn tác bằng cách chọn lại Google làm Digital assistant.

## Thử trên máy

1. Cài APK, mở, bấm nút **Thử mở ứng dụng MiCTS**; nếu không mở, MiCTS chưa được cài/không có launcher activity phù hợp.
2. Bấm **Chọn trợ lý cho thao tác giữ Home**, chọn MiCTS Home Bridge nếu HyperOS cung cấp.
3. Đảm bảo cài đặt nhấn giữ Home → trợ lý đã được bật. Có thể cần ADB một lần:
```bat
adb shell settings put system long_press_home_key launch_google_search
```
4. Nếu muốn ứng dụng thử khôi phục cài đặt Home sau reboot, cấp quyền **Sửa cài đặt hệ thống**, bật Autostart, pin Không hạn chế. Một số ROM không cho APK ghi khóa private này; nếu vậy bản thử nghiệm không tự khôi phục được.
5. Nhấn giữ Home để kiểm tra.

## Hạn chế rất quan trọng

MiCTS hoạt động không root (VIS) **thường yêu cầu Google làm default digital assistant**. Nếu chuyển default assistant sang MiCTS Home Bridge, Android có thể ngắt Google Voice Interaction Service làm MiCTS **mở được nhưng hiện Trigger failed**. Không thể vừa bảo đảm VIS Google vừa gán Assistant role cho cầu nối bằng API Android thông thường. Khi có lỗi hãy chọn lại **Google** trong Default digital assistant app để phục hồi.

HyperOS 3 có thể hardcode `launch_google_search` mở Google thay vì Android assistant role; cũng có thể không liệt kê bridge trong danh sách Digital assistant. **APK biên dịch thành công không đồng nghĩa đã giải quyết được giữ Home thực tế.** Vì thế đây là **bản thử nghiệm riêng**, không sửa ứng dụng Home → Gemini v6.0.

Không dùng Accessibility, không đọc màn hình, không sử dụng Internet hay microphone. Package `com.ldd.mictshomebridge`.
