# MiCTS Keeper v1.2 — Chế độ an toàn HyperOS

**Thay đổi quan trọng:** v1.0/v1.1 tự ghi `Settings.Secure.assistant=MiCTS`, khiến MiCTS có thể không hoạt động sau reboot vì không kết nối được với Google VoiceInteractionService. **v1.2 loại bỏ hoàn toàn thao tác ghi này**, ngay cả khi bạn đã bật kiểm tra tự động trong bản cũ.

MiCTS (không root, VIS trigger) yêu cầu **Google** làm Digital Assistant mặc định: https://github.com/parallelcc/MiCTS/blob/main/README_en.md

## Hoạt động
- Tự kiểm tra sau khởi động ở khoảng **15 / 45 / 90 giây**; không có tác vụ định kỳ.
- Chỉ đọc `Settings.Secure.assistant` và `voice_interaction_service`; không sửa các cài đặt này.
- Có nút mở trang Digital Assistant, chạy thử MiCTS và kiểm tra nhanh.
- Không cần cấp `WRITE_SECURE_SETTINGS` qua ADB cho Keeper nữa.

## Cách sửa MiCTS lỗi sau khi reboot
1. Mở Keeper → **Mở cài đặt Digital Assistant**.
2. Đặt mặc định **Google**. Nếu hiện Google mà MiCTS vẫn lỗi, đổi **Google → Không có → Google** để hệ thống kết nối lại.
3. Thử **Mở MiCTS thử**, hoặc thêm **MiCTS Quick Settings tile** từ bảng cài đặt nhanh.
4. Bật tự khởi động và bỏ giới hạn pin cho Google và MiCTS.

**Giới hạn:** Chế độ Google mặc định giúp MiCTS hoạt động theo yêu cầu VIS, nhưng không bảo đảm nhấn giữ nút nguồn sẽ mở MiCTS thay Gemini. Việc dùng nút nguồn làm trigger cố định trên HyperOS ROM Trung Quốc có thể cần hook hệ thống/root; không thể cam kết bằng ứng dụng thường.

## Nâng cấp
Cài APK v1.2 đè v1.1 (cùng `applicationId` và khóa debug trong cùng workflow). Nếu lỗi `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, cần gỡ bản cũ trước, sau đó cài bản mới. Sau cập nhật, các tác vụ tự ghi vào cài đặt trợ lý của v1.1 không còn được thực thi. Bạn có thể gỡ quyền cũ (nếu còn) bằng ADB:

```bat
adb shell pm revoke com.ldd.mictskeeper android.permission.WRITE_SECURE_SETTINGS
```

Nếu ROM không cho revoke, không cần can thiệp thêm vì v1.2 không dùng quyền đó.
