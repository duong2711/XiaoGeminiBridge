# Gemini Restore v4.0 — Chuyển Gemini / MiCTS

Một app `com.ldd.geminirestore` với hai nút chuyển chế độ; không root.

## Giới hạn quan trọng
- MiCTS (không root, VIS) **yêu cầu Google làm trợ lý mặc định**: https://github.com/parallelcc/MiCTS/blob/main/README_en.md
- Vì vậy việc ghi `Settings.Secure.assistant` thành Activity MiCTS chỉ là thử nghiệm cho phím nguồn HyperOS; có thể khiến Circle to Search lỗi. Nếu không được, trở lại Google và kích hoạt MiCTS bằng tile Quick Settings.
- Không thể đổi thiết lập bảo mật trực tiếp từ app nếu HyperOS không cấp `WRITE_SECURE_SETTINGS`; **KHÔNG** khẳng định chỉ cần ADB grant là được. Khi bị từ chối phải dùng lệnh ADB từ máy tính.
- Phiên bản này không ghi `voice_interaction_service` và không sửa `power_button_long_press`.

## Cài đặt
1. Chọn Google làm Digital Assistant và thử Gemini trước khi mở Gemini Restore để ứng dụng lưu thành phần trợ lý Google chính xác.
2. Cài MiCTS.
3. Cài APK Gemini Restore v4.0. Tên package trùng bản cũ, nhưng nếu chữ ký APK khác, bạn phải gỡ bản cũ rồi cài v4.0.
4. Thử cấp quyền (nếu ROM cho phép):
```bat
adb shell pm grant com.ldd.geminirestore android.permission.WRITE_SECURE_SETTINGS
```
5. Chọn Gemini hoặc MiCTS bằng các nút trong app; app chỉ báo thành công nếu đọc lại thấy giá trị thực tế đã đổi.

Nếu không cấp quyền được, mở CMD ở platform-tools và thử:
```bat
adb shell settings put secure assistant com.parallelc.micts/com.parallelc.micts.ui.activity.MainActivity
adb shell settings put secure assistant com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
```
Lệnh Google bên trên là **giá trị phổ biến**, hãy ưu tiên giá trị trợ lý Google mà ứng dụng đã lưu.

## Sau reboot
- Chỉ lên lịch 15 / 45 / 90 giây (best effort qua JobScheduler, không phải mốc chính xác).
- Chế độ Gemini có thể tự khôi phục trong 90 giây nếu app có quyền, tùy chọn bật/tắt.
- Chế độ MiCTS chỉ kiểm tra, không tự ghi sau reboot do lỗi của MiCTS Keeper v1.1.
- Tắt/gỡ MiCTS Keeper cũ nếu nó còn đang tự ép MiCTS.

**Phiên bản thử nghiệm: cần kiểm tra trên Xiaomi HyperOS 3 thật.**
