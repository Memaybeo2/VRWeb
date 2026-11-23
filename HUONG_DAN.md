# VR Box Web Mirror – Hướng dẫn cho người mới bắt đầu

Ứng dụng Android (nhắm tới **Android 16 / API 36**, chạy được từ Android 8.0 trở lên) cho phép **lướt web ở chế độ chia đôi màn hình (side-by-side)** để đặt vào kính **VR Box**. Mắt trái và mắt phải hiển thị **cùng một hình** (mirror).

File APK được **GitHub Actions tự build** – bạn **không cần cài Android Studio**.

---

## 0. Ứng dụng làm được gì?

| Tính năng | Mô tả |
|---|---|
| Lướt web | Nhập địa chỉ hoặc từ khoá, giống một trình duyệt đơn giản |
| Chế độ VR | Chia đôi màn hình: trái = trang web thật, phải = bản sao theo thời gian thực |
| Con trỏ theo đầu | Xoay đầu để di chuyển con trỏ (dùng cảm biến xoay của điện thoại) |
| Nhìn để bấm | Nhìn yên một điểm ~1,3 giây = bấm (có vòng xanh đếm giờ) |
| Cuộn trang | Nhìn vào nút "Lên"/"Xuống" để cuộn liên tục, hoặc dùng phím âm lượng |
| Thanh nút VR | Lùi, Tiến, Tải lại, Home, Chữ -, Chữ +, Lên, Xuống, Giữa, Thoát |
| Chỉnh lề | Chỉnh lề ngoài/trong/trên-dưới cho vừa kính của bạn |
| Desktop mode | Bật giao diện web máy tính |
| Chế độ chuột | Dùng chuột qua hub OTG, tắt con trỏ theo đầu / nhìn để bấm / cuộn bằng nhìn |

---

## 1. Chuẩn bị

1. Một **tài khoản GitHub miễn phí**: https://github.com/signup
2. Máy tính (Windows/Mac/Linux) để tải và đưa code lên GitHub.
3. Điện thoại Android (khuyến nghị Android 16) và kính VR Box.

---

## 2. Giải nén dự án

Giải nén file `VRBoxWebMirror.zip`. Bạn sẽ có thư mục `VRBoxWebMirror` với cấu trúc:

```
VRBoxWebMirror/
├── .github/workflows/build-apk.yml      <- "công thức" để GitHub tự build APK
├── .gitignore
├── HUONG_DAN.md                         <- file bạn đang đọc
├── settings.gradle.kts                  <- khai báo dự án
├── build.gradle.kts                     <- phiên bản plugin Android/Kotlin
├── gradle.properties                    <- cấu hình Gradle
└── app/
    ├── build.gradle.kts                 <- cấu hình app (Android 16, tên gói...)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml          <- khai báo quyền Internet, màn hình chính
        ├── java/com/vrbox/webmirror/
        │   ├── MainActivity.kt          <- TOÀN BỘ logic của app
        │   └── GazeCursorView.kt        <- vẽ con trỏ "nhìn để bấm"
        └── res/
            ├── layout/activity_main.xml <- bố cục màn hình (thanh URL, 2 mắt, cài đặt)
            ├── values/strings.xml       <- tên app, chữ gợi ý
            ├── values/colors.xml
            ├── values/themes.xml        <- giao diện + kiểu nút
            ├── drawable/ic_launcher_foreground.xml   <- hình biểu tượng
            └── mipmap-anydpi-v26/ic_launcher*.xml    <- biểu tượng app
```

> **Lưu ý:** thư mục `.github` bắt đầu bằng dấu chấm nên có thể bị **ẩn**.
> - Windows: Explorer → View → bật **Hidden items**.
> - Mac: nhấn **Cmd + Shift + .** (dấu chấm) trong Finder.

---

## 3. Tạo repository (kho chứa code) trên GitHub

1. Đăng nhập GitHub → bấm dấu **+** góc trên bên phải → **New repository**.
2. **Repository name:** `VRBoxWebMirror`.
3. Chọn **Public** (hoặc Private đều được).
4. **Không** tick "Add a README file".
5. Bấm **Create repository**.

---

## 4. Đưa code lên GitHub

### Cách A – Upload bằng trình duyệt (dễ nhất)

1. Ở trang repository vừa tạo, bấm liên kết **uploading an existing file**.
2. Mở thư mục `VRBoxWebMirror` trên máy, **chọn TẤT CẢ file và thư mục bên trong** (bao gồm cả `.github`, `app`, `.gitignore`...) rồi **kéo thả** vào trang GitHub.
   - Dùng trình duyệt Chrome/Edge/Firefox để kéo thả được cả thư mục.
3. Chờ tải xong. Kiểm tra danh sách bên dưới có dòng `.github/workflows/build-apk.yml` và `app/...`.
4. Kéo xuống, bấm **Commit changes**.

### Cách B – Dùng Git (nếu bạn đã cài Git)

Mở Terminal / Command Prompt **trong thư mục `VRBoxWebMirror`**:

```bash
git init
git add .
git commit -m "VR Box Web Mirror lan dau"
git branch -M main
git remote add origin https://github.com/TEN_CUA_BAN/VRBoxWebMirror.git
git push -u origin main
```

(Thay `TEN_CUA_BAN` bằng tên tài khoản GitHub của bạn.)

---

## 5. Xem GitHub tự build APK

1. Vào tab **Actions** của repository.
2. Bạn sẽ thấy workflow **Build VR Box Web Mirror APK** đang chạy (biểu tượng tròn vàng).
   - Nếu không thấy gì: chọn workflow ở cột trái → **Run workflow** → **Run workflow**.
   - Nếu GitHub hỏi "I understand my workflows, go ahead and enable them" → bấm đồng ý.
3. Chờ khoảng **3–8 phút** (lần đầu lâu hơn). Khi có dấu **tích xanh ✓** là build thành công.

---

## 6. Tải file APK

1. Trong tab **Actions**, bấm vào lần chạy (run) có dấu tích xanh.
2. Kéo xuống cuối trang, mục **Artifacts**.
3. Bấm **VRBoxWebMirror-APK** để tải về (file `.zip`).
4. **Giải nén** file zip → bạn có `VRBoxWebMirror.apk`.

> Muốn có link tải cố định? Trong tab **Releases** hoặc dùng lệnh: `git tag v1.0` rồi `git push origin v1.0`. Workflow sẽ tự tạo **Release** kèm file APK.

---

## 7. Cài APK lên điện thoại

1. Chép `VRBoxWebMirror.apk` vào điện thoại (cáp USB, Zalo, Google Drive...).
2. Mở file APK trên điện thoại.
3. Nếu máy báo chặn cài từ nguồn không rõ: bấm **Cài đặt** → bật **Cho phép từ nguồn này** → quay lại bấm **Cài đặt**.
4. Nếu **Play Protect** cảnh báo: chọn **Vẫn cài đặt** (đây là app do bạn tự build).
5. Mở app **VR Box Web Mirror**.

---

## 8. Cách dùng app

### 8.1. Chế độ thường (cầm điện thoại như bình thường)
- Gõ địa chỉ (ví dụ `youtube.com`) hoặc từ khoá vào ô trên cùng → bấm **Đi**.
- **Cài đặt:** chỉnh lề, độ nhạy, thời gian nhìn để bấm, Desktop mode.
- Mọi việc cần **gõ chữ** (nhập URL, đăng nhập) hãy làm ở chế độ này **trước khi** vào VR.

### 8.2. Chế độ VR
1. Mở trang web bạn muốn xem.
2. Bấm nút **VR**. Màn hình tự xoay ngang và chia đôi.
3. Đặt điện thoại vào kính VR Box. **Sau 5 giây** con trỏ tự căn giữa theo hướng bạn đang nhìn.
4. **Xoay đầu** để di chuyển con trỏ. **Nhìn yên** vào một liên kết/nút → vòng xanh chạy đầy → bấm.
5. Nhìn vào nút **Lên / Xuống** (thanh dưới) để cuộn trang liên tục.
6. Nút **Giữa** để căn lại con trỏ khi nó bị lệch. Nút **Thoát** để quay về chế độ thường.

### 8.3. Phím điều khiển (tay cầm / phím vật lý)

| Phím | Tác dụng trong chế độ VR |
|---|---|
| Âm lượng + / D-pad lên | Cuộn lên |
| Âm lượng − / D-pad xuống | Cuộn xuống |
| Nút A / Enter / D-pad giữa | Bấm tại vị trí con trỏ |
| Nút B | Căn giữa lại con trỏ |
| Back | Quay lại trang trước, hết trang thì thoát VR |

### 8.4. Dùng chuột (cắm qua hub OTG) – tắt cảm biến đầu
1. Cắm hub/OTG có chuột vào điện thoại.
2. Mở **Cài đặt** → tick **Chế độ chuột**. Tuỳ chọn này tự **tắt**:
   - Con trỏ theo đầu (cảm biến xoay)
   - Nhìn để bấm
   - Cuộn bằng cách nhìn vào nút Lên/Xuống
3. Bấm **Xong** rồi bấm **VR**. Trong VR app **khoá chuột** và tự vẽ con trỏ (hiện ở cả hai mắt):

| Thao tác chuột | Tác dụng |
|---|---|
| Di chuột | Di chuyển con trỏ (giữ nút trái và di chuột để kéo) |
| Chuột trái | Bấm / chạm |
| Lăn chuột | Cuộn trang |
| Chuột phải | Quay lại trang trước |

Muốn dùng lại cảm biến đầu: thoát VR, vào **Cài đặt**, bỏ tick **Chế độ chuột**.
Ở chế độ thường (không VR) chuột hoạt động như bình thường.

### 8.5. Chỉnh cho vừa kính
Vào **Cài đặt** (chế độ thường):
- **Lề ngoài / Lề trong / Lề trên-dưới:** thu nhỏ vùng hiển thị mỗi mắt cho nằm gọn trong thấu kính. Thử vài lần cho đến khi nhìn rõ cả hai mắt, không bị méo/cắt mép.
- **Độ nhạy xoay đầu:** cao = con trỏ chạy nhanh hơn.
- **Thời gian nhìn để bấm:** tăng nếu bị bấm nhầm.

---

## 9. Tuỳ chỉnh nhanh

| Muốn đổi | Sửa ở đâu |
|---|---|
| Trang chủ mặc định | `MainActivity.kt` → dòng `HOME_URL = "https://www.google.com"` |
| Tên hiển thị của app | `res/values/strings.xml` → `app_name` |
| Tên gói (applicationId) | `app/build.gradle.kts` → `applicationId` và `namespace` |
| Số phiên bản | `app/build.gradle.kts` → `versionCode`, `versionName` |
| Màu nền biểu tượng | `res/values/colors.xml` → `ic_launcher_background` |

Sau khi sửa, **commit/push** lại lên GitHub, Actions sẽ tự build APK mới.

---

## 10. Xử lý sự cố

**Build lỗi (dấu ✗ đỏ trong Actions)**
1. Bấm vào lần chạy lỗi → bấm bước có dấu ✗ (thường là *Build APK (debug)*).
2. Đọc dòng có chữ `error:` hoặc `e: file:///...`. Sao chép đoạn lỗi đó gửi cho người hỗ trợ.
3. Nguyên nhân thường gặp: thiếu file khi upload (nhất là thư mục `.github` hoặc `app`), hoặc upload sai cấp thư mục. Ở trang chủ repository phải thấy ngay `app/`, `build.gradle.kts`, `settings.gradle.kts`.

**Không thấy tab Actions chạy**
- Kiểm tra file nằm đúng `.github/workflows/build-apk.yml`.
- Dùng **Run workflow** để chạy thủ công.

**Con trỏ không di chuyển trong VR**
- Máy có thể không có cảm biến xoay (hiếm). Dùng tay cầm/bàn phím Bluetooth (mục 8.3).
- Bấm nút **Giữa** để căn lại.

**Con trỏ trôi dần**
- Cảm biến "game rotation" có thể trôi nhẹ. Bấm **Giữa** (hoặc nút B) mỗi vài phút.

**Mắt phải bị đen / không giống mắt trái**
- Một số video có bảo vệ bản quyền (DRM) hoặc một số máy cho kết quả đen ở mắt phải. Thử trang khác hoặc tắt/bật lại chế độ VR.

**Gõ chữ trong VR**
- Bàn phím ảo của Android **không** được sao chép sang mắt phải. Hãy gõ trước ở chế độ thường, hoặc dùng bàn phím Bluetooth.

**Video toàn màn hình**
- Bấm nút toàn màn hình của trình phát. Video sẽ nằm trong khung mắt trái và được sao chép sang mắt phải.

---

## 11. Giới hạn hiện tại (nên biết)

- App tạo hiệu ứng **hai mắt giống nhau (2D mirror)**, không phải 3D lập thể.
- Hình mắt phải là bản sao ~30 khung hình/giây nên có thể trễ nhẹ so với mắt trái.
- APK này là bản **debug** (đã tự ký bằng khoá debug) – cài được trực tiếp trên điện thoại, nhưng chưa phù hợp để đưa lên Google Play.
- Chưa có méo thấu kính (barrel distortion). Nếu kính của bạn cần, hãy dùng chỉnh lề để bù.

Chúc bạn lướt web VR vui vẻ!
