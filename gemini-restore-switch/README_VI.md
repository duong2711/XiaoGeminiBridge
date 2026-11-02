# Gemini Restore v4.1 — Giữ chế độ Gemini/MiCTS sau khởi động

- Chỉ **một** ứng dụng `com.ldd.geminirestore` để đổi trợ lý. Không cần MiCTS Keeper riêng.
- Khi nhấn **Chuyển sang Gemini** hoặc **Chuyển sang MiCTS**, app lưu chế độ **chỉ sau khi đọc lại hệ thống xác nhận đã đổi thành công**.
- Nếu chuyển bằng lệnh ADB ở CMD bên ngoài, hãy vào app và nhấn **Ghi nhớ chế độ đang sử dụng** để đồng bộ lựa chọn.
- Sau BOOT_COMPLETED, thử giữ chế độ trước đó ở mốc 15 / 45 / 85 giây rồi **dừng trước 90 giây**. Android JobScheduler có thể thực hiện trễ/không chạy nếu ROM giới hạn nền.
- Nếu trợ lý vẫn đang ở lựa chọn đúng, không ghi lại thiết lập.
- Với Gemini: khôi phục Google Assistant component đã lưu nếu cần.
- Với MiCTS: chỉ khôi phục khi Google `voice_interaction_service` hiện diện. Nếu Google VIS không có/kết nối chưa xong, app không cưỡng ép MiCTS; hiển thị cảnh báo để tránh lặp lỗi MiCTS không gọi Circle to Search sau khởi động.

## Cấp quyền một lần (nếu HyperOS cho phép)

```bat
adb shell pm grant com.ldd.geminirestore android.permission.WRITE_SECURE_SETTINGS
```

Mở Cài đặt → Ứng dụng → Gemini Restore, bật **Tự khởi động** và **Không hạn chế pin**.

Nếu ADB `pm grant` bị chặn bởi ROM, app không thể tự thay đổi Secure Settings. Cần chuyển bằng lệnh ADB trong CMD và sau khi khởi động lại không bảo đảm tự khôi phục.

## Giới hạn của MiCTS trên HyperOS

MiCTS VIS không root cần **Google** là trợ lý mặc định để xử lý Circle to Search: https://github.com/parallelcc/MiCTS/blob/main/README_en.md

Ghi `Settings.Secure.assistant` sang Activity MiCTS để nhấn giữ nút nguồn là **workaround không chính thức** và có thể khiến Google VIS không được bind lại khi reboot. Vì vậy, **không thể cam kết MiCTS luôn vừa được gọi bằng nút nguồn vừa chạy Circle to Search sau reboot** chỉ với quyền ADB này. Khi bị lỗi, chuyển Digital Assistant về **Google** và kích hoạt MiCTS qua Quick Settings Tile hoặc app launcher.

## Nâng cấp

- VersionCode 41 / v4.1.0, package vẫn `com.ldd.geminirestore`.
- Cài đè lên v4.0 nếu chữ ký APK trùng nhau. Nếu không, gỡ bản cũ và cài lại, sau đó cấp lại quyền ADB.
- Tắt và gỡ MiCTS Keeper v1.x cũ nếu còn tự sửa trợ lý để tránh giành thiết lập.
