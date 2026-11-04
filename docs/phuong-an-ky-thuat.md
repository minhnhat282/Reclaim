# Reclaim: phương án kỹ thuật (bản 2, khớp với mã nguồn)

Android 11+ (minSdk 30), Kotlin, không phát hành lên Play Store, không dùng thư viện ngoài.
Package: `com.minhnhat.reclaim`. Màu nhấn xanh lá, tự đổi theo chế độ sáng/tối.

## 1. Thay đổi so với bản 1

| Nội dung | Bản 1 | Bản 2 (đã cài đặt) | Lý do |
|---|---|---|---|
| UI | Compose | View thuần dựng bằng code | Cùng bộ công cụ với MNLocker (AGP 9.4), tránh lệch phiên bản plugin Compose; overlay vốn đã là View thuần |
| Overlay | `GONE` ↔ `VISIBLE` | Tạo sẵn một lần, đổi `alpha` + `FLAG_NOT_TOUCHABLE` | Học từ MNLocker, nhanh hơn vì không tạo lại cửa sổ |
| Về Home khi khóa | `GLOBAL_ACTION_HOME` | Chỉ che overlay | Không giật màn hình; Back/Home/Recents vẫn dùng bình thường |
| Phát hiện foreground | Sự kiện `packageName` | Cửa sổ `isActive` + map `windowId → package`, dọn map theo `windows` thực tế | Event có thể đến từ IME/SystemUI/popup; map hết rò rỉ windowId bị tái dùng |
| Mốc lịch của khóa app | AlarmManager | `postDelayed` trong tiến trình + đánh giá lại khi `SCREEN_ON`, `USER_PRESENT`, đổi giờ/múi giờ | Chỉ app foreground mới quan trọng; màn hình bật thì CPU không ngủ |
| Audio focus | Tùy chọn | **Bỏ** | Giữ focus có thể làm hỏng cuộc gọi VoIP (Zalo, Messenger) |
| Lịch | nhiều khung/ngày | 1 khung/ngày, tích chọn từng ngày, bước 15 phút | Khớp mẫu giao diện bạn gửi |
| Chặn phím âm lượng | tùy chọn | Mặc định bật, không chặn khi đang có cuộc gọi | `onKeyEvent` chặn từ gốc; vẫn giữ broadcast + kẹp lại làm lớp dự phòng |
| `GuardService` | chỉ khi mất Trợ năng | Chạy liên tục khi có tính năng nào đang cấu hình | Android 12+ chặn khởi chạy foreground service từ nền, nên không thể đợi tới lúc mất Trợ năng mới bật |
| Báo thức chính xác | `USE_EXACT_ALARM` | `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` (maxSdk 32), hạ xuống báo thức gần đúng nếu bị từ chối | `USE_EXACT_ALARM` chỉ có từ Android 13 |

## 2. Kiến trúc

```
com.minhnhat.reclaim
├─ ReclaimApp, AppGraph        Bộ dựng phụ thuộc thủ công; mọi thành phần dùng chung tạo một lần
├─ domain/                     Thuần Kotlin, không chạm Android, có unit test
│    Schedule (DayWindow, WeeklySchedule), Config, PolicyEvaluator
├─ data/                       ConfigStore (RAM + SharedPreferences/JSON), PinManager, VolumeMemory, InstalledApps
├─ platform/                   Compat (MỌI nhánh theo phiên bản Android), Permissions, Notifications
├─ blocking/                   LockAccessibilityService, OverlayController, ForegroundTracker,
│                              UsageStatsBlocker (dự phòng), TamperGuard
├─ media/                      MediaGuard, ReclaimNotificationListener
├─ runtime/                    GuardService, GuardController, Scheduler, SystemEventsReceiver, ServiceState
└─ ui/                         MainActivity, 3 màn hình (Screen), ScheduleEditor/Sheet, Dialogs, Ui.kt
```

Dòng phụ thuộc: `ui, blocking, media, runtime → data, platform → domain`. `domain` không phụ thuộc gì.

**Bỏ hỗ trợ Android cũ:** mọi `Build.VERSION.SDK_INT` chỉ nằm ở `platform/Compat.kt` (và `Permissions.kt` cho phần quyền), mỗi nhánh có chú thích "bỏ khi minSdk >= N". Nâng `minSdk` chỉ cần xóa nhánh cũ ở đó. `targetSdk` hiện là 30 (như MNLocker); các nhánh cho target cao hơn vẫn có sẵn nên nâng bằng một dòng trong `build.gradle.kts`.

**Mô hình lịch:** mỗi ngày một `DayWindow(enabled, startMin, endMin)`, `0 <= start < end <= 1440`. Đây là lịch MỞ KHÓA; ngày không tích hoặc không có ngày nào được tích = khóa cả ngày. Không có giờ qua đêm vì phần bù (đêm) mặc định là khóa.

**Nguyên tắc:** hot path luôn tự tính theo `LocalDateTime.now()` qua `PolicyEvaluator`, không dựa vào cờ do timer đặt. Timer/alarm chỉ dùng để chủ động hành động đúng lúc, nên trễ hay mất cũng không làm sai logic.

## 3. Khóa ứng dụng

**Đường chính (Trợ năng):** `ForegroundTracker` xác định app foreground → `PolicyEvaluator.isAppLocked` → `OverlayController` hiện/ẩn. Overlay là cửa sổ `TYPE_ACCESSIBILITY_OVERLAY` tạo sẵn khi service connect (không cần `SYSTEM_ALERT_WINDOW`); chỉ có mầm cây và dòng "Ứng dụng này đang bị khóa". Khi app khóa đang foreground và có lịch, `postDelayed` hẹn đúng mốc đổi trạng thái.

**Dự phòng (Trợ năng tắt):** `GuardService` thấy `accessibilityConnected == false` thì bật `UsageStatsBlocker`: hỏi `UsageStatsManager.queryEvents` mỗi 250 ms (chỉ khi màn hình bật) lấy `ACTIVITY_RESUMED`, và dùng cùng `OverlayController` với `TYPE_APPLICATION_OVERLAY`. Cần Usage access + Hiển thị trên ứng dụng khác. Chậm hơn khoảng 100-300 ms; thông báo thường trực báo rõ "Chế độ dự phòng".

**Chống tự vượt rào (tùy chọn):** ở `com.android.settings` và trình gỡ cài đặt, bật động `TYPE_WINDOW_CONTENT_CHANGED` (giới hạn 300 ms) và che nếu màn hình nhắc tên/package của Reclaim. Có thể tạm dừng 5 phút (chỉ lưu RAM). Mã PIN tùy chọn chặn mở Reclaim; Device Admin chặn gỡ cài đặt.

## 4. Hạn chế đa phương tiện

Trong giờ hạn chế, `MediaGuard` (chạy được cả khi không có service nào, ví dụ từ báo thức):

1. **Dừng trình phát:** `MediaSessionManager.getActiveSessions` (cần Truy cập thông báo) + `MediaController.Callback`: `PLAYING`/`BUFFERING` thì `pause()`, 500 ms sau vẫn phát thì `stop()`. Không can thiệp khi `AudioManager.mode != MODE_NORMAL` (đang gọi).
2. **`AudioPlaybackCallback`:** có âm thanh phát thì kẹp âm lượng và quét lại phiên.
3. **Âm lượng về 0:** `setStreamVolume(STREAM_MUSIC, min)` trên thiết bị đầu ra đang dùng (Android 11+ nhớ âm lượng riêng theo từng thiết bị). Kẹp lại khi `AudioDeviceCallback` báo đổi thiết bị, khi `VOLUME_CHANGED_ACTION` báo tăng âm, và thử lại sau 0,3 s / 1,2 s cho Bluetooth absolute volume.
4. **Chặn phím âm lượng:** `onKeyEvent` của dịch vụ Trợ năng nuốt `VOLUME_UP/DOWN` (không chặn khi có cuộc gọi; có công tắc tắt).
5. **Khôi phục âm lượng:** mặc định giữ nguyên ở 0; tùy chọn đưa về mức cũ theo từng thiết bị. Android chỉ đặt được âm lượng của thiết bị đang phát nên mức của thiết bị chưa kết nối được giữ lại và trả khi thiết bị đó kết nối (ngoài giờ hạn chế).
6. **Thang độ xám (tùy chọn `media.grayscale`):** `PolicyEvaluator.isGrayscaleActive` = tùy chọn + công tắc tổng + đang trong giờ hạn chế (dùng chung lịch, báo thức, tick, boot với phần media). `MediaGuard.evaluate()` gọi `GrayscaleController.apply`. Không có API công khai nên ghi `Settings.Secure` `accessibility_display_daltonizer(_enabled)` ở chế độ đơn sắc (0), cần `WRITE_SECURE_SETTINGS` cấp bằng ADB. Controller lưu giá trị chỉnh màu cũ (SharedPreferences) và chỉ khôi phục khi chính Reclaim đã đổi; nếu người dùng vốn đã bật thang độ xám thì không đụng vào. `ContentObserver` trên hai khóa kéo lại nếu người dùng tự tắt trong giờ hạn chế. Rủi ro: gỡ app hoặc thu hồi quyền lúc đang đen trắng thì màn hình kẹt cho tới khi tắt thủ công ở Trợ năng → Chỉnh màu; ROM có thể chặn (Xiaomi cần Gỡ lỗi USB (Cài đặt bảo mật)).

**Lịch:** `Scheduler` đặt báo thức chính xác tại mốc đổi trạng thái kế tiếp; `SystemEventsReceiver` xử lý báo thức, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` rồi đánh giá lại và đặt báo thức tiếp theo.

**Hạn chế còn lại:** app không có MediaSession (một số game/WebView) chỉ còn lớp âm lượng; phần hình vẫn có thể chạy. Chrome/WebView chỉ tạo MediaSession sau vài giây đầu phát nên có thể lọt tiếng ngắn trước khi bị dừng.

## 5. Giao diện

Ba tab: **Hạn chế ứng dụng** (tìm kiếm, mỗi dòng có công tắc + biểu tượng đồng hồ mở bottom sheet lịch), **Hạn chế đa phương tiện** (công tắc tổng, trạng thái hiện tại, lịch tuần), **Cài đặt** (quyền, tùy chọn, PIN). Lịch tuần theo mẫu: ô tích + tên ngày + giờ bắt đầu → giờ kết thúc, chọn giờ bằng hộp danh sách tự cuộn tới giờ đang chọn.

## 6. Rủi ro cần kiểm thử trên máy thật

- OEM kill tiến trình (Xiaomi, Oppo, Samsung, Vivo); bật autostart và bỏ tối ưu pin.
- Độ trễ overlay đường chính vs dự phòng; overlay bị hệ thống gỡ.
- PiP, cửa sổ nổi, chia đôi màn hình chưa được che.
- `VOLUME_CHANGED_ACTION` là action không chính thức, có thể khác nhau theo ROM (đã có lớp chặn phím và quét theo sự kiện thiết bị làm dự phòng).
- Android 13+: phải cho phép "cài đặt bị hạn chế" trước khi bật Trợ năng và Truy cập thông báo.
- Chưa được biên dịch hay chạy thử trên máy thật tại thời điểm viết; xem README.
