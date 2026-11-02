# Gemini Home Restore — Legacy v4.5 (THỬ NGHIỆM, chưa xác nhận trên máy thật)

> Bản thử nghiệm tách riêng khỏi v4.4. **Không root.** Chỉ hướng đến Xiaomi 13 Ultra / HyperOS 3 ROM Trung Quốc. Mục tiêu: khôi phục `long_press_home_key=launch_google_search` sau khi khởi động lại hoặc mở khóa.
>
> Không bảo đảm thành công trên mọi bản HyperOS. Không có thay đổi nào trong bản v4.4 chính thức.

## Vì sao thử targetSdk 22?

Với targetSdk 33, Android có thể ném `IllegalArgumentException` vì APK không có UID hệ thống/shell khi ghi khóa private `Settings.System`. AOSP có logic tương thích với ứng dụng target API 22 trở xuống cho một số thao tác tương tự; đây là **cách thử nghiệm**, chưa được kiểm chứng trên ROM Trung Quốc này. Việc `WRITE_SECURE_SETTINGS` được ADB cấp cũng không tự động bỏ qua cơ chế kiểm tra UID/target SDK.

**Cảnh báo bảo mật:** Android 15/16 cố tình chặn cài ứng dụng targetSdk 22. Lệnh ADB dưới đây bỏ qua lớp bảo vệ khi cài một APK cũ. Chỉ dùng với mã APK bạn tự biên dịch hoặc tin tưởng, cân nhắc rủi ro và không phân phối rộng rãi. App không yêu cầu Internet, root, Accessibility, microphone hay quyền WRITE_SECURE_SETTINGS.

## Cài thử nghiệm một lần bằng máy tính

1. Mở GitHub Actions ở nhánh `experiment/home-gemini-legacy-autorestore-20261102` và tải artifact **Gemini-Home-Restore-Legacy-v4.5-APK** từ lượt build thành công. Giải nén để lấy `app-debug.apk`.
2. Trên máy tính Windows đã có platform-tools/ADB, bật USB debugging, xác nhận kết nối:

```bat
adb devices
adb install --bypass-low-target-sdk-block -r app-debug.apk
```

3. Trên điện thoại mở **Gemini Home Restore (Legacy Test)**, chọn **Tự khôi phục Home → Gemini**. Cấp quyền **Sửa cài đặt hệ thống**, bật **Tự khởi động** và pin **Không hạn chế**.
4. Nếu báo chưa có quyền, có thể dùng ADB một lần để cấp AppOps (lệnh không root):

```bat
adb shell appops set com.ldd.geminirestore.legacy WRITE_SETTINGS allow
```

5. Chọn Google làm **Digital assistant app**, Google voice input / Speech Recognition & Synthesis from Google nếu có, và trong Google app chọn Gemini làm trợ lý. Giữ chế độ điều hướng bằng 3 phím để dùng nút Home.
6. Đặt lối tắt Home một lần để thử, nếu ROM chưa có tùy chọn hệ thống phù hợp:

```bat
adb shell settings put system long_press_home_key launch_google_search
adb shell settings get system long_press_home_key
```

7. Khởi động lại điện thoại và mở khóa. App nghe BOOT_COMPLETED hoặc USER_UNLOCKED và kiểm tra tức thời + 15/45/85 giây. Để biết app thật sự tự sửa được hay không, sau reboot **chưa mở ứng dụng**, thử giữ Home. Nếu muốn kiểm tra bằng ADB:

```bat
adb shell settings get system long_press_home_key
```

**Nếu Home vẫn không hoạt động:** mở app để xem trạng thái. Nếu xuất hiện `IllegalArgumentException`, `SecurityException` hoặc `WRITE_SETTINGS` bị chặn thì bản ROM không cho phép đường ghi này; không thể tuyên bố đã có tự phục hồi rootless. Shizuku không chạy tự động qua reboot ở chế độ không root. Nếu người dùng thực hiện *khôi phục cài đặt gốc* (factory reset), mọi APK bên thứ ba và tùy chọn trong ứng dụng đều bị xóa.

## Phạm vi

Chỉ xử lý nhấn giữ Home; không chuyển Gemini/MiCTS, không thay nút nguồn, không gỡ Xiao AI. Package thử nghiệm là `com.ldd.geminirestore.legacy`, có thể cùng tồn tại với v4.4. Sau khi kiểm nghiệm nên quay lại app target SDK hiện đại nếu không cần cách thử này.
