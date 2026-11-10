# Xiao AI → Gemini (Shizuku, không root, không Trợ năng)

**Prototype – chưa thử trên Xiaomi 13 Ultra HyperOS 3.**

### Nguyên lý

- Người dùng chủ động bật foreground service.
- Khi màn hình đang mở, Shizuku (ADB UID 2000) cho phép đọc `dumpsys window windows` định kỳ ~800 ms.
- Nếu tìm được cửa sổ nổi của `com.miui.voiceassist` / các gói Xiaomi tương ứng thì gửi **KEYCODE_BACK** ngay, sau đó chạy `am start -a android.intent.action.ASSIST` để gọi trợ lý Android mặc định (hy vọng Gemini).
- Nếu lệnh ASSIST thất bại thì thử mở ứng dụng Gemini `com.google.android.apps.bard` trực tiếp.
- Có chống lặp 6,5 giây, ngừng quét khi màn hình tắt, dừng hoàn toàn nếu Shizuku mất quyền.
- Không root, không Accessibility, không micro riêng hay ghi hình. Không tắt Xiaomi VoiceTrigger, không chỉnh sửa tài khoản và không đụng dữ liệu ứng dụng ngân hàng.

### Điều kiện sử dụng

1. Cài Shizuku từ https://shizuku.rikka.app/ và khởi chạy bằng **Wireless debugging/ADB**, không dùng root.
2. Cài APK này và chọn **Cấp quyền Shizuku** (chỉ cấp nếu bạn tin tưởng mã nguồn).
3. Đặt Gemini làm trợ lý mặc định Android trong Settings.
4. Bật **Theo dõi Xiao AI**. Nhấn **Dừng theo dõi ngay** để dừng.
5. Sau khi khởi động lại phải kích hoạt lại Shizuku bằng Wireless debugging trước khi sử dụng; giới hạn của Shizuku không root.

### Hạn chế nghiêm trọng

- Chưa xác minh Xiaomi VoiceAssist hiển thị đủ tín hiệu qua WindowManager; biểu tượng Xiao AI có thể không được phát hiện.
- Đọc dump mỗi ~800ms có thể gây hao pin, dù không nghe micro và tạm ngừng khi màn hình tắt.
- Shizuku có quyền ADB shell rộng, chỉ cấp cho APK tự kiểm chứng nguồn. Việc không bật Accessibility **không bảo đảm** VNeID/ngân hàng tương thích.
- Khi phát hiện sai, thao tác Back có thể đóng ứng dụng đang mở; chỉ thử trên màn hình chính, không bật khi đang thao tác ngân hàng.
- Hệ thống có thể chặn giao diện Gemini hoặc yêu cầu mở khóa màn hình; không cam kết “ngay lập tức” trong mọi trường hợp.
- Không có cơ chế bí mật tự bật lại quyền, không force-stop Xiaomi, không xóa dữ liệu.

Nguồn API: https://github.com/RikkaApps/Shizuku-API

Bản này là nhánh thử nghiệm riêng; không được hợp nhất vào phiên bản ổn định.
