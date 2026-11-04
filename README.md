# Reclaim

Ứng dụng tự kiềm chế: khóa ứng dụng theo lịch và hạn chế đa phương tiện (dừng trình phát, âm lượng về 0, tùy chọn chuyển màn hình sang thang độ xám).
Android 11+, Kotlin, không thư viện ngoài, không phát hành lên Play Store. Package `com.minhnhat.reclaim`.

Thiết kế chi tiết: [docs/phuong-an-ky-thuat.md](docs/phuong-an-ky-thuat.md).

## Build

1. Mở thư mục `Reclaim` bằng Android Studio.
2. Dự án dùng cùng bộ công cụ với MNLocker (AGP `9.4.0`, compileSdk 36, Kotlin tích hợp sẵn trong AGP).
   Nếu bạn dùng AGP khác, sửa `agp` trong `gradle/libs.versions.toml`.
3. Gói này **không có Gradle wrapper**. Copy thư mục `gradle/wrapper/` cùng `gradlew`, `gradlew.bat`
   từ dự án MNLocker sang (hoặc để Android Studio tự cấu hình Gradle).
4. Chạy `app`. Bản release ký bằng khóa debug để cài thẳng.
5. Unit test lõi lịch (tùy chọn): `./gradlew :app:testDebugUnitTest`.

## Cấp quyền (vào tab Cài đặt trong app)

Làm theo thứ tự:

1. **Android 13+:** `Cài đặt → Ứng dụng → Reclaim → ⋮ → Cho phép cài đặt bị hạn chế`. Nếu không, công tắc Trợ năng và Truy cập thông báo sẽ bị mờ.
2. **Trợ năng** (bắt buộc): khóa ứng dụng nhanh + chặn phím âm lượng.
3. **Truy cập thông báo** (bắt buộc cho đa phương tiện): để dừng trình phát.
4. Khuyến nghị: Thông báo, Bỏ tối ưu pin, và bật Tự khởi động nếu ROM có (Xiaomi, Oppo, Samsung...).
5. Chế độ dự phòng (khi Trợ năng bị tắt): Quyền xem mức sử dụng + Hiển thị trên ứng dụng khác.
6. Tùy chọn: Quản trị thiết bị (chặn gỡ), Chống tắt/gỡ Reclaim, mã PIN.
7. Thang độ xám (tùy chọn): cấp **Ghi cài đặt bảo mật** một lần bằng ADB (tab Cài đặt có hộp thoại hướng dẫn kèm lệnh sao chép được):
   `adb shell pm grant com.minhnhat.reclaim android.permission.WRITE_SECURE_SETTINGS`.
   Xiaomi/Oppo cần bật thêm "Gỡ lỗi USB (Cài đặt bảo mật)". Quyền mất khi gỡ cài đặt. Nếu màn hình kẹt đen trắng,
   tắt ở `Cài đặt → Trợ năng → Chỉnh màu`.

## Cấu trúc mã nguồn

```
app/src/main/java/com/minhnhat/reclaim/
  domain/     Lịch, cấu hình, PolicyEvaluator (thuần Kotlin, có test)
  data/       Lưu cấu hình, PIN, danh sách app
  platform/   Compat (mọi nhánh theo phiên bản Android), quyền, thông báo
  blocking/   Dịch vụ Trợ năng, overlay, dự phòng UsageStats, chống tắt
  media/      MediaGuard, GrayscaleController, NotificationListener
  runtime/    GuardService, báo thức, receiver
  ui/         MainActivity, các màn hình, hộp thoại, trình chỉnh lịch
```

Bỏ hỗ trợ Android cũ: chỉ sửa `platform/Compat.kt` và `platform/Permissions.kt`, rồi nâng `minSdk`.

## Danh sách kiểm thử gợi ý

- [ ] Khóa một app 24/7: mở app thì overlay hiện ngay; Back/Home vẫn dùng được.
- [ ] Hẹn giờ mở khóa: trong khung giờ mở được; đúng giờ kết thúc, overlay tự hiện dù đang ở trong app.
- [ ] Ngày không tích: khóa cả ngày. Không tích ngày nào: khóa 24/7.
- [ ] Tắt Trợ năng: thông báo chuyển "Chế độ dự phòng", overlay vẫn hiện (chậm hơn một chút).
- [ ] Media: phát YouTube/Spotify/Chrome trong giờ hạn chế → bị dừng, âm lượng về 0; cắm/rút tai nghe, kết nối Bluetooth vẫn về 0.
- [ ] Thang độ xám: bật tùy chọn (đã cấp quyền ADB) → vào giờ hạn chế màn hình chuyển đen trắng; hết giờ trở lại màu.
- [ ] Thang độ xám: đang dùng "Chỉnh màu" kiểu khác (vd. deuteranomaly) → sau giờ hạn chế được trả đúng kiểu cũ.
- [ ] Thang độ xám: tự tắt Chỉnh màu trong giờ hạn chế (Cài đặt nhanh) → tự bật lại. Chưa cấp quyền → hiện banner nhắc.
- [ ] Bấm phím tăng âm lượng trong giờ hạn chế: không tăng. Trong cuộc gọi: chỉnh được.
- [ ] Khởi động lại máy, đổi giờ/múi giờ: lịch vẫn đúng.
- [ ] Bật Chống tắt/gỡ + PIN: vào màn Trợ năng của hệ thống thấy bị che; "Tạm dừng 5 phút" cho phép thao tác.
