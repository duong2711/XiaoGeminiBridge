# Home → Gemini Auto Restore v6.0

Mục tiêu duy nhất: sau mỗi lần **khởi động lại máy** (không phải khôi phục cài đặt gốc), tự thử khôi phục khóa hệ thống:

```bat
adb shell settings put system long_press_home_key launch_google_search
```

APK chỉ ghi `Settings.System.long_press_home_key=launch_google_search` khi giá trị chưa đúng. Kiểm tra ngay lúc nhận `BOOT_COMPLETED` hoặc `USER_UNLOCKED`, sau đó thử lại khoảng 15/45/85 giây trong cửa sổ tối đa 90 giây. Nếu đã đúng, không ghi lại.

**Không thay đổi Google Digital assistant**, không chuyển None → Google, không đụng nút nguồn/MiCTS, không có Accessibility, Shizuku, micro, Internet hoặc root.

## Cài thử nghiệm

Package riêng: `com.ldd.homegeminirestore`, không đụng tới bản v3 hay bản Legacy cũ.

Tải artifact `Gemini-Home-Only-v6.0-APK` từ GitHub Actions của nhánh `feature/home-only-boot-restore-20261102`, giải nén để lấy APK. Trong thư mục platform-tools:

```bat
adb devices
adb install --bypass-low-target-sdk-block -r app-debug.apk
adb shell appops set com.ldd.homegeminirestore WRITE_SETTINGS allow
adb shell settings put system long_press_home_key launch_google_search
```

Mở APK → bật **Tự khôi phục Home khi khởi động**, bật Autostart và Không hạn chế pin trong HyperOS. Để thử, khởi động lại máy, chưa thao tác ADB, thử nhấn giữ Home rồi xem trạng thái trong APK.

## Giới hạn quan trọng

Android/HyperOS có thể chặn một APK thông thường ghi khóa hệ thống riêng `long_press_home_key`, dù ADB shell đặt được. Bản thử nghiệm dùng `targetSdk 22` để thử tính tương thích; Android mới chặn cài theo mặc định (cần `--bypass-low-target-sdk-block`), giảm lớp bảo vệ tương thích/bảo mật. Không cam kết tự phục hồi thành công nếu ROM từ chối ghi. Bản này chưa thử trên Xiaomi 13 Ultra/HyperOS 3 thực tế.

Quyền sửa cài đặt hệ thống phải được cấp thủ công, và HyperOS cần cho phép Auto-start. Nếu bạn **khôi phục cài đặt gốc (factory reset)**, ứng dụng bị xóa và không thể tự phục hồi.
