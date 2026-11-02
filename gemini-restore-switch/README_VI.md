# Gemini Home Restore v5.0 — bản thử nghiệm không Trợ năng

Dựa trên việc kiểm tra mã bên trong file **Gemini_Restore_Auto_V3(1).apk** do người dùng cung cấp. Bản gốc v3 dùng quyền **WRITE_SECURE_SETTINGS** được cấp một lần bằng ADB; không dùng Accessibility. Trình tự v3 gồm xóa tạm `assistant` + `voice_interaction_service`, chờ khoảng 1500 ms, ghi lại Google Voice Interaction Service cụ thể, chờ khoảng 1000 ms và kiểm tra; v3 còn đặt `Settings.Global.power_button_long_press=5` cho **nút nguồn**.

Bản v5.0 này chỉ khôi phục **giữ Home**, không động vào nút nguồn. Sau **mỗi** lượt Home thành công, nó thử trình tự ngắt/bật Google tương đương v3, thay thế bản v4.9 dùng Accessibility. Không còn khai báo hoặc dịch vụ Trợ năng.

## Quyền và cài đặt
- App `com.ldd.geminirestore.legacy` riêng, không ghi đè bản v3 có package `com.ldd.geminirestore`.
- Android target SDK 22 cũ để thử ghi `long_press_home_key`; Android mới cần lệnh bỏ chặn cài SDK cũ, chỉ nên cài APK tự biên dịch/tin cậy:

```bat
adb install --bypass-low-target-sdk-block -r Gemini-Home-Restore-v5.0.apk
adb shell pm grant com.ldd.geminirestore.legacy android.permission.WRITE_SECURE_SETTINGS
adb shell settings put system long_press_home_key launch_google_search
```

- Chọn Google trong Default digital assistant app, bật cả hai công tắc và Tự khởi động/Không hạn chế pin của HyperOS.
- Nút **Thử ngắt → bật Google theo cách v3** thực hiện thao tác ngay để kiểm nghiệm. Nút **Khôi phục Home → làm mới Google ngay** chạy đủ chuỗi.
- Sau khởi động, ứng dụng có thể nhận `BOOT_COMPLETED` hoặc `USER_UNLOCKED` và lên lịch kiểm tra vào khoảng 15, 45, 85 giây trong tối đa 90 giây. JobScheduler có thể trễ và ROM có thể chặn nền.

## Cảnh báo
- **Chưa được kiểm nghiệm thành công trên Xiaomi 13 Ultra HyperOS 3 thực tế**; thao tác ghi Secure Settings có thể không tạo đủ side effect của thao tác None → Google trong giao diện.
- Quyền `WRITE_SECURE_SETTINGS` là quyền mạnh; chỉ cấp cho APK tin tưởng. `targetSdk 22` hạ mức bảo vệ tương thích Android.
- App không tự thay MiCTS nếu đang chọn MiCTS. Khi phát hiện lỗi, ứng dụng thử khôi phục cài đặt trước đó; nếu Google bị đặt về None, vào Settings để chọn Google thủ công.
- Bản v3 gốc điều khiển `power_button_long_press` (nút nguồn), **không phải nút Home**. Việc áp dụng cơ chế dịch vụ trợ lý sang Home là giả thuyết cần thử thực tế.

Không root, không Accessibility, không quyền mic, không mạng.
