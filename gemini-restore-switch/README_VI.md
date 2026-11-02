# Gemini Home Restore v4.9 — thử nghiệm UI Assistant (không root)

Bản này khắc phục cách làm **không có tác dụng trong v4.8**: thay vì chỉ ghi `Settings.Secure.assistant` / `voice_interaction_service`, phiên bản mới dùng dịch vụ Accessibility do người dùng chủ động bật để **thao tác thật trong Settings → Default apps → Digital assistant app → None → Google**. Android có RoleManager; ghi secure settings không chắc tạo sự kiện cập nhật role/voice-interaction như đổi qua UI.

## Trình tự

- Sau boot (nếu màn hình đã mở khóa): khôi phục Home trước, sau đó yêu cầu Accessibility mở Default apps để chọn None rồi Google.
- Nếu còn khóa màn hình: chờ tới khi người dùng mở khóa.
- Tiếp tục thử theo mốc khoảng 15/45/85 giây sau sự kiện khởi động; chỉ sau mỗi lượt Home thành công mới yêu cầu đổi trợ lý.
- Dịch vụ tự động chỉ hoạt động khi **đã cấp Accessibility**, có thể bị HyperOS hạn chế; ứng dụng có nút thử thao tác bằng giao diện.
- Nếu cấu hình Google không phải trợ lý được chọn, không chủ động ghi đè MiCTS hoặc None.
- Không dùng root, microphone, Internet, nhận diện màn hình bằng ảnh hoặc tọa độ bấm cố định. Accessibility chỉ tìm nhãn trong màn hình Settings khi có một yêu cầu làm mới. Tuy nhiên quyền Accessibility có phạm vi truy cập dữ liệu màn hình rất rộng, chỉ nên cấp khi bạn hiểu và tin tưởng ứng dụng.

## Cách cài thử (Xiaomi 13 Ultra, HyperOS 3 ROM China)

1. Tải artifact `Gemini-Home-Restore-UI-v4.9-APK` từ GitHub Actions rồi giải nén. Nếu dùng bản APK mà ChatGPT cung cấp, đưa `.apk` vào thư mục platform-tools.
2. Cài APK bằng ADB (vì targetSdk 22 là API cũ, bị Android hạn chế):

```bat
adb install --bypass-low-target-sdk-block -r Gemini-Home-Restore-v4.9.apk
adb shell settings put system long_press_home_key launch_google_search
```

3. Bật Gemini làm trợ lý bên trong ứng dụng Google và chọn Google trong Settings → Default digital assistant app.
4. Trên điện thoại mở **Gemini Home Restore (Legacy Test)**; bật cả hai công tắc **Tự khôi phục Home** và **Tự đổi None → Google thật sau mỗi lượt Home**.
5. Chọn nút **Bật quyền Trợ năng (Accessibility)** và bật dịch vụ **Gemini Home: làm mới trợ lý mặc định**. Nếu Android hiện "Restricted setting", mở App info → ⋮ → Allow restricted settings (nếu ROM cung cấp). Chỉ cấp cho app bạn tin tưởng. Bật Tự khởi động, đặt pin Không hạn chế trong HyperOS.
6. Bấm **Thử đổi None → Google bằng Cài đặt**. Quan sát máy có mở trang trợ lý, chọn None rồi Google thật hay không. Kiểm tra lại bằng cách giữ Home.
7. Chỉ khi bước 6 thành công mới nên thử reboot và theo dõi tự động sau mở khóa.

## Lưu ý

- **Chưa xác thực trên máy Xiaomi 13 Ultra HyperOS 3 thực tế**. Khả năng tự bấm tùy thuộc nhãn/phân cấp UI và sự cho phép Accessibility/background launch của HyperOS; máy có thể yêu cầu thao tác xác nhận.
- Nếu dịch vụ Accessibility bị HyperOS tắt sau reboot, không có cách đáng tin cậy để một APK thường âm thầm tự bật lại quyền đó. Một lần bật lần đầu là bắt buộc.
- Nếu thao tác đã chọn None nhưng chưa đưa được về Google, vào Settings → Default digital assistant app để chọn Google thủ công và xem kết quả trạng thái trong APK. Ứng dụng không ép thoát màn hình Settings khi lỗi, để bạn có thể sửa ngay.
- Bản này dùng targetSdk 22 để thử tương thích ghi cài đặt Home; mức bảo vệ Android thấp hơn ứng dụng hiện đại. Chỉ sử dụng cho kiểm thử cá nhân.
- Bản v4.4 ở package khác không bị ghi đè; v4.9 dùng package `com.ldd.geminirestore.legacy`.
