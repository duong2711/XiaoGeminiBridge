# MiCTS Keeper v1.1 — HyperOS (không root)

Đây là ứng dụng bổ trợ, **không sửa APK MiCTS gốc**. App thử đặt MiCTS thành trợ lý bằng `Settings.Secure.assistant` sau khi HyperOS hoàn tất khởi động.

## Các lần kiểm tra

Sau khi Android gửi `BOOT_COMPLETED`, Keeper lên lịch **3 lần** kiểm tra tại mốc khoảng **15 giây, 45 giây và 90 giây**. **Không kiểm tra định kỳ** và không lên lịch thêm sau lần cuối. Khi nâng cấp từ v1.0, ứng dụng hủy tác vụ cũ 3/6/10 phút và tác vụ 15 phút/lần.

**Quan trọng:** Android `JobScheduler` không bảo đảm thực hiện chính xác đến từng giây, nhất là với Xiaomi HyperOS. App có kiểm tra giới hạn 90 giây để không **ghi thay đổi** sau cửa sổ này; lần kiểm tra cuối có thể bị bỏ qua nếu Android trì hoãn. Mốc thời gian được tính khi nhận broadcast hoàn tất khởi động hoặc khi người dùng chủ động nhấn bật lại; không phải chính xác từ lúc bấm nút nguồn.

## Cài đặt

1. Cài MiCTS gốc trước, rồi cài APK Keeper.
2. Kết nối ADB và cấp quyền cho Keeper bằng lệnh:

```bat
adb shell pm grant com.ldd.mictskeeper android.permission.WRITE_SECURE_SETTINGS
```

3. Mở Keeper, chọn **Bật tự khôi phục sau khi khởi động**.
4. Trong HyperOS bật **Tự khởi động** và **Không hạn chế pin** cho Keeper.
5. Khởi động lại, thử nhấn giữ nút nguồn sau khi máy ổn định.

Tên package của Keeper: `com.ldd.mictskeeper`. MiCTS phải có package `com.parallelc.micts` và activity `com.parallelc.micts.ui.activity.MainActivity`.

## Hạn chế

- Không root không đồng nghĩa có quyền can thiệp vô điều kiện: ROM có thể từ chối/ghi đè `Settings.Secure.assistant`.
- Một số ROM xử lý phím nguồn trực tiếp và bỏ qua giá trị `assistant`.
- Việc cấp quyền phải được thực hiện sau khi cài lần đầu.
- APK debug là bản thử nghiệm. Kiểm tra trạng thái ngay trong giao diện.
