# Gemini Assist Handoff (không root, không trợ năng)

Đây là **ứng dụng thật**, nhưng **không phải bản đã giải được câu đánh thức Xiao AI**.

- Không có service, receiver, vòng lặp hay tác vụ nền.
- Không yêu cầu root, Accessibility, quyền micro, đọc màn hình, overlay hay đặc quyền ngân hàng.
- Khi Android gửi `ACTION_ASSIST` cho app, app mở `com.google.android.apps.bard` và tự kết thúc.
- Nút **Thử mở Gemini** có thể dùng để kiểm tra thao tác mở ứng dụng.
- **Cài đặt trợ lý mặc định** mở Settings để chọn app nếu HyperOS cho phép.

## Giới hạn quan trọng

Bảng nổi nghe của Xiao AI do app Xiaomi hiển thị. Không có API Android công khai cho app không có quyền đặc biệt và không chạy nền để nhận biết thời điểm một cửa sổ app khác xuất hiện. Nếu Xiaomi bắt hotword và gọi thẳng `com.miui.voiceassist`, app này **không được gọi**. Cũng không thể ép dừng cửa sổ Xiao AI bằng API thường. Do đó không thể cam kết `小爱同学` -> Gemini ngay trên HyperOS 3.

## Kiểm tra hành vi trợ lý Android (tùy chọn)

```bat
adb shell am start -a android.intent.action.ASSIST -n com.ldd.assisthandoff/.AssistHandoffActivity
```

Lệnh chỉ thực hiện một lần, không bật dịch vụ nền. Nếu chọn ứng dụng làm trợ lý mặc định, thao tác phím/góc mà Android route tới ACTION_ASSIST có thể gọi Gemini. Việc chọn ứng dụng khác làm trợ lý mặc định có thể ảnh hưởng thao tác gọi Gemini bằng nút nguồn trước đó; dễ khôi phục bằng Settings.

## Mã nguồn
Thư mục riêng `assist-handoff`, không thay đổi dự án Accessibility cũ. APK debug được build bằng GitHub Actions.
